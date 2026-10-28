package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ChessSoundEffect
import com.example.data.CheesseLocalDatabase
import com.example.data.CheesseLocalRepository
import com.example.engine.ChessPosition
import com.example.engine.EngineConfig
import com.example.engine.PieceType
import com.example.engine.PlayingStyleProfile
import com.example.engine.Side
import com.example.engine.UciAnalysisEngine
import com.example.service.UciEngineService
import com.example.viewmodel.CheesseUiState
import com.example.viewmodel.CheesseViewModel
import com.example.viewmodel.LeaderboardScope
import com.example.viewmodel.LeaderboardSortMetric
import com.example.viewmodel.PieceAnimationSpeed
import com.example.viewmodel.PieceAnimationStyle
import com.example.viewmodel.formatChessClockMs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Cheesse", appName)
    }

    @Test
    fun `uci engine produces multi-pv lines and progressive 3-step hints`() {
        val startPos = ChessPosition.fromFen(ChessPosition.START_FEN)
        val config = EngineConfig(threads = 4, hashMb = 512, multiPv = 5)
        val lines = UciAnalysisEngine.analyzePositionMultiPv(startPos, config, 12)
        assertEquals(5, lines.size)

        val step1 = UciAnalysisEngine.buildProgressiveHint(startPos, lines.first(), 1)
        val step2 = UciAnalysisEngine.buildProgressiveHint(startPos, lines.first(), 2)
        val step3 = UciAnalysisEngine.buildProgressiveHint(startPos, lines.first(), 3)
        assertEquals(1, step1.currentStep)
        assertEquals(2, step2.currentStep)
        assertEquals(3, step3.currentStep)
        assertTrue(step3.step3Text.contains("Step 3"))
    }

    @Test
    fun `uci engine service binds and exposes suspend move evaluation functions off main thread`() {
        runBlocking {
            val controller = Robolectric.buildService(UciEngineService::class.java).create()
            val binder = controller.get().onBind(Intent()) as UciEngineService.UciEngineBinder
            val service = binder.getService()

            val uciResponse = service.sendUciCommand("uci")
            assertTrue(uciResponse.contains("uciok"))

            val readyResponse = service.sendUciCommand("isready")
            assertTrue(readyResponse.contains("readyok"))

            val startPos = ChessPosition.fromFen(ChessPosition.START_FEN)
            val cpEval = service.evaluatePosition(startPos)
            assertTrue(cpEval in -500..500)

            val candidates = service.evaluateCandidateMoves(startPos)
            assertTrue(candidates.isNotEmpty())

            val e2e4Move = startPos.generateLegalMoves().first { it.uci == "e2e4" }
            val e2e4Eval = service.evaluateMove(startPos, e2e4Move)
            assertNotNull(e2e4Eval)
            assertEquals("e2e4", e2e4Eval.move.uci)
            assertTrue(e2e4Eval.formattedScore.isNotBlank())

            controller.destroy()
        }
    }

    @Test
    fun `chessboard drag start highlights legal moves and drag end updates notation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        // Reset to starting position and start dragging White's e2 pawn (square 52: row 6, col 4)
        vm.syncFromExternalFen(ChessPosition.START_FEN)
        val e2Square = 6 * 8 + 4
        val e4Square = 4 * 8 + 4

        vm.onPieceDragStarted(e2Square)
        assertEquals(e2Square, vm.uiState.value.selectedSquare)
        assertTrue(vm.uiState.value.legalMovesForSelected.any { it.to == e4Square })

        vm.onPieceDragged(e2Square, e4Square)
        assertTrue(vm.uiState.value.moveHistory.any { it.uci == "e2e4" })
        assertEquals("e2e4", vm.uiState.value.lastMove?.uci)
    }

    @Test
    fun `arrow draw mode routes drag gestures to user arrow creation and deletion without moving pieces`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        vm.syncFromExternalFen(ChessPosition.START_FEN)
        val e2Square = 6 * 8 + 4
        val e4Square = 4 * 8 + 4

        // Enable isArrowDrawMode
        vm.toggleArrowDrawMode()
        assertTrue(vm.uiState.value.isArrowDrawMode)

        // Starting a drag on a movable piece square (e2) does NOT select the piece
        vm.onPieceDragStarted(e2Square)
        assertNull(vm.uiState.value.selectedSquare)

        // Dragging from e2 to e4 draws a tactical arrow instead of playing e2e4
        vm.onPieceDragged(e2Square, e4Square)
        assertTrue(vm.uiState.value.moveHistory.isEmpty())
        assertEquals(1, vm.uiState.value.drawnArrows.size)
        assertEquals(e2Square, vm.uiState.value.drawnArrows.first().from)
        assertEquals(e4Square, vm.uiState.value.drawnArrows.first().to)

        // Dragging the same arrow again deletes it
        vm.onPieceDragged(e2Square, e4Square)
        assertTrue(vm.uiState.value.drawnArrows.isEmpty())
    }

    @Test
    fun `pawn promotion move is intercepted into pendingPromotionMove until promotion piece is selected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        // White pawn on a7 (square 8: row 1, col 0), ready to promote on a8 (square 0: row 0, col 0)
        val promoFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        vm.syncFromExternalFen(promoFen)
        val a7Square = 1 * 8 + 0
        val a8Square = 0 * 8 + 0

        vm.onPieceDragged(a7Square, a8Square)
        // Must intercept into pendingPromotionMove before committing FEN or notation update
        assertNotNull(vm.uiState.value.pendingPromotionMove)
        assertTrue(vm.uiState.value.moveHistory.isEmpty())

        // Selecting KNIGHT commits underpromotion a8=N
        vm.selectPromotionPiece(PieceType.KNIGHT)
        assertNull(vm.uiState.value.pendingPromotionMove)
        assertEquals(1, vm.uiState.value.moveHistory.size)
        assertEquals(PieceType.KNIGHT, vm.uiState.value.position.pieceAt(a8Square)?.type)
        assertEquals("a7a8n", vm.uiState.value.lastMove?.uci)
    }

    @Test
    fun `anti-cheat taint flag locks engine in ranked session and blocks tainted leaderboard submission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        vm.startUnassistedRankedSession()
        assertTrue(vm.uiState.value.isUnassistedRankedSession)
        assertFalse(vm.uiState.value.isTaintedByEngine)
        assertFalse(vm.uiState.value.smartEvalBar)
        assertFalse(vm.uiState.value.isEngineRunning)

        // Clean win increments user gamesWon
        val winsBefore = vm.uiState.value.leaderboardEntries.first { it.isCurrentUser }.gamesWon
        vm.recordUserGameVictory(accuracyEarned = 94.5)
        val winsAfterClean = vm.uiState.value.leaderboardEntries.first { it.isCurrentUser }.gamesWon
        assertEquals(winsBefore + 1, winsAfterClean)

        // Viewing a progressive hint mid-session permanently marks isTaintedByEngine = true
        vm.advanceProgressiveHint()
        assertTrue(vm.uiState.value.isTaintedByEngine)

        // Attempting to submit victory after taint does NOT increment leaderboard wins
        vm.recordUserGameVictory(accuracyEarned = 99.0)
        val winsAfterTainted = vm.uiState.value.leaderboardEntries.first { it.isCurrentUser }.gamesWon
        assertEquals(winsAfterClean, winsAfterTainted)
    }

    @Test
    fun `synchronized player move clocks and undo redo restore state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        vm.syncFromExternalFen(ChessPosition.START_FEN)
        vm.resetMoveClocks(initialMinutes = 10)
        val initialTime = 10 * 60 * 1000L

        assertEquals(Side.WHITE, vm.uiState.value.position.turn)
        assertFalse(vm.uiState.value.canUndo)
        assertFalse(vm.uiState.value.canRedo)

        // Tick 3 seconds on White's turn
        vm.tickClock(3_000L)
        assertEquals(initialTime - 3_000L, vm.uiState.value.whiteTimeRemainingMs)
        assertEquals(initialTime, vm.uiState.value.blackTimeRemainingMs)
        assertEquals("09:57", formatChessClockMs(vm.uiState.value.whiteTimeRemainingMs))

        // White plays e2e4 -> turn switches to Black
        val e2Square = 6 * 8 + 4
        val e4Square = 4 * 8 + 4
        vm.onPieceDragged(e2Square, e4Square)
        assertEquals(Side.BLACK, vm.uiState.value.position.turn)
        assertEquals(1, vm.uiState.value.currentPlyIndex)
        assertTrue(vm.uiState.value.canUndo)
        assertFalse(vm.uiState.value.canRedo)

        // Tick 5 seconds on Black's turn -> only Black's clock decrements
        vm.tickClock(5_000L)
        assertEquals(initialTime - 3_000L, vm.uiState.value.whiteTimeRemainingMs)
        assertEquals(initialTime - 5_000L, vm.uiState.value.blackTimeRemainingMs)
        assertEquals("09:55", formatChessClockMs(vm.uiState.value.blackTimeRemainingMs))

        // Undo move -> restores ply 0 (White's turn) and White/Black clocks at ply 0
        vm.undoMove()
        assertEquals(0, vm.uiState.value.currentPlyIndex)
        assertEquals(Side.WHITE, vm.uiState.value.position.turn)
        assertFalse(vm.uiState.value.canUndo)
        assertTrue(vm.uiState.value.canRedo)
        assertEquals(initialTime - 3_000L, vm.uiState.value.whiteTimeRemainingMs)
        assertEquals(initialTime, vm.uiState.value.blackTimeRemainingMs)

        // Redo move -> restores ply 1 (Black's turn) and Black's clock at ply 1
        vm.redoMove()
        assertEquals(1, vm.uiState.value.currentPlyIndex)
        assertEquals(Side.BLACK, vm.uiState.value.position.turn)
        assertTrue(vm.uiState.value.canUndo)
        assertFalse(vm.uiState.value.canRedo)
        assertEquals(initialTime - 3_000L, vm.uiState.value.whiteTimeRemainingMs)
        assertEquals(initialTime - 5_000L, vm.uiState.value.blackTimeRemainingMs)
    }

    @Test
    fun `sound manager triggers move and capture audio effects during piece placement`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))
        vm.soundManager.resetCounters()

        // 1. e2e4 quiet move triggers MOVE sound
        vm.syncFromExternalFen(ChessPosition.START_FEN)
        val e2 = 6 * 8 + 4
        val e4 = 4 * 8 + 4
        vm.onPieceDragged(e2, e4)
        assertEquals(ChessSoundEffect.MOVE, vm.soundManager.lastPlayedEffect)
        assertEquals(1, vm.soundManager.movePlayCount)

        // 2. d7d5 quiet move triggers MOVE sound
        val d7 = 1 * 8 + 3
        val d5 = 3 * 8 + 3
        vm.onPieceDragged(d7, d5)
        assertEquals(ChessSoundEffect.MOVE, vm.soundManager.lastPlayedEffect)
        assertEquals(2, vm.soundManager.movePlayCount)

        // 3. exd5 capture move triggers CAPTURE sound
        vm.onPieceDragged(e4, d5)
        assertEquals(ChessSoundEffect.CAPTURE, vm.soundManager.lastPlayedEffect)
        assertEquals(1, vm.soundManager.capturePlayCount)

        // 4. Undo exd5 (capture) triggers CAPTURE sound; Undo d7d5 (quiet) triggers MOVE sound; Redo triggers MOVE sound
        vm.undoMove()
        assertEquals(2, vm.soundManager.capturePlayCount)
        vm.undoMove()
        assertEquals(3, vm.soundManager.movePlayCount)
        vm.redoMove()
        assertEquals(4, vm.soundManager.movePlayCount)
    }

    @Test
    fun `leaderboards rank by accuracy score and games won across global and friends scopes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        // Global scope sorted by ACCURACY descending
        vm.setLeaderboardScope(LeaderboardScope.GLOBAL)
        vm.setLeaderboardSortMetric(LeaderboardSortMetric.ACCURACY)
        val globalByAcc = vm.uiState.value.displayedLeaderboard
        assertTrue(globalByAcc.size >= 6)
        assertTrue(globalByAcc[0].accuracyScore >= globalByAcc[1].accuracyScore)

        // Switch sort metric to GAMES_WON descending
        vm.setLeaderboardSortMetric(LeaderboardSortMetric.GAMES_WON)
        val globalByWins = vm.uiState.value.displayedLeaderboard
        assertTrue(globalByWins[0].gamesWon >= globalByWins[1].gamesWon)

        // Friends scope filters only friends + user
        vm.setLeaderboardScope(LeaderboardScope.FRIENDS)
        val friendsList = vm.uiState.value.displayedLeaderboard
        assertTrue(friendsList.all { it.isFriend || it.isCurrentUser })

        // Adding a friend increases the Friends leaderboard count
        val beforeCount = friendsList.size
        vm.addFriendToLeaderboard("IM Alexey R.")
        val afterList = vm.uiState.value.displayedLeaderboard
        assertEquals(beforeCount + 1, afterList.size)
        assertTrue(afterList.any { it.username.contains("Alexey") })
    }

    @Test
    fun `coach mode generates style-tailored suggestions and integrates with progressive hints`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        vm.syncFromExternalFen(ChessPosition.START_FEN)
        vm.toggleCoachMode(true)
        vm.setUserPlayingStyle(PlayingStyleProfile.TACTICAL_AGGRESSIVE)

        val state = vm.uiState.value
        assertTrue(state.coachModeActive)
        assertEquals(PlayingStyleProfile.TACTICAL_AGGRESSIVE, state.userPlayingStyle)
        assertTrue(state.coachSuggestions.isNotEmpty())
        assertTrue(
            state.coachSuggestions.first().styleAlignmentBadge.contains(
                PlayingStyleProfile.TACTICAL_AGGRESSIVE.label
            )
        )

        // Verify integration with 3-step progressive hint system
        vm.advanceProgressiveHint()
        val hintStep1 = vm.uiState.value.progressiveHint
        assertNotNull(hintStep1)
        assertEquals(1, hintStep1.currentStep)
        assertTrue(hintStep1.coachCommentary.isNotBlank())
    }

    @Test
    fun `slowmo animation style overrides fast speed to slow`() {
        val state = CheesseUiState(
            pieceAnimationSpeed = PieceAnimationSpeed.FAST,
            pieceAnimationStyle = PieceAnimationStyle.SLOWMO
        )
        assertEquals(PieceAnimationSpeed.SLOW.durationMs, state.effectiveAnimationDurationMs)
    }

    @Test
    fun `FEN validator accepts valid positions and rejects malformed FEN strings`() {
        // 1. Valid starting FEN
        val startResult = ChessPosition.validateAndParseFen(ChessPosition.START_FEN)
        assertTrue(startResult.isValid)
        assertNotNull(startResult.position)
        assertEquals(Side.WHITE, startResult.position?.turn)

        // 2. Valid tactical scenario FEN (Morphy's Opera Sacrifice)
        val operaPreset = ChessPosition.QUICK_FEN_SCENARIOS.first { it.id == "opera_game" }
        val operaResult = ChessPosition.validateAndParseFen(operaPreset.fen)
        assertTrue(operaResult.isValid)
        assertEquals(Side.WHITE, operaResult.position?.turn)

        // 3. Invalid rank count (only 7 ranks)
        val badRanks = ChessPosition.validateAndParseFen("8/8/8/8/8/8/8 w - - 0 1")
        assertFalse(badRanks.isValid)
        assertTrue(badRanks.errorMessage?.contains("8 ranks") == true)

        // 4. Missing White King
        val missingWhiteKing = ChessPosition.validateAndParseFen("4k3/8/8/8/8/8/8/8 w - - 0 1")
        assertFalse(missingWhiteKing.isValid)
        assertTrue(missingWhiteKing.errorMessage?.contains("White King") == true)

        // 5. Invalid side-to-move token
        val invalidTurn = ChessPosition.validateAndParseFen("4k3/8/8/8/8/8/8/4K3 x - - 0 1")
        assertFalse(invalidTurn.isValid)
        assertTrue(invalidTurn.errorMessage?.contains("active color") == true)
    }

    @Test
    fun `importing FEN string and scenario preset loads position into workbench`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))
        vm.soundManager.resetCounters()

        // 1. Open FEN import sheet
        vm.toggleFenImportSheet(true)
        assertTrue(vm.uiState.value.showFenImportSheet)

        // 2. Reject invalid FEN input and surface error message
        vm.onFenInputTextChanged("8/8/8/8/8/8/8 w - - 0 1")
        assertNotNull(vm.uiState.value.fenImportError)
        val rejected = vm.importFenPosition("8/8/8/8/8/8/8 w - - 0 1")
        assertFalse(rejected)
        assertNotNull(vm.uiState.value.fenImportError)

        // 3. Import valid custom endgame FEN (Black to move)
        val customFen = "1K1k4/1P6/8/8/8/8/r7/2R5 b - - 0 1"
        val accepted = vm.importFenPosition(customFen)
        assertTrue(accepted)
        assertFalse(vm.uiState.value.showFenImportSheet)
        assertEquals( null, vm.uiState.value.fenImportError)
        assertEquals(Side.BLACK, vm.uiState.value.position.turn)
        assertEquals(customFen, vm.uiState.value.position.toFen())
        assertEquals(0, vm.uiState.value.currentPlyIndex)
        assertTrue(vm.uiState.value.moveHistory.isEmpty())
        assertEquals(ChessSoundEffect.MOVE, vm.soundManager.lastPlayedEffect)

        // 4. Load Quick Scenario Preset (Saavedra Promotion Study)
        val saavedra = ChessPosition.QUICK_FEN_SCENARIOS.first { it.id == "saavedra_study" }
        val presetLoaded = vm.selectFenScenarioPreset(saavedra)
        assertTrue(presetLoaded)
        assertEquals(saavedra.fen, vm.uiState.value.position.toFen())
        assertTrue(vm.uiState.value.statusBannerMessage?.contains(saavedra.label) == true)
    }

    @Test
    fun `default tournament board theme and gauge bar multi-pv controls update engine state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = CheesseLocalDatabase.getInstance(context)
        val vm = CheesseViewModel(CheesseLocalRepository(db.cheesseDao()))

        // Default theme is CLASSIC_WALNUT (#E8D0AA light squares, #B58863 dark squares)
        val defaultTheme = vm.uiState.value.boardTheme
        assertEquals(com.example.ui.theme.BoardThemeOption.CLASSIC_WALNUT, defaultTheme)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE8D0AA), defaultTheme.lightSquare)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFB58863), defaultTheme.darkSquare)

        // Adjust Multi-PV count via + / - circular buttons
        val initialMultiPv = vm.uiState.value.engineConfig.multiPv
        vm.adjustMultiPvCount(1)
        assertEquals((initialMultiPv + 1).coerceAtMost(5), vm.uiState.value.engineConfig.multiPv)
        vm.adjustMultiPvCount(-2)
        assertEquals(((initialMultiPv + 1).coerceAtMost(5) - 2).coerceAtLeast(1), vm.uiState.value.engineConfig.multiPv)

        // Toggle Engine Play/Pause via circular ▶ button
        val wasRunning = vm.uiState.value.isEngineRunning
        vm.toggleEngineRunning()
        assertEquals(!wasRunning, vm.uiState.value.isEngineRunning)
    }
}
