package com.example.ui.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.CapturedSummary
import com.example.engine.ChessMove
import com.example.engine.ChessPosition
import com.example.engine.EngineConfig
import com.example.engine.MoveStrength
import com.example.engine.MultiPvLine
import com.example.engine.PieceType
import com.example.engine.ProgressiveHintState
import com.example.engine.Side
import com.example.engine.TacticalThreat
import com.example.service.UciServiceState
import com.example.ui.theme.BoardThemeOption
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.MoveBlunderColor
import com.example.ui.theme.MoveBookColor
import com.example.ui.theme.MoveGoodColor
import com.example.ui.theme.MoveInaccuracyColor
import com.example.ui.theme.MoveMistakeColor
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
    // Notation strip props
    moveHistory: List<ChessMove>,
    moveStrengths: List<MoveStrength>,
    currentPlyIndex: Int,
    figurineNotation: Boolean,
    variationMoveSymbols: Boolean,
    variations: List<VariationBranch>,
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
    modifier: Modifier = Modifier
) {
    val topSide = if (boardFlipped) Side.WHITE else Side.BLACK
    val bottomSide = if (boardFlipped) Side.BLACK else Side.WHITE
    val hideEngineForFairPlay = isUnassistedRankedSession && !isTaintedByEngine

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chess_board_component"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Engine Status Header positioned directly above the board (isolates high-frequency StateFlow telemetry collection)
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

        // 2. Top Player Strip
        PlayerRowStrip(
            playerName = if (topSide == Side.WHITE) whitePlayerName else blackPlayerName,
            side = topSide,
            isTurn = position.turn == topSide,
            showCapturedPieces = showCapturedPieces,
            capturedSummary = capturedSummary
        )

        // 3. Interactive 8x8 ChessBoard Canvas (Pieces, Draw-Phase-Isolated Drag-and-Drop, Legal Move Dots & Inline Pawn Promotion Overlay)
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
            onSquareTapped = onSquareTapped,
            onPieceDragStarted = onPieceDragStarted,
            onPieceDragged = onPieceDragged,
            onUserArrowDrawn = onUserArrowDrawn,
            onSelectPromotionPiece = onSelectPromotionPiece,
            onDismissPromotion = onDismissPromotion
        )

        // 4. Bottom Player Strip
        PlayerRowStrip(
            playerName = if (bottomSide == Side.WHITE) whitePlayerName else blackPlayerName,
            side = bottomSide,
            isTurn = position.turn == bottomSide,
            showCapturedPieces = showCapturedPieces,
            capturedSummary = capturedSummary
        )

        // 5. Smart Evaluation Bar (hidden when unassisted ranked anti-cheat lock is active)
        if (smartEvalBar && !hideEngineForFairPlay) {
            SmartEvaluationBar(
                evalCentipawns = evalCentipawns,
                verticalLeftMode = false
            )
        }

        // 6. Move Notation Strip positioned directly below the board (with isArrowDrawMode toggle next to ply buttons)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (moves.isEmpty()) {
                Text(
                    text = "1. Drag or tap any piece on the board to record algebraic move notation...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                moves.forEachIndexed { index, move ->
                    val plyNumber = index + 1
                    val isSelected = currentPlyIndex == plyNumber
                    val strength = moveStrengths.getOrNull(index) ?: MoveStrength.GOOD
                    val moveNumberPrefix = if (index % 2 == 0) "${(index / 2) + 1}." else ""
                    val formattedMove = move.formatNotation(
                        useFigurine = figurineNotation,
                        showSymbols = variationMoveSymbols,
                        strength = strength
                    )
                    val moveText = if (moveNumberPrefix.isNotEmpty()) "$moveNumberPrefix $formattedMove" else formattedMove

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) TacticalCyan.copy(alpha = 0.24f)
                                else SlateSurfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) TacticalCyan else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onJumpToPly(plyNumber) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("notation_move_chip_$plyNumber")
                    ) {
                        Text(
                            text = moveText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (strength == MoveStrength.BLUNDER || strength == MoveStrength.MISTAKE) {
                                moveStrengthColor(strength)
                            } else {
                                TextPrimary
                            }
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
                        .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
                        .clickable { onVariationClicked(branch) }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "($text)",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                        color = TextSecondary
                    )
                }
            }
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
        val sideDp = (maxWidth * boardSizeScale.coerceIn(0.70f, 1.0f))

        Surface(
            modifier = Modifier
                .size(sideDp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, Color(0xFF2B3947), RoundedCornerShape(8.dp))
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

                            // Last move highlight (optionally colored by move strength)
                            if (lastMove != null && (sq == lastMove.from || sq == lastMove.to)) {
                                val strengthTint = if (colorLastMoveByStrength && lastMoveStrength != null) {
                                    moveStrengthColor(lastMoveStrength).copy(alpha = 0.45f)
                                } else {
                                    Color(0xFFFACC15).copy(alpha = 0.36f)
                                }
                                drawRect(
                                    color = strengthTint,
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

                    // 2. Draw rank & file coordinates along board edges
                    for (i in 0..7) {
                        val rankNum = if (boardFlipped) (i + 1).toString() else (8 - i).toString()
                        val fileChar = if (boardFlipped) ('h' - i).toString() else ('a' + i).toString()

                        drawText(
                            textMeasurer = textMeasurer,
                            text = rankNum,
                            topLeft = Offset(3.dp.toPx(), i * cellSize + 2.dp.toPx()),
                            style = TextStyle(
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = boardTheme.coordColor
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
                                color = boardTheme.coordColor
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

                    // 4. Draw Chess Pieces (Unicode high-contrast silhouettes with crisp stroke badge)
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

                    // 5. Previous Best-Move Arrow (faint transparent arrow from previous position)
                    if (showPreviousBestMoveArrow && previousBestMove != null) {
                        drawBoardArrow(
                            fromSq = previousBestMove.from,
                            toSq = previousBestMove.to,
                            cellSize = cellSize,
                            boardFlipped = boardFlipped,
                            color = Color(0xFF94A3B8).copy(alpha = 0.38f),
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

                    // 7. Global Toggle: Show Engine-Line Arrows on Board (with Strength Colors & Arrow Numbers)
                    if (showEngineArrows && multiPvLines.isNotEmpty()) {
                        val showNumbers = showAnalysisArrowsNumbers && multiPvLines.size > 1
                        for (line in multiPvLines.reversed()) {
                            val arrowColor = if (line.rank == 1) {
                                MoveBestColor.copy(alpha = 0.88f)
                            } else if (showEngineLineStrengthColors) {
                                moveStrengthColor(line.strengthCategory).copy(alpha = 0.76f)
                            } else {
                                TacticalCyan.copy(alpha = 0.65f)
                            }
                            val widthPx = if (line.rank == 1) 8.5.dp.toPx() else 6.dp.toPx()
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
