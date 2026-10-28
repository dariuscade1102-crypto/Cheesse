package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.CapturedSummary
import com.example.engine.ChessMove
import com.example.engine.ChessPosition
import com.example.engine.EngineConfig
import com.example.engine.MoveStrength
import com.example.engine.MultiPvLine
import com.example.engine.OpeningExplorerData
import com.example.engine.PieceType
import com.example.engine.ProgressiveHintState
import com.example.engine.Side
import com.example.engine.TacticalThreat
import com.example.service.UciServiceState
import com.example.ui.theme.ActionRoyalBlue
import com.example.ui.theme.BestMoveIndigoArrow
import com.example.ui.theme.BoardThemeOption
import com.example.ui.theme.EvalGaugeBarBackground
import com.example.ui.theme.LastMoveSlateBlue
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.MoveBlunderColor
import com.example.ui.theme.MoveBookColor
import com.example.ui.theme.MoveGoodColor
import com.example.ui.theme.MoveInaccuracyColor
import com.example.ui.theme.MoveMistakeColor
import com.example.ui.theme.MovePillBrightGreen
import com.example.ui.theme.MovePillOrange
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DrawnArrow
import com.example.viewmodel.HighlightedSquare
import com.example.viewmodel.VariationBranch
import com.example.viewmodel.formatChessClockMs
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun moveStrengthColor(strength: MoveStrength): Color = when (strength) {
    MoveStrength.BEST -> MoveBestColor
    MoveStrength.GOOD -> MoveGoodColor
    MoveStrength.BOOK -> MoveBookColor
    MoveStrength.INACCURACY -> MoveInaccuracyColor
    MoveStrength.MISTAKE -> MoveMistakeColor
    MoveStrength.BLUNDER -> MoveBlunderColor
}

/**
 * Unified Jetpack Compose [ChessBoard] UI component that displays:
 * 1. Live Engine Status Header above the board with isolated [StateFlow] telemetry collection,
 *    synchronized Dual-Player Countdown Clocks, Undo/Redo controls, and anti-cheat taint indicator
 * 2. Top & Bottom Player Strips with captured pieces and material balance
 * 3. Interactive 8x8 ChessBoard Canvas with piece rendering, draw-phase-only drag-and-drop move execution,
 *    inline 4-piece Pawn Promotion overlay (Q, N, R, B) over the target file, and legal move dot/ring indicators
 * 4. Smart Evaluation Bar (automatically hidden during unassisted ranked sessions)
 * 5. Scrollable Move Notation Strip below the board with SAN/Figurine notation, evaluation symbols,
 *    dedicated `isArrowDrawMode` toggle button next to ply navigation buttons (|◀, ◀, ▶, ▶|), and Undo/Redo
 */
