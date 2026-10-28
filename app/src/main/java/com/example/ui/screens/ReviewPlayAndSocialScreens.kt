package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.example.bluetooth.DiscoveredEBoard
import com.example.bluetooth.EBoardBrand
import com.example.bluetooth.EBoardConnectionState
import com.example.data.AnalyzedGameEntity
import com.example.data.FriendChallengeEntity
import com.example.engine.ChessMove
import com.example.engine.ChessPiece
import com.example.engine.FenScenarioPreset
import com.example.engine.MoveStrength
import com.example.engine.PieceType
import com.example.engine.PlayingStyleProfile
import com.example.engine.PlyReviewItem
import com.example.engine.Side
import com.example.ui.components.CoachModeGuidancePanel
import com.example.ui.components.FenPositionImportPanel
import com.example.ui.components.InteractiveChessBoard
import com.example.ui.components.SmartEvaluationBar
import com.example.ui.components.moveStrengthColor
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.MoveBlunderColor
import com.example.ui.theme.MoveMistakeColor
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CheesseUiState
import com.example.viewmodel.LeaderboardPlayerEntry
import com.example.viewmodel.LeaderboardScope
import com.example.viewmodel.LeaderboardSortMetric
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private val LeaderboardCardBackground = Color(0xFF161E26)
private val LeaderboardCardBorder = Color(0xFF263240)

