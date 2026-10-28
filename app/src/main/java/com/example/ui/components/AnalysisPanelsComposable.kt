package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CapturedSummary
import com.example.engine.ChessMove
import com.example.engine.ChessPosition
import com.example.engine.CoachMoveSuggestion
import com.example.engine.EngineConfig
import com.example.engine.FenScenarioPreset
import com.example.engine.KeyPositionElement
import com.example.engine.MoveStrength
import com.example.engine.MultiPvLine
import com.example.engine.OpeningExplorerData
import com.example.engine.PlayingStyleProfile
import com.example.engine.ProgressiveHintState
import com.example.engine.Side
import com.example.engine.TacticalThreat
import com.example.service.MoveEvaluationResult
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.MoveBlunderColor
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DrawingColorOption
import com.example.viewmodel.VariationBranch

@Composable
fun PlayerRowStrip(
    playerName: String,
    side: Side,
    isTurn: Boolean,
    showCapturedPieces: Boolean,
    capturedSummary: CapturedSummary,
    modifier: Modifier = Modifier
) {
    val pieces = if (side == Side.WHITE) capturedSummary.capturedByWhite else capturedSummary.capturedByBlack
    val lead = if (side == Side.WHITE) capturedSummary.whiteMaterialLead else capturedSummary.blackMaterialLead

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurface)
            .border(1.dp, if (isTurn) MoveBestColor.copy(alpha = 0.6f) else SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (side == Side.WHITE) Color(0xFFF8FAFC) else Color(0xFF334155))
                    .border(1.dp, TextSecondary, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playerName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showCapturedPieces) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("captured_pieces_${side.name.lowercase()}")
            ) {
                if (pieces.isNotEmpty()) {
                    Text(
                        text = pieces.joinToString("") { it.figurine(side.opposite()) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (lead > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+$lead",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MoveBestColor
                    )
                }
            }
        }
    }
}