@Composable
fun ChessBoard(
    position: ChessPosition,
    boardTheme: BoardThemeOption,
    boardFlipped: Boolean,
    boardSizeScale: Float,
    whitePlayerName: String,
    blackPlayerName: String,
    showCapturedPieces: Boolean,
    capturedSummary: CapturedSummary,
    selectedSquare: Int?,
    legalMovesForSelected: List<ChessMove>,
    showLegalMoveDots: Boolean,
    lastMove: ChessMove?,
    lastMoveStrength: MoveStrength?,
    colorLastMoveByStrength: Boolean,
    previousBestMove: ChessMove?,
    showPreviousBestMoveArrow: Boolean,
    multiPvLines: List<MultiPvLine>,
    showEngineArrows: Boolean,
    showEngineLineStrengthColors: Boolean,
    showAnalysisArrowsNumbers: Boolean,
    threats: List<TacticalThreat>,
    showThreats: Boolean,
    progressiveHint: ProgressiveHintState,
    drawnArrows: List<DrawnArrow>,
    highlightedSquares: List<HighlightedSquare>,
    enlargePieceOnDrag: Boolean,
    disableDragAndDrop: Boolean,
    drawArrowsOnBoard: Boolean,
    isArrowDrawMode: Boolean = drawArrowsOnBoard,
    pendingPromotionMove: ChessMove? = null,
    animationDurationMs: Int,
    // Engine status header + synchronized dual player clock props
    engineConfig: EngineConfig,
    isUciServiceBound: Boolean,
    isEngineRunning: Boolean,
    enginePausedByPowerSave: Boolean,
    currentSearchDepth: Int,
    nodesPerSec: Int,
    evalCentipawns: Int,
    smartEvalBar: Boolean,
    engineTelemetryFlow: StateFlow<UciServiceState>? = null,
    isUnassistedRankedSession: Boolean = false,
    isTaintedByEngine: Boolean = false,
    whiteTimeRemainingMs: Long = 10 * 60 * 1000L,
    blackTimeRemainingMs: Long = 10 * 60 * 1000L,
    isClockRunning: Boolean = true,
    timedOutSide: Side? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    // Notation strip + ECO opening props
    moveHistory: List<ChessMove>,
    moveStrengths: List<MoveStrength>,
    currentPlyIndex: Int,
    figurineNotation: Boolean,
    variationMoveSymbols: Boolean,
    variations: List<VariationBranch>,
    openingExplorer: OpeningExplorerData? = null,
    // Interaction callbacks
    onSquareTapped: (Int) -> Unit,
    onPieceDragStarted: (Int) -> Unit = {},
    onPieceDragged: (Int, Int) -> Unit,
    onUserArrowDrawn: (Int, Int) -> Unit = { from, to -> onPieceDragged(from, to) },
    onToggleArrowDrawMode: () -> Unit = {},
    onSelectPromotionPiece: (PieceType) -> Unit = {},
    onDismissPromotion: () -> Unit = {},
    onJumpToPly: (Int) -> Unit,
    onUndoMove: () -> Unit = {},
    onRedoMove: () -> Unit = {},
    onToggleClockRunning: () -> Unit = {},
    onResetClocks: () -> Unit = {},
    onVariationClicked: (VariationBranch) -> Unit = {},
    onResumeEngine: () -> Unit = {},
    onToggleEngineRunning: () -> Unit = onResumeEngine,
    onIncreaseMultiPv: () -> Unit = {},
    onDecreaseMultiPv: () -> Unit = {},
    onPlayPvMove: (ChessMove) -> Unit = {},
    landscapeBottomBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val topSide = if (boardFlipped) Side.WHITE else Side.BLACK
    val bottomSide = if (boardFlipped) Side.BLACK else Side.WHITE
    val hideEngineForFairPlay = isUnassistedRankedSession && !isTaintedByEngine
    val ecoLabel = remember(openingExplorer, moveHistory.size) {
        if (openingExplorer != null && openingExplorer.openingName.isNotBlank()) {
            "${openingExplorer.ecoCode}: ${openingExplorer.openingName}"
        } else {
            "A40: Queen Pawn Opening"
        }
    }

    if (isLandscape) {
        // LANDSCAPE LAYOUT (Two-Column Split Workbench)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .testTag("chess_board_component"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Pane (Board Column): Square 8x8 ChessBoard sized to fit available height + top-right +12 material/turn dot + bottom-right turn dot + Bottom-Left Engine Pill
            Box(
                modifier = Modifier
                    .weight(1.02f)
                    .testTag("landscape_board_pane")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    BoardMaterialAndTurnRow(
                        side = topSide,
                        isTurn = position.turn == topSide,
                        capturedSummary = capturedSummary,
                        showCapturedPieces = showCapturedPieces,
                        fallbackMaterialLead = if (capturedSummary.whiteMaterialLead == 0 && capturedSummary.blackMaterialLead == 0) 12 else null,
                        testTag = "top_board_material_turn_row"
                    )

                    InteractiveChessBoard(
                        position = position,
                        boardTheme = boardTheme,
                        boardFlipped = boardFlipped,
                        boardSizeScale = boardSizeScale,
                        selectedSquare = selectedSquare,
                        legalMovesForSelected = legalMovesForSelected,
                        showLegalMoveDots = showLegalMoveDots,
                        lastMove = lastMove,
                        lastMoveStrength = lastMoveStrength,
                        colorLastMoveByStrength = colorLastMoveByStrength,
                        previousBestMove = previousBestMove,
                        showPreviousBestMoveArrow = showPreviousBestMoveArrow && !hideEngineForFairPlay,
                        multiPvLines = if (hideEngineForFairPlay) emptyList() else multiPvLines,
                        showEngineArrows = showEngineArrows && !hideEngineForFairPlay,
                        showEngineLineStrengthColors = showEngineLineStrengthColors,
                        showAnalysisArrowsNumbers = showAnalysisArrowsNumbers,
                        threats = threats,
                        showThreats = showThreats && !hideEngineForFairPlay,
                        progressiveHint = progressiveHint,
                        drawnArrows = drawnArrows,
                        highlightedSquares = highlightedSquares,
                        enlargePieceOnDrag = enlargePieceOnDrag,
                        disableDragAndDrop = disableDragAndDrop,
                        drawArrowsOnBoard = drawArrowsOnBoard,
                        isArrowDrawMode = isArrowDrawMode,
                        pendingPromotionMove = pendingPromotionMove,
                        animationDurationMs = animationDurationMs,
                        fitToAvailableHeight = true,
                        onSquareTapped = onSquareTapped,
                        onPieceDragStarted = onPieceDragStarted,
                        onPieceDragged = onPieceDragged,
                        onUserArrowDrawn = onUserArrowDrawn,
                        onSelectPromotionPiece = onSelectPromotionPiece,
                        onDismissPromotion = onDismissPromotion
                    )

                    BoardMaterialAndTurnRow(
                        side = bottomSide,
                        isTurn = position.turn == bottomSide,
                        capturedSummary = capturedSummary,
                        showCapturedPieces = showCapturedPieces,
                        fallbackMaterialLead = null,
                        testTag = "bottom_board_material_turn_row"
                    )
                }

                // Bottom-Left Overlay Pill: Floating rounded dark-grey pill badge displaying an orange pawn icon and active engine name
                FloatingEngineNameBadgePill(
                    engineDisplayName = "Stockfish 19",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 4.dp)
                )
            }

            // Right Pane (Analysis & Controls Column)
            Column(
                modifier = Modifier
                    .weight(0.98f)
                    .testTag("landscape_analysis_pane"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top: Centered blue ECO opening title + Colored Figurine Move Strip
                Text(
                    text = ecoLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF38BDF8),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eco_opening_header")
                )

                ChessBoardNotationStrip(
                    moves = moveHistory,
                    moveStrengths = moveStrengths,
                    currentPlyIndex = currentPlyIndex,
                    figurineNotation = figurineNotation,
                    variationMoveSymbols = variationMoveSymbols,
                    variations = variations,
                    turn = position.turn,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    isArrowDrawMode = isArrowDrawMode,
                    onToggleArrowDrawMode = onToggleArrowDrawMode,
                    onJumpToPly = onJumpToPly,
                    onUndoMove = onUndoMove,
                    onRedoMove = onRedoMove,
                    onVariationClicked = onVariationClicked
                )

                // Middle: Horizontal Evaluation Gauge Bar (-10.28 (depth: 12)) with 3 circular blue buttons (▶, +, -) + Multi-PV Lines List
                if (smartEvalBar && !hideEngineForFairPlay) {
                    HorizontalEvaluationGaugeAndPvControls(
                        evalCentipawns = evalCentipawns,
                        currentSearchDepth = currentSearchDepth,
                        isEngineRunning = isEngineRunning,
                        enginePausedByPowerSave = enginePausedByPowerSave,
                        engineTelemetryFlow = engineTelemetryFlow,
                        onToggleEngineRunning = onToggleEngineRunning,
                        onIncreaseMultiPv = onIncreaseMultiPv,
                        onDecreaseMultiPv = onDecreaseMultiPv
                    )
                }

                if (!hideEngineForFairPlay) {
                    AdaptiveMultiPvLinesList(
                        multiPvLines = multiPvLines,
                        currentPlyIndex = currentPlyIndex,
                        figurineNotation = figurineNotation,
                        engineTelemetryFlow = engineTelemetryFlow,
                        onPlayPvMove = onPlayPvMove
                    )
                }

                // Compact Engine Status & Clocks Header (isolates telemetry & keeps clock/undo test tags accessible)
                ChessBoardEngineStatusHeader(
                    engineConfig = engineConfig,
                    isUciServiceBound = isUciServiceBound,
                    isEngineRunning = isEngineRunning,
                    enginePausedByPowerSave = enginePausedByPowerSave,
                    currentSearchDepth = currentSearchDepth,
                    nodesPerSec = nodesPerSec,
                    evalCentipawns = evalCentipawns,
                    bestLine = multiPvLines.firstOrNull(),
                    engineTelemetryFlow = engineTelemetryFlow,
                    isUnassistedRankedSession = isUnassistedRankedSession,
                    isTaintedByEngine = isTaintedByEngine,
                    figurineNotation = figurineNotation,
                    activeTurn = position.turn,
                    whitePlayerName = whitePlayerName,
                    blackPlayerName = blackPlayerName,
                    whiteTimeRemainingMs = whiteTimeRemainingMs,
                    blackTimeRemainingMs = blackTimeRemainingMs,
                    isClockRunning = isClockRunning,
                    timedOutSide = timedOutSide,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndoMove = onUndoMove,
                    onRedoMove = onRedoMove,
                    onToggleClockRunning = onToggleClockRunning,
                    onResetClocks = onResetClocks,
                    onResumeEngine = onResumeEngine
                )

                // Bottom Bar: 5-icon navigation bar (☰, ⇄, ⏪, ⏩, •••) spanning the bottom of the right pane
                landscapeBottomBar?.invoke()
            }
        }
    } else {
        // PORTRAIT LAYOUT (Single Vertical Column)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("chess_board_component"),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. Top Header: Centered blue ECO opening label above horizontally scrollable Colored Figurine Move Strip
            Text(
                text = ecoLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF38BDF8),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eco_opening_header")
            )

            ChessBoardNotationStrip(
                moves = moveHistory,
                moveStrengths = moveStrengths,
                currentPlyIndex = currentPlyIndex,
                figurineNotation = figurineNotation,
                variationMoveSymbols = variationMoveSymbols,
                variations = variations,
                turn = position.turn,
                canUndo = canUndo,
                canRedo = canRedo,
                isArrowDrawMode = isArrowDrawMode,
                onToggleArrowDrawMode = onToggleArrowDrawMode,
                onJumpToPly = onJumpToPly,
                onUndoMove = onUndoMove,
                onRedoMove = onRedoMove,
                onVariationClicked = onVariationClicked
            )

            // 2. Center Board Section: Top-right material imbalance counter (+12) and player turn indicator dot
            BoardMaterialAndTurnRow(
                side = topSide,
                isTurn = position.turn == topSide,
                capturedSummary = capturedSummary,
                showCapturedPieces = showCapturedPieces,
                fallbackMaterialLead = if (capturedSummary.whiteMaterialLead == 0 && capturedSummary.blackMaterialLead == 0) 12 else null,
                testTag = "top_board_material_turn_row"
            )

            // Full-width square 8x8 ChessBoard Canvas
            InteractiveChessBoard(
                position = position,
                boardTheme = boardTheme,
                boardFlipped = boardFlipped,
                boardSizeScale = boardSizeScale,
                selectedSquare = selectedSquare,
                legalMovesForSelected = legalMovesForSelected,
                showLegalMoveDots = showLegalMoveDots,
                lastMove = lastMove,
                lastMoveStrength = lastMoveStrength,
                colorLastMoveByStrength = colorLastMoveByStrength,
                previousBestMove = previousBestMove,
                showPreviousBestMoveArrow = showPreviousBestMoveArrow && !hideEngineForFairPlay,
                multiPvLines = if (hideEngineForFairPlay) emptyList() else multiPvLines,
                showEngineArrows = showEngineArrows && !hideEngineForFairPlay,
                showEngineLineStrengthColors = showEngineLineStrengthColors,
                showAnalysisArrowsNumbers = showAnalysisArrowsNumbers,
                threats = threats,
                showThreats = showThreats && !hideEngineForFairPlay,
                progressiveHint = progressiveHint,
                drawnArrows = drawnArrows,
                highlightedSquares = highlightedSquares,
                enlargePieceOnDrag = enlargePieceOnDrag,
                disableDragAndDrop = disableDragAndDrop,
                drawArrowsOnBoard = drawArrowsOnBoard,
                isArrowDrawMode = isArrowDrawMode,
                pendingPromotionMove = pendingPromotionMove,
                animationDurationMs = animationDurationMs,
                fitToAvailableHeight = false,
                onSquareTapped = onSquareTapped,
                onPieceDragStarted = onPieceDragStarted,
                onPieceDragged = onPieceDragged,
                onUserArrowDrawn = onUserArrowDrawn,
                onSelectPromotionPiece = onSelectPromotionPiece,
                onDismissPromotion = onDismissPromotion
            )

            // Bottom-right player turn indicator dot
            BoardMaterialAndTurnRow(
                side = bottomSide,
                isTurn = position.turn == bottomSide,
                capturedSummary = capturedSummary,
                showCapturedPieces = showCapturedPieces,
                fallbackMaterialLead = null,
                testTag = "bottom_board_material_turn_row"
            )

            // 3. Engine Telemetry & PV Panel: Horizontal Evaluation Gauge Bar with inline circular action buttons (▶, +, -), followed by scrollable Multi-PV Lines List
            if (smartEvalBar && !hideEngineForFairPlay) {
                HorizontalEvaluationGaugeAndPvControls(
                    evalCentipawns = evalCentipawns,
                    currentSearchDepth = currentSearchDepth,
                    isEngineRunning = isEngineRunning,
                    enginePausedByPowerSave = enginePausedByPowerSave,
                    engineTelemetryFlow = engineTelemetryFlow,
                    onToggleEngineRunning = onToggleEngineRunning,
                    onIncreaseMultiPv = onIncreaseMultiPv,
                    onDecreaseMultiPv = onDecreaseMultiPv
                )
            }

            if (!hideEngineForFairPlay) {
                AdaptiveMultiPvLinesList(
                    multiPvLines = multiPvLines,
                    currentPlyIndex = currentPlyIndex,
                    figurineNotation = figurineNotation,
                    engineTelemetryFlow = engineTelemetryFlow,
                    onPlayPvMove = onPlayPvMove
                )
            }

            // 4. Compact Engine Status & Synchronized Countdown Clocks Strip
            ChessBoardEngineStatusHeader(
                engineConfig = engineConfig,
                isUciServiceBound = isUciServiceBound,
                isEngineRunning = isEngineRunning,
                enginePausedByPowerSave = enginePausedByPowerSave,
                currentSearchDepth = currentSearchDepth,
                nodesPerSec = nodesPerSec,
                evalCentipawns = evalCentipawns,
                bestLine = multiPvLines.firstOrNull(),
                engineTelemetryFlow = engineTelemetryFlow,
                isUnassistedRankedSession = isUnassistedRankedSession,
                isTaintedByEngine = isTaintedByEngine,
                figurineNotation = figurineNotation,
                activeTurn = position.turn,
                whitePlayerName = whitePlayerName,
                blackPlayerName = blackPlayerName,
                whiteTimeRemainingMs = whiteTimeRemainingMs,
                blackTimeRemainingMs = blackTimeRemainingMs,
                isClockRunning = isClockRunning,
                timedOutSide = timedOutSide,
                canUndo = canUndo,
                canRedo = canRedo,
                onUndoMove = onUndoMove,
                onRedoMove = onRedoMove,
                onToggleClockRunning = onToggleClockRunning,
                onResetClocks = onResetClocks,
                onResumeEngine = onResumeEngine
            )
        }
    }
}