@Composable
fun DeepGameReportScreen(
    uiState: CheesseUiState,
    onToggleShowAvgCpl: (Boolean) -> Unit,
    onPlayWhyBlunderLine: (PlyReviewItem) -> Unit,
    onJumpToPly: (Int) -> Unit,
    onSquareTapped: (Int) -> Unit,
    onPieceDragged: (Int, Int) -> Unit,
    onUserArrowDrawn: (Int, Int) -> Unit = { from, to -> onPieceDragged(from, to) },
    onSelectPromotionPiece: (PieceType) -> Unit = {},
    onDismissPromotion: () -> Unit = {}
) {
    val report = uiState.deepReport
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deep_game_report_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Smart Evaluation Bar moves to the LEFT on report screens!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.smartEvalBar && !uiState.hideEngineTelemetryForFairPlay) {
                SmartEvaluationBar(
                    evalCentipawns = uiState.evalCentipawns,
                    verticalLeftMode = true,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InteractiveChessBoard(
                    position = uiState.position,
                    boardTheme = uiState.boardTheme,
                    boardFlipped = uiState.boardFlipped,
                    boardSizeScale = 0.86f,
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
                    onSquareTapped = onSquareTapped,
                    onPieceDragged = onPieceDragged,
                    onUserArrowDrawn = onUserArrowDrawn,
                    onSelectPromotionPiece = onSelectPromotionPiece,
                    onDismissPromotion = onDismissPromotion
                )
            }
        }

        if (report != null) {
            // Accuracy & Show Average CPL Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DEEP GAME REPORT · ${report.gameTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Show Avg CPL",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = uiState.showAverageCplInReport,
                            onCheckedChange = onToggleShowAvgCpl,
                            modifier = Modifier.testTag("toggle_show_avg_cpl")
                        )
                    }
                }

                // Player Accuracy + Optional Average CPL Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(text = "White: ${report.whitePlayer}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = "${report.whiteAccuracy}% Accuracy",
                            style = MaterialTheme.typography.titleLarge,
                            color = MoveBestColor
                        )
                        if (uiState.showAverageCplInReport) {
                            Text(
                                text = "Avg CPL: ${report.whiteAvgCpl}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TacticalCyan,
                                modifier = Modifier.testTag("white_avg_cpl_text")
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(text = "Black: ${report.blackPlayer}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = "${report.blackAccuracy}% Accuracy",
                            style = MaterialTheme.typography.titleLarge,
                            color = MoveBestColor
                        )
                        if (uiState.showAverageCplInReport) {
                            Text(
                                text = "Avg CPL: ${report.blackAvgCpl}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TacticalCyan,
                                modifier = Modifier.testTag("black_avg_cpl_text")
                            )
                        }
                    }
                }

                // Classification breakdown row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        MoveStrength.BEST,
                        MoveStrength.GOOD,
                        MoveStrength.INACCURACY,
                        MoveStrength.MISTAKE,
                        MoveStrength.BLUNDER
                    ).forEach { str ->
                        val wCount = report.whiteCounts[str] ?: 0
                        val bCount = report.blackCounts[str] ?: 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = str.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = moveStrengthColor(str)
                            )
                            Text(
                                text = "$wCount / $bCount",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Active Blunder Refutation Banner
            if (uiState.activeWhyBlunderLine.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MoveBlunderColor.copy(alpha = 0.16f))
                        .border(1.dp, MoveBlunderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Why Blunder Refutation Line: ${uiState.activeWhyBlunderLine.joinToString(" → ")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }

            // Ply-by-Ply Review & "Play / Show Why Blunder Line" Interactive Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MOVE-BY-MOVE DIAGNOSTICS & REFUTATIONS",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TacticalCyan
                )

                report.plyReviews.forEach { item ->
                    val isSuboptimal = item.strength == MoveStrength.INACCURACY ||
                        item.strength == MoveStrength.MISTAKE ||
                        item.strength == MoveStrength.BLUNDER

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurfaceVariant.copy(alpha = 0.65f))
                            .clickable { onJumpToPly(item.plyIndex + 1) }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.moveNumber}${if (item.side == Side.WHITE) "." else "..."} ${item.playedMove.san}${item.strength.symbol}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = moveStrengthColor(item.strength)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${item.strength.label} (-${item.cpLoss} cp)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }

                            if (isSuboptimal || item.whyBlunderRefutationMoves.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { onPlayWhyBlunderLine(item) },
                                    modifier = Modifier.testTag("play_why_blunder_${item.plyIndex}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Why Blunder Line",
                                        modifier = Modifier.size(16.dp),
                                        tint = MoveBlunderColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Play Why Line",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.whyExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayAndArchiveTrainerScreen(
    uiState: CheesseUiState,
    cloudGames: List<AnalyzedGameEntity>,
    onToggleVsEnginePlay: (Boolean) -> Unit,
    onToggleShowEachMoveStrength: (Boolean) -> Unit,
    onTogglePauseOnMistake: (Boolean) -> Unit,
    onToggleArchiveSelection: (String) -> Unit,
    onLoadArchivePreset: (com.example.viewmodel.ArchiveStudyPreset) -> Unit,
    onAnalyzeMultipleInBackground: () -> Unit,
    onStartRetryMistakesMultiGame: () -> Unit,
    onNextPuzzle: () -> Unit,
    onAdvanceHint: () -> Unit,
    onSquareTapped: (Int) -> Unit,
    onPieceDragged: (Int, Int) -> Unit,
    onUserArrowDrawn: (Int, Int) -> Unit = { from, to -> onPieceDragged(from, to) },
    onSelectPromotionPiece: (PieceType) -> Unit = {},
    onDismissPromotion: () -> Unit = {},
    onStartRankedSession: () -> Unit = {},
    onExitRankedSession: () -> Unit = {},
    onToggleCoachMode: (Boolean) -> Unit = {},
    onSelectPlayingStyle: (PlayingStyleProfile) -> Unit = {},
    onPlaySuggestedMove: (ChessMove) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("play_and_archive_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Real-Time Move Strength While Playing, Anti-Cheat Ranked Session & Pause On Mistake controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isRetryMistakesMode) {
                            "RETRY MISTAKES TRAINER (${uiState.currentPuzzleIndex + 1}/${uiState.mistakePuzzles.size})"
                        } else {
                            "LIVE ENGINE SPARRING & RANKED CHALLENGE"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MoveBestColor
                    )
                    if (uiState.showEachMoveStrengthWhilePlaying && uiState.lastMove != null && uiState.lastMoveStrength != null) {
                        Text(
                            text = "Last Move Strength: ${uiState.lastMove.san} → ${uiState.lastMoveStrength.label}${uiState.lastMoveStrength.symbol}",
                            style = MaterialTheme.typography.labelLarge,
                            color = moveStrengthColor(uiState.lastMoveStrength)
                        )
                    }
                }

                Button(
                    onClick = { onToggleVsEnginePlay(!uiState.isVsEnginePlayActive) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isVsEnginePlayActive) MoveBlunderColor else MoveBestColor
                    ),
                    modifier = Modifier.testTag("toggle_vs_engine_button")
                ) {
                    Text(
                        text = if (uiState.isVsEnginePlayActive) "Stop Sparring" else "Play vs Engine",
                        color = Color(0xFF090D11)
                    )
                }
            }

            // Anti-Cheat Ranked Mode Row (Pauses UciEngineService, hides eval bar/PV line, tracks taint)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateSurfaceVariant)
                    .border(
                        width = 1.dp,
                        color = when {
                            uiState.isTaintedByEngine -> MoveBlunderColor
                            uiState.isUnassistedRankedSession -> TacticalCyan
                            else -> SlateBorder
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("ranked_anti_cheat_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isTaintedByEngine) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = "Anti-Cheat Status",
                        tint = when {
                            uiState.isTaintedByEngine -> MoveBlunderColor
                            uiState.isUnassistedRankedSession -> TacticalCyan
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = when {
                                uiState.isTaintedByEngine -> "SESSION TAINTED BY ENGINE (UNRANKED)"
                                uiState.isUnassistedRankedSession -> "UNASSISTED RANKED MODE (CLEAN)"
                                else -> "ASSISTED ANALYSIS MODE"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                uiState.isTaintedByEngine -> MoveBlunderColor
                                uiState.isUnassistedRankedSession -> TacticalCyan
                                else -> TextPrimary
                            }
                        )
                        Text(
                            text = if (uiState.isUnassistedRankedSession) {
                                "Stockfish eval bar & candidate lines locked. Viewing hints taints session."
                            } else {
                                "Start Ranked Mode to lock engine telemetry and qualify for Leaderboard."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (uiState.isUnassistedRankedSession) onExitRankedSession() else onStartRankedSession()
                    },
                    modifier = Modifier.testTag("toggle_ranked_session_button")
                ) {
                    Text(
                        text = if (uiState.isUnassistedRankedSession) "Exit Ranked" else "Start Ranked",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.isUnassistedRankedSession) MoveMistakeColor else TacticalCyan
                    )
                }
            }

            if (uiState.pausedMistakeAlert != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MoveMistakeColor.copy(alpha = 0.18f))
                        .border(1.dp, MoveMistakeColor, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .testTag("pause_on_mistake_banner")
                ) {
                    Text(
                        text = uiState.pausedMistakeAlert,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            if (uiState.isRetryMistakesMode && uiState.puzzleFeedback != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TacticalCyan.copy(alpha = 0.16f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.puzzleFeedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onAdvanceHint) {
                            Text("Hint (${uiState.progressiveHint.currentStep}/3)")
                        }
                        Button(onClick = onNextPuzzle, modifier = Modifier.testTag("next_mistake_puzzle_button")) {
                            Text("Next")
                        }
                    }
                }
            }
        }

        // Interactive Board for Sparring / Mistake Retry
        InteractiveChessBoard(
            position = uiState.position,
            boardTheme = uiState.boardTheme,
            boardFlipped = uiState.boardFlipped,
            boardSizeScale = uiState.boardSizeScale,
            selectedSquare = uiState.selectedSquare,
            legalMovesForSelected = uiState.legalMovesForSelected,
            showLegalMoveDots = uiState.showLegalMoveDots,
            lastMove = uiState.lastMove,
            lastMoveStrength = uiState.lastMoveStrength,
            colorLastMoveByStrength = uiState.colorLastMoveByStrength,
            previousBestMove = uiState.previousBestMove,
            showPreviousBestMoveArrow = uiState.showPreviousBestMoveArrow && !uiState.hideEngineTelemetryForFairPlay,
            multiPvLines = if (uiState.hideEngineTelemetryForFairPlay) emptyList() else uiState.multiPvLines,
            showEngineArrows = uiState.showEngineArrows && !uiState.hideEngineTelemetryForFairPlay,
            showEngineLineStrengthColors = uiState.showEngineLineStrengthColors,
            showAnalysisArrowsNumbers = uiState.showAnalysisArrowsNumbers,
            threats = uiState.threats,
            showThreats = uiState.showThreats && !uiState.hideEngineTelemetryForFairPlay,
            progressiveHint = uiState.progressiveHint,
            drawnArrows = uiState.drawnArrows,
            highlightedSquares = uiState.highlightedSquares,
            enlargePieceOnDrag = uiState.enlargePieceOnDrag,
            disableDragAndDrop = uiState.disableDragAndDrop,
            drawArrowsOnBoard = uiState.drawArrowsOnBoard,
            isArrowDrawMode = uiState.isArrowDrawMode,
            pendingPromotionMove = uiState.pendingPromotionMove,
            animationDurationMs = uiState.effectiveAnimationDurationMs,
            onSquareTapped = onSquareTapped,
            onPieceDragged = onPieceDragged,
            onUserArrowDrawn = onUserArrowDrawn,
            onSelectPromotionPiece = onSelectPromotionPiece,
            onDismissPromotion = onDismissPromotion
        )

        // Coach Mode Guidance integrated with Progressive 3-Step Hints during Play & Training
        CoachModeGuidancePanel(
            coachModeActive = uiState.coachModeActive,
            userPlayingStyle = uiState.userPlayingStyle,
            coachSuggestions = uiState.coachSuggestions,
            progressiveHint = uiState.progressiveHint,
            figurineNotation = uiState.figurineNotation,
            onToggleCoachMode = onToggleCoachMode,
            onSelectPlayingStyle = onSelectPlayingStyle,
            onAdvanceHint = onAdvanceHint,
            onPlaySuggestedMove = onPlaySuggestedMove
        )

        // Games Archive: Analyze Multiple Games in Background + Retry Mistakes for Multiple Games
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .testTag("games_archive_section"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GAMES ARCHIVE · MULTI-GAME BATCH ANALYSIS & MISTAKE RETRY",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = TacticalCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyzeMultipleInBackground,
                    enabled = !uiState.isBatchAnalyzingBackground && uiState.selectedArchiveIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyze_multiple_background_button")
                ) {
                    Text(
                        text = "Analyze Selected (${uiState.selectedArchiveIds.size})",
                        color = Color(0xFF062536),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onStartRetryMistakesMultiGame,
                    colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("retry_mistakes_multi_game_button")
                ) {
                    Text(
                        text = "Retry Mistakes (${uiState.selectedArchiveIds.size})",
                        color = Color(0xFF042F16),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (uiState.batchAnalysisProgressText.isNotEmpty()) {
                Text(
                    text = uiState.batchAnalysisProgressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MoveBestColor
                )
            }

            uiState.archiveGames.forEach { preset ->
                val isSelected = preset.id in uiState.selectedArchiveIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceVariant)
                        .clickable { onLoadArchivePreset(preset) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleArchiveSelection(preset.id) }
                        )
                        Column {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${preset.whitePlayer} vs ${preset.blackPlayer} · " +
                                    if (preset.analyzed) "Acc ${preset.whiteAccuracy}%/${preset.blackAccuracy}% · ${preset.mistakeCount} mistakes"
                                    else "Pending Deep Analysis",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialChallengesAndEBoardScreen(
    uiState: CheesseUiState,
    openChallenges: List<FriendChallengeEntity>,
    eBoardState: EBoardConnectionState,
    discoveredBoards: List<DiscoveredEBoard>,
    onCreateChallenge: () -> Unit,
    onJoinChallenge: (FriendChallengeEntity) -> Unit,
    onScanEBoard: (EBoardBrand) -> Unit,
    onConnectEBoard: (DiscoveredEBoard) -> Unit,
    onDisconnectEBoard: () -> Unit,
    onSelectLeaderboardScope: (LeaderboardScope) -> Unit = {},
    onSelectLeaderboardSortMetric: (LeaderboardSortMetric) -> Unit = {},
    onRecordUserVictory: () -> Unit = {},
    onAddFriendToLeaderboard: (String) -> Unit = {},
    onGoogleSignInIdTokenReceived: (String) -> Unit = {},
    onStartRankedSession: () -> Unit = {},
    onExitRankedSession: () -> Unit = {},
    onSyncPendingGames: () -> Unit = {}
) {
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onScanEBoard(EBoardBrand.CHESSNUT)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("social_and_eboard_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Global & Friends Leaderboards (Tactical Slate Styling + Lazy Google Account Linking + Offline Sync)
        LeaderboardsCardSection(
            uiState = uiState,
            onSelectScope = onSelectLeaderboardScope,
            onSelectSortMetric = onSelectLeaderboardSortMetric,
            onRecordUserVictory = onRecordUserVictory,
            onAddFriend = { onAddFriendToLeaderboard("TacticalFriend_${(10..99).random()}") },
            onGoogleSignInIdTokenReceived = onGoogleSignInIdTokenReceived,
            onStartRankedSession = onStartRankedSession,
            onExitRankedSession = onExitRankedSession,
            onSyncPendingGames = onSyncPendingGames
        )

        // 1. Live Real-Time Friend Challenges with Shareable Codes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE FRIEND CHALLENGES",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoveBestColor
                    )
                    Text(
                        text = "Create a real-time cloud match room or join a friend's invite code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onCreateChallenge,
                    colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                    modifier = Modifier.testTag("create_friend_challenge_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Create Challenge",
                        tint = Color(0xFF042F16),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Room", color = Color(0xFF042F16))
                }
            }

            if (openChallenges.isEmpty()) {
                Text(
                    text = "No open friend challenge rooms right now. Tap 'New Room' to generate a shareable 6-character code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else {
                openChallenges.forEach { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurfaceVariant)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Code: ${room.inviteCode} · Host: ${room.hostName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Status: ${room.status.uppercase()} · Live Sync",
                                style = MaterialTheme.typography.labelMedium,
                                color = TacticalCyan
                            )
                        }
                        Button(
                            onClick = { onJoinChallenge(room) },
                            modifier = Modifier.testTag("join_challenge_${room.inviteCode}")
                        ) {
                            Text("Join Live")
                        }
                    }
                }
            }
        }

        // 2. Connect to an E-Board (Chessnut & Millennium Electronic Chess Boards)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .testTag("eboard_bluetooth_section"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "E-Board Bluetooth",
                    tint = TacticalCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "CONNECT TO AN E-BOARD (CHESSNUT / MILLENNIUM)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Web Bluetooth / Android BLE GATT bridge while Cheesse is open",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val requestScan: (EBoardBrand) -> Unit = { brand ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        blePermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    }
                    onScanEBoard(brand)
                }

                Button(
                    onClick = { requestScan(EBoardBrand.CHESSNUT) },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_chessnut_button")
                ) {
                    Text("Scan Chessnut", color = Color(0xFF062536))
                }

                Button(
                    onClick = { requestScan(EBoardBrand.MILLENNIUM) },
                    colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_millennium_button")
                ) {
                    Text("Scan Millennium", color = Color(0xFF042F16))
                }
            }

            val statusText = when (eBoardState) {
                is EBoardConnectionState.Disconnected -> "Status: Disconnected — Select Chessnut or Millennium above to scan."
                is EBoardConnectionState.Scanning -> "Status: Scanning BLE for ${eBoardState.preferredBrand.displayName}..."
                is EBoardConnectionState.Connecting -> "Status: Connecting to ${eBoardState.boardName}..."
                is EBoardConnectionState.Connected -> "Status: Connected to ${eBoardState.boardName} (${eBoardState.batteryPct}% Battery)"
                is EBoardConnectionState.Error -> "Status: ${eBoardState.reason}"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = if (eBoardState is EBoardConnectionState.Connected) MoveBestColor else TextSecondary
            )

            discoveredBoards.forEach { board ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${board.name} (${board.address}) · ${board.rssi} dBm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Button(onClick = { onConnectEBoard(board) }) {
                        Text("Pair")
                    }
                }
            }
        }
    }
}

