package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bluetooth.EBoardBluetoothManager
import com.example.data.CheesseLocalDatabase
import com.example.data.CheesseLocalRepository
import com.example.data.FirestoreLeaderboardRepository
import com.example.service.UciEngineService
import com.example.ui.components.AdaptiveFiveIconBottomToolbar
import com.example.ui.components.BoardTransportAndHintToolbar
import com.example.ui.components.ChessBoard
import com.example.ui.components.CoachModeGuidancePanel
import com.example.ui.components.FenPositionImportPanel
import com.example.ui.components.MultiPvEngineLinesPanel
import com.example.ui.components.OpeningExplorerAndDiagnosticsPanel
import com.example.ui.components.PortraitMovesTape
import com.example.ui.screens.BoardEditorScreen
import com.example.ui.screens.CompleteSettingsScreen
import com.example.ui.screens.DeepGameReportScreen
import com.example.ui.screens.LeaderboardsCardSection
import com.example.ui.screens.PlayAndArchiveTrainerScreen
import com.example.ui.screens.SocialChallengesAndEBoardScreen
import com.example.ui.theme.EspressoBackgroundBottom
import com.example.ui.theme.EspressoBackgroundTop
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CheesseViewModel
import com.example.viewmodel.StudioTab
import com.example.viewmodel.WorkbenchPanelTab
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Zero-login launch: opens directly into the Cheesse Analysis Workbench
                CheesseStudioScreen()
            }
        }
    }
}