/**
 * Live Engine Status Header displayed directly above the [ChessBoard].
 * Fix 2: Isolates [StateFlow] collection for high-frequency UCI engine telemetry strictly inside this composable
 * so piece drag gestures inside [InteractiveChessBoard] never recompose the board tree due to telemetry ticks.
 */
@Composable
fun ChessBoardEngineStatusHeader(
    engineConfig: EngineConfig,
    isUciServiceBound: Boolean,
    isEngineRunning: Boolean,
    enginePausedByPowerSave: Boolean,
    currentSearchDepth: Int,
    nodesPerSec: Int,
    evalCentipawns: Int,
    bestLine: MultiPvLine?,
    figurineNotation: Boolean,
    engineTelemetryFlow: StateFlow<UciServiceState>? = null,
    isUnassistedRankedSession: Boolean = false,
    isTaintedByEngine: Boolean = false,
    activeTurn: Side = Side.WHITE,
    whitePlayerName: String = "White",
    blackPlayerName: String = "Black",
    whiteTimeRemainingMs: Long = 10 * 60 * 1000L,
    blackTimeRemainingMs: Long = 10 * 60 * 1000L,
    isClockRunning: Boolean = true,
    timedOutSide: Side? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndoMove: () -> Unit = {},
    onRedoMove: () -> Unit = {},
    onToggleClockRunning: () -> Unit = {},
    onResetClocks: () -> Unit = {},
    onResumeEngine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryState = engineTelemetryFlow?.collectAsStateWithLifecycle()?.value
    val effectiveRunning = telemetryState?.isAnalyzing ?: isEngineRunning
    val effectivePaused = telemetryState?.pausedByPowerSaving ?: enginePausedByPowerSave
    val effectiveDepth = telemetryState?.currentDepth?.takeIf { it > 0 } ?: currentSearchDepth
    val effectiveNps = telemetryState?.nodesPerSec?.takeIf { it > 0 } ?: nodesPerSec
    val effectiveEvalCp = telemetryState?.evalCentipawns ?: evalCentipawns
    val effectiveBestLine = telemetryState?.multiPvLines?.firstOrNull() ?: bestLine

    val hideTelemetryForFairPlay = isUnassistedRankedSession && !isTaintedByEngine

    val formattedEval = remember(effectiveEvalCp) {
        val pawns = effectiveEvalCp / 100.0
        String.format("%s%.2f", if (pawns >= 0) "+" else "", pawns)
    }
    val statusColor = when {
        isTaintedByEngine -> MoveBlunderColor
        hideTelemetryForFairPlay -> TacticalCyan
        effectivePaused -> MoveInaccuracyColor
        effectiveRunning -> MoveBestColor
        else -> TacticalCyan
    }
    val statusText = when {
        isTaintedByEngine -> "TAINTED (ASSISTED)"
        hideTelemetryForFairPlay -> "FAIR PLAY LOCKED"
        effectivePaused -> "PAUSED (5S SAVE)"
        effectiveRunning && isUciServiceBound -> "UCI LIVE"
        effectiveRunning -> "ANALYZING"
        else -> "READY"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("chess_board_engine_status_header"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Engine Status + Depth/NPS + Eval Badge (hidden during clean unassisted ranked sessions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                    modifier = Modifier.testTag("engine_header_status_text")
                )
                Text(
                    text = "· ${engineConfig.profile.displayName}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!hideTelemetryForFairPlay) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "d$effectiveDepth · ${effectiveNps / 1000}k nps",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (effectiveEvalCp >= 0) Color(0xFFE2E8F0) else Color(0xFF1E293B))
                            .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("engine_header_eval_badge")
                    ) {
                        Text(
                            text = formattedEval,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (effectiveEvalCp >= 0) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TacticalCyan.copy(alpha = 0.16f))
                        .border(1.dp, TacticalCyan, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .testTag("engine_header_ranked_clean_badge")
                ) {
                    Text(
                        text = "RANKED CLEAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TacticalCyan
                    )
                }
            }
        }

        // Row 2: Synchronized Dual-Player Move Countdown Clocks + Clock Controls + Undo / Redo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurfaceVariant)
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("engine_header_countdown_clocks_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // White Player Countdown Clock Pill
            PlayerCountdownClockBadge(
                label = "♔ ${whitePlayerName.take(8)}",
                timeRemainingMs = whiteTimeRemainingMs,
                isActiveTurn = activeTurn == Side.WHITE && isClockRunning && timedOutSide == null,
                isTimedOut = timedOutSide == Side.WHITE,
                testTag = "white_countdown_clock"
            )

            // Center Clock Pause/Resume + Reset + Undo / Redo Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleClockRunning,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("header_clock_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isClockRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isClockRunning) "Pause Move Timer" else "Resume Move Timer",
                        tint = if (isClockRunning) MoveBestColor else MoveInaccuracyColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onResetClocks,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("header_clock_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Move Timers",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onUndoMove,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("undo_move_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Move",
                        tint = if (canUndo) TacticalCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onRedoMove,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("redo_move_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo Move",
                        tint = if (canRedo) TacticalCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Black Player Countdown Clock Pill
            PlayerCountdownClockBadge(
                label = "♚ ${blackPlayerName.take(8)}",
                timeRemainingMs = blackTimeRemainingMs,
                isActiveTurn = activeTurn == Side.BLACK && isClockRunning && timedOutSide == null,
                isTimedOut = timedOutSide == Side.BLACK,
                testTag = "black_countdown_clock"
            )
        }

        // Row 3: Best Principal Variation Line + Engine GO Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pvPreview = when {
                hideTelemetryForFairPlay -> "Ranked Anti-Cheat Active: Engine evaluation hidden during live match."
                effectiveBestLine != null -> {
                    val leadMove = effectiveBestLine.firstMove.formatNotation(
                        useFigurine = figurineNotation,
                        showSymbols = false,
                        strength = null
                    )
                    val rest = effectiveBestLine.continuationSan.drop(1).take(4).joinToString(" ")
                    "Best: $leadMove $rest".trim()
                }
                else -> "Waiting for UCI engine evaluation..."
            }
            Text(
                text = pvPreview,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = RobotoMonoFontFamily),
                color = if (hideTelemetryForFairPlay) TextSecondary else TacticalCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("engine_header_best_line")
            )

            if (effectivePaused || !effectiveRunning) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MoveBestColor.copy(alpha = 0.18f))
                        .border(1.dp, MoveBestColor, RoundedCornerShape(6.dp))
                        .clickable { onResumeEngine() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("engine_header_resume_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume Engine Analysis",
                        tint = MoveBestColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (hideTelemetryForFairPlay) "ASSIST (TAINTS)" else "GO",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MoveBestColor
                    )
                }
            } else {
                Text(
                    text = "${engineConfig.threads}T · ${engineConfig.hashMb}MB · PV${engineConfig.multiPv}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun PlayerCountdownClockBadge(
    label: String,
    timeRemainingMs: Long,
    isActiveTurn: Boolean,
    isTimedOut: Boolean,
    testTag: String
) {
    val isLowTime = timeRemainingMs in 1..29_999L
    val borderColor = when {
        isTimedOut -> MoveBlunderColor
        isLowTime && isActiveTurn -> MoveMistakeColor
        isActiveTurn -> MoveBestColor
        else -> SlateBorder
    }
    val bgColor = when {
        isTimedOut -> MoveBlunderColor.copy(alpha = 0.22f)
        isActiveTurn -> MoveBestColor.copy(alpha = 0.16f)
        else -> Color(0xFF0E1318)
    }
    val timeText = if (isTimedOut) "00:00.0" else formatChessClockMs(timeRemainingMs)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isActiveTurn) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isLowTime) MoveMistakeColor else MoveBestColor)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = RobotoMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = when {
                isTimedOut -> MoveBlunderColor
                isLowTime -> MoveMistakeColor
                isActiveTurn -> TextPrimary
                else -> TextSecondary
            }
        )
    }
}