/**
 * Tactical Slate Leaderboard Panel:
 * - Styled using `#161E26` elevated slate cards, `1.dp` `#263240` borders, Space Grotesk headers,
 *   and Roboto Mono for player ratings, rank numbers, and win/loss streaks.
 * - Highlights the current user's pinned row with the `#38BDF8` Tactical Cyan border.
 * - Includes the compact "Sign In / Claim Rank" banner with Jetpack CredentialManager (`GetSignInWithGoogleOption`),
 *   Anti-Cheat Taint status indicator, and Offline-First Room-to-Firestore sync status.
 */
@Composable
fun LeaderboardsCardSection(
    uiState: CheesseUiState,
    onSelectScope: (LeaderboardScope) -> Unit,
    onSelectSortMetric: (LeaderboardSortMetric) -> Unit,
    onRecordUserVictory: () -> Unit,
    onAddFriend: () -> Unit,
    onGoogleSignInIdTokenReceived: (String) -> Unit = {},
    onStartRankedSession: () -> Unit = {},
    onExitRankedSession: () -> Unit = {},
    onSyncPendingGames: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val entries = uiState.displayedLeaderboard
    val pinnedSelfEntry = entries.firstOrNull { it.isCurrentUser }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LeaderboardCardBackground)
            .border(1.dp, LeaderboardCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("leaderboards_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CHESS LEADERBOARDS · ${uiState.leaderboardScope.label.uppercase()}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MoveBestColor
                )
                Text(
                    text = "Rankings by Accuracy Score & Games Won · Offline-First Room + Cloud Sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Anti-Cheat Ranked Session Quick Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            uiState.isTaintedByEngine -> MoveBlunderColor.copy(alpha = 0.18f)
                            uiState.isUnassistedRankedSession -> MoveBestColor.copy(alpha = 0.18f)
                            else -> SlateSurfaceVariant
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            uiState.isTaintedByEngine -> MoveBlunderColor
                            uiState.isUnassistedRankedSession -> MoveBestColor
                            else -> LeaderboardCardBorder
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        if (uiState.isUnassistedRankedSession) onExitRankedSession() else onStartRankedSession()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("leaderboard_ranked_mode_chip")
            ) {
                Text(
                    text = when {
                        uiState.isTaintedByEngine -> "TAINTED (ASSISTED)"
                        uiState.isUnassistedRankedSession -> "RANKED CLEAN"
                        else -> "START RANKED"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = when {
                        uiState.isTaintedByEngine -> MoveBlunderColor
                        uiState.isUnassistedRankedSession -> MoveBestColor
                        else -> TacticalCyan
                    }
                )
            }
        }

        // Compact "Sign In / Claim Rank" Banner (Lazy Auth & Account Linking via CredentialManager)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E1318))
                .border(1.dp, if (uiState.isCloudAuthenticated) MoveBestColor else TacticalCyan, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("leaderboard_claim_rank_banner"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.isCloudAuthenticated) {
                        "CLOUD RANK CLAIMED · ${uiState.cloudDisplayName ?: "CheessePlayer"}"
                    } else {
                        "SIGN IN / CLAIM RANK (ZERO-LOSS ACCOUNT LINKING)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (uiState.isCloudAuthenticated) MoveBestColor else TacticalCyan
                )
                Text(
                    text = if (uiState.isCloudAuthenticated) {
                        "Synced with Firestore · Pending local clean games: ${uiState.pendingCloudSyncCount}"
                    } else {
                        "Link Google Account to sync local Room rating (${uiState.userRating}) & ${uiState.pendingCloudSyncCount} pending clean games."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!uiState.isCloudAuthenticated) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val serverClientId = context.getString(R.string.default_web_client_id)
                                val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
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
                                    onGoogleSignInIdTokenReceived(googleIdTokenCredential.idToken)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                    modifier = Modifier.testTag("sign_in_claim_rank_button")
                ) {
                    Text(
                        text = "Sign In / Claim Rank",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF062536)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSyncPendingGames,
                    modifier = Modifier.testTag("sync_cloud_leaderboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Sync Now",
                        tint = MoveBestColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                        color = MoveBestColor
                    )
                }
            }
        }

        // Scope Selector: Global Leaderboard vs Friends Leaderboard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderboardScope.entries.forEach { scope ->
                val isSelected = uiState.leaderboardScope == scope
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MoveBestColor.copy(alpha = 0.22f) else SlateSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) MoveBestColor else LeaderboardCardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectScope(scope) }
                        .padding(vertical = 8.dp)
                        .testTag("leaderboard_scope_${scope.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = scope.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) MoveBestColor else TextPrimary
                    )
                }
            }
        }

        // Sort Metric Selector: Accuracy Score vs Games Won
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeaderboardSortMetric.entries.forEach { metric ->
                val isSelected = uiState.leaderboardSortMetric == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) TacticalCyan.copy(alpha = 0.22f) else SlateSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) TacticalCyan else LeaderboardCardBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectSortMetric(metric) }
                        .padding(vertical = 6.dp)
                        .testTag("leaderboard_sort_${metric.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Rank by ${metric.label}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) TacticalCyan else TextSecondary
                    )
                }
            }
        }

        // Pinned Current User Row (Highlighted with #38BDF8 Tactical Cyan border)
        if (pinnedSelfEntry != null) {
            val selfRank = entries.indexOfFirst { it.id == pinnedSelfEntry.id }.let { if (it >= 0) it + 1 else 1 }
            LeaderboardPlayerRowCard(
                rankNumber = selfRank,
                player = pinnedSelfEntry,
                sortMetric = uiState.leaderboardSortMetric,
                isPinnedSelfHeader = true
            )
        }

        // Ranked Player Rows (#161E26 elevated slate cards, 1.dp #263240 borders, #38BDF8 Tactical Cyan border for current user)
        entries.forEachIndexed { index, player ->
            val rankNumber = index + 1
            LeaderboardPlayerRowCard(
                rankNumber = rankNumber,
                player = player,
                sortMetric = uiState.leaderboardSortMetric,
                isPinnedSelfHeader = false
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRecordUserVictory,
                modifier = Modifier
                    .weight(1f)
                    .testTag("leaderboard_record_win_button")
            ) {
                Text(
                    text = if (uiState.isTaintedByEngine) "Log Win (Tainted)" else "Log Match Win (+1)",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily)
                )
            }
            OutlinedButton(
                onClick = onAddFriend,
                modifier = Modifier
                    .weight(1f)
                    .testTag("leaderboard_add_friend_button")
            ) {
                Text(
                    text = "Add Friend",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily)
                )
            }
        }
    }
}

