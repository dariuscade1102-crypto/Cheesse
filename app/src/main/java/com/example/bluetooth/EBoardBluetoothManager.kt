package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class EBoardBrand(val displayName: String, val serviceUuidPrefix: String) {
    CHESSNUT("Chessnut (Air / Pro / Evo)", "1b7e8261"),
    MILLENNIUM("Millennium (eONE / Supreme)", "49535343")
}

data class DiscoveredEBoard(
    val name: String,
    val address: String,
    val brand: EBoardBrand,
    val rssi: Int,
    val device: BluetoothDevice? = null
)

sealed interface EBoardConnectionState {
    data object Disconnected : EBoardConnectionState
    data class Scanning(val preferredBrand: EBoardBrand) : EBoardConnectionState
    data class Connecting(val boardName: String) : EBoardConnectionState
    data class Connected(val boardName: String, val brand: EBoardBrand, val batteryPct: Int = 94) : EBoardConnectionState
    data class Error(val reason: String) : EBoardConnectionState
}

class EBoardBluetoothManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<EBoardConnectionState>(EBoardConnectionState.Disconnected)
    val connectionState: StateFlow<EBoardConnectionState> = _connectionState.asStateFlow()

    private val _discoveredBoards = MutableStateFlow<List<DiscoveredEBoard>>(emptyList())
    val discoveredBoards: StateFlow<List<DiscoveredEBoard>> = _discoveredBoards.asStateFlow()

    private val _incomingFenStream = MutableStateFlow<String?>(null)
    val incomingFenStream: StateFlow<String?> = _incomingFenStream.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var isScanning = false

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = dev.name ?: result.scanRecord?.deviceName ?: return
            val lower = name.lowercase()
            val detectedBrand = when {
                lower.contains("chessnut") -> EBoardBrand.CHESSNUT
                lower.contains("millennium") || lower.contains("eone") -> EBoardBrand.MILLENNIUM
                else -> null
            } ?: return

            val entry = DiscoveredEBoard(
                name = name,
                address = dev.address,
                brand = detectedBrand,
                rssi = result.rssi,
                device = dev
            )
            val current = _discoveredBoards.value.toMutableList()
            if (current.none { it.address == entry.address }) {
                current.add(entry)
                _discoveredBoards.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _connectionState.value = EBoardConnectionState.Error("BLE Scan failed (code $errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(preferredBrand: EBoardBrand) {
        if (!hasBlePermissions()) {
            _connectionState.value = EBoardConnectionState.Error("Bluetooth permissions required to scan for ${preferredBrand.displayName}.")
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = EBoardConnectionState.Error("Bluetooth adapter is disabled or unavailable on this device.")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = EBoardConnectionState.Error("BLE Scanner unavailable.")
            return
        }
        stopScan()
        _discoveredBoards.value = emptyList()
        isScanning = true
        _connectionState.value = EBoardConnectionState.Scanning(preferredBrand)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
            isScanning = false
        }
        if (_connectionState.value is EBoardConnectionState.Scanning) {
            _connectionState.value = EBoardConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToBoard(board: DiscoveredEBoard) {
        stopScan()
        val dev = board.device
        if (dev == null || !hasBlePermissions()) {
            _connectionState.value = EBoardConnectionState.Error("Cannot connect: Bluetooth device handle or permission missing.")
            return
        }
        _connectionState.value = EBoardConnectionState.Connecting(board.name)
        activeGatt?.close()
        activeGatt = dev.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = EBoardConnectionState.Connected(board.name, board.brand)
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = EBoardConnectionState.Disconnected
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                for (service in gatt.services) {
                    val uuidStr = service.uuid.toString().lowercase()
                    if (uuidStr.startsWith(board.brand.serviceUuidPrefix)) {
                        for (characteristic in service.characteristics) {
                            gatt.setCharacteristicNotification(characteristic, true)
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val bytes = characteristic.value ?: return
                val text = bytes.decodeToString().trim()
                if (text.contains("/")) {
                    _incomingFenStream.value = text
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {
        }
        activeGatt = null
        _connectionState.value = EBoardConnectionState.Disconnected
    }
}