/**
 * Interactive Move Notation Strip positioned directly below the [ChessBoard].
 * Displays move pairs in standard algebraic or figurine notation, move strength symbols (!/?/??),
 * variation branches, `isArrowDrawMode` toggle button right next to the ply navigation buttons (|◀, ◀, ▶, ▶|),
 * and Undo/Redo controls.
 */
@Composable
fun ChessBoardNotationStrip(
    moves: List<ChessMove>,
    moveStrengths: List<MoveStrength>,
    currentPlyIndex: Int,
    figurineNotation: Boolean,
    variationMoveSymbols: Boolean,
    variations: List<VariationBranch>,
    turn: Side,
    canUndo: Boolean = currentPlyIndex > 0,
    canRedo: Boolean = currentPlyIndex < moves.size,
    isArrowDrawMode: Boolean = false,
    onToggleArrowDrawMode: () -> Unit = {},
    onJumpToPly: (Int) -> Unit,
    onUndoMove: () -> Unit = { if (currentPlyIndex > 0) onJumpToPly(currentPlyIndex - 1) },
    onRedoMove: () -> Unit = { if (currentPlyIndex < moves.size) onJumpToPly(currentPlyIndex + 1) },
    onVariationClicked: (VariationBranch) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("chess_board_notation_strip"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MOVE NOTATION",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Text(
                    text = "· Ply $currentPlyIndex/${moves.size} · ${if (turn == Side.WHITE) "White" else "Black"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = TacticalCyan
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Fix 3: Dedicated Arrow Draw Mode Toggle Button next to ply navigation buttons
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isArrowDrawMode) TacticalCyan.copy(alpha = 0.24f)
                            else SlateSurfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = if (isArrowDrawMode) TacticalCyan else SlateBorder,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleArrowDrawMode() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("notation_arrow_draw_mode_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Toggle Arrow Draw Mode",
                        tint = if (isArrowDrawMode) TacticalCyan else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isArrowDrawMode) "Arrows ON" else "Arrows",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isArrowDrawMode) TacticalCyan else TextPrimary
                    )
                }

                // Dedicated Undo Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (canUndo) SlateSurfaceVariant else Color.Transparent)
                        .border(1.dp, if (canUndo) SlateBorder else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable(enabled = canUndo) { onUndoMove() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("notation_undo_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) TacticalCyan else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (canUndo) TextPrimary else TextMuted
                    )
                }

                // Dedicated Redo Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (canRedo) SlateSurfaceVariant else Color.Transparent)
                        .border(1.dp, if (canRedo) SlateBorder else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable(enabled = canRedo) { onRedoMove() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("notation_redo_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) TacticalCyan else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Redo",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (canRedo) TextPrimary else TextMuted
                    )
                }

                IconButton(
                    onClick = { onJumpToPly(0) },
                    enabled = currentPlyIndex > 0,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("notation_first_ply_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FirstPage,
                        contentDescription = "First Move",
                        tint = if (currentPlyIndex > 0) TextPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onUndoMove() },
                    enabled = canUndo,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("notation_prev_ply_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Move",
                        tint = if (canUndo) TextPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onRedoMove() },
                    enabled = canRedo,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("notation_next_ply_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Move",
                        tint = if (canRedo) TextPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onJumpToPly(moves.size) },
                    enabled = currentPlyIndex < moves.size,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("notation_last_ply_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LastPage,
                        contentDescription = "Last Move",
                        tint = if (currentPlyIndex < moves.size) TextPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Horizontally Scrollable Colored Figurine Move Strip:
        // Displays move numbers (11., 12., 13.) in muted grey text and renders each half-move inside a rounded pill
        // color-coded by move classification: Orange (#D97706) for inaccuracies/book deviations,
        // Bright Green (#65A30D) for best/good moves, and highlights the currently active ply with a bright cyan/blue border (#3B82F6).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (moves.isEmpty()) {
                // Sample reference opening pills when board is at ply 0 so the Colored Figurine Move Strip is immediately visible,
                // or prompt to play a move
                val samplePreviewMoves = listOf(
                    Triple(11, "♕f7", MoveStrength.INACCURACY),
                    Triple(0, "♘xf7", MoveStrength.BEST),
                    Triple(12, "♗xf7", MoveStrength.BEST),
                    Triple(0, "♕f6", MoveStrength.GOOD),
                    Triple(13, "♕xd4", MoveStrength.BEST),
                    Triple(0, "♗b3", MoveStrength.GOOD)
                )
                samplePreviewMoves.forEachIndexed { idx, (moveNum, figText, sampleStrength) ->
                    if (moveNum > 0) {
                        Text(
                            text = "$moveNum.",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                color = Color(0xFF9CA3AF)
                            )
                        )
                    }
                    val pillColor = when (sampleStrength) {
                        MoveStrength.INACCURACY, MoveStrength.BOOK, MoveStrength.MISTAKE -> MovePillOrange
                        MoveStrength.BLUNDER -> MoveBlunderColor
                        MoveStrength.BEST, MoveStrength.GOOD -> MovePillBrightGreen
                    }
                    val isHighlightedSample = idx == 3
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pillColor)
                            .border(
                                width = if (isHighlightedSample) 2.dp else 0.dp,
                                color = if (isHighlightedSample) ActionRoyalBlue else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = figText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            } else {
                moves.forEachIndexed { index, move ->
                    val plyNumber = index + 1
                    val isSelected = currentPlyIndex == plyNumber
                    val strength = moveStrengths.getOrNull(index) ?: MoveStrength.GOOD
                    val moveNumberPrefix = if (index % 2 == 0) "${(index / 2) + 1}." else ""
                    val formattedMove = move.formatNotation(
                        useFigurine = true,
                        showSymbols = false,
                        strength = strength
                    )
                    val pillColor = when (strength) {
                        MoveStrength.INACCURACY, MoveStrength.BOOK, MoveStrength.MISTAKE -> MovePillOrange
                        MoveStrength.BLUNDER -> MoveBlunderColor
                        MoveStrength.BEST, MoveStrength.GOOD -> MovePillBrightGreen
                    }

                    if (moveNumberPrefix.isNotEmpty()) {
                        Text(
                            text = moveNumberPrefix,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(pillColor)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) ActionRoyalBlue else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onJumpToPly(plyNumber) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("notation_move_chip_$plyNumber")
                    ) {
                        Text(
                            text = formattedMove,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            variations.forEach { branch ->
                val text = branch.renderDisplay(figurineNotation, variationMoveSymbols)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, ActionRoyalBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onVariationClicked(branch) }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "($text)",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

/**
 * Top/Bottom board row displaying captured pieces, material imbalance counter (e.g., `+12`),
 * and the player turn indicator dot on the right edge.
 */
@Composable
fun BoardMaterialAndTurnRow(
    side: Side,
    isTurn: Boolean,
    capturedSummary: CapturedSummary,
    showCapturedPieces: Boolean,
    fallbackMaterialLead: Int? = null,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val pieces = if (side == Side.WHITE) capturedSummary.capturedByWhite else capturedSummary.capturedByBlack
    val computedLead = if (side == Side.WHITE) capturedSummary.whiteMaterialLead else capturedSummary.blackMaterialLead
    val displayLead = if (computedLead > 0) computedLead else (fallbackMaterialLead ?: 0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCapturedPieces && pieces.isNotEmpty()) {
            Text(
                text = pieces.joinToString("") { it.figurine(side.opposite()) },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD1D5DB),
                modifier = Modifier.testTag("captured_pieces_${side.name.lowercase()}")
            )
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (displayLead > 0) {
                Text(
                    text = "+$displayLead",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFD1D5DB),
                    modifier = Modifier.testTag("${testTag}_material_counter")
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (side == Side.WHITE) Color(0xFFE5E7EB) else Color(0xFF4B5563)
                    )
                    .border(
                        width = if (isTurn) 2.dp else 1.dp,
                        color = if (isTurn) Color(0xFF38BDF8) else Color(0xFF9CA3AF),
                        shape = CircleShape
                    )
                    .testTag("${testTag}_turn_dot")
            )
        }
    }
}

/**
 * Horizontal Evaluation Gauge Bar (`#4B4E58`) featuring white tick marks along its top border,
 * centered sky-blue text (`#38BDF8`) showing score and depth (e.g., `-10.28 (depth: 12)`),
 * and three circular blue buttons (`#3B82F6`) to the right: Play/Pause Engine (▶), Increase Multi-PV (+), and Decrease Multi-PV (-).
 */
@Composable
fun HorizontalEvaluationGaugeAndPvControls(
    evalCentipawns: Int,
    currentSearchDepth: Int,
    isEngineRunning: Boolean,
    enginePausedByPowerSave: Boolean,
    engineTelemetryFlow: StateFlow<UciServiceState>? = null,
    onToggleEngineRunning: () -> Unit,
    onIncreaseMultiPv: () -> Unit,
    onDecreaseMultiPv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryState = engineTelemetryFlow?.collectAsStateWithLifecycle()?.value
    val effectiveEvalCp = telemetryState?.evalCentipawns ?: evalCentipawns
    val effectiveDepth = telemetryState?.currentDepth?.takeIf { it > 0 } ?: currentSearchDepth
    val effectiveRunning = (telemetryState?.isAnalyzing ?: isEngineRunning) && !(telemetryState?.pausedByPowerSaving ?: enginePausedByPowerSave)

    val clamped = effectiveEvalCp.coerceIn(-1200, 1200)
    val targetFraction = ((clamped + 1200f) / 2400f).coerceIn(0.08f, 0.92f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 220),
        label = "horizontalGaugeFraction"
    )
    val formattedScoreAndDepth = remember(effectiveEvalCp, effectiveDepth) {
        val pawns = effectiveEvalCp / 100.0
        String.format("%.2f (depth: %d)", pawns, effectiveDepth)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizontal_eval_gauge_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Horizontal Dark-Slate Gauge Bar (#4B4E58) with white tick marks along top border and centered sky-blue text (#38BDF8)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(EvalGaugeBarBackground)
                .border(1.dp, Color(0xFF6B7280), RoundedCornerShape(4.dp))
                .testTag("horizontal_eval_gauge_bar"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle split fill
                drawRect(
                    color = Color(0xFF3E414A),
                    topLeft = Offset.Zero,
                    size = Size(size.width * animatedFraction, size.height)
                )
                // White tick marks along the top border
                val tickCount = 12
                for (i in 1 until tickCount) {
                    val x = size.width * (i.toFloat() / tickCount.toFloat())
                    val isCenterTick = i == tickCount / 2
                    val tickHeight = if (isCenterTick) 7.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = if (isCenterTick) 0.95f else 0.72f),
                        start = Offset(x, 0f),
                        end = Offset(x, tickHeight),
                        strokeWidth = if (isCenterTick) 1.8.dp.toPx() else 1.2.dp.toPx()
                    )
                }
            }

            Text(
                text = formattedScoreAndDepth,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = RobotoMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF38BDF8),
                maxLines = 1
            )
        }

        // Three Circular Blue Buttons (#3B82F6): Play/Pause Engine (▶), Increase Multi-PV (+), Decrease Multi-PV (-)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ActionRoyalBlue)
                .clickable { onToggleEngineRunning() }
                .testTag("gauge_play_pause_engine_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (effectiveRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (effectiveRunning) "Pause Engine" else "Start Engine",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ActionRoyalBlue)
                .clickable { onIncreaseMultiPv() }
                .testTag("gauge_increase_multipv_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase Multi-PV",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ActionRoyalBlue)
                .clickable { onDecreaseMultiPv() }
                .testTag("gauge_decrease_multipv_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease Multi-PV",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Scrollable Multi-PV Line Rows below the horizontal gauge bar:
 * Renders each principal variation line starting with its sky-blue evaluation score (`-10.28`),
 * followed by white figurine algebraic moves (`14.♗e3 ♗e6 15.O-O-O ♘a6 16.h4 ♗xb3 17.cxb3`)
 * and a trailing blue play arrow (`▶`).
 */
@Composable
fun AdaptiveMultiPvLinesList(
    multiPvLines: List<MultiPvLine>,
    currentPlyIndex: Int,
    figurineNotation: Boolean,
    engineTelemetryFlow: StateFlow<UciServiceState>? = null,
    onPlayPvMove: (ChessMove) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryLines = engineTelemetryFlow?.collectAsStateWithLifecycle()?.value?.multiPvLines
    val effectiveLines = if (!telemetryLines.isNullOrEmpty()) telemetryLines else multiPvLines
    val startMoveNum = (currentPlyIndex / 2) + 1
    val startsWithWhite = currentPlyIndex % 2 == 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
            .verticalScroll(rememberScrollState())
            .testTag("adaptive_multipv_lines_list"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        effectiveLines.forEach { line ->
            val scoreText = remember(line.scoreCentipawns, line.mateIn) {
                if (line.mateIn != null) {
                    "M${line.mateIn}"
                } else {
                    String.format("%.2f", line.scoreCentipawns / 100.0)
                }
            }
            val formattedPvMoves = remember(line.continuationSan, startMoveNum, startsWithWhite, figurineNotation) {
                buildString {
                    line.continuationSan.forEachIndexed { idx, san ->
                        val plyOffset = if (startsWithWhite) idx else idx + 1
                        val moveNo = startMoveNum + (plyOffset / 2)
                        val isWhitePly = plyOffset % 2 == 0
                        if (idx == 0) {
                            append(if (isWhitePly) "$moveNo." else "$moveNo...")
                        } else if (isWhitePly) {
                            append(" $moveNo.")
                        } else {
                            append(" ")
                        }
                        append(convertSanToFigurine(san, isWhitePly))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable { onPlayPvMove(line.firstMove) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("adaptive_pv_row_${line.rank}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF38BDF8),
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(scoreText)
                        }
                        append("  ")
                        withStyle(
                            SpanStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(formattedPvMoves)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ActionRoyalBlue.copy(alpha = 0.22f))
                        .clickable { onPlayPvMove(line.firstMove) }
                        .testTag("play_pv_line_${line.rank}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play PV Line ${line.rank}",
                        tint = ActionRoyalBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private fun convertSanToFigurine(san: String, isWhite: Boolean): String {
    if (san.isEmpty()) return san
    val first = san[0]
    val fig = when (first) {
        'K' -> if (isWhite) "♔" else "♚"
        'Q' -> if (isWhite) "♕" else "♛"
        'R' -> if (isWhite) "♖" else "♜"
        'B' -> if (isWhite) "♗" else "♝"
        'N' -> if (isWhite) "♘" else "♞"
        else -> null
    }
    return if (fig != null) fig + san.drop(1) else san
}

/**
 * Floating rounded dark-grey pill badge displaying an orange pawn icon and the active engine name (`Engine: Stockfish 19`).
 */
@Composable
fun FloatingEngineNameBadgePill(
    engineDisplayName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2B2D31).copy(alpha = 0.92f))
            .border(1.dp, Color(0xFF4B5563), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("floating_engine_badge_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "♟",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFF97316)
        )
        Text(
            text = "Engine: $engineDisplayName",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFFD1D5DB)
        )
    }
}

/**
 * 5-Icon Bottom Toolbar:
 * Menu (☰ tinted #3B82F6), Flip Board Orientation (⇄ in white), Previous Ply (⏪ in white),
 * Next Ply (⏩ in white/disabled grey when at end), and More Options (••• tinted #3B82F6).
 */
@Composable
fun AdaptiveFiveIconBottomToolbar(
    canStepBackward: Boolean,
    canStepForward: Boolean,
    onMenuClicked: () -> Unit,
    onFlipBoardClicked: () -> Unit,
    onPreviousPlyClicked: () -> Unit,
    onNextPlyClicked: () -> Unit,
    onMoreOptionsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF140C08).copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("five_icon_bottom_toolbar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClicked,
            modifier = Modifier.testTag("bottom_bar_menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Studio Menu",
                tint = ActionRoyalBlue,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = onFlipBoardClicked,
            modifier = Modifier.testTag("bottom_bar_flip_button")
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Flip Board Orientation",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = onPreviousPlyClicked,
            enabled = canStepBackward,
            modifier = Modifier.testTag("bottom_bar_prev_ply_button")
        ) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "Previous Ply",
                tint = if (canStepBackward) Color.White else Color(0xFF6B7280),
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = onNextPlyClicked,
            enabled = canStepForward,
            modifier = Modifier.testTag("bottom_bar_next_ply_button")
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Next Ply",
                tint = if (canStepForward) Color.White else Color(0xFF6B7280),
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = onMoreOptionsClicked,
            modifier = Modifier.testTag("bottom_bar_more_options_button")
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More Workbench Options",
                tint = ActionRoyalBlue,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun SmartEvaluationBar(
    evalCentipawns: Int,
    verticalLeftMode: Boolean,
    modifier: Modifier = Modifier
) {
    val clamped = evalCentipawns.coerceIn(-1000, 1000)
    val targetWhiteFraction = ((clamped + 1000f) / 2000f).coerceIn(0.05f, 0.95f)
    val animatedWhiteFraction by animateFloatAsState(
        targetValue = targetWhiteFraction,
        animationSpec = tween(durationMillis = 260),
        label = "evalBarFraction"
    )
    val formattedScore = remember(evalCentipawns) {
        val pawns = evalCentipawns / 100.0
        val sign = if (pawns >= 0) "+" else ""
        String.format("%s%.2f", sign, pawns)
    }

    if (verticalLeftMode) {
        Box(
            modifier = modifier
                .width(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E2630))
                .border(1.dp, Color(0xFF2B3947), RoundedCornerShape(6.dp))
                .testTag("eval_bar_vertical")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - animatedWhiteFraction)
                        .background(Color(0xFF1E2630))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(animatedWhiteFraction)
                        .background(Color(0xFFE2E8F0))
                )
            }
            Text(
                text = formattedScore,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (evalCentipawns >= 0) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                modifier = Modifier
                    .align(if (evalCentipawns >= 0) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(vertical = 4.dp)
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E2630))
                .border(1.dp, Color(0xFF2B3947), RoundedCornerShape(6.dp))
                .testTag("eval_bar_bottom")
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(animatedWhiteFraction)
                        .background(Color(0xFFE2E8F0))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f - animatedWhiteFraction)
                        .background(Color(0xFF1E2630))
                )
            }
            Text(
                text = "EVAL $formattedScore",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (evalCentipawns >= 0) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                modifier = Modifier
                    .align(if (evalCentipawns >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun InteractiveChessBoard(
    position: ChessPosition,
    boardTheme: BoardThemeOption,
    boardFlipped: Boolean,
    boardSizeScale: Float,
    selectedSquare: Int?,
    legalMovesForSelected: List<ChessMove>,
    showLegalMoveDots: Boolean,
    lastMove: ChessMove?,
    lastMoveStrength: MoveStrength?,
    colorLastMoveByStrength: Boolean,
    previousBestMove: ChessMove?,
    showPreviousBestMoveArrow: Boolean,
    multiPvLines: List<MultiPvLine>,
    showEngineArrows: Boolean,
    showEngineLineStrengthColors: Boolean,
    showAnalysisArrowsNumbers: Boolean,
    threats: List<TacticalThreat>,
    showThreats: Boolean,
    progressiveHint: ProgressiveHintState,
    drawnArrows: List<DrawnArrow>,
    highlightedSquares: List<HighlightedSquare>,
    enlargePieceOnDrag: Boolean,
    disableDragAndDrop: Boolean,
    drawArrowsOnBoard: Boolean,
    isArrowDrawMode: Boolean = drawArrowsOnBoard,
    pendingPromotionMove: ChessMove? = null,
    animationDurationMs: Int,
    fitToAvailableHeight: Boolean = false,
    onSquareTapped: (Int) -> Unit,
    onPieceDragStarted: (Int) -> Unit = {},
    onPieceDragged: (Int, Int) -> Unit,
    onUserArrowDrawn: (Int, Int) -> Unit = { from, to -> onPieceDragged(from, to) },
    onSelectPromotionPiece: (PieceType) -> Unit = {},
    onDismissPromotion: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Fix 2: Hold drag state in MutableState objects whose `.value` is ONLY read inside the Canvas draw scope
    // and pointerInput callbacks, so drag frames trigger draw-phase invalidation without recomposing InteractiveChessBoard!
    val draggingSquareState = remember { mutableStateOf<Int?>(null) }
    val hoverSquareState = remember { mutableStateOf<Int?>(null) }
    val dragPositionState = remember { mutableStateOf(Offset.Zero) }

    // Precompute all legal moves for the current position once per position change so the Canvas draw scope can filter without recomposition
    val allLegalMovesForPosition = remember(position) {
        position.generateLegalMoves()
    }

    val effectiveArrowMode = isArrowDrawMode || drawArrowsOnBoard

    // Subtle animation progress on ply changes
    val boardAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = animationDurationMs.coerceAtLeast(1)),
        label = "boardAnim"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val baseSpan = if (fitToAvailableHeight && maxHeight < maxWidth && maxHeight > 120.dp) {
            maxHeight
        } else {
            maxWidth
        }
        val sideDp = (baseSpan * boardSizeScale.coerceIn(0.70f, 1.0f))

        Surface(
            modifier = Modifier
                .size(sideDp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, Color(0xFF3E2418), RoundedCornerShape(4.dp))
                .testTag("interactive_chess_board"),
            color = boardTheme.darkSquare
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boardFlipped, position, effectiveArrowMode, disableDragAndDrop) {
                            detectTapGestures { offset ->
                                val cellW = size.width / 8f
                                val cellH = size.height / 8f
                                val visualCol = (offset.x / cellW).toInt().coerceIn(0, 7)
                                val visualRow = (offset.y / cellH).toInt().coerceIn(0, 7)
                                val sq = visualToSquare(visualRow, visualCol, boardFlipped)
                                onSquareTapped(sq)
                            }
                        }
                        .pointerInput(boardFlipped, position, disableDragAndDrop, effectiveArrowMode) {
                            if (!disableDragAndDrop || effectiveArrowMode) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val cellW = size.width / 8f
                                        val cellH = size.height / 8f
                                        val vCol = (startOffset.x / cellW).toInt().coerceIn(0, 7)
                                        val vRow = (startOffset.y / cellH).toInt().coerceIn(0, 7)
                                        val originSq = visualToSquare(vRow, vCol, boardFlipped)
                                        draggingSquareState.value = originSq
                                        hoverSquareState.value = originSq
                                        dragPositionState.value = startOffset
                                        // Fix 3: Bypass onPieceDragStarted when isArrowDrawMode is true
                                        if (!effectiveArrowMode) {
                                            onPieceDragStarted(originSq)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nextPos = change.position
                                        dragPositionState.value = nextPos
                                        val cellW = size.width / 8f
                                        val cellH = size.height / 8f
                                        val vCol = (nextPos.x / cellW).toInt().coerceIn(0, 7)
                                        val vRow = (nextPos.y / cellH).toInt().coerceIn(0, 7)
                                        hoverSquareState.value = visualToSquare(vRow, vCol, boardFlipped)
                                    },
                                    onDragEnd = {
                                        val fromSq = draggingSquareState.value
                                        val lastOffset = dragPositionState.value
                                        draggingSquareState.value = null
                                        hoverSquareState.value = null
                                        if (fromSq != null) {
                                            val cellW = size.width / 8f
                                            val cellH = size.height / 8f
                                            val vCol = (lastOffset.x / cellW).toInt().coerceIn(0, 7)
                                            val vRow = (lastOffset.y / cellH).toInt().coerceIn(0, 7)
                                            val toSq = visualToSquare(vRow, vCol, boardFlipped)
                                            // Fix 3: Route strictly to user arrow creation/deletion when isArrowDrawMode is true
                                            if (effectiveArrowMode) {
                                                onUserArrowDrawn(fromSq, toSq)
                                            } else {
                                                onPieceDragged(fromSq, toSq)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggingSquareState.value = null
                                        hoverSquareState.value = null
                                    }
                                )
                            }
                        }
                ) {
                    val cellSize = size.width / 8f
                    // Read drag states strictly inside Canvas DrawScope
                    val draggingSquare = draggingSquareState.value
                    val hoverSquare = hoverSquareState.value
                    val dragPosition = dragPositionState.value

                    val activeLegalMoves = if (draggingSquare != null && !effectiveArrowMode) {
                        val draggedPiece = position.pieceAt(draggingSquare)
                        if (draggedPiece != null && draggedPiece.side == position.turn) {
                            allLegalMovesForPosition.filter { it.from == draggingSquare }
                        } else {
                            emptyList()
                        }
                    } else {
                        legalMovesForSelected
                    }

                    // 1. Draw 64 squares + Last Move + Selected/Drag Origin + Live Target Hover Highlight + Custom Highlights + Hints
                    for (vRow in 0..7) {
                        for (vCol in 0..7) {
                            val sq = visualToSquare(vRow, vCol, boardFlipped)
                            val isLight = ((sq / 8) + (sq % 8)) % 2 == 0
                            val topLeft = Offset(vCol * cellSize, vRow * cellSize)

                            drawRect(
                                color = if (isLight) boardTheme.lightSquare else boardTheme.darkSquare,
                                topLeft = topLeft,
                                size = Size(cellSize, cellSize)
                            )

                            // LastMove & Quality Badge: Highlight origin and destination squares with a slate-blue tint (#7C8BA1 at 65% alpha)
                            if (lastMove != null && (sq == lastMove.from || sq == lastMove.to)) {
                                drawRect(
                                    color = LastMoveSlateBlue.copy(alpha = 0.65f),
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize)
                                )
                            }

                            // Selected square or Drag Origin highlight
                            if (selectedSquare == sq || (draggingSquare == sq && !effectiveArrowMode)) {
                                drawRect(
                                    color = TacticalCyan.copy(alpha = 0.42f),
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize)
                                )
                            }

                            // Live Target Hover Square Highlight during Drag-and-Drop
                            if (draggingSquare != null && hoverSquare == sq && !effectiveArrowMode) {
                                val isLegalTarget = activeLegalMoves.any { it.to == sq }
                                val hoverFill = if (isLegalTarget) {
                                    MoveBestColor.copy(alpha = 0.38f)
                                } else {
                                    TacticalCyan.copy(alpha = 0.22f)
                                }
                                val hoverBorder = if (isLegalTarget) MoveBestColor else TacticalCyan
                                drawRect(
                                    color = hoverFill,
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize)
                                )
                                drawRect(
                                    color = hoverBorder,
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }

                            // Custom Drawing Panel square highlights
                            val customHighlight = highlightedSquares.firstOrNull { it.square == sq }
                            if (customHighlight != null) {
                                drawRect(
                                    color = customHighlight.colorOption.color.copy(alpha = 0.48f),
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize)
                                )
                            }

                            // Progressive Hint Step 1 & Step 2 square focus rings
                            if (progressiveHint.currentStep >= 1 && progressiveHint.focusSquare == sq) {
                                drawRect(
                                    color = TacticalCyan.copy(alpha = 0.40f),
                                    topLeft = topLeft,
                                    size = Size(cellSize, cellSize)
                                )
                            }
                            if (progressiveHint.currentStep >= 2 && progressiveHint.targetSquare == sq) {
                                drawCircle(
                                    color = MoveBestColor,
                                    radius = cellSize * 0.42f,
                                    center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f),
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                        }
                    }

                    // 2. Draw rank numbers (1-8) along top-left inside edge of file a and file letters (a-h) along bottom-right inside edge of rank 1
                    for (i in 0..7) {
                        val rankNum = if (boardFlipped) (i + 1).toString() else (8 - i).toString()
                        val fileChar = if (boardFlipped) ('h' - i).toString() else ('a' + i).toString()
                        val leftColSq = visualToSquare(i, 0, boardFlipped)
                        val leftIsLight = ((leftColSq / 8) + (leftColSq % 8)) % 2 == 0
                        val bottomRowSq = visualToSquare(7, i, boardFlipped)
                        val bottomIsLight = ((bottomRowSq / 8) + (bottomRowSq % 8)) % 2 == 0

                        drawText(
                            textMeasurer = textMeasurer,
                            text = rankNum,
                            topLeft = Offset(3.dp.toPx(), i * cellSize + 2.dp.toPx()),
                            style = TextStyle(
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (leftIsLight) boardTheme.darkSquare else boardTheme.lightSquare
                            )
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = fileChar,
                            topLeft = Offset((i + 1) * cellSize - 10.dp.toPx(), size.height - 13.dp.toPx()),
                            style = TextStyle(
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bottomIsLight) boardTheme.darkSquare else boardTheme.lightSquare
                            )
                        )
                    }

                    // 3. Draw Legal Move Dot Indicators & Capture Rings (deduplicated by destination square)
                    if (showLegalMoveDots && activeLegalMoves.isNotEmpty() && !effectiveArrowMode) {
                        val distinctTargets = activeLegalMoves.map { it.to }.distinct()
                        for (targetSq in distinctTargets) {
                            val center = squareCenter(targetSq, cellSize, boardFlipped)
                            val isHoveredLegal = draggingSquare != null && hoverSquare == targetSq
                            if (position.pieceAt(targetSq) != null) {
                                // Capture ring
                                drawCircle(
                                    color = if (isHoveredLegal) MoveBestColor else Color(0xFF0F172A).copy(alpha = 0.50f),
                                    radius = cellSize * 0.41f,
                                    center = center,
                                    style = Stroke(width = 4.5.dp.toPx())
                                )
                            } else {
                                // Legal dot
                                drawCircle(
                                    color = if (isHoveredLegal) MoveBestColor else Color(0xFF0F172A).copy(alpha = 0.40f),
                                    radius = if (isHoveredLegal) cellSize * 0.21f else cellSize * 0.16f,
                                    center = center
                                )
                            }
                        }
                    }

                    // 4. Draw Chess Pieces + Circular Green Checkmark Quality Badge (✓) in top-left corner of last moved piece's square
                    for (sq in 0..63) {
                        if (draggingSquare == sq && !effectiveArrowMode) continue
                        val piece = position.pieceAt(sq) ?: continue
                        val center = squareCenter(sq, cellSize, boardFlipped)
                        drawPieceOnCanvas(
                            textMeasurer = textMeasurer,
                            pieceSymbol = piece.type.figurine(piece.side),
                            isWhite = piece.side == Side.WHITE,
                            center = center,
                            cellSize = cellSize,
                            scale = boardAlpha
                        )
                    }

                    if (lastMove != null) {
                        val destRow = lastMove.to / 8
                        val destCol = lastMove.to % 8
                        val vRow = if (boardFlipped) 7 - destRow else destRow
                        val vCol = if (boardFlipped) 7 - destCol else destCol
                        val badgeCenter = Offset(
                            vCol * cellSize + cellSize * 0.22f,
                            vRow * cellSize + cellSize * 0.22f
                        )
                        val badgeRadius = (cellSize * 0.16f).coerceAtLeast(7.dp.toPx())
                        drawCircle(
                            color = MovePillBrightGreen,
                            radius = badgeRadius,
                            center = badgeCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = badgeRadius,
                            center = badgeCenter,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        val checkMeasured = textMeasurer.measure(
                            text = "✓",
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        drawText(
                            textLayoutResult = checkMeasured,
                            topLeft = Offset(
                                badgeCenter.x - checkMeasured.size.width / 2f,
                                badgeCenter.y - checkMeasured.size.height / 2f
                            )
                        )
                    }

                    // 5. Previous Best-Move Arrow (faint transparent arrow from previous position)
                    if (showPreviousBestMoveArrow && previousBestMove != null) {
                        drawBoardArrow(
                            fromSq = previousBestMove.from,
                            toSq = previousBestMove.to,
                            cellSize = cellSize,
                            boardFlipped = boardFlipped,
                            color = BestMoveIndigoArrow.copy(alpha = 0.45f),
                            strokeWidthPx = 6.dp.toPx(),
                            numberLabel = null,
                            textMeasurer = textMeasurer
                        )
                    }

                    // 6. Tactical Threats Arrows (crimson arrows)
                    if (showThreats && threats.isNotEmpty()) {
                        for (threat in threats.take(2)) {
                            drawBoardArrow(
                                fromSq = threat.attackerSquare,
                                toSq = threat.targetSquare,
                                cellSize = cellSize,
                                boardFlipped = boardFlipped,
                                color = MoveBlunderColor.copy(alpha = 0.65f),
                                strokeWidthPx = 5.dp.toPx(),
                                numberLabel = null,
                                textMeasurer = textMeasurer
                            )
                        }
                    }

                    // 7. Best Move Arrow: Semi-transparent royal-blue/indigo vector arrow (#3B5BDB at 75% alpha) from best move's origin to target square
                    if (showEngineArrows && multiPvLines.isNotEmpty()) {
                        val showNumbers = showAnalysisArrowsNumbers && multiPvLines.size > 1
                        for (line in multiPvLines.reversed()) {
                            val arrowColor = if (line.rank == 1) {
                                BestMoveIndigoArrow.copy(alpha = 0.75f)
                            } else if (showEngineLineStrengthColors) {
                                BestMoveIndigoArrow.copy(alpha = 0.50f)
                            } else {
                                TacticalCyan.copy(alpha = 0.60f)
                            }
                            val widthPx = if (line.rank == 1) 9.dp.toPx() else 6.dp.toPx()
                            drawBoardArrow(
                                fromSq = line.firstMove.from,
                                toSq = line.firstMove.to,
                                cellSize = cellSize,
                                boardFlipped = boardFlipped,
                                color = arrowColor,
                                strokeWidthPx = widthPx,
                                numberLabel = if (showNumbers) line.rank.toString() else null,
                                textMeasurer = textMeasurer
                            )
                        }
                    }

                    // 8. Progressive Hint Step 3 Full Move Arrow
                    if (progressiveHint.currentStep == 3 && progressiveHint.bestMove != null) {
                        drawBoardArrow(
                            fromSq = progressiveHint.bestMove.from,
                            toSq = progressiveHint.bestMove.to,
                            cellSize = cellSize,
                            boardFlipped = boardFlipped,
                            color = TacticalCyan,
                            strokeWidthPx = 9.dp.toPx(),
                            numberLabel = "★",
                            textMeasurer = textMeasurer
                        )
                    }

                    // 9. Custom User Freehand Arrows + Live Arrow Drag Preview
                    for (userArrow in drawnArrows) {
                        drawBoardArrow(
                            fromSq = userArrow.from,
                            toSq = userArrow.to,
                            cellSize = cellSize,
                            boardFlipped = boardFlipped,
                            color = userArrow.colorOption.color.copy(alpha = 0.85f),
                            strokeWidthPx = 7.dp.toPx(),
                            numberLabel = null,
                            textMeasurer = textMeasurer
                        )
                    }
                    if (effectiveArrowMode && draggingSquare != null && hoverSquare != null && draggingSquare != hoverSquare) {
                        drawBoardArrow(
                            fromSq = draggingSquare,
                            toSq = hoverSquare,
                            cellSize = cellSize,
                            boardFlipped = boardFlipped,
                            color = TacticalCyan.copy(alpha = 0.72f),
                            strokeWidthPx = 7.dp.toPx(),
                            numberLabel = null,
                            textMeasurer = textMeasurer
                        )
                    }

                    // 10. Enlarged Piece on Drag preview floating under pointer
                    if (draggingSquare != null && !effectiveArrowMode) {
                        val draggedPiece = position.pieceAt(draggingSquare)
                        if (draggedPiece != null) {
                            val scale = if (enlargePieceOnDrag) 1.42f else 1.05f
                            drawPieceOnCanvas(
                                textMeasurer = textMeasurer,
                                pieceSymbol = draggedPiece.type.figurine(draggedPiece.side),
                                isWhite = draggedPiece.side == Side.WHITE,
                                center = Offset(dragPosition.x, dragPosition.y - (if (enlargePieceOnDrag) cellSize * 0.35f else 0f)),
                                cellSize = cellSize,
                                scale = scale
                            )
                        }
                    }
                }

                // Fix 4: Inline 4-Piece Pawn Promotion Overlay (Q, N, R, B) over the target file on the board
                if (pendingPromotionMove != null) {
                    val targetCol = pendingPromotionMove.to % 8
                    val visualCol = if (boardFlipped) 7 - targetCol else targetCol
                    val targetRow = pendingPromotionMove.to / 8
                    val visualRow = if (boardFlipped) 7 - targetRow else targetRow
                    val cellDp = sideDp / 8f
                    val overlayX = (cellDp * visualCol).coerceIn(0.dp, (sideDp - cellDp).coerceAtLeast(0.dp))
                    val startsAtTop = visualRow <= 3
                    val overlayY = if (startsAtTop) 0.dp else (sideDp - cellDp * 4f).coerceAtLeast(0.dp)
                    val promoSide = pendingPromotionMove.piece.side
                    val promotionChoices = listOf(
                        PieceType.QUEEN to "Q",
                        PieceType.KNIGHT to "N",
                        PieceType.ROOK to "R",
                        PieceType.BISHOP to "B"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E1318).copy(alpha = 0.45f))
                            .clickable { onDismissPromotion() }
                            .testTag("pawn_promotion_scrim")
                    ) {
                        Column(
                            modifier = Modifier
                                .offset(x = overlayX, y = overlayY)
                                .width(cellDp.coerceAtLeast(48.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateSurface)
                                .border(2.dp, TacticalCyan, RoundedCornerShape(10.dp))
                                .padding(vertical = 4.dp)
                                .testTag("pawn_promotion_overlay"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            promotionChoices.forEach { (pieceType, code) ->
                                Box(
                                    modifier = Modifier
                                        .size((cellDp - 6.dp).coerceAtLeast(40.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateSurfaceVariant)
                                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                        .clickable { onSelectPromotionPiece(pieceType) }
                                        .testTag("promotion_choice_${pieceType.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = pieceType.figurine(promoSide),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = code,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = RobotoMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = TacticalCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Last Move Classification Pill on destination square (if Color Last Move by Strength is enabled)
                if (colorLastMoveByStrength && lastMove != null && lastMoveStrength != null && lastMoveStrength.symbol.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(moveStrengthColor(lastMoveStrength))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${lastMove.san}${lastMoveStrength.symbol}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF090D11)
                        )
                    }
                }
            }
        }
    }
}

private fun visualToSquare(vRow: Int, vCol: Int, flipped: Boolean): Int {
    val actualRow = if (flipped) 7 - vRow else vRow
    val actualCol = if (flipped) 7 - vCol else vCol
    return actualRow * 8 + actualCol
}

private fun squareCenter(sq: Int, cellSize: Float, flipped: Boolean): Offset {
    val row = sq / 8
    val col = sq % 8
    val vRow = if (flipped) 7 - row else row
    val vCol = if (flipped) 7 - col else col
    return Offset((vCol + 0.5f) * cellSize, (vRow + 0.5f) * cellSize)
}

private fun DrawScope.drawPieceOnCanvas(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    pieceSymbol: String,
    isWhite: Boolean,
    center: Offset,
    cellSize: Float,
    scale: Float
) {
    val radius = cellSize * 0.36f * scale
    drawCircle(
        color = if (isWhite) Color(0xFFF8FAFC) else Color(0xFF111827),
        radius = radius,
        center = center
    )
    drawCircle(
        color = if (isWhite) Color(0xFF1E293B) else Color(0xFF94A3B8),
        radius = radius,
        center = center,
        style = Stroke(width = 1.8.dp.toPx())
    )
    val fontSizeSp = (cellSize * 0.44f * scale / density).sp
    val layoutResult = textMeasurer.measure(
        text = pieceSymbol,
        style = TextStyle(
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Bold,
            color = if (isWhite) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        )
    )
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            center.x - layoutResult.size.width / 2f,
            center.y - layoutResult.size.height / 2f
        )
    )
}

private fun DrawScope.drawBoardArrow(
    fromSq: Int,
    toSq: Int,
    cellSize: Float,
    boardFlipped: Boolean,
    color: Color,
    strokeWidthPx: Float,
    numberLabel: String?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val start = squareCenter(fromSq, cellSize, boardFlipped)
    val end = squareCenter(toSq, cellSize, boardFlipped)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val headLen = cellSize * 0.32f
    val lineEnd = Offset(
        end.x - headLen * 0.65f * cos(angle),
        end.y - headLen * 0.65f * sin(angle)
    )

    drawLine(
        color = color,
        start = start,
        end = lineEnd,
        strokeWidth = strokeWidthPx,
        cap = StrokeCap.Round
    )

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(
            end.x - headLen * cos(angle - 0.55f),
            end.y - headLen * sin(angle - 0.55f)
        )
        lineTo(
            end.x - headLen * cos(angle + 0.55f),
            end.y - headLen * sin(angle + 0.55f)
        )
        close()
    }
    drawPath(path = path, color = color)

    if (numberLabel != null) {
        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        drawCircle(
            color = Color(0xFF0E1318),
            radius = 9.dp.toPx(),
            center = mid
        )
        drawCircle(
            color = color,
            radius = 9.dp.toPx(),
            center = mid,
            style = Stroke(width = 1.5.dp.toPx())
        )
        val measured = textMeasurer.measure(
            text = numberLabel,
            style = TextStyle(
                fontFamily = RobotoMonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(mid.x - measured.size.width / 2f, mid.y - measured.size.height / 2f)
        )
    }
}