@Composable
private fun LeaderboardPlayerRowCard(
    rankNumber: Int,
    player: LeaderboardPlayerEntry,
    sortMetric: LeaderboardSortMetric,
    isPinnedSelfHeader: Boolean
) {
    val isTopThree = rankNumber <= 3
    val rankColor = when (rankNumber) {
        1 -> Color(0xFFFACC15) // Gold
        2 -> Color(0xFFE2E8F0) // Silver
        3 -> Color(0xFFF97316) // Bronze
        else -> TextSecondary
    }
    val rowBorderColor = if (player.isCurrentUser) TacticalCyan else LeaderboardCardBorder
    val rowBorderWidth = if (player.isCurrentUser) 1.5.dp else 1.dp
    val rowTag = if (isPinnedSelfHeader) "leaderboard_pinned_user_row" else "leaderboard_row_$rankNumber"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (player.isCurrentUser) TacticalCyan.copy(alpha = 0.12f)
                else LeaderboardCardBackground
            )
            .border(
                width = rowBorderWidth,
                color = rowBorderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(rowTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isTopThree) rankColor.copy(alpha = 0.2f) else Color(0xFF0E1318))
                    .border(1.dp, rankColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rankNumber",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = rankColor
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TacticalCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = player.titleBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TacticalCyan
                        )
                    }
                    Text(
                        text = player.username,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    if (player.isCurrentUser) {
                        Text(
                            text = if (isPinnedSelfHeader) "(PINNED YOU)" else "(YOU)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TacticalCyan
                        )
                    } else if (player.isFriend) {
                        Text(
                            text = "· FRIEND",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan
                        )
                    }
                }
                Text(
                    text = "${player.rating} ELO · +${player.winStreak}W streak · ${player.playingStyle.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = TextSecondary
                )
            }
        }

        // Accuracy Score & Games Won Badges (Roboto Mono)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.1f%% Acc", player.accuracyScore),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (sortMetric == LeaderboardSortMetric.ACCURACY) MoveBestColor else TextPrimary
                )
                Text(
                    text = "${player.gamesWon}W / ${player.gamesPlayed}G",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (sortMetric == LeaderboardSortMetric.GAMES_WON) TacticalCyan else TextSecondary
                )
            }
        }
    }
}

