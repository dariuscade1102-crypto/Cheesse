package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.engine.ChessMove
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class ChessSoundEffect(val label: String) {
    MOVE("Wooden Move Click"),
    CAPTURE("Wooden Capture Snap")
}

/**
 * Synthesizes and plays low-latency wooden chessboard 'move' and 'capture' audio effects
 * using 16-bit mono PCM waveforms via Android [AudioTrack].
 */
class ChessSoundManager {

    private val sampleRate = 22_050
    private val movePcmBuffer: ShortArray = synthesizeMoveClickPcm()
    private val capturePcmBuffer: ShortArray = synthesizeCaptureSnapPcm()

    @Volatile
    var lastPlayedEffect: ChessSoundEffect? = null
        private set

    @Volatile
    var movePlayCount: Int = 0
        private set

    @Volatile
    var capturePlayCount: Int = 0
        private set

    val totalPlayCount: Int
        get() = movePlayCount + capturePlayCount

    /**
     * Triggers the 'move' wooden tap sound effect when a piece is placed on an empty square.
     */
    fun playMove(enabled: Boolean = true) {
        if (!enabled) return
        lastPlayedEffect = ChessSoundEffect.MOVE
        movePlayCount += 1
        playPcmAsync(movePcmBuffer)
    }

    /**
     * Triggers the 'capture' dual-transient wooden snap sound effect when a piece captures or replaces another piece.
     */
    fun playCapture(enabled: Boolean = true) {
        if (!enabled) return
        lastPlayedEffect = ChessSoundEffect.CAPTURE
        capturePlayCount += 1
        playPcmAsync(capturePcmBuffer)
    }

    /**
     * Inspects [move] and triggers 'capture' if [ChessMove.captured] is non-null, otherwise 'move'.
     */
    fun playForMove(move: ChessMove, enabled: Boolean = true) {
        if (!enabled) return
        if (move.captured != null) {
            playCapture(enabled = true)
        } else {
            playMove(enabled = true)
        }
    }

    fun resetCounters() {
        lastPlayedEffect = null
        movePlayCount = 0
        capturePlayCount = 0
    }

    private fun playPcmAsync(pcmData: ShortArray) {
        try {
            val byteSize = pcmData.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()
        } catch (_: Throwable) {
            // Safe fallback on headless JVM / unit test environments where AudioTrack native hardware is stubbed
        }
    }

    /**
     * Synthesizes a 50ms warm wooden board tap (185 Hz + 370 Hz damped resonance + initial transient click).
     */
    private fun synthesizeMoveClickPcm(): ShortArray {
        val durationSec = 0.050
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * 68.0)
            val clickTransient = if (t < 0.004) sin(2.0 * PI * 1180.0 * t) * 0.45 else 0.0
            val woodBody = sin(2.0 * PI * 185.0 * t) * 0.65 + sin(2.0 * PI * 370.0 * t) * 0.25
            val sample = ((woodBody + clickTransient) * envelope).coerceIn(-1.0, 1.0)
            buffer[i] = (sample * Short.MAX_VALUE * 0.72).toInt().toShort()
        }
        return buffer
    }

    /**
     * Synthesizes a 75ms crisp dual-impact wooden capture snap (initial piece strike + secondary board thud).
     */
    private fun synthesizeCaptureSnapPcm(): ShortArray {
        val durationSec = 0.075
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // First transient at t = 0 (piece-on-piece clack)
            val env1 = exp(-t * 95.0)
            val strike1 = (sin(2.0 * PI * 640.0 * t) * 0.55 + sin(2.0 * PI * 1420.0 * t) * 0.35) * env1

            // Second transient at t = 14ms (captured square settlement thud)
            val t2 = (t - 0.014).coerceAtLeast(0.0)
            val env2 = if (t >= 0.014) exp(-t2 * 58.0) else 0.0
            val strike2 = (sin(2.0 * PI * 235.0 * t2) * 0.70 + sin(2.0 * PI * 490.0 * t2) * 0.30) * env2

            val combined = (strike1 + strike2).coerceIn(-1.0, 1.0)
            buffer[i] = (combined * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return buffer
    }
}
