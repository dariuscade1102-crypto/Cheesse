package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.EngineConfig
import com.example.engine.EngineProfile
import com.example.engine.GameAnalysisMode
import com.example.ui.theme.BoardThemeOption
import com.example.ui.theme.MoveBestColor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CheesseUiState
import com.example.viewmodel.PieceAnimationSpeed
import com.example.viewmodel.PieceAnimationStyle
import com.example.viewmodel.VariationMoveBehavior

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompleteSettingsScreen(
    uiState: CheesseUiState,
    onUpdateEngine: ((EngineConfig) -> EngineConfig) -> Unit,
    onUpdateVisuals: ((CheesseUiState) -> CheesseUiState) -> Unit
) {
    val cfg = uiState.engineConfig

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("complete_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SECTION 1: UCI Engine Settings
        SettingsSectionCard(title = "1. UCI ENGINE SETTINGS") {
            Text(
                text = "Choose Engine",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EngineProfile.entries.forEach { profile ->
                    SelectableChip(
                        label = profile.displayName,
                        selected = cfg.profile == profile,
                        onClick = { onUpdateEngine { it.copy(profile = profile) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Threads / Cores (${cfg.threads} CPU Threads)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4, 6, 8).forEach { t ->
                    SelectableChip(
                        label = "${t}T",
                        selected = cfg.threads == t,
                        onClick = { onUpdateEngine { it.copy(threads = t) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hash Memory Allocation (${cfg.hashMb} MB)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(16, 64, 128, 256, 512, 1024).forEach { mb ->
                    SelectableChip(
                        label = "$mb MB",
                        selected = cfg.hashMb == mb,
                        onClick = { onUpdateEngine { it.copy(hashMb = mb) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Multi-PV Engine Lines (${cfg.multiPv} Lines)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { lines ->
                    SelectableChip(
                        label = "$lines Lines",
                        selected = cfg.multiPv == lines,
                        onClick = { onUpdateEngine { it.copy(multiPv = lines) } }
                    )
                }
            }

            SettingsSwitchRow(
                title = "Power Saving Mode",
                subtitle = "Reduces CPU usage by limiting continuous analysis to 5 seconds per position",
                checked = cfg.powerSavingMode,
                tag = "switch_power_saving_mode",
                onCheckedChange = { onUpdateEngine { c -> c.copy(powerSavingMode = it) } }
            )

            SettingsSwitchRow(
                title = "Engine Always On",
                subtitle = "Keeps analyzing the current position when the app goes into the background",
                checked = cfg.engineAlwaysOn,
                tag = "switch_engine_always_on",
                onCheckedChange = { onUpdateEngine { c -> c.copy(engineAlwaysOn = it) } }
            )

            Text(
                text = "Game Analysis Settings (${cfg.gameAnalysisMode.label})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameAnalysisMode.entries.forEach { mode ->
                    SelectableChip(
                        label = mode.label,
                        selected = cfg.gameAnalysisMode == mode,
                        onClick = { onUpdateEngine { it.copy(gameAnalysisMode = mode) } }
                    )
                }
            }
        }

        // SECTION 2: Evaluation Bar & Analysis Arrows
        SettingsSectionCard(title = "2. EVALUATION BAR & BOARD ARROWS") {
            SettingsSwitchRow(
                title = "Live Evaluation Bar (Smart Positioning)",
                subtitle = "Bottom position when there is enough space; moves to the left on Game Report screens",
                checked = uiState.smartEvalBar,
                tag = "switch_smart_eval_bar",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(smartEvalBar = it) } }
            )

            SettingsSwitchRow(
                title = "Show Evaluation Bar in Board Editor",
                subtitle = "Live engine evaluation while editing a custom position",
                checked = uiState.showEvalBarInEditor,
                tag = "switch_eval_bar_in_editor",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showEvalBarInEditor = it) } }
            )

            SettingsSwitchRow(
                title = "Show Engine-Line Arrows on Board",
                subtitle = "Global toggle to show or turn off board arrows for engine lines everywhere",
                checked = uiState.showEngineArrows,
                tag = "switch_show_engine_arrows",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showEngineArrows = it) } }
            )

            SettingsSwitchRow(
                title = "Show Engine Line Strength Colors",
                subtitle = "Colors alternate arrows and alternate engine-line score badges by proximity to best move",
                checked = uiState.showEngineLineStrengthColors,
                tag = "switch_engine_line_strength_colors",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showEngineLineStrengthColors = it) } }
            )

            SettingsSwitchRow(
                title = "Show Analysis Arrows Numbers",
                subtitle = "Displays rank numbers (1, 2, 3...) next to analysis arrows whenever >1 arrow is shown",
                checked = uiState.showAnalysisArrowsNumbers,
                tag = "switch_analysis_arrows_numbers",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showAnalysisArrowsNumbers = it) } }
            )

            SettingsSwitchRow(
                title = "Show Previous Best-Move Arrow",
                subtitle = "Renders a faint transparent arrow indicating the engine's best move from the previous position",
                checked = uiState.showPreviousBestMoveArrow,
                tag = "switch_previous_best_move_arrow",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showPreviousBestMoveArrow = it) } }
            )

            SettingsSwitchRow(
                title = "Color Last Move by Strength",
                subtitle = "Last-move square highlight adopts move classification color (Best, Good, Inaccuracy, Mistake, Blunder)",
                checked = uiState.colorLastMoveByStrength,
                tag = "switch_color_last_move_by_strength",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(colorLastMoveByStrength = it) } }
            )
        }

        // SECTION 3: Game Review, Threats & Key Elements
        SettingsSectionCard(title = "3. GAME REVIEW, THREATS & TRAINING") {
            SettingsSwitchRow(
                title = "Show Average CPL in Game Report",
                subtitle = "Displays Average Centipawn Loss alongside Accuracy (only accuracy is shown by default)",
                checked = uiState.showAverageCplInReport,
                tag = "switch_show_average_cpl",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showAverageCplInReport = it) } }
            )

            SettingsSwitchRow(
                title = "Each Move Strength While Playing",
                subtitle = "Real-time display of each move's classification strength while playing",
                checked = uiState.showEachMoveStrengthWhilePlaying,
                tag = "switch_each_move_strength_playing",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showEachMoveStrengthWhilePlaying = it) } }
            )

            SettingsSwitchRow(
                title = "Pause On Mistake While Playing",
                subtitle = "Automatically pauses and alerts when a mistake or blunder is played",
                checked = uiState.pauseOnMistakeWhilePlaying,
                tag = "switch_pause_on_mistake",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(pauseOnMistakeWhilePlaying = it) } }
            )

            SettingsSwitchRow(
                title = "Threats Settings (Highlight Tactical Threats)",
                subtitle = "Highlights opponent tactical threats and undefended targets on the board",
                checked = uiState.showThreats,
                tag = "switch_show_threats",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showThreats = it) } }
            )

            SettingsSwitchRow(
                title = "Key Elements Analyzer",
                subtitle = "Evaluates Center Control, Piece Mobility, King Safety, and Pawn Structure",
                checked = uiState.showKeyElements,
                tag = "switch_show_key_elements",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showKeyElements = it) } }
            )
        }

        // SECTION 4: Notation, Variations & Your Usernames
        SettingsSectionCard(title = "4. NOTATION, VARIATIONS & PLAYER USERNAMES") {
            Text(
                text = "Analysis Board Variation Moves Behavior",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VariationMoveBehavior.entries.forEach { behavior ->
                    SelectableChip(
                        label = behavior.label,
                        selected = uiState.variationMoveBehavior == behavior,
                        onClick = { onUpdateVisuals { s -> s.copy(variationMoveBehavior = behavior) } }
                    )
                }
            }

            SettingsSwitchRow(
                title = "Variation Move Symbols (!, ?, ??)",
                subtitle = "Adds evaluation symbols to variation moves; leaves existing variation text unchanged when turned off",
                checked = uiState.variationMoveSymbols,
                tag = "switch_variation_move_symbols",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(variationMoveSymbols = it) } }
            )

            SettingsSwitchRow(
                title = "Figurine Notation (♘, ♗, ♖, ♕, ♔)",
                subtitle = "Displays moves with chess piece symbols instead of letters N, B, R, Q, K",
                checked = uiState.figurineNotation,
                tag = "switch_figurine_notation",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(figurineNotation = it) } }
            )

            SettingsSwitchRow(
                title = "Show Captured Pieces & Material Point Lead",
                subtitle = "Displays captured pieces and material lead (+3) next to the player rows",
                checked = uiState.showCapturedPieces,
                tag = "switch_show_captured_pieces",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showCapturedPieces = it) } }
            )

            SettingsSwitchRow(
                title = "Show Moves in Portrait Mode",
                subtitle = "Displays move tape above the board in portrait mode (disable on smaller screens to free up space)",
                checked = uiState.showMovesInPortrait,
                tag = "switch_show_moves_portrait",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showMovesInPortrait = it) } }
            )

            OutlinedTextField(
                value = uiState.yourUsernames,
                onValueChange = { text -> onUpdateVisuals { s -> s.copy(yourUsernames = text) } },
                label = { Text("Your Usernames (Auto-flips board to your side)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_your_usernames")
            )
        }

        // SECTION 5: Board Controls, Drawing, Animation & Appearance
        SettingsSectionCard(title = "5. BOARD INTERACTION, ANIMATION & THEMES") {
            SettingsSwitchRow(
                title = "Legal Moves Dot Indicators",
                subtitle = "Shows dots on legal empty squares and rings on capture targets when a piece is selected",
                checked = uiState.showLegalMoveDots,
                tag = "switch_legal_move_dots",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showLegalMoveDots = it) } }
            )

            SettingsSwitchRow(
                title = "Draw Arrows on Board",
                subtitle = "Allows freehand arrow drawing while temporarily deactivating piece drag-and-drop",
                checked = uiState.drawArrowsOnBoard,
                tag = "switch_draw_arrows_on_board",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(drawArrowsOnBoard = it) } }
            )

            SettingsSwitchRow(
                title = "Show Drawing Panel",
                subtitle = "Displays multi-color palette to highlight squares and draw arrows on the main board",
                checked = uiState.showDrawingPanel,
                tag = "switch_show_drawing_panel",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(showDrawingPanel = it) } }
            )

            SettingsSwitchRow(
                title = "Enlarge Piece on Drag",
                subtitle = "Enlarges the chess piece under your finger while dragging",
                checked = uiState.enlargePieceOnDrag,
                tag = "switch_enlarge_piece_on_drag",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(enlargePieceOnDrag = it) } }
            )

            SettingsSwitchRow(
                title = "Disable Drag and Drop",
                subtitle = "Use tap-to-select and tap-to-move exclusively",
                checked = uiState.disableDragAndDrop,
                tag = "switch_disable_drag_and_drop",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(disableDragAndDrop = it) } }
            )

            Text(
                text = "Autoplay Speed (${uiState.autoplaySpeedMs} ms for Autoplay, Engine & Blunder Review)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L to "0.5s Fast", 1000L to "1.0s Normal", 2000L to "2.0s Study", 3500L to "3.5s Deep").forEach { (ms, label) ->
                    SelectableChip(
                        label = label,
                        selected = uiState.autoplaySpeedMs == ms,
                        onClick = { onUpdateVisuals { s -> s.copy(autoplaySpeedMs = ms) } }
                    )
                }
            }

            Text(
                text = "Piece Animation Speed (Effective: ${uiState.effectiveAnimationDurationMs} ms)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PieceAnimationSpeed.entries.forEach { spd ->
                    SelectableChip(
                        label = spd.label,
                        selected = uiState.pieceAnimationSpeed == spd,
                        onClick = { onUpdateVisuals { s -> s.copy(pieceAnimationSpeed = spd) } }
                    )
                }
            }

            Text(
                text = "Piece Animation Style (Slowmo overrides Fast speed to Slow)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PieceAnimationStyle.entries.forEach { style ->
                    SelectableChip(
                        label = style.label,
                        selected = uiState.pieceAnimationStyle == style,
                        onClick = { onUpdateVisuals { s -> s.copy(pieceAnimationStyle = style) } }
                    )
                }
            }

            Text(
                text = "Customizable Board Themes",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoardThemeOption.entries.forEach { theme ->
                    SelectableChip(
                        label = theme.label,
                        selected = uiState.boardTheme == theme,
                        onClick = { onUpdateVisuals { s -> s.copy(boardTheme = theme) } }
                    )
                }
            }

            Text(
                text = "Adjustable Board Size (${(uiState.boardSizeScale * 100).toInt()}%)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Slider(
                value = uiState.boardSizeScale,
                onValueChange = { v -> onUpdateVisuals { s -> s.copy(boardSizeScale = v) } },
                valueRange = 0.72f..1.0f,
                modifier = Modifier.testTag("slider_board_size")
            )

            SettingsSwitchRow(
                title = "Sound Effects",
                subtitle = "Synthesizes acoustic feedback for moves, captures, and blunder alerts",
                checked = uiState.soundEnabled,
                tag = "switch_sound_enabled",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(soundEnabled = it) } }
            )

            SettingsSwitchRow(
                title = "Full Screen Mode",
                subtitle = "Maximizes workspace by hiding secondary chrome",
                checked = uiState.fullScreenMode,
                tag = "switch_full_screen_mode",
                onCheckedChange = { onUpdateVisuals { s -> s.copy(fullScreenMode = it) } }
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = TacticalCyan
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MoveBestColor else SlateSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color(0xFF042F16) else TextPrimary
        )
    }
}