@Composable
fun BoardEditorScreen(
    uiState: CheesseUiState,
    onToggleEvalBarInEditor: (Boolean) -> Unit,
    onEditorSquareTapped: (Int) -> Unit,
    onSelectEditorPiece: (ChessPiece, Boolean) -> Unit,
    onClearEditor: () -> Unit,
    onResetEditor: () -> Unit,
    onAnalyzeEditorPosition: () -> Unit,
    onFenInputTextChanged: (String) -> Unit = {},
    onApplyFenImport: (String) -> Unit = {},
    onSelectScenarioPreset: (FenScenarioPreset) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("board_editor_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show Evaluation Bar in Board Editor",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Switch(
                checked = uiState.showEvalBarInEditor,
                onCheckedChange = onToggleEvalBarInEditor,
                modifier = Modifier.testTag("toggle_eval_bar_editor")
            )
        }

        if (uiState.showEvalBarInEditor) {
            SmartEvaluationBar(
                evalCentipawns = uiState.editorEvalCp,
                verticalLeftMode = false
            )
        }

        InteractiveChessBoard(
            position = uiState.editorPosition,
            boardTheme = uiState.boardTheme,
            boardFlipped = uiState.boardFlipped,
            boardSizeScale = uiState.boardSizeScale,
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            showLegalMoveDots = false,
            lastMove = null,
            lastMoveStrength = null,
            colorLastMoveByStrength = false,
            previousBestMove = null,
            showPreviousBestMoveArrow = false,
            multiPvLines = emptyList(),
            showEngineArrows = false,
            showEngineLineStrengthColors = false,
            showAnalysisArrowsNumbers = false,
            threats = emptyList(),
            showThreats = false,
            progressiveHint = com.example.engine.ProgressiveHintState(),
            drawnArrows = emptyList(),
            highlightedSquares = emptyList(),
            enlargePieceOnDrag = false,
            disableDragAndDrop = true,
            drawArrowsOnBoard = false,
            isArrowDrawMode = false,
            pendingPromotionMove = null,
            animationDurationMs = 0,
            onSquareTapped = onEditorSquareTapped,
            onPieceDragged = { _, _ -> }
        )

        // Piece selector palette
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(Side.WHITE, Side.BLACK).forEach { side ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PieceType.entries.forEach { pType ->
                        val piece = ChessPiece(pType, side)
                        val isSelected = !uiState.editorEraseMode && uiState.editorSelectedPiece == piece
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TacticalCyan.copy(alpha = 0.28f) else SlateSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) TacticalCyan else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectEditorPiece(piece, false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pType.figurine(side),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectEditorPiece(uiState.editorSelectedPiece, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.editorEraseMode) "Erasing (Active)" else "Erase Square")
                }
                OutlinedButton(
                    onClick = onClearEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Kings Only")
                }
                OutlinedButton(
                    onClick = onResetEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start FEN")
                }
            }

            Button(
                onClick = onAnalyzeEditorPosition,
                colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyze_editor_position_button")
            ) {
                Text(
                    text = "Analyze Custom Position in Workbench",
                    color = Color(0xFF042F16),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Direct FEN Importer & Scenario Loader inside Board Editor
        FenPositionImportPanel(
            currentBoardFen = uiState.editorPosition.toFen(),
            fenInputText = uiState.fenInputText,
            fenImportError = uiState.fenImportError,
            scenarioPresets = uiState.fenScenarioPresets,
            onFenInputTextChanged = onFenInputTextChanged,
            onApplyFenImport = onApplyFenImport,
            onSelectScenarioPreset = onSelectScenarioPreset
        )
    }
}