@Composable
fun CheesseStudioScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val viewModel: CheesseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = CheesseLocalDatabase.getInstance(app)
                CheesseViewModel(
                    repository = CheesseLocalRepository(db.cheesseDao()),
                    cloudLeaderboardRepository = FirestoreLeaderboardRepository(app)
                )
            }
        }
    )

    // Non-blocking silent CredentialManager check for returning authorized Google accounts (zero login wall)
    LaunchedEffect(Unit) {
        try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                viewModel.onGoogleSignInIdTokenReceived(googleIdTokenCredential.idToken)
            }
        } catch (_: Exception) {
            // Silent fallback to pure offline guest Room play
        }
    }

    // Bind to Android UciEngineService component and expose its move evaluation functions to the UI
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val uciBinder = binder as? UciEngineService.UciEngineBinder
                if (uciBinder != null) {
                    viewModel.attachUciService(uciBinder.getService())
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.detachUciService()
            }
        }
        val serviceIntent = Intent(context, UciEngineService::class.java)
        try {
            context.startService(serviceIntent)
            context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
        }

        onDispose {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {
            }
            viewModel.detachUciService()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedGames by viewModel.savedGames.collectAsStateWithLifecycle()
    val openChallenges by viewModel.openChallenges.collectAsStateWithLifecycle()

    val eBoardManager = remember { EBoardBluetoothManager(context.applicationContext) }
    val eBoardState by eBoardManager.connectionState.collectAsStateWithLifecycle()
    val discoveredBoards by eBoardManager.discoveredBoards.collectAsStateWithLifecycle()
    val incomingEBoardFen by eBoardManager.incomingFenStream.collectAsStateWithLifecycle()

    LaunchedEffect(incomingEBoardFen) {
        incomingEBoardFen?.let { fen ->
            viewModel.syncFromExternalFen(fen)
        }
    }

    // Fix 5: Lifecycle observer automatically sends `stop` when MainActivity hits Lifecycle.Event.ON_STOP
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgroundStateChanged(true)
                Lifecycle.Event.ON_START -> viewModel.onAppBackgroundStateChanged(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // BackHandler on secondary tabs returning to Analysis workbench
    BackHandler(enabled = uiState.activeTab != StudioTab.ANALYZE) {
        viewModel.selectTab(StudioTab.ANALYZE)
    }

    val capturedSummary = remember(uiState.position) {
        uiState.position.computeCapturedSummary()
    }

    var showStudioTabBar by remember { mutableStateOf(true) }

    val espressoWoodBrush = remember {
        Brush.verticalGradient(
            colors = listOf(EspressoBackgroundTop, EspressoBackgroundBottom)
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(espressoWoodBrush),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!uiState.fullScreenMode) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // In Portrait mode, anchor the 5-icon control bar (☰, ⇄, ⏪, ⏩, •••) to the bottom edge
                    if (!isLandscape) {
                        AdaptiveFiveIconBottomToolbar(
                            canStepBackward = uiState.currentPlyIndex > 0,
                            canStepForward = uiState.currentPlyIndex < uiState.moveHistory.size,
                            onMenuClicked = { showStudioTabBar = !showStudioTabBar },
                            onFlipBoardClicked = { viewModel.flipBoardManually() },
                            onPreviousPlyClicked = { viewModel.stepPly(-1) },
                            onNextPlyClicked = { viewModel.stepPly(1) },
                            onMoreOptionsClicked = { viewModel.toggleFenImportSheet() }
                        )
                    }

                    if (showStudioTabBar) {
                        NavigationBar(
                            containerColor = SlateSurface.copy(alpha = 0.92f),
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            StudioTab.entries.forEach { tab ->
                                val icon = when (tab) {
                                    StudioTab.ANALYZE -> Icons.Default.Analytics
                                    StudioTab.REVIEW -> Icons.Default.Assessment
                                    StudioTab.PLAY -> Icons.Default.MenuBook
                                    StudioTab.SOCIAL -> Icons.Default.Group
                                    StudioTab.EDITOR -> Icons.Default.Edit
                                    StudioTab.SETTINGS -> Icons.Default.Settings
                                }
                                NavigationBarItem(
                                    selected = uiState.activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(espressoWoodBrush)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minimalist Top Studio Header (Zero Sign-In Gate)
            if (!uiState.fullScreenMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CHEESSE",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MoveBestColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UCI d${uiState.currentSearchDepth} · ${uiState.engineConfig.threads}T · ${uiState.engineConfig.hashMb}MB",
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalCyan
                        )
                    }

                    Text(
                        text = if (uiState.isUciServiceBound) "UCI SERVICE ACTIVE" else "ENGINE READY",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.testTag("uci_service_status_badge")
                    )
                }
            }

            if (uiState.statusBannerMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TacticalCyan.copy(alpha = 0.16f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = uiState.statusBannerMessage!!,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }

            when (uiState.activeTab) {
                StudioTab.ANALYZE -> {
                    if (uiState.showMovesInPortrait) {
                        PortraitMovesTape(
                            moves = uiState.moveHistory,
                            moveStrengths = uiState.moveStrengths,
                            currentPlyIndex = uiState.currentPlyIndex,
                            figurineNotation = uiState.figurineNotation,
                            variationMoveSymbols = uiState.variationMoveSymbols,
                            variations = uiState.variations,
                            onPlyClicked = { viewModel.jumpToPly(it) },
                            onVariationClicked = { branch ->
                                viewModel.syncFromExternalFen(branch.targetFen)
                            }
                        )
                    }

                    // Unified Adaptive Portrait/Landscape ChessBoard Component
                    ChessBoard(
                        position = uiState.position,
                        boardTheme = uiState.boardTheme,
                        boardFlipped = uiState.boardFlipped,
                        boardSizeScale = uiState.boardSizeScale,
                        whitePlayerName = uiState.whitePlayerName,
                        blackPlayerName = uiState.blackPlayerName,
                        showCapturedPieces = uiState.showCapturedPieces,
                        capturedSummary = capturedSummary,
                        selectedSquare = uiState.selectedSquare,
                        legalMovesForSelected = uiState.legalMovesForSelected,
                        showLegalMoveDots = uiState.showLegalMoveDots,
                        lastMove = uiState.lastMove,
                        lastMoveStrength = uiState.lastMoveStrength,
                        colorLastMoveByStrength = uiState.colorLastMoveByStrength,
                        previousBestMove = uiState.previousBestMove,
                        showPreviousBestMoveArrow = uiState.showPreviousBestMoveArrow,
                        multiPvLines = uiState.multiPvLines,
                        showEngineArrows = uiState.showEngineArrows,
                        showEngineLineStrengthColors = uiState.showEngineLineStrengthColors,
                        showAnalysisArrowsNumbers = uiState.showAnalysisArrowsNumbers,
                        threats = uiState.threats,
                        showThreats = uiState.showThreats,
                        progressiveHint = uiState.progressiveHint,
                        drawnArrows = uiState.drawnArrows,
                        highlightedSquares = uiState.highlightedSquares,
                        enlargePieceOnDrag = uiState.enlargePieceOnDrag,
                        disableDragAndDrop = uiState.disableDragAndDrop,
                        drawArrowsOnBoard = uiState.drawArrowsOnBoard,
                        isArrowDrawMode = uiState.isArrowDrawMode,
                        pendingPromotionMove = uiState.pendingPromotionMove,
                        animationDurationMs = uiState.effectiveAnimationDurationMs,
                        engineConfig = uiState.engineConfig,
                        isUciServiceBound = uiState.isUciServiceBound,
                        isEngineRunning = uiState.isEngineRunning,
                        enginePausedByPowerSave = uiState.enginePausedByPowerSave,
                        currentSearchDepth = uiState.currentSearchDepth,
                        nodesPerSec = uiState.nodesPerSec,
                        evalCentipawns = uiState.evalCentipawns,
                        smartEvalBar = uiState.smartEvalBar,
                        engineTelemetryFlow = viewModel.engineTelemetryFlow,
                        isUnassistedRankedSession = uiState.isUnassistedRankedSession,
                        isTaintedByEngine = uiState.isTaintedByEngine,
                        whiteTimeRemainingMs = uiState.whiteTimeRemainingMs,
                        blackTimeRemainingMs = uiState.blackTimeRemainingMs,
                        isClockRunning = uiState.isClockRunning,
                        timedOutSide = uiState.timedOutSide,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        moveHistory = uiState.moveHistory,
                        moveStrengths = uiState.moveStrengths,
                        currentPlyIndex = uiState.currentPlyIndex,
                        figurineNotation = uiState.figurineNotation,
                        variationMoveSymbols = uiState.variationMoveSymbols,
                        variations = uiState.variations,
                        openingExplorer = uiState.openingExplorer,
                        onSquareTapped = { viewModel.onSquareTapped(it) },
                        onPieceDragStarted = { viewModel.onPieceDragStarted(it) },
                        onPieceDragged = { from, to -> viewModel.onPieceDragged(from, to) },
                        onUserArrowDrawn = { from, to -> viewModel.onUserArrowDrawn(from, to) },
                        onToggleArrowDrawMode = { viewModel.toggleArrowDrawMode() },
                        onSelectPromotionPiece = { pieceType -> viewModel.selectPromotionPiece(pieceType) },
                        onDismissPromotion = { viewModel.dismissPromotionOverlay() },
                        onJumpToPly = { ply -> viewModel.jumpToPly(ply) },
                        onUndoMove = { viewModel.undoMove() },
                        onRedoMove = { viewModel.redoMove() },
                        onToggleClockRunning = { viewModel.toggleClockRunning() },
                        onResetClocks = { viewModel.resetMoveClocks() },
                        onVariationClicked = { branch -> viewModel.syncFromExternalFen(branch.targetFen) },
                        onResumeEngine = { viewModel.resumeEngineFromHeader() },
                        onToggleEngineRunning = { viewModel.toggleEngineRunning() },
                        onIncreaseMultiPv = { viewModel.adjustMultiPvCount(1) },
                        onDecreaseMultiPv = { viewModel.adjustMultiPvCount(-1) },
                        onPlayPvMove = { move -> viewModel.executeMoveOnBoard(move) },
                        landscapeBottomBar = {
                            AdaptiveFiveIconBottomToolbar(
                                canStepBackward = uiState.currentPlyIndex > 0,
                                canStepForward = uiState.currentPlyIndex < uiState.moveHistory.size,
                                onMenuClicked = { showStudioTabBar = !showStudioTabBar },
                                onFlipBoardClicked = { viewModel.flipBoardManually() },
                                onPreviousPlyClicked = { viewModel.stepPly(-1) },
                                onNextPlyClicked = { viewModel.stepPly(1) },
                                onMoreOptionsClicked = { viewModel.toggleFenImportSheet() }
                            )
                        }
                    )

                    BoardTransportAndHintToolbar(
                        currentPly = uiState.currentPlyIndex,
                        totalPlies = uiState.moveHistory.size,
                        isAutoplayRunning = uiState.isAutoplayRunning,
                        drawArrowsOnBoard = uiState.isArrowDrawMode || uiState.drawArrowsOnBoard,
                        showDrawingPanel = uiState.showDrawingPanel,
                        activeDrawingColor = uiState.activeDrawingColor,
                        progressiveHint = uiState.progressiveHint,
                        showFenImportSheet = uiState.showFenImportSheet,
                        onStepBackward = { viewModel.stepPly(-1) },
                        onStepForward = { viewModel.stepPly(1) },
                        onToggleAutoplay = { viewModel.toggleAutoplay() },
                        onFlipBoard = { viewModel.flipBoardManually() },
                        onToggleDrawArrows = { viewModel.toggleArrowDrawMode() },
                        onSelectDrawingColor = { c ->
                            viewModel.updateVisualSettings { it.copy(activeDrawingColor = c) }
                        },
                        onClearDrawings = { viewModel.clearBoardDrawings() },
                        onAdvanceHint = { viewModel.advanceProgressiveHint() },
                        onToggleFenImportSheet = { viewModel.toggleFenImportSheet() }
                    )

                    if (uiState.showFenImportSheet) {
                        FenPositionImportPanel(
                            currentBoardFen = uiState.position.toFen(),
                            fenInputText = uiState.fenInputText,
                            fenImportError = uiState.fenImportError,
                            scenarioPresets = uiState.fenScenarioPresets,
                            onFenInputTextChanged = { viewModel.onFenInputTextChanged(it) },
                            onApplyFenImport = { rawFen -> viewModel.importFenPosition(rawFen) },
                            onSelectScenarioPreset = { preset -> viewModel.selectFenScenarioPreset(preset) },
                            onClosePanel = { viewModel.toggleFenImportSheet(false) }
                        )
                    }

                    // Workbench Panel Selector Tabs below the ChessBoard (Engine & Coach | Leaderboard | Opening & Threats)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161E26))
                            .border(1.dp, Color(0xFF263240), RoundedCornerShape(8.dp))
                            .padding(4.dp)
                            .testTag("workbench_panel_tab_bar"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WorkbenchPanelTab.entries.forEach { panelTab ->
                            val isSelected = uiState.activeWorkbenchPanel == panelTab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) TacticalCyan.copy(alpha = 0.22f)
                                        else SlateSurfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) TacticalCyan else Color(0xFF263240),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.selectWorkbenchPanel(panelTab) }
                                    .padding(vertical = 7.dp)
                                    .testTag("workbench_tab_${panelTab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = panelTab.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) TacticalCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    when (uiState.activeWorkbenchPanel) {
                        WorkbenchPanelTab.LEADERBOARD -> {
                            LeaderboardsCardSection(
                                uiState = uiState,
                                onSelectScope = { viewModel.setLeaderboardScope(it) },
                                onSelectSortMetric = { viewModel.setLeaderboardSortMetric(it) },
                                onRecordUserVictory = { viewModel.recordUserGameVictory() },
                                onAddFriend = { viewModel.addFriendToLeaderboard("TacticalFriend_${(10..99).random()}") },
                                onGoogleSignInIdTokenReceived = { idToken ->
                                    viewModel.onGoogleSignInIdTokenReceived(idToken)
                                },
                                onStartRankedSession = { viewModel.startUnassistedRankedSession() },
                                onExitRankedSession = { viewModel.exitUnassistedRankedSession() },
                                onSyncPendingGames = { viewModel.syncPendingCleanGamesToCloud() }
                            )
                        }

                        WorkbenchPanelTab.EXPLORER -> {
                            OpeningExplorerAndDiagnosticsPanel(
                                openingExplorer = uiState.openingExplorer,
                                showThreats = uiState.showThreats,
                                threats = uiState.threats,
                                showKeyElements = uiState.showKeyElements,
                                keyElements = uiState.keyElements,
                                onBookMoveSelected = { from, to ->
                                    val legal = uiState.position.generateLegalMoves().firstOrNull { it.from == from && it.to == to }
                                    if (legal != null) viewModel.executeMoveOnBoard(legal)
                                }
                            )
                        }

                        WorkbenchPanelTab.ENGINE_LINES -> {
                            CoachModeGuidancePanel(
                                coachModeActive = uiState.coachModeActive,
                                userPlayingStyle = uiState.userPlayingStyle,
                                coachSuggestions = uiState.coachSuggestions,
                                progressiveHint = uiState.progressiveHint,
                                figurineNotation = uiState.figurineNotation,
                                onToggleCoachMode = { viewModel.toggleCoachMode(it) },
                                onSelectPlayingStyle = { viewModel.setUserPlayingStyle(it) },
                                onAdvanceHint = { viewModel.advanceProgressiveHint() },
                                onPlaySuggestedMove = { viewModel.executeMoveOnBoard(it) }
                            )

                            MultiPvEngineLinesPanel(
                                engineConfig = uiState.engineConfig,
                                isUciServiceBound = uiState.isUciServiceBound,
                                isEngineRunning = uiState.isEngineRunning,
                                enginePausedByPowerSave = uiState.enginePausedByPowerSave,
                                currentDepth = uiState.currentSearchDepth,
                                nodesPerSec = uiState.nodesPerSec,
                                multiPvLines = uiState.multiPvLines,
                                candidateEvaluations = uiState.candidateMoveEvaluations,
                                inspectedMoveEvaluation = uiState.inspectedMoveEvaluation,
                                lastUciProtocolOutput = uiState.lastUciProtocolOutput,
                                showEngineLineStrengthColors = uiState.showEngineLineStrengthColors,
                                figurineNotation = uiState.figurineNotation,
                                onChangeMultiPvCount = { count ->
                                    viewModel.updateEngineConfig { it.copy(multiPv = count) }
                                },
                                onResumeEngine = { viewModel.resumeEngineFromHeader() },
                                onSendUciCommand = { cmd -> viewModel.sendUciCommandToService(cmd) },
                                onInspectMoveEvaluation = { move -> viewModel.inspectMoveEvaluation(move) },
                                onPlayPvMove = { move -> viewModel.executeMoveOnBoard(move) }
                            )

                            OpeningExplorerAndDiagnosticsPanel(
                                openingExplorer = uiState.openingExplorer,
                                showThreats = uiState.showThreats,
                                threats = uiState.threats,
                                showKeyElements = uiState.showKeyElements,
                                keyElements = uiState.keyElements,
                                onBookMoveSelected = { from, to ->
                                    val legal = uiState.position.generateLegalMoves().firstOrNull { it.from == from && it.to == to }
                                    if (legal != null) viewModel.executeMoveOnBoard(legal)
                                }
                            )
                        }
                    }
                }

                StudioTab.REVIEW -> {
                    DeepGameReportScreen(
                        uiState = uiState,
                        onToggleShowAvgCpl = { checked ->
                            viewModel.updateVisualSettings { it.copy(showAverageCplInReport = checked) }
                        },
                        onPlayWhyBlunderLine = { reviewItem ->
                            viewModel.playWhyBlunderRefutation(reviewItem)
                        },
                        onJumpToPly = { ply -> viewModel.jumpToPly(ply) },
                        onSquareTapped = { viewModel.onSquareTapped(it) },
                        onPieceDragged = { from, to -> viewModel.onPieceDragged(from, to) },
                        onUserArrowDrawn = { from, to -> viewModel.onUserArrowDrawn(from, to) },
                        onSelectPromotionPiece = { viewModel.selectPromotionPiece(it) },
                        onDismissPromotion = { viewModel.dismissPromotionOverlay() }
                    )
                }

                StudioTab.PLAY -> {
                    PlayAndArchiveTrainerScreen(
                        uiState = uiState,
                        cloudGames = savedGames,
                        onToggleVsEnginePlay = { active ->
                            viewModel.updateVisualSettings { it.copy(isVsEnginePlayActive = active) }
                        },
                        onToggleShowEachMoveStrength = { checked ->
                            viewModel.updateVisualSettings { it.copy(showEachMoveStrengthWhilePlaying = checked) }
                        },
                        onTogglePauseOnMistake = { checked ->
                            viewModel.updateVisualSettings { it.copy(pauseOnMistakeWhilePlaying = checked) }
                        },
                        onToggleArchiveSelection = { id -> viewModel.toggleArchiveGameSelection(id) },
                        onLoadArchivePreset = { preset ->
                            viewModel.loadPresetIntoBoard(preset, generateReportNow = true)
                            viewModel.selectTab(StudioTab.ANALYZE)
                        },
                        onAnalyzeMultipleInBackground = { viewModel.analyzeMultipleGamesInBackground() },
                        onStartRetryMistakesMultiGame = { viewModel.startRetryMistakesForMultipleGames() },
                        onNextPuzzle = { viewModel.nextMistakePuzzle() },
                        onAdvanceHint = { viewModel.advanceProgressiveHint() },
                        onStartRankedSession = { viewModel.startUnassistedRankedSession() },
                        onExitRankedSession = { viewModel.exitUnassistedRankedSession() },
                        onToggleCoachMode = { viewModel.toggleCoachMode(it) },
                        onSelectPlayingStyle = { viewModel.setUserPlayingStyle(it) },
                        onPlaySuggestedMove = { viewModel.executeMoveOnBoard(it) },
                        onSquareTapped = { viewModel.onSquareTapped(it) },
                        onPieceDragged = { from, to -> viewModel.onPieceDragged(from, to) },
                        onUserArrowDrawn = { from, to -> viewModel.onUserArrowDrawn(from, to) },
                        onSelectPromotionPiece = { viewModel.selectPromotionPiece(it) },
                        onDismissPromotion = { viewModel.dismissPromotionOverlay() }
                    )
                }

                StudioTab.SOCIAL -> {
                    SocialChallengesAndEBoardScreen(
                        uiState = uiState,
                        openChallenges = openChallenges,
                        eBoardState = eBoardState,
                        discoveredBoards = discoveredBoards,
                        onSelectLeaderboardScope = { viewModel.setLeaderboardScope(it) },
                        onSelectLeaderboardSortMetric = { viewModel.setLeaderboardSortMetric(it) },
                        onRecordUserVictory = { viewModel.recordUserGameVictory() },
                        onAddFriendToLeaderboard = { viewModel.addFriendToLeaderboard(it) },
                        onGoogleSignInIdTokenReceived = { idToken ->
                            viewModel.onGoogleSignInIdTokenReceived(idToken)
                        },
                        onStartRankedSession = { viewModel.startUnassistedRankedSession() },
                        onExitRankedSession = { viewModel.exitUnassistedRankedSession() },
                        onSyncPendingGames = { viewModel.syncPendingCleanGamesToCloud() },
                        onCreateChallenge = { viewModel.createLiveChallengeRoom() },
                        onJoinChallenge = { room -> viewModel.joinLiveChallengeRoom(room) },
                        onScanEBoard = { brand -> eBoardManager.startScan(brand) },
                        onConnectEBoard = { board -> eBoardManager.connectToBoard(board) },
                        onDisconnectEBoard = { eBoardManager.disconnect() }
                    )
                }

                StudioTab.EDITOR -> {
                    BoardEditorScreen(
                        uiState = uiState,
                        onToggleEvalBarInEditor = { checked ->
                            viewModel.updateVisualSettings { it.copy(showEvalBarInEditor = checked) }
                        },
                        onEditorSquareTapped = { sq -> viewModel.onEditorSquareTapped(sq) },
                        onSelectEditorPiece = { piece, erase -> viewModel.setEditorSelectedPiece(piece, erase) },
                        onClearEditor = { viewModel.clearEditorBoard() },
                        onResetEditor = { viewModel.resetEditorToStart() },
                        onAnalyzeEditorPosition = { viewModel.applyEditorPositionToAnalysis() },
                        onFenInputTextChanged = { viewModel.onFenInputTextChanged(it) },
                        onApplyFenImport = { rawFen -> viewModel.importFenPosition(rawFen) },
                        onSelectScenarioPreset = { preset -> viewModel.selectFenScenarioPreset(preset) }
                    )
                }

                StudioTab.SETTINGS -> {
                    CompleteSettingsScreen(
                        uiState = uiState,
                        onUpdateEngine = { transform -> viewModel.updateEngineConfig(transform) },
                        onUpdateVisuals = { transform -> viewModel.updateVisualSettings(transform) }
                    )
                }
            }
        }
    }
}