@Composable
fun PortraitMovesTape(
    moves: List<ChessMove>,
    moveStrengths: List<MoveStrength>,
    currentPlyIndex: Int,
    figurineNotation: Boolean,
    variationMoveSymbols: Boolean,
    variations: List<VariationBranch>,
    onPlyClicked: (Int) -> Unit,
    onVariationClicked: (VariationBranch) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("portrait_moves_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (moves.isEmpty()) {
                Text(
                    text = "1. Play a move on the board or select an Opening Explorer book line",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            } else {
                moves.forEachIndexed { idx, move ->
                    val moveNumPrefix = if (idx % 2 == 0) "${(idx / 2) + 1}." else ""
                    val strength = moveStrengths.getOrNull(idx)
                    val formatted = move.formatNotation(
                        useFigurine = figurineNotation,
                        showSymbols = variationMoveSymbols,
                        strength = strength
                    )
                    val isSelected = (idx + 1) == currentPlyIndex

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) SlateSurfaceVariant else Color.Transparent)
                            .clickable { onPlyClicked(idx + 1) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (moveNumPrefix.isNotEmpty()) "$moveNumPrefix $formatted" else formatted,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) TacticalCyan else TextPrimary
                        )
                    }
                }
            }
        }

        if (variations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Branches:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                variations.forEach { branch ->
                    Text(
                        text = "[${branch.renderDisplay(figurineNotation, variationMoveSymbols)}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = TacticalCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SlateSurfaceVariant)
                            .clickable { onVariationClicked(branch) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BoardTransportAndHintToolbar(
    currentPly: Int,
    totalPlies: Int,
    isAutoplayRunning: Boolean,
    drawArrowsOnBoard: Boolean,
    showDrawingPanel: Boolean,
    activeDrawingColor: DrawingColorOption,
    progressiveHint: ProgressiveHintState,
    showFenImportSheet: Boolean = false,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onFlipBoard: () -> Unit,
    onToggleDrawArrows: () -> Unit,
    onSelectDrawingColor: (DrawingColorOption) -> Unit,
    onClearDrawings: () -> Unit,
    onAdvanceHint: () -> Unit,
    onToggleFenImportSheet: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onStepBackward,
                    enabled = currentPly > 0,
                    modifier = Modifier.testTag("step_backward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous move",
                        tint = if (currentPly > 0) TextPrimary else TextMuted
                    )
                }
                IconButton(
                    onClick = onToggleAutoplay,
                    modifier = Modifier.testTag("autoplay_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isAutoplayRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isAutoplayRunning) "Pause autoplay" else "Start autoplay",
                        tint = MoveBestColor
                    )
                }
                IconButton(
                    onClick = onStepForward,
                    enabled = currentPly < totalPlies,
                    modifier = Modifier.testTag("step_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next move",
                        tint = if (currentPly < totalPlies) TextPrimary else TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onToggleFenImportSheet,
                    color = if (showFenImportSheet) MoveBestColor.copy(alpha = 0.22f) else SlateSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("open_fen_import_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Import FEN Position",
                            tint = if (showFenImportSheet) MoveBestColor else TacticalCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "FEN",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (showFenImportSheet) MoveBestColor else TextPrimary
                        )
                    }
                }

                Surface(
                    onClick = onAdvanceHint,
                    color = if (progressiveHint.currentStep > 0) TacticalCyan.copy(alpha = 0.22f) else SlateSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("progressive_hint_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Hint",
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (progressiveHint.currentStep == 0) "Hint (1-3)" else "Hint ${progressiveHint.currentStep}/3",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }

                IconButton(
                    onClick = onToggleDrawArrows,
                    modifier = Modifier.testTag("draw_arrows_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Draw arrows on board",
                        tint = if (drawArrowsOnBoard || showDrawingPanel) MoveBestColor else TextSecondary
                    )
                }

                IconButton(
                    onClick = onFlipBoard,
                    modifier = Modifier.testTag("flip_board_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Flip board",
                        tint = TextSecondary
                    )
                }
            }
        }

        if (progressiveHint.currentStep > 0) {
            val activeText = when (progressiveHint.currentStep) {
                1 -> progressiveHint.step1Text
                2 -> "${progressiveHint.step1Text}\n${progressiveHint.step2Text}"
                else -> "${progressiveHint.step2Text}\n${progressiveHint.step3Text}"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TacticalCyan.copy(alpha = 0.14f))
                    .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .testTag("hint_explanation_card"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (progressiveHint.coachCommentary.isNotBlank()) {
                    Text(
                        text = progressiveHint.coachCommentary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MoveBestColor,
                        modifier = Modifier.testTag("hint_coach_commentary")
                    )
                }
            }
        }

        if (showDrawingPanel || drawArrowsOnBoard) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("board_drawing_panel"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Draw / Highlight:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrawingColorOption.entries.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (activeDrawingColor == option) 2.5.dp else 1.dp,
                                    color = if (activeDrawingColor == option) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectDrawingColor(option) }
                        )
                    }
                    IconButton(onClick = onClearDrawings, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear board drawings",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MultiPvEngineLinesPanel(
    engineConfig: EngineConfig,
    isUciServiceBound: Boolean,
    isEngineRunning: Boolean,
    enginePausedByPowerSave: Boolean,
    currentDepth: Int,
    nodesPerSec: Int,
    multiPvLines: List<MultiPvLine>,
    candidateEvaluations: List<MoveEvaluationResult>,
    inspectedMoveEvaluation: MoveEvaluationResult?,
    lastUciProtocolOutput: String,
    showEngineLineStrengthColors: Boolean,
    figurineNotation: Boolean,
    onChangeMultiPvCount: (Int) -> Unit,
    onResumeEngine: () -> Unit,
    onSendUciCommand: (String) -> Unit,
    onInspectMoveEvaluation: (ChessMove) -> Unit,
    onPlayPvMove: (ChessMove) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("multi_pv_engine_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Bound UCI Engine Service Header & Multi-PV (+/-) Quick Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isEngineRunning) MoveBestColor else Color(0xFFFACC15))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = engineConfig.profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = (if (isUciServiceBound) "SERVICE BOUND" else "LOCAL WORKER") +
                        " · d$currentDepth · ${nodesPerSec / 1000}k nps · ${engineConfig.threads}T · ${engineConfig.hashMb}MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = TacticalCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onChangeMultiPvCount((engineConfig.multiPv - 1).coerceAtLeast(1)) },
                    enabled = engineConfig.multiPv > 1,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("decrease_multipv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Fewer engine lines",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${engineConfig.multiPv} PV",
                    style = MaterialTheme.typography.labelLarge,
                    color = TacticalCyan
                )
                IconButton(
                    onClick = { onChangeMultiPvCount((engineConfig.multiPv + 1).coerceAtMost(5)) },
                    enabled = engineConfig.multiPv < 5,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("increase_multipv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "More engine lines",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (enginePausedByPowerSave) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFACC15).copy(alpha = 0.14f))
                    .clickable { onResumeEngine() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Power Saving Mode: Paused after 5s limit — Tap to resume",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFACC15)
                )
            }
        }

        // Render Multi-PV Lines
        multiPvLines.forEach { line ->
            val badgeBg = if (line.rank == 1) {
                MoveBestColor
            } else if (showEngineLineStrengthColors) {
                moveStrengthColor(line.strengthCategory)
            } else {
                SlateSurfaceVariant
            }
            val badgeFg = if (line.rank == 1 || showEngineLineStrengthColors) Color(0xFF090D11) else TextPrimary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateSurfaceVariant.copy(alpha = 0.65f))
                    .clickable { onPlayPvMove(line.firstMove) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${line.rank} ${line.formattedEvaluation()}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = badgeFg
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val formattedMoves = line.continuationSan.mapIndexed { idx, san ->
                    if (idx == 0) line.firstMove.formatNotation(figurineNotation, false) else san
                }.joinToString(" ")
                Text(
                    text = formattedMoves,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // UCI Engine Service Move Evaluator & Protocol Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurfaceVariant.copy(alpha = 0.45f))
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .testTag("uci_service_move_evaluator"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "UCI Service",
                        tint = MoveBestColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UCI SERVICE MOVE EVALUATOR",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MoveBestColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("uci", "isready", "go", "stop").forEach { cmd ->
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TacticalCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SlateSurface)
                                .clickable { onSendUciCommand(cmd) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("uci_cmd_$cmd")
                        )
                    }
                }
            }

            if (candidateEvaluations.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidateEvaluations.forEach { evalItem ->
                        val color = moveStrengthColor(evalItem.strength)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateSurface)
                                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { onInspectMoveEvaluation(evalItem.move) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("eval_candidate_${evalItem.move.uci}")
                        ) {
                            Text(
                                text = "${evalItem.move.san} (${evalItem.formattedScore} · ${evalItem.strength.label})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (inspectedMoveEvaluation != null) {
                Text(
                    text = "Evaluated Move ${inspectedMoveEvaluation.move.san}: Score ${inspectedMoveEvaluation.formattedScore}, " +
                        "CP Loss ${inspectedMoveEvaluation.cpLoss} (${inspectedMoveEvaluation.strength.label}) · " +
                        "Best: ${inspectedMoveEvaluation.bestAlternativeMove?.san ?: inspectedMoveEvaluation.move.san}",
                    style = MaterialTheme.typography.labelMedium,
                    color = moveStrengthColor(inspectedMoveEvaluation.strength),
                    modifier = Modifier.testTag("inspected_move_eval_text")
                )
            }

            Text(
                text = lastUciProtocolOutput,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("uci_protocol_output_text")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningExplorerAndDiagnosticsPanel(
    openingExplorer: OpeningExplorerData?,
    showThreats: Boolean,
    threats: List<TacticalThreat>,
    showKeyElements: Boolean,
    keyElements: List<KeyPositionElement>,
    onBookMoveSelected: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (openingExplorer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("opening_explorer_panel"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPENING EXPLORER · ${openingExplorer.ecoCode}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = TacticalCyan
                    )
                    Text(
                        text = openingExplorer.openingName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                openingExplorer.bookMoves.forEach { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SlateSurfaceVariant.copy(alpha = 0.5f))
                            .clickable { onBookMoveSelected(book.from, book.to) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(110.dp)) {
                            Text(
                                text = book.san,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${book.gamesCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(book.whiteWinPct.coerceAtLeast(1).toFloat())
                                    .background(Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${book.whiteWinPct}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(book.drawPct.coerceAtLeast(1).toFloat())
                                    .background(Color(0xFF64748B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${book.drawPct}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(book.blackWinPct.coerceAtLeast(1).toFloat())
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${book.blackWinPct}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
                                    color = Color(0xFFF8FAFC)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showThreats || showKeyElements) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("key_elements_and_threats_panel"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showThreats) {
                    Text(
                        text = "TACTICAL THREATS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MoveBlunderColor
                    )
                    if (threats.isEmpty()) {
                        Text(
                            text = "No immediate undefended material threats detected in current position.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        threats.forEach { t ->
                            Text(
                                text = "• ${t.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                if (showKeyElements && keyElements.isNotEmpty()) {
                    Text(
                        text = "KEY POSITION ELEMENTS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MoveBestColor
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keyElements.forEach { el ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${el.category}: ${el.summary}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Coach Mode Panel that offers move suggestions with explanations tailored
 * to the current position and the user's playing style, directly integrated with the
 * move-by-move progressive 3-step hint system.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachModeGuidancePanel(
    coachModeActive: Boolean,
    userPlayingStyle: PlayingStyleProfile,
    coachSuggestions: List<CoachMoveSuggestion>,
    progressiveHint: ProgressiveHintState,
    figurineNotation: Boolean,
    onToggleCoachMode: (Boolean) -> Unit,
    onSelectPlayingStyle: (PlayingStyleProfile) -> Unit,
    onAdvanceHint: () -> Unit,
    onPlaySuggestedMove: (ChessMove) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, if (coachModeActive) MoveBestColor.copy(alpha = 0.65f) else SlateBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("coach_mode_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COACH MODE · ${userPlayingStyle.coachPersona.uppercase()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MoveBestColor
                )
                Text(
                    text = "Move suggestions with explanations tailored to position & playing style + 3-step hints",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Switch(
                checked = coachModeActive,
                onCheckedChange = onToggleCoachMode,
                modifier = Modifier.testTag("coach_mode_toggle")
            )
        }

        if (coachModeActive) {
            // Playing Style Selector Pills
            Text(
                text = "Your Playing Style Profile:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlayingStyleProfile.entries.forEach { style ->
                    val isSelected = style == userPlayingStyle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) TacticalCyan.copy(alpha = 0.24f) else SlateSurfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) TacticalCyan else SlateBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectPlayingStyle(style) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("coach_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) TacticalCyan else TextPrimary
                        )
                    }
                }
            }

            // Integration with Progressive 3-Step Hint System
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateSurfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (progressiveHint.currentStep == 0) {
                            "Coach 3-Step Hint: Ready (Step 1 Piece → Step 2 Target → Step 3 Move)"
                        } else {
                            "Coach Hint Step ${progressiveHint.currentStep}/3 Active"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = TacticalCyan
                    )
                    Text(
                        text = if (progressiveHint.coachCommentary.isNotBlank()) {
                            progressiveHint.coachCommentary
                        } else {
                            userPlayingStyle.description
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAdvanceHint,
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan),
                    modifier = Modifier.testTag("coach_advance_hint_button")
                ) {
                    Text(
                        text = if (progressiveHint.currentStep == 0) "Coach Hint 1/3" else "Next Step (${(progressiveHint.currentStep % 3) + 1}/3)",
                        color = Color(0xFF062536),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Tailored Coach Move Suggestions
            coachSuggestions.forEach { suggestion ->
                val displayMove = suggestion.move.formatNotation(
                    useFigurine = figurineNotation,
                    showSymbols = false,
                    strength = null
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101820))
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .testTag("coach_suggestion_card_${suggestion.rank}"),
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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MoveBestColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#${suggestion.rank} $displayMove (${suggestion.scoreFormatted})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MoveBestColor
                                )
                            }
                            Text(
                                text = suggestion.styleAlignmentBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalCyan
                            )
                        }

                        Button(
                            onClick = { onPlaySuggestedMove(suggestion.move) },
                            colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("play_coach_suggestion_${suggestion.rank}")
                        ) {
                            Text(
                                text = "Play $displayMove",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF042F16)
                            )
                        }
                    }

                    Text(
                        text = "Position Insight: ${suggestion.positionExplanation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Style Fit: ${suggestion.styleExplanation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = suggestion.hintStepPreview,
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FenPositionImportPanel(
    currentBoardFen: String,
    fenInputText: String,
    fenImportError: String?,
    scenarioPresets: List<FenScenarioPreset>,
    onFenInputTextChanged: (String) -> Unit,
    onApplyFenImport: (String) -> Unit,
    onSelectScenarioPreset: (FenScenarioPreset) -> Unit,
    onClosePanel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val isInputValid = fenInputText.isNotBlank() && fenImportError == null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, TacticalCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("fen_import_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row with Validation Badge & Close Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FEN POSITION IMPORTER & SCENARIO LOADER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TacticalCyan
                )
                Text(
                    text = "Paste any Forsyth–Edwards Notation string or pick a curated tactical scenario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isInputValid) MoveBestColor.copy(alpha = 0.18f)
                            else MoveBlunderColor.copy(alpha = 0.18f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isInputValid) MoveBestColor else MoveBlunderColor,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("fen_validation_status_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isInputValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (isInputValid) MoveBestColor else MoveBlunderColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isInputValid) "VALID FEN" else "INVALID FEN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isInputValid) MoveBestColor else MoveBlunderColor
                        )
                    }
                }

                if (onClosePanel != null) {
                    IconButton(
                        onClick = onClosePanel,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("close_fen_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close FEN panel",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Live Current Position FEN Bar + Copy / Paste Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E1318))
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ACTIVE BOARD FEN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextSecondary
                )
                Text(
                    text = currentBoardFen,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("current_board_fen_text")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(currentBoardFen))
                        onFenInputTextChanged(currentBoardFen)
                    },
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("copy_current_fen_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Active FEN",
                            tint = TacticalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onFenInputTextChanged(clip.trim())
                        }
                    },
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("paste_fen_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste FEN from Clipboard",
                            tint = MoveBestColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // FEN String Input TextField (Monospace)
        OutlinedTextField(
            value = fenInputText,
            onValueChange = onFenInputTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fen_input_text_field"),
            label = {
                Text(
                    text = "FEN String (8 ranks · side · castling · ep · clocks)",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = RobotoMonoFontFamily,
                color = TextPrimary
            ),
            singleLine = true,
            isError = fenImportError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TacticalCyan,
                unfocusedBorderColor = SlateBorder,
                errorBorderColor = MoveBlunderColor,
                focusedLabelColor = TacticalCyan,
                unfocusedLabelColor = TextSecondary
            )
        )

        if (fenImportError != null) {
            Text(
                text = fenImportError,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFontFamily),
                color = MoveBlunderColor,
                modifier = Modifier.testTag("fen_validation_error_text")
            )
        }

        // Action Buttons: Load into Workbench & Reset to Start FEN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onApplyFenImport(fenInputText) },
                enabled = isInputValid,
                colors = ButtonDefaults.buttonColors(containerColor = MoveBestColor),
                modifier = Modifier
                    .weight(1f)
                    .testTag("apply_fen_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = Color(0xFF042F16),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Load FEN into Workbench",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF042F16)
                )
            }

            OutlinedButton(
                onClick = {
                    onFenInputTextChanged(ChessPosition.START_FEN)
                    onApplyFenImport(ChessPosition.START_FEN)
                },
                modifier = Modifier.testTag("reset_start_fen_button")
            ) {
                Text(
                    text = "Start FEN",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = RobotoMonoFontFamily),
                    color = TacticalCyan
                )
            }
        }

        // Curated Quick Scenario Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "QUICK SCENARIO PRESETS (1-TAP LOAD)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = TextSecondary
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scenarioPresets.forEach { preset ->
                    val isCurrentlyLoaded = currentBoardFen.substringBefore(" ") == preset.fen.substringBefore(" ")
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrentlyLoaded) TacticalCyan.copy(alpha = 0.2f)
                                else SlateSurfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCurrentlyLoaded) TacticalCyan else SlateBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onFenInputTextChanged(preset.fen)
                                onSelectScenarioPreset(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("fen_preset_${preset.id}")
                    ) {
                        Text(
                            text = "${preset.category} · ${preset.label}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isCurrentlyLoaded) TacticalCyan else TextPrimary
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}


