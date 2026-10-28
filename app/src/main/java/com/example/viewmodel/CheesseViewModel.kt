package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChessSoundManager
import com.example.data.AnalyzedGameEntity
import com.example.data.CheesseLocalRepository
import com.example.data.CloudLeaderboardDoc
import com.example.data.FirestoreLeaderboardRepository
import com.example.data.FriendChallengeEntity
import com.example.data.LocalPlayerStatsSnapshot
import com.example.engine.ChessMove
import com.example.engine.ChessPiece
import com.example.engine.ChessPosition
import com.example.engine.CoachMoveSuggestion
import com.example.engine.DeepGameReport
import com.example.engine.EngineConfig
import com.example.engine.FenScenarioPreset
import com.example.engine.FenValidationResult
import com.example.engine.KeyPositionElement
import com.example.engine.MoveStrength
import com.example.engine.MultiPvLine
import com.example.engine.OpeningExplorerData
import com.example.engine.PieceType
import com.example.engine.PlayingStyleProfile
import com.example.engine.PlyReviewItem
import com.example.engine.ProgressiveHintState
import com.example.engine.Side
import com.example.engine.TacticalThreat
import com.example.engine.UciAnalysisEngine
import com.example.service.MoveEvaluationResult
import com.example.service.UciEngineService
import com.example.service.UciServiceState
import com.example.ui.theme.BoardThemeOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudioTab(val label: String) {
    ANALYZE("Analysis"),
    REVIEW("Game Review"),
    PLAY("Play & Train"),
    SOCIAL("Challenges"),
    EDITOR("Editor"),
    SETTINGS("Settings")
}

enum class WorkbenchPanelTab(val label: String) {
    ENGINE_LINES("Engine & Coach"),
    LEADERBOARD("Leaderboard"),
    EXPLORER("Opening & Threats")
}

enum class VariationMoveBehavior(val label: String) {
    CREATE_VARIATION("Create Sub-Variation"),
    PROMOTE_MAINLINE("Promote to Mainline"),
    OVERWRITE_LINE("Overwrite From Here")
}

enum class PieceAnimationSpeed(val label: String, val durationMs: Int) {
    INSTANT("Instant", 0),
    FAST("Fast", 110),
    NORMAL("Normal", 220),
    SLOW("Slow", 480)
}

enum class PieceAnimationStyle(val label: String) {
    STANDARD("Standard Slide"),
    ARC("Knight Arc"),
    FADE("Smooth Crossfade"),
    SLOWMO("Slowmo (Overrides Fast → Slow)")
}

enum class DrawingColorOption(val label: String, val color: Color) {
    EMERALD("Green", Color(0xFF22C55E)),
    CRIMSON("Red", Color(0xFFEF4444)),
    CYAN("Blue", Color(0xFF38BDF8)),
    AMBER("Amber", Color(0xFFFACC15))
}

data class DrawnArrow(
    val from: Int,
    val to: Int,
    val colorOption: DrawingColorOption
)

data class HighlightedSquare(
    val square: Int,
    val colorOption: DrawingColorOption
)

data class VariationBranch(
    val id: String,
    val parentPly: Int,
    val rawLabel: String,
    val moveSan: String,
    val strength: MoveStrength,
    val targetFen: String
) {
    fun renderDisplay(figurine: Boolean, includeSymbols: Boolean): String {
        return if (includeSymbols && strength.symbol.isNotEmpty() && !rawLabel.endsWith(strength.symbol)) {
            "$rawLabel${strength.symbol}"
        } else {
            rawLabel
        }
    }
}

data class MistakePuzzleItem(
    val id: String,
    val gameTitle: String,
    val fenBefore: String,
    val playedBadMoveSan: String,
    val bestMove: ChessMove,
    val explanation: String,
    val sideToMove: Side,
    val solved: Boolean = false
)

data class ArchiveStudyPreset(
    val id: String,
    val title: String,
    val whitePlayer: String,
    val blackPlayer: String,
    val movesUci: List<String>,
    val movesSan: List<String>,
    val analyzed: Boolean = true,
    val whiteAccuracy: Int = 91,
    val blackAccuracy: Int = 84,
    val whiteAvgCpl: Int = 19,
    val blackAvgCpl: Int = 34,
    val mistakeCount: Int = 2,
    val blunderCount: Int = 1
)

enum class LeaderboardScope(val label: String) {
    GLOBAL("Global Leaderboard"),
    FRIENDS("Friends Leaderboard")
}

enum class LeaderboardSortMetric(val label: String) {
    ACCURACY("Accuracy Score"),
    GAMES_WON("Games Won")
}

data class LeaderboardPlayerEntry(
    val id: String,
    val username: String,
    val titleBadge: String,
    val rating: Int = 1850,
    val accuracyScore: Double,
    val gamesWon: Int,
    val gamesPlayed: Int,
    val winStreak: Int = 3,
    val isFriend: Boolean,
    val isCurrentUser: Boolean = false,
    val playingStyle: PlayingStyleProfile = PlayingStyleProfile.UNIVERSAL_BALANCED
)

data class CheesseUiState(
    val activeTab: StudioTab = StudioTab.ANALYZE,
    val activeWorkbenchPanel: WorkbenchPanelTab = WorkbenchPanelTab.ENGINE_LINES,
    val position: ChessPosition = ChessPosition.fromFen(ChessPosition.START_FEN),
    val boardFlipped: Boolean = false,
    val whitePlayerName: String = "CheessePlayer",
    val blackPlayerName: String = "Stockfish 16 NNUE",
    val moveHistory: List<ChessMove> = emptyList(),
    val moveStrengths: List<MoveStrength> = emptyList(),
    val positionHistoryFens: List<String> = listOf(ChessPosition.START_FEN),
    val currentPlyIndex: Int = 0,
    val variations: List<VariationBranch> = emptyList(),

    // Synchronized Dual-Player Move Countdown Clocks
    val initialClockTimeMs: Long = 10 * 60 * 1000L,
    val whiteTimeRemainingMs: Long = 10 * 60 * 1000L,
    val blackTimeRemainingMs: Long = 10 * 60 * 1000L,
    val clockHistoryWhiteMs: List<Long> = listOf(10 * 60 * 1000L),
    val clockHistoryBlackMs: List<Long> = listOf(10 * 60 * 1000L),
    val isClockRunning: Boolean = true,
    val timedOutSide: Side? = null,

    // Selection, Legal Moves & Pawn Promotion Interception
    val selectedSquare: Int? = null,
    val legalMovesForSelected: List<ChessMove> = emptyList(),
    val pendingPromotionMove: ChessMove? = null,
    val lastMove: ChessMove? = null,
    val lastMoveStrength: MoveStrength? = null,
    val previousBestMove: ChessMove? = null,

    // Bound UCI Engine Service & Move Evaluation Stream
    val isUciServiceBound: Boolean = false,
    val engineConfig: EngineConfig = EngineConfig(),
    val isEngineRunning: Boolean = true,
    val enginePausedByPowerSave: Boolean = false,
    val currentSearchDepth: Int = 6,
    val nodesPerSec: Int = 148000,
    val elapsedAnalysisMs: Long = 0L,
    val multiPvLines: List<MultiPvLine> = emptyList(),
    val candidateMoveEvaluations: List<MoveEvaluationResult> = emptyList(),
    val inspectedMoveEvaluation: MoveEvaluationResult? = null,
    val lastUciCommand: String = "uci",
    val lastUciProtocolOutput: String = "id name Stockfish 16.1 NNUE · uciok · readyok",
    val evalCentipawns: Int = 20,
    val threats: List<TacticalThreat> = emptyList(),
    val keyElements: List<KeyPositionElement> = emptyList(),
    val openingExplorer: OpeningExplorerData? = null,

    // Anti-Cheat Ranked Session & Engine Taint Tracking
    val isUnassistedRankedSession: Boolean = false,
    val isTaintedByEngine: Boolean = false,

    // Visual & Evaluation Bar Toggles
    val smartEvalBar: Boolean = true,
    val showEvalBarInEditor: Boolean = true,
    val showEngineArrows: Boolean = true,
    val showEngineLineStrengthColors: Boolean = true,
    val showAnalysisArrowsNumbers: Boolean = true,
    val showPreviousBestMoveArrow: Boolean = true,
    val colorLastMoveByStrength: Boolean = true,
    val showThreats: Boolean = true,
    val showKeyElements: Boolean = true,

    // Notation, Captured Pieces & Player Auto-Flip
    val figurineNotation: Boolean = true,
    val variationMoveSymbols: Boolean = true,
    val variationMoveBehavior: VariationMoveBehavior = VariationMoveBehavior.CREATE_VARIATION,
    val showCapturedPieces: Boolean = true,
    val yourUsernames: String = "CheessePlayer, HeroGM",
    val showMovesInPortrait: Boolean = true,

    // Board Interaction, Arrow Draw Mode, Drawing & Animation Settings
    val showLegalMoveDots: Boolean = true,
    val isArrowDrawMode: Boolean = false,
    val drawArrowsOnBoard: Boolean = false,
    val showDrawingPanel: Boolean = false,
    val activeDrawingColor: DrawingColorOption = DrawingColorOption.EMERALD,
    val drawnArrows: List<DrawnArrow> = emptyList(),
    val highlightedSquares: List<HighlightedSquare> = emptyList(),
    val drawArrowStartSquare: Int? = null,
    val enlargePieceOnDrag: Boolean = true,
    val disableDragAndDrop: Boolean = false,
    val autoplaySpeedMs: Long = 1000L,
    val isAutoplayRunning: Boolean = false,
    val pieceAnimationSpeed: PieceAnimationSpeed = PieceAnimationSpeed.NORMAL,
    val pieceAnimationStyle: PieceAnimationStyle = PieceAnimationStyle.STANDARD,
    val soundEnabled: Boolean = true,
    val fullScreenMode: Boolean = false,
    val boardTheme: BoardThemeOption = BoardThemeOption.TOURNAMENT_EMERALD,
    val boardSizeScale: Float = 1.0f,

    // Game Review, Blunder Refutation & Multi-Game Archive
    val deepReport: DeepGameReport? = null,
    val showAverageCplInReport: Boolean = false,
    val activeWhyBlunderLine: List<String> = emptyList(),
    val isPlayingWhyBlunder: Boolean = false,
    val showEachMoveStrengthWhilePlaying: Boolean = true,
    val pauseOnMistakeWhilePlaying: Boolean = true,
    val pausedMistakeAlert: String? = null,
    val isVsEnginePlayActive: Boolean = false,

    // Background Batch Analysis & Multi-Game Mistake Retry
    val archiveGames: List<ArchiveStudyPreset> = defaultArchivePresets(),
    val selectedArchiveIds: Set<String> = setOf("preset_1", "preset_2"),
    val isBatchAnalyzingBackground: Boolean = false,
    val batchAnalysisProgressText: String = "",
    val isRetryMistakesMode: Boolean = false,
    val mistakePuzzles: List<MistakePuzzleItem> = emptyList(),
    val currentPuzzleIndex: Int = 0,
    val puzzleFeedback: String? = null,

    // Progressive 3-Step Hint System & Coach Mode
    val progressiveHint: ProgressiveHintState = ProgressiveHintState(),
    val coachModeActive: Boolean = true,
    val userPlayingStyle: PlayingStyleProfile = PlayingStyleProfile.TACTICAL_AGGRESSIVE,
    val coachSuggestions: List<CoachMoveSuggestion> = emptyList(),
    val lastSoundEffectLabel: String? = null,

    // Global & Friends Leaderboards + Cloud Auth / Offline Sync State
    val leaderboardScope: LeaderboardScope = LeaderboardScope.GLOBAL,
    val leaderboardSortMetric: LeaderboardSortMetric = LeaderboardSortMetric.ACCURACY,
    val leaderboardEntries: List<LeaderboardPlayerEntry> = defaultLeaderboardEntries(),
    val isCloudAuthenticated: Boolean = false,
    val cloudUserId: String? = null,
    val cloudDisplayName: String? = null,
    val pendingCloudSyncCount: Int = 0,
    val userRating: Int = 1850,
    val userWinStreak: Int = 3,

    // Board Editor & FEN Position Importer State
    val showFenImportSheet: Boolean = false,
    val fenInputText: String = ChessPosition.START_FEN,
    val fenImportError: String? = null,
    val fenScenarioPresets: List<FenScenarioPreset> = ChessPosition.QUICK_FEN_SCENARIOS,
    val editorPosition: ChessPosition = ChessPosition.fromFen(ChessPosition.START_FEN),
    val editorSelectedPiece: ChessPiece = ChessPiece(PieceType.QUEEN, Side.WHITE),
    val editorEraseMode: Boolean = false,
    val editorEvalCp: Int = 20,

    // Local Friend Challenges
    val activeChallenge: FriendChallengeEntity? = null,
    val statusBannerMessage: String? = null
) {
    val effectiveAnimationDurationMs: Int
        get() = if (pieceAnimationStyle == PieceAnimationStyle.SLOWMO && pieceAnimationSpeed == PieceAnimationSpeed.FAST) {
            PieceAnimationSpeed.SLOW.durationMs
        } else {
            pieceAnimationSpeed.durationMs
        }

    val canUndo: Boolean
        get() = currentPlyIndex > 0

    val canRedo: Boolean
        get() = currentPlyIndex < moveHistory.size

    val hideEngineTelemetryForFairPlay: Boolean
        get() = isUnassistedRankedSession && !isTaintedByEngine

    val displayedLeaderboard: List<LeaderboardPlayerEntry>
        get() {
            val filtered = when (leaderboardScope) {
                LeaderboardScope.GLOBAL -> leaderboardEntries
                LeaderboardScope.FRIENDS -> leaderboardEntries.filter { it.isFriend || it.isCurrentUser }
            }
            return when (leaderboardSortMetric) {
                LeaderboardSortMetric.ACCURACY -> filtered.sortedWith(
                    compareByDescending<LeaderboardPlayerEntry> { it.accuracyScore }
                        .thenByDescending { it.gamesWon }
                        .thenByDescending { it.rating }
                )
                LeaderboardSortMetric.GAMES_WON -> filtered.sortedWith(
                    compareByDescending<LeaderboardPlayerEntry> { it.gamesWon }
                        .thenByDescending { it.accuracyScore }
                        .thenByDescending { it.rating }
                )
            }
        }
}

fun formatChessClockMs(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    if (clamped < 10_000L) {
        val secs = clamped / 1000L
        val tenths = (clamped % 1000L) / 100L
        return String.format("00:%02d.%d", secs, tenths)
    }
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}

private fun defaultLeaderboardEntries(): List<LeaderboardPlayerEntry> = listOf(
    LeaderboardPlayerEntry(
        id = "lb_gm_nordic",
        username = "NordicVikingGM",
        titleBadge = "GM",
        rating = 2480,
        accuracyScore = 95.8,
        gamesWon = 64,
        gamesPlayed = 76,
        winStreak = 7,
        isFriend = false,
        isCurrentUser = false,
        playingStyle = PlayingStyleProfile.UNIVERSAL_BALANCED
    ),
    LeaderboardPlayerEntry(
        id = "lb_im_elena",
        username = "ElenaTactics",
        titleBadge = "IM",
        rating = 2365,
        accuracyScore = 93.4,
        gamesWon = 52,
        gamesPlayed = 68,
        winStreak = 5,
        isFriend = true,
        isCurrentUser = false,
        playingStyle = PlayingStyleProfile.TACTICAL_AGGRESSIVE
    ),
    LeaderboardPlayerEntry(
        id = "lb_user_self",
        username = "CheessePlayer",
        titleBadge = "CM",
        rating = 1850,
        accuracyScore = 91.6,
        gamesWon = 38,
        gamesPlayed = 49,
        winStreak = 3,
        isFriend = true,
        isCurrentUser = true,
        playingStyle = PlayingStyleProfile.TACTICAL_AGGRESSIVE
    ),
    LeaderboardPlayerEntry(
        id = "lb_fm_karpovfan",
        username = "EndgameArchitect",
        titleBadge = "FM",
        rating = 2210,
        accuracyScore = 90.2,
        gamesWon = 45,
        gamesPlayed = 60,
        winStreak = 2,
        isFriend = true,
        isCurrentUser = false,
        playingStyle = PlayingStyleProfile.POSITIONAL_SOLID
    ),
    LeaderboardPlayerEntry(
        id = "lb_nm_gambit",
        username = "EvansGambitKing",
        titleBadge = "NM",
        rating = 2125,
        accuracyScore = 87.9,
        gamesWon = 59,
        gamesPlayed = 84,
        winStreak = 4,
        isFriend = false,
        isCurrentUser = false,
        playingStyle = PlayingStyleProfile.DYNAMIC_GAMBIT
    ),
    LeaderboardPlayerEntry(
        id = "lb_club_88",
        username = "ClubMaster_88",
        titleBadge = "1950",
        rating = 1950,
        accuracyScore = 84.5,
        gamesWon = 27,
        gamesPlayed = 48,
        winStreak = 1,
        isFriend = true,
        isCurrentUser = false,
        playingStyle = PlayingStyleProfile.UNIVERSAL_BALANCED
    )
)

private fun defaultArchivePresets(): List<ArchiveStudyPreset> = listOf(
    ArchiveStudyPreset(
        id = "preset_1",
        title = "Italian Game: Evans Gambit Tactical Clash",
        whitePlayer = "CheessePlayer",
        blackPlayer = "ClubMaster_88",
        movesUci = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "b2b4", "c5b4", "c2c3", "b4a5", "d2d4", "e5d4"),
        movesSan = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "b4", "Bxb4", "c3", "Ba5", "d4", "exd4"),
        analyzed = true,
        whiteAccuracy = 92,
        blackAccuracy = 79,
        whiteAvgCpl = 18,
        blackAvgCpl = 47,
        mistakeCount = 2,
        blunderCount = 1
    ),
    ArchiveStudyPreset(
        id = "preset_2",
        title = "Sicilian Defense: Dragon Sharp Counterattack",
        whitePlayer = "Grandmaster_K",
        blackPlayer = "CheessePlayer",
        movesUci = listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "g7g6"),
        movesSan = listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "g6"),
        analyzed = true,
        whiteAccuracy = 88,
        blackAccuracy = 85,
        whiteAvgCpl = 25,
        blackAvgCpl = 31,
        mistakeCount = 2,
        blunderCount = 1
    ),
    ArchiveStudyPreset(
        id = "preset_3",
        title = "Queen's Gambit: Central Outpost Study",
        whitePlayer = "CheessePlayer",
        blackPlayer = "TacticalBot",
        movesUci = listOf("d2d4", "d7d5", "c2c4", "e7e6", "b1c3", "g8f6", "c1g5", "f8e7"),
        movesSan = listOf("d4", "d5", "c4", "e6", "Nc3", "Nf6", "Bg5", "Be7"),
        analyzed = false,
        whiteAccuracy = 0,
        blackAccuracy = 0,
        whiteAvgCpl = 0,
        blackAvgCpl = 0,
        mistakeCount = 1,
        blunderCount = 0
    )
)

class CheesseViewModel(
    private val repository: CheesseLocalRepository,
    private val cloudLeaderboardRepository: FirestoreLeaderboardRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheesseUiState())
    val uiState: StateFlow<CheesseUiState> = _uiState.asStateFlow()

    // Dedicated high-frequency telemetry stream isolated for ChessBoardEngineStatusHeader
    private val _engineTelemetryFlow = MutableStateFlow(UciServiceState())
    val engineTelemetryFlow: StateFlow<UciServiceState> = _engineTelemetryFlow.asStateFlow()

    val savedGames: StateFlow<List<AnalyzedGameEntity>> = repository.analyzedGames
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val openChallenges: StateFlow<List<FriendChallengeEntity>> = repository.friendChallenges
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val soundManager = ChessSoundManager()

    private var uciService: UciEngineService? = null
    private var serviceCollectionJob: Job? = null
    private var fallbackEngineJob: Job? = null
    private var debouncedEvalJob: Job? = null
    private var autoplayJob: Job? = null
    private var clockTickerJob: Job? = null
    private var cloudLeaderboardCollectionJob: Job? = null

    init {
        loadPresetIntoBoard(defaultArchivePresets().first(), generateReportNow = true)
        startLiveUciEngineAnalysis()
        startMoveClockTicker()
        initializeCloudAuthAndOfflineSyncWorker()
    }

    private fun initializeCloudAuthAndOfflineSyncWorker() {
        val cloudRepo = cloudLeaderboardRepository ?: return
        val uid = cloudRepo.currentUserId
        val displayName = cloudRepo.currentUserDisplayName
        if (uid != null) {
            _uiState.update {
                it.copy(
                    isCloudAuthenticated = true,
                    cloudUserId = uid,
                    cloudDisplayName = displayName
                )
            }
            observeCloudLeaderboardStream()
        }
        viewModelScope.launch {
            refreshPendingSyncCount()
            if (cloudRepo.currentUserId != null) {
                syncPendingCleanGamesToCloud()
            }
        }
    }

    private fun observeCloudLeaderboardStream() {
        val cloudRepo = cloudLeaderboardRepository ?: return
        cloudLeaderboardCollectionJob?.cancel()
        cloudLeaderboardCollectionJob = viewModelScope.launch {
            cloudRepo.observePublicLeaderboard().collect { cloudDocs ->
                if (cloudDocs.isNotEmpty()) {
                    mergeCloudLeaderboardDocsIntoUi(cloudDocs)
                }
            }
        }
    }

    private fun mergeCloudLeaderboardDocsIntoUi(cloudDocs: List<CloudLeaderboardDoc>) {
        val currentUid = cloudLeaderboardRepository?.currentUserId
        _uiState.update { state ->
            val cloudMapped = cloudDocs.map { doc ->
                val style = PlayingStyleProfile.entries.firstOrNull {
                    it.name.equals(doc.playingStyle, ignoreCase = true)
                } ?: PlayingStyleProfile.TACTICAL_AGGRESSIVE
                val isSelf = (currentUid != null && doc.userId == currentUid) ||
                    doc.username.equals(state.whitePlayerName, ignoreCase = true)
                LeaderboardPlayerEntry(
                    id = doc.id.ifEmpty { doc.userId },
                    username = doc.username,
                    titleBadge = doc.titleBadge,
                    rating = doc.rating,
                    accuracyScore = doc.accuracyScore,
                    gamesWon = doc.gamesWon,
                    gamesPlayed = doc.gamesPlayed,
                    winStreak = doc.winStreak,
                    isFriend = true,
                    isCurrentUser = isSelf,
                    playingStyle = style
                )
            }
            val nonOverlappingDefaults = state.leaderboardEntries.filter { local ->
                cloudMapped.none { cloud ->
                    cloud.id == local.id ||
                        cloud.username.equals(local.username, ignoreCase = true) ||
                        (cloud.isCurrentUser && local.isCurrentUser)
                }
            }
            state.copy(leaderboardEntries = cloudMapped + nonOverlappingDefaults)
        }
    }

    private suspend fun refreshPendingSyncCount() {
        val pending = try {
            repository.getPendingCleanGamesForSync().size
        } catch (_: Exception) {
            0
        }
        _uiState.update { it.copy(pendingCloudSyncCount = pending) }
    }

    /**
     * Offline-first coroutine sync worker: pushes un-synced clean games (`isTaintedByEngine == false && pendingCloudSync == true`)
     * from Room to Firestore `leaderboard/{uid}` using an atomic `runTransaction`.
     */
    fun syncPendingCleanGamesToCloud() {
        val cloudRepo = cloudLeaderboardRepository ?: return
        val uid = cloudRepo.currentUserId ?: return
        viewModelScope.launch {
            try {
                val pendingClean = repository.getPendingCleanGamesForSync()
                val selfEntry = _uiState.value.leaderboardEntries.firstOrNull { it.isCurrentUser }
                val localSnapshot = LocalPlayerStatsSnapshot(
                    username = _uiState.value.cloudDisplayName ?: selfEntry?.username ?: "CheessePlayer",
                    titleBadge = selfEntry?.titleBadge ?: "CM",
                    rating = selfEntry?.rating ?: _uiState.value.userRating,
                    accuracyScore = selfEntry?.accuracyScore ?: 91.6,
                    gamesWon = selfEntry?.gamesWon ?: 38,
                    gamesPlayed = selfEntry?.gamesPlayed ?: 49,
                    winStreak = selfEntry?.winStreak ?: _uiState.value.userWinStreak,
                    playingStyle = _uiState.value.userPlayingStyle.name
                )
                val updatedDoc = cloudRepo.syncCleanGamesAndMergeProfile(
                    uid = uid,
                    localStats = localSnapshot,
                    cleanGamesToSync = pendingClean
                )
                if (updatedDoc != null) {
                    pendingClean.forEach { game ->
                        repository.markGameCloudSynced(game.id)
                    }
                    refreshPendingSyncCount()
                    _uiState.update { state ->
                        val updatedEntries = state.leaderboardEntries.map { entry ->
                            if (entry.isCurrentUser) {
                                entry.copy(
                                    username = updatedDoc.username,
                                    rating = updatedDoc.rating,
                                    accuracyScore = updatedDoc.accuracyScore,
                                    gamesWon = updatedDoc.gamesWon,
                                    gamesPlayed = updatedDoc.gamesPlayed,
                                    winStreak = updatedDoc.winStreak
                                )
                            } else entry
                        }
                        state.copy(
                            userRating = updatedDoc.rating,
                            userWinStreak = updatedDoc.winStreak,
                            leaderboardEntries = updatedEntries
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Called when the user signs in with Google via Jetpack CredentialManager in the Leaderboard panel.
     * Links the current session or resolves `FirebaseAuthUserCollisionException` by merging local Room
     * stats with the cloud Firestore profile (`max(localScore, cloudScore)`).
     */
    fun onGoogleSignInIdTokenReceived(idToken: String) {
        val cloudRepo = cloudLeaderboardRepository ?: return
        viewModelScope.launch {
            try {
                val pendingClean = repository.getPendingCleanGamesForSync()
                val selfEntry = _uiState.value.leaderboardEntries.firstOrNull { it.isCurrentUser }
                val localSnapshot = LocalPlayerStatsSnapshot(
                    username = selfEntry?.username ?: "CheessePlayer",
                    titleBadge = selfEntry?.titleBadge ?: "CM",
                    rating = selfEntry?.rating ?: _uiState.value.userRating,
                    accuracyScore = selfEntry?.accuracyScore ?: 91.6,
                    gamesWon = selfEntry?.gamesWon ?: 38,
                    gamesPlayed = selfEntry?.gamesPlayed ?: 49,
                    winStreak = selfEntry?.winStreak ?: _uiState.value.userWinStreak,
                    playingStyle = _uiState.value.userPlayingStyle.name
                )
                val mergedDoc = cloudRepo.linkOrSignInWithGoogleIdToken(
                    idToken = idToken,
                    localStats = localSnapshot,
                    cleanGames = pendingClean
                )
                if (mergedDoc != null) {
                    pendingClean.forEach { repository.markGameCloudSynced(it.id) }
                    refreshPendingSyncCount()
                    observeCloudLeaderboardStream()
                    _uiState.update { state ->
                        val updatedEntries = state.leaderboardEntries.map { entry ->
                            if (entry.isCurrentUser) {
                                entry.copy(
                                    id = mergedDoc.id.ifEmpty { mergedDoc.userId },
                                    username = mergedDoc.username,
                                    rating = mergedDoc.rating,
                                    accuracyScore = mergedDoc.accuracyScore,
                                    gamesWon = mergedDoc.gamesWon,
                                    gamesPlayed = mergedDoc.gamesPlayed,
                                    winStreak = mergedDoc.winStreak
                                )
                            } else entry
                        }
                        state.copy(
                            isCloudAuthenticated = true,
                            cloudUserId = mergedDoc.userId,
                            cloudDisplayName = mergedDoc.username,
                            userRating = mergedDoc.rating,
                            userWinStreak = mergedDoc.winStreak,
                            leaderboardEntries = updatedEntries,
                            statusBannerMessage = "Linked Google Account (${mergedDoc.username}) & synced clean Room stats to Cloud Leaderboard!"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Google Sign-In failed: ${e.localizedMessage ?: "Please try again"}")
                }
            }
        }
    }

    fun selectWorkbenchPanel(panel: WorkbenchPanelTab) {
        _uiState.update { it.copy(activeWorkbenchPanel = panel) }
    }

    private fun startMoveClockTicker() {
        clockTickerJob?.cancel()
        clockTickerJob = viewModelScope.launch {
            while (true) {
                delay(200L)
                if (_uiState.value.isClockRunning && _uiState.value.timedOutSide == null) {
                    tickClock(200L)
                }
            }
        }
    }

    /**
     * Advances the active player's move countdown clock by [deltaMs] in synchronization with [ChessPosition.turn].
     */
    fun tickClock(deltaMs: Long) {
        _uiState.update { state ->
            if (!state.isClockRunning || state.timedOutSide != null || deltaMs <= 0L) {
                return@update state
            }
            val activeTurn = state.position.turn
            val nextWhite = if (activeTurn == Side.WHITE) {
                (state.whiteTimeRemainingMs - deltaMs).coerceAtLeast(0L)
            } else {
                state.whiteTimeRemainingMs
            }
            val nextBlack = if (activeTurn == Side.BLACK) {
                (state.blackTimeRemainingMs - deltaMs).coerceAtLeast(0L)
            } else {
                state.blackTimeRemainingMs
            }

            val updatedWhiteHistory = state.clockHistoryWhiteMs.toMutableList().apply {
                if (state.currentPlyIndex in indices) {
                    this[state.currentPlyIndex] = nextWhite
                }
            }
            val updatedBlackHistory = state.clockHistoryBlackMs.toMutableList().apply {
                if (state.currentPlyIndex in indices) {
                    this[state.currentPlyIndex] = nextBlack
                }
            }

            val flaggedSide = when {
                nextWhite <= 0L -> Side.WHITE
                nextBlack <= 0L -> Side.BLACK
                else -> null
            }

            state.copy(
                whiteTimeRemainingMs = nextWhite,
                blackTimeRemainingMs = nextBlack,
                clockHistoryWhiteMs = updatedWhiteHistory,
                clockHistoryBlackMs = updatedBlackHistory,
                timedOutSide = flaggedSide,
                isClockRunning = if (flaggedSide != null) false else state.isClockRunning,
                statusBannerMessage = if (flaggedSide != null) {
                    "${if (flaggedSide == Side.WHITE) state.whitePlayerName else state.blackPlayerName} (${flaggedSide.name}) flagged on time!"
                } else {
                    state.statusBannerMessage
                }
            )
        }
    }

    fun toggleClockRunning() {
        _uiState.update { state ->
            if (state.timedOutSide != null) {
                state
            } else {
                state.copy(isClockRunning = !state.isClockRunning)
            }
        }
    }

    fun resetMoveClocks(initialMinutes: Int = 10) {
        val baseMs = initialMinutes.coerceIn(1, 180) * 60 * 1000L
        _uiState.update { state ->
            val plyCount = state.positionHistoryFens.size.coerceAtLeast(1)
            state.copy(
                initialClockTimeMs = baseMs,
                whiteTimeRemainingMs = baseMs,
                blackTimeRemainingMs = baseMs,
                clockHistoryWhiteMs = List(plyCount) { baseMs },
                clockHistoryBlackMs = List(plyCount) { baseMs },
                timedOutSide = null,
                isClockRunning = true,
                statusBannerMessage = null
            )
        }
    }

    /**
     * Starts an unassisted ranked Play/Train session:
     * Automatically pauses `UciEngineService` (`stop`), hides the Engine Status Header eval pill/PV line
     * and Smart Evaluation Bar, and locks candidate move evaluations.
     */
    fun startUnassistedRankedSession() {
        debouncedEvalJob?.cancel()
        fallbackEngineJob?.cancel()
        uciService?.stopAnalysis()
        _uiState.update {
            it.copy(
                isUnassistedRankedSession = true,
                isTaintedByEngine = false,
                isEngineRunning = false,
                smartEvalBar = false,
                showEngineArrows = false,
                multiPvLines = emptyList(),
                candidateMoveEvaluations = emptyList(),
                inspectedMoveEvaluation = null,
                progressiveHint = ProgressiveHintState(),
                coachModeActive = false,
                coachSuggestions = emptyList(),
                statusBannerMessage = "Unassisted Ranked Session Active — Engine telemetry & eval bar locked for anti-cheat protection."
            )
        }
    }

    fun exitUnassistedRankedSession() {
        _uiState.update {
            it.copy(
                isUnassistedRankedSession = false,
                smartEvalBar = true,
                showEngineArrows = true,
                statusBannerMessage = "Exited Ranked Session — Engine analysis restored."
            )
        }
        startLiveUciEngineAnalysis()
    }

    /**
     * Marks the current session as permanently tainted by engine assistance (`isTaintedByEngine = true`)
     * if the user enables engine evaluation, eval bar, or hints during a ranked session.
     */
    private fun markSessionTaintedByEngine(reason: String) {
        _uiState.update { state ->
            if (state.isUnassistedRankedSession && !state.isTaintedByEngine) {
                state.copy(
                    isTaintedByEngine = true,
                    statusBannerMessage = "Anti-Cheat Notice: $reason enabled mid-game — Session marked TAINTED (Leaderboard submission disabled)."
                )
            } else {
                state
            }
        }
    }

    /**
     * Binds the Android [UciEngineService] component to this ViewModel and subscribes
     * to its reactive [UciEngineService.serviceState] stream.
     */
    fun attachUciService(service: UciEngineService) {
        uciService = service
        fallbackEngineJob?.cancel()
        serviceCollectionJob?.cancel()

        _uiState.update { it.copy(isUciServiceBound = true) }
        if (!_uiState.value.hideEngineTelemetryForFairPlay) {
            service.startLiveAnalysis(_uiState.value.position, _uiState.value.engineConfig)
        }

        serviceCollectionJob = viewModelScope.launch {
            service.serviceState.collect { svcState ->
                _engineTelemetryFlow.value = svcState
                _uiState.update { cur ->
                    if (cur.hideEngineTelemetryForFairPlay) {
                        return@update cur.copy(
                            isUciServiceBound = true,
                            isEngineRunning = false,
                            multiPvLines = emptyList(),
                            candidateMoveEvaluations = emptyList()
                        )
                    }
                    val updatedHint = if (cur.progressiveHint.currentStep > 0) {
                        UciAnalysisEngine.buildProgressiveHint(
                            pos = cur.position,
                            bestLine = svcState.multiPvLines.firstOrNull(),
                            nextStep = cur.progressiveHint.currentStep,
                            coachModeActive = cur.coachModeActive,
                            playingStyle = cur.userPlayingStyle
                        )
                    } else {
                        cur.progressiveHint
                    }
                    val updatedCoach = if (cur.coachModeActive) {
                        UciAnalysisEngine.generateCoachSuggestions(
                            pos = cur.position,
                            multiPvLines = svcState.multiPvLines,
                            style = cur.userPlayingStyle
                        )
                    } else {
                        emptyList()
                    }
                    cur.copy(
                        isUciServiceBound = true,
                        isEngineRunning = svcState.isAnalyzing,
                        enginePausedByPowerSave = svcState.pausedByPowerSaving,
                        currentSearchDepth = svcState.currentDepth,
                        nodesPerSec = svcState.nodesPerSec,
                        elapsedAnalysisMs = svcState.elapsedMs,
                        multiPvLines = svcState.multiPvLines,
                        candidateMoveEvaluations = svcState.candidateMoveEvaluations,
                        lastUciCommand = svcState.lastUciCommand,
                        lastUciProtocolOutput = svcState.lastUciProtocolOutput,
                        evalCentipawns = svcState.evalCentipawns,
                        threats = svcState.threats,
                        keyElements = svcState.keyElements,
                        openingExplorer = svcState.openingExplorer,
                        progressiveHint = updatedHint,
                        coachSuggestions = updatedCoach
                    )
                }
            }
        }
    }

    fun detachUciService() {
        serviceCollectionJob?.cancel()
        uciService = null
        _uiState.update { it.copy(isUciServiceBound = false) }
    }

    /**
     * Sends a standard UCI protocol command directly to the bound [UciEngineService].
     */
    fun sendUciCommandToService(command: String) {
        if (command.trim().startsWith("go", ignoreCase = true) && _uiState.value.isUnassistedRankedSession) {
            markSessionTaintedByEngine("Manual UCI 'go' command")
        }
        val svc = uciService
        if (svc != null) {
            svc.sendUciCommand(command)
        } else {
            _uiState.update {
                it.copy(
                    lastUciCommand = command,
                    lastUciProtocolOutput = "id name ${it.engineConfig.profile.displayName} · uciok · readyok"
                )
            }
        }
    }

    /**
     * Uses the bound [UciEngineService] suspend move evaluation function asynchronously on [viewModelScope]
     * to inspect a candidate move without blocking the UI thread.
     */
    fun inspectMoveEvaluation(move: ChessMove) {
        val state = _uiState.value
        if (state.isUnassistedRankedSession && !state.isTaintedByEngine) {
            markSessionTaintedByEngine("Candidate Move Evaluator")
        }
        viewModelScope.launch {
            val currentState = _uiState.value
            val evalResult = uciService?.evaluateMove(
                positionBefore = currentState.position,
                move = move,
                isOpeningBookPly = currentState.currentPlyIndex < 4,
                config = currentState.engineConfig
            ) ?: run {
                val lines = UciAnalysisEngine.analyzePositionMultiPv(currentState.position, currentState.engineConfig, 10)
                val best = lines.firstOrNull()
                val afterPos = currentState.position.applyMove(move)
                val afterEval = UciAnalysisEngine.evaluateStaticCentipawns(afterPos, currentState.engineConfig.profile)
                val beforeEval = best?.scoreCentipawns ?: 0
                val loss = kotlin.math.abs(beforeEval - afterEval)
                MoveEvaluationResult(
                    move = move,
                    evalBeforeCp = beforeEval,
                    evalAfterCp = afterEval,
                    cpLoss = loss,
                    bestAlternativeMove = best?.firstMove,
                    strength = MoveStrength.fromCpLoss(loss),
                    principalVariationSan = listOf(move.san),
                    formattedScore = String.format("%+.2f", afterEval / 100.0)
                )
            }
            _uiState.update { it.copy(inspectedMoveEvaluation = evalResult) }
        }
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab, statusBannerMessage = null) }
    }

    /**
     * Fix 5: Automatically sends `stop` when MainActivity hits `Lifecycle.Event.ON_STOP` (`inBackground == true`)
     * so the engine never burns CPU in the background.
     */
    fun onAppBackgroundStateChanged(inBackground: Boolean) {
        if (inBackground) {
            debouncedEvalJob?.cancel()
            fallbackEngineJob?.cancel()
            uciService?.stopAnalysis()
            _uiState.update { it.copy(isEngineRunning = false) }
        } else if (!_uiState.value.isEngineRunning && !_uiState.value.hideEngineTelemetryForFairPlay) {
            startLiveUciEngineAnalysis()
        }
    }

    /**
     * Fix 5: Debounces FEN evaluation requests by [debounceMs] (75ms default) during rapid ply scrubbing
     * (`|◀`, `◀`, `▶`, `▶|`) so `UciEngineService` is not flooded with overlapping `position` and `go` commands.
     */
    fun requestDebouncedUciEvaluation(debounceMs: Long = 75L) {
        debouncedEvalJob?.cancel()
        if (_uiState.value.hideEngineTelemetryForFairPlay) {
            uciService?.stopAnalysis()
            return
        }
        // Update immediate lightweight static board diagnostics while debouncing deep UCI search
        updateImmediateStaticBoardDiagnostics(_uiState.value.position, _uiState.value.engineConfig)
        debouncedEvalJob = viewModelScope.launch {
            delay(debounceMs)
            startLiveUciEngineAnalysis(fromUserManualTrigger = false)
        }
    }

    private fun updateImmediateStaticBoardDiagnostics(pos: ChessPosition, config: EngineConfig) {
        val initialLines = UciAnalysisEngine.analyzePositionMultiPv(pos, config, 6)
        val initialEval = initialLines.firstOrNull()?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(pos, config.profile)
        val threats = UciAnalysisEngine.analyzeThreats(pos)
        val keyEls = UciAnalysisEngine.analyzeKeyElements(pos)
        val opening = UciAnalysisEngine.getOpeningExplorer(pos, initialLines)
        val candidates = initialLines.map { line ->
            MoveEvaluationResult(
                move = line.firstMove,
                evalBeforeCp = initialEval,
                evalAfterCp = line.scoreCentipawns,
                cpLoss = line.cpDeltaFromBest,
                bestAlternativeMove = initialLines.firstOrNull()?.firstMove,
                strength = line.strengthCategory,
                principalVariationSan = line.continuationSan,
                formattedScore = line.formattedEvaluation()
            )
        }
        val state = _uiState.value
        val coachList = if (state.coachModeActive) {
            UciAnalysisEngine.generateCoachSuggestions(pos, initialLines, state.userPlayingStyle)
        } else {
            emptyList()
        }
        _engineTelemetryFlow.update {
            it.copy(
                isAnalyzing = true,
                currentDepth = 6,
                evalCentipawns = initialEval,
                multiPvLines = initialLines,
                candidateMoveEvaluations = candidates,
                threats = threats,
                keyElements = keyEls,
                openingExplorer = opening
            )
        }
        _uiState.update {
            it.copy(
                multiPvLines = initialLines,
                candidateMoveEvaluations = candidates,
                evalCentipawns = initialEval,
                threats = threats,
                keyElements = keyEls,
                openingExplorer = opening,
                coachSuggestions = coachList
            )
        }
    }

    fun resumeEngineFromHeader() {
        if (_uiState.value.isUnassistedRankedSession) {
            markSessionTaintedByEngine("Manual Engine Resume")
        }
        startLiveUciEngineAnalysis(fromUserManualTrigger = true)
    }

    fun startLiveUciEngineAnalysis(fromUserManualTrigger: Boolean = false) {
        if (fromUserManualTrigger && _uiState.value.isUnassistedRankedSession) {
            markSessionTaintedByEngine("Engine Analysis")
        }
        val state = _uiState.value
        if (state.hideEngineTelemetryForFairPlay) {
            uciService?.stopAnalysis()
            return
        }
        val pos = state.position
        val config = state.engineConfig

        val svc = uciService
        if (svc != null) {
            svc.startLiveAnalysis(pos, config)
            return
        }

        // Immediate local fallback while ServiceConnection is binding
        fallbackEngineJob?.cancel()
        val initialLines = UciAnalysisEngine.analyzePositionMultiPv(pos, config, 8)
        val initialEval = initialLines.firstOrNull()?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(pos, config.profile)
        val threats = UciAnalysisEngine.analyzeThreats(pos)
        val keyEls = UciAnalysisEngine.analyzeKeyElements(pos)
        val opening = UciAnalysisEngine.getOpeningExplorer(pos, initialLines)
        val candidates = initialLines.map { line ->
            MoveEvaluationResult(
                move = line.firstMove,
                evalBeforeCp = initialEval,
                evalAfterCp = line.scoreCentipawns,
                cpLoss = line.cpDeltaFromBest,
                bestAlternativeMove = initialLines.firstOrNull()?.firstMove,
                strength = line.strengthCategory,
                principalVariationSan = line.continuationSan,
                formattedScore = line.formattedEvaluation()
            )
        }

        val coachList = if (state.coachModeActive) {
            UciAnalysisEngine.generateCoachSuggestions(pos, initialLines, state.userPlayingStyle)
        } else {
            emptyList()
        }

        _engineTelemetryFlow.update {
            it.copy(
                isAnalyzing = true,
                pausedByPowerSaving = false,
                currentDepth = 8,
                evalCentipawns = initialEval,
                multiPvLines = initialLines,
                candidateMoveEvaluations = candidates,
                threats = threats,
                keyElements = keyEls,
                openingExplorer = opening
            )
        }

        _uiState.update {
            it.copy(
                isEngineRunning = true,
                enginePausedByPowerSave = false,
                currentSearchDepth = 8,
                multiPvLines = initialLines,
                candidateMoveEvaluations = candidates,
                evalCentipawns = initialEval,
                threats = threats,
                keyElements = keyEls,
                openingExplorer = opening,
                coachSuggestions = coachList
            )
        }
    }

    /**
     * Fix 3: Dedicated toggle for `isArrowDrawMode` in the Move Notation Strip control bar.
     * When `isArrowDrawMode` is true, dragging on the board bypasses `onPieceDragStarted`
     * and routes `onDragEnd` strictly to user arrow creation/deletion.
     */
    fun toggleArrowDrawMode() {
        _uiState.update { state ->
            val nextMode = !state.isArrowDrawMode
            state.copy(
                isArrowDrawMode = nextMode,
                drawArrowsOnBoard = nextMode,
                showDrawingPanel = nextMode,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                drawArrowStartSquare = null
            )
        }
    }

    /**
     * Fix 3: Routes drag-end gestures when `isArrowDrawMode` is active strictly to user arrow
     * creation/deletion (or square highlight toggle when `fromSquare == toSquare`).
     */
    fun onUserArrowDrawn(fromSquare: Int, toSquare: Int) {
        _uiState.update { state ->
            if (fromSquare == toSquare) {
                val exists = state.highlightedSquares.any { it.square == fromSquare }
                val updatedHighlights = if (exists) {
                    state.highlightedSquares.filterNot { it.square == fromSquare }
                } else {
                    state.highlightedSquares + HighlightedSquare(fromSquare, state.activeDrawingColor)
                }
                state.copy(highlightedSquares = updatedHighlights, drawArrowStartSquare = null)
            } else {
                val exists = state.drawnArrows.any { it.from == fromSquare && it.to == toSquare }
                val updatedArrows = if (exists) {
                    state.drawnArrows.filterNot { it.from == fromSquare && it.to == toSquare }
                } else {
                    state.drawnArrows + DrawnArrow(fromSquare, toSquare, state.activeDrawingColor)
                }
                state.copy(drawnArrows = updatedArrows, drawArrowStartSquare = null)
            }
        }
    }

    /**
     * Fix 4: Checks whether a legal move is a 1st/8th rank pawn promotion.
     */
    private fun isPawnPromotionMove(move: ChessMove): Boolean {
        val targetRankRow = move.to / 8
        return move.piece.type == PieceType.PAWN && (targetRankRow == 0 || targetRankRow == 7)
    }

    /**
     * Fix 4: Commits the pending pawn promotion move with the user-selected piece (`Q`, `N`, `R`, `B`).
     */
    fun selectPromotionPiece(pieceType: PieceType) {
        val pending = _uiState.value.pendingPromotionMove ?: return
        val matchingLegal = _uiState.value.position.generateLegalMoves().firstOrNull {
            it.from == pending.from && it.to == pending.to && it.promotion == pieceType
        } ?: pending.withPromotionPiece(pieceType)
        _uiState.update { it.copy(pendingPromotionMove = null) }
        executeMoveOnBoard(matchingLegal)
    }

    fun dismissPromotionOverlay() {
        _uiState.update {
            it.copy(
                pendingPromotionMove = null,
                selectedSquare = null,
                legalMovesForSelected = emptyList()
            )
        }
    }

    fun onSquareTapped(square: Int) {
        val state = _uiState.value

        if (state.pendingPromotionMove != null) {
            dismissPromotionOverlay()
            return
        }

        if (state.isArrowDrawMode || state.drawArrowsOnBoard || state.showDrawingPanel) {
            val startSq = state.drawArrowStartSquare
            if (startSq == null) {
                _uiState.update { it.copy(drawArrowStartSquare = square) }
            } else {
                onUserArrowDrawn(startSq, square)
            }
            return
        }

        val matchingLegalMoves = state.legalMovesForSelected.filter { it.to == square }
        if (matchingLegalMoves.isNotEmpty()) {
            val firstMove = matchingLegalMoves.first()
            if (isPawnPromotionMove(firstMove)) {
                _uiState.update {
                    it.copy(
                        pendingPromotionMove = firstMove,
                        selectedSquare = null,
                        legalMovesForSelected = emptyList()
                    )
                }
            } else {
                executeMoveOnBoard(firstMove)
            }
            return
        }

        val piece = state.position.pieceAt(square)
        if (piece != null && piece.side == state.position.turn) {
            val allLegal = state.position.generateLegalMoves()
            val movesForSq = allLegal.filter { it.from == square }
            _uiState.update {
                it.copy(
                    selectedSquare = square,
                    legalMovesForSelected = movesForSq
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList()
                )
            }
        }
    }

    fun onPieceDragStarted(square: Int) {
        val state = _uiState.value
        if (state.disableDragAndDrop || state.isArrowDrawMode || state.drawArrowsOnBoard) return
        val piece = state.position.pieceAt(square)
        if (piece != null && piece.side == state.position.turn) {
            val movesForSq = state.position.generateLegalMoves().filter { it.from == square }
            _uiState.update {
                it.copy(
                    selectedSquare = square,
                    legalMovesForSelected = movesForSq
                )
            }
        }
    }

    fun onPieceDragged(fromSquare: Int, toSquare: Int) {
        val state = _uiState.value
        if (state.isArrowDrawMode || state.drawArrowsOnBoard) {
            onUserArrowDrawn(fromSquare, toSquare)
            return
        }
        if (state.disableDragAndDrop) return

        val matchingMoves = state.position.generateLegalMoves().filter {
            it.from == fromSquare && it.to == toSquare
        }
        if (matchingMoves.isNotEmpty()) {
            val candidate = matchingMoves.first()
            if (isPawnPromotionMove(candidate)) {
                // Fix 4: Hold move in pendingPromotionMove before committing FEN update, notation append, or UCI evaluation
                _uiState.update {
                    it.copy(
                        pendingPromotionMove = candidate,
                        selectedSquare = null,
                        legalMovesForSelected = emptyList()
                    )
                }
            } else {
                executeMoveOnBoard(candidate)
            }
        }
    }

    fun executeMoveOnBoard(move: ChessMove) {
        val state = _uiState.value

        if (state.isRetryMistakesMode && state.mistakePuzzles.isNotEmpty()) {
            handlePuzzleMoveAttempt(move)
            return
        }

        val currentPos = state.position
        val nextPos = currentPos.applyMove(move)

        // Immediate non-blocking local classification so UI and tests update synchronously without main-thread ANR
        val linesBefore = UciAnalysisEngine.analyzePositionMultiPv(currentPos, state.engineConfig, 10)
        val bestBeforeMove = linesBefore.firstOrNull()?.firstMove
        val evalBefore = linesBefore.firstOrNull()?.scoreCentipawns ?: 0
        val linesAfter = UciAnalysisEngine.analyzePositionMultiPv(nextPos, state.engineConfig, 10)
        val evalAfter = linesAfter.firstOrNull()?.scoreCentipawns ?: 0
        val rawLoss = if (currentPos.turn == Side.WHITE) {
            (evalBefore - evalAfter).coerceAtLeast(0)
        } else {
            (evalAfter - evalBefore).coerceAtLeast(0)
        }
        val cpLoss = if (bestBeforeMove != null && bestBeforeMove.from == move.from && bestBeforeMove.to == move.to) 0 else rawLoss
        val initialStrength = MoveStrength.fromCpLoss(cpLoss, isBook = state.currentPlyIndex < 4 && cpLoss <= 20)
        val initialMoveEval = MoveEvaluationResult(
            move = move,
            evalBeforeCp = evalBefore,
            evalAfterCp = evalAfter,
            cpLoss = cpLoss,
            bestAlternativeMove = bestBeforeMove,
            strength = initialStrength,
            principalVariationSan = listOf(move.san),
            formattedScore = String.format("%+.2f", evalAfter / 100.0)
        )

        val isMidHistory = state.currentPlyIndex < state.moveHistory.size
        val nextMoveInHistory = state.moveHistory.getOrNull(state.currentPlyIndex)
        var updatedVariations = state.variations
        var updatedMoveHistory = state.moveHistory
        var updatedStrengths = state.moveStrengths
        var updatedFens = state.positionHistoryFens

        if (isMidHistory && nextMoveInHistory != null && nextMoveInHistory.uci != move.uci) {
            when (state.variationMoveBehavior) {
                VariationMoveBehavior.CREATE_VARIATION -> {
                    val baseText = "${(state.currentPlyIndex / 2) + 1}${if (currentPos.turn == Side.WHITE) "." else "..."} ${move.san}"
                    val labelWithSym = if (state.variationMoveSymbols && initialStrength.symbol.isNotEmpty()) {
                        "$baseText${initialStrength.symbol}"
                    } else {
                        baseText
                    }
                    val branch = VariationBranch(
                        id = "var_${System.currentTimeMillis()}",
                        parentPly = state.currentPlyIndex,
                        rawLabel = labelWithSym,
                        moveSan = move.san,
                        strength = initialStrength,
                        targetFen = nextPos.toFen()
                    )
                    updatedVariations = updatedVariations + branch
                }
                VariationMoveBehavior.PROMOTE_MAINLINE, VariationMoveBehavior.OVERWRITE_LINE -> {
                    updatedMoveHistory = state.moveHistory.take(state.currentPlyIndex) + move
                    updatedStrengths = state.moveStrengths.take(state.currentPlyIndex) + initialStrength
                    updatedFens = state.positionHistoryFens.take(state.currentPlyIndex + 1) + nextPos.toFen()
                }
            }
        } else if (!isMidHistory) {
            updatedMoveHistory = state.moveHistory + move
            updatedStrengths = state.moveStrengths + initialStrength
            updatedFens = state.positionHistoryFens + nextPos.toFen()
        }

        val pauseMessage = if ((state.isVsEnginePlayActive || state.showEachMoveStrengthWhilePlaying) &&
            state.pauseOnMistakeWhilePlaying &&
            (initialStrength == MoveStrength.MISTAKE || initialStrength == MoveStrength.BLUNDER)
        ) {
            "Paused on ${initialStrength.label} (${move.san}${initialStrength.symbol}) — Engine preferred ${bestBeforeMove?.san ?: "a stronger continuation"}."
        } else {
            null
        }

        playMoveFeedbackTone(move, initialStrength)

        var updatedClockWhite = state.clockHistoryWhiteMs
        var updatedClockBlack = state.clockHistoryBlackMs
        if (isMidHistory && nextMoveInHistory != null && nextMoveInHistory.uci != move.uci) {
            if (state.variationMoveBehavior != VariationMoveBehavior.CREATE_VARIATION) {
                updatedClockWhite = state.clockHistoryWhiteMs.take(state.currentPlyIndex + 1) + state.whiteTimeRemainingMs
                updatedClockBlack = state.clockHistoryBlackMs.take(state.currentPlyIndex + 1) + state.blackTimeRemainingMs
            }
        } else if (!isMidHistory) {
            updatedClockWhite = state.clockHistoryWhiteMs + state.whiteTimeRemainingMs
            updatedClockBlack = state.clockHistoryBlackMs + state.blackTimeRemainingMs
        }

        val targetNextPly = if (isMidHistory && nextMoveInHistory != null && nextMoveInHistory.uci != move.uci &&
            state.variationMoveBehavior == VariationMoveBehavior.CREATE_VARIATION
        ) {
            state.currentPlyIndex
        } else {
            state.currentPlyIndex + 1
        }

        _uiState.update {
            it.copy(
                position = nextPos,
                moveHistory = updatedMoveHistory,
                moveStrengths = updatedStrengths,
                positionHistoryFens = updatedFens,
                clockHistoryWhiteMs = updatedClockWhite,
                clockHistoryBlackMs = updatedClockBlack,
                currentPlyIndex = targetNextPly,
                variations = updatedVariations,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                pendingPromotionMove = null,
                lastMove = move,
                lastMoveStrength = initialStrength,
                previousBestMove = bestBeforeMove,
                inspectedMoveEvaluation = if (it.hideEngineTelemetryForFairPlay) null else initialMoveEval,
                pausedMistakeAlert = pauseMessage,
                progressiveHint = ProgressiveHintState()
            )
        }

        // Fix 1: Invoke suspend evaluateMove on UciEngineService asynchronously from viewModelScope
        val boundSvc = uciService
        if (boundSvc != null && !state.hideEngineTelemetryForFairPlay) {
            val isOpeningBook = state.currentPlyIndex < 4
            val cfg = state.engineConfig
            viewModelScope.launch {
                val asyncEval = boundSvc.evaluateMove(
                    positionBefore = currentPos,
                    move = move,
                    isOpeningBookPly = isOpeningBook,
                    config = cfg
                )
                _uiState.update { cur ->
                    if (cur.lastMove == move && !cur.hideEngineTelemetryForFairPlay) {
                        cur.copy(
                            lastMoveStrength = asyncEval.strength,
                            previousBestMove = asyncEval.bestAlternativeMove,
                            inspectedMoveEvaluation = asyncEval
                        )
                    } else cur
                }
            }
        }

        state.activeChallenge?.let { challenge ->
            viewModelScope.launch {
                val uciJoined = updatedMoveHistory.joinToString(" ") { it.uci }
                repository.pushChallengeMove(
                    challenge = challenge,
                    newFen = nextPos.toFen(),
                    movesUci = uciJoined,
                    lastMoveStrength = initialStrength.label
                )
            }
        }

        startLiveUciEngineAnalysis()

        if (state.isVsEnginePlayActive && pauseMessage == null) {
            viewModelScope.launch {
                delay(state.autoplaySpeedMs.coerceAtLeast(350L))
                val curPos = _uiState.value.position
                val topLines = UciAnalysisEngine.analyzePositionMultiPv(curPos, _uiState.value.engineConfig, 10)
                val reply = topLines.firstOrNull()?.firstMove
                if (reply != null && _uiState.value.isVsEnginePlayActive) {
                    executeEngineReply(reply)
                }
            }
        }
    }

    private fun executeEngineReply(move: ChessMove) {
        val state = _uiState.value
        val nextPos = state.position.applyMove(move)
        _uiState.update {
            it.copy(
                position = nextPos,
                moveHistory = it.moveHistory + move,
                moveStrengths = it.moveStrengths + MoveStrength.BEST,
                positionHistoryFens = it.positionHistoryFens + nextPos.toFen(),
                clockHistoryWhiteMs = it.clockHistoryWhiteMs + it.whiteTimeRemainingMs,
                clockHistoryBlackMs = it.clockHistoryBlackMs + it.blackTimeRemainingMs,
                currentPlyIndex = it.currentPlyIndex + 1,
                lastMove = move,
                lastMoveStrength = MoveStrength.BEST
            )
        }
        playMoveFeedbackTone(move, MoveStrength.BEST)
        startLiveUciEngineAnalysis()
    }

    private fun playMoveFeedbackTone(move: ChessMove, strength: MoveStrength) {
        val enabled = _uiState.value.soundEnabled
        soundManager.playForMove(move, enabled = enabled)
        if (enabled) {
            _uiState.update {
                it.copy(lastSoundEffectLabel = soundManager.lastPlayedEffect?.label)
            }
        }
    }

    /**
     * Undoes the last move (steps back 1 ply), triggers move/capture sound effect,
     * and synchronizes board state, move notation, and player countdown clocks.
     */
    fun undoMove() {
        val state = _uiState.value
        if (!state.canUndo) return
        val undoneMove = state.moveHistory.getOrNull(state.currentPlyIndex - 1)
        if (undoneMove != null) {
            soundManager.playForMove(undoneMove, enabled = state.soundEnabled)
            if (state.soundEnabled) {
                _uiState.update { it.copy(lastSoundEffectLabel = soundManager.lastPlayedEffect?.label) }
            }
        }
        jumpToPly(state.currentPlyIndex - 1)
    }

    /**
     * Redoes the previously undone move (steps forward 1 ply), triggers move/capture sound effect,
     * and synchronizes board state, move notation, and player countdown clocks.
     */
    fun redoMove() {
        val state = _uiState.value
        if (!state.canRedo) return
        val redoneMove = state.moveHistory.getOrNull(state.currentPlyIndex)
        if (redoneMove != null) {
            soundManager.playForMove(redoneMove, enabled = state.soundEnabled)
            if (state.soundEnabled) {
                _uiState.update { it.copy(lastSoundEffectLabel = soundManager.lastPlayedEffect?.label) }
            }
        }
        jumpToPly(state.currentPlyIndex + 1)
    }

    fun stepPly(delta: Int) {
        val state = _uiState.value
        val targetIndex = (state.currentPlyIndex + delta).coerceIn(0, state.moveHistory.size)
        jumpToPly(targetIndex)
    }

    fun jumpToPly(targetIndex: Int) {
        val state = _uiState.value
        val validIndex = targetIndex.coerceIn(0, state.moveHistory.size)
        val fen = state.positionHistoryFens.getOrElse(validIndex) { ChessPosition.START_FEN }
        val pos = ChessPosition.fromFen(fen)
        val lastMove = if (validIndex > 0) state.moveHistory.getOrNull(validIndex - 1) else null
        val lastStrength = if (validIndex > 0) state.moveStrengths.getOrNull(validIndex - 1) else null

        val prevFen = if (validIndex > 0) state.positionHistoryFens.getOrNull(validIndex - 1) else null
        val prevBest = prevFen?.let {
            val prevPos = ChessPosition.fromFen(it)
            UciAnalysisEngine.analyzePositionMultiPv(prevPos, state.engineConfig, 8).firstOrNull()?.firstMove
        }

        val restoredWhiteClock = state.clockHistoryWhiteMs.getOrElse(validIndex) { state.whiteTimeRemainingMs }
        val restoredBlackClock = state.clockHistoryBlackMs.getOrElse(validIndex) { state.blackTimeRemainingMs }
        val restoredTimedOut = when {
            restoredWhiteClock <= 0L -> Side.WHITE
            restoredBlackClock <= 0L -> Side.BLACK
            else -> null
        }

        _uiState.update {
            it.copy(
                position = pos,
                currentPlyIndex = validIndex,
                whiteTimeRemainingMs = restoredWhiteClock,
                blackTimeRemainingMs = restoredBlackClock,
                timedOutSide = restoredTimedOut,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                pendingPromotionMove = null,
                lastMove = lastMove,
                lastMoveStrength = lastStrength,
                previousBestMove = prevBest,
                progressiveHint = ProgressiveHintState(),
                pausedMistakeAlert = null
            )
        }
        // Fix 5: Debounce FEN evaluation requests by 75ms on ply scrubbing
        requestDebouncedUciEvaluation(75L)
    }

    fun toggleAutoplay() {
        val currentlyRunning = _uiState.value.isAutoplayRunning
        if (currentlyRunning) {
            autoplayJob?.cancel()
            _uiState.update { it.copy(isAutoplayRunning = false) }
            return
        }
        _uiState.update { it.copy(isAutoplayRunning = true) }
        autoplayJob = viewModelScope.launch {
            while (_uiState.value.isAutoplayRunning) {
                val st = _uiState.value
                if (st.currentPlyIndex < st.moveHistory.size) {
                    stepPly(1)
                } else {
                    val engineBest = st.multiPvLines.firstOrNull()?.firstMove
                    if (engineBest != null) {
                        executeMoveOnBoard(engineBest)
                    } else {
                        _uiState.update { it.copy(isAutoplayRunning = false) }
                        break
                    }
                }
                delay(_uiState.value.autoplaySpeedMs)
            }
        }
    }

    fun advanceProgressiveHint() {
        if (_uiState.value.isUnassistedRankedSession) {
            markSessionTaintedByEngine("Progressive Move Hint")
        }
        val state = _uiState.value
        val nextStep = if (state.progressiveHint.currentStep >= 3) 1 else state.progressiveHint.currentStep + 1
        val bestLine = state.multiPvLines.firstOrNull()
            ?: UciAnalysisEngine.analyzePositionMultiPv(state.position, state.engineConfig, 8).firstOrNull()
        val hint = UciAnalysisEngine.buildProgressiveHint(
            pos = state.position,
            bestLine = bestLine,
            nextStep = nextStep,
            coachModeActive = state.coachModeActive,
            playingStyle = state.userPlayingStyle
        )
        _uiState.update { it.copy(progressiveHint = hint) }
    }

    fun toggleCoachMode(active: Boolean) {
        if (active && _uiState.value.isUnassistedRankedSession) {
            markSessionTaintedByEngine("Coach Mode")
        }
        _uiState.update { state ->
            val lines = state.multiPvLines.ifEmpty {
                UciAnalysisEngine.analyzePositionMultiPv(state.position, state.engineConfig, 8)
            }
            val suggestions = if (active) {
                UciAnalysisEngine.generateCoachSuggestions(state.position, lines, state.userPlayingStyle)
            } else {
                emptyList()
            }
            val updatedHint = if (state.progressiveHint.currentStep > 0) {
                UciAnalysisEngine.buildProgressiveHint(
                    pos = state.position,
                    bestLine = lines.firstOrNull(),
                    nextStep = state.progressiveHint.currentStep,
                    coachModeActive = active,
                    playingStyle = state.userPlayingStyle
                )
            } else {
                state.progressiveHint
            }
            state.copy(
                coachModeActive = active,
                coachSuggestions = suggestions,
                progressiveHint = updatedHint
            )
        }
    }

    fun setUserPlayingStyle(style: PlayingStyleProfile) {
        _uiState.update { state ->
            val lines = state.multiPvLines.ifEmpty {
                UciAnalysisEngine.analyzePositionMultiPv(state.position, state.engineConfig, 8)
            }
            val suggestions = if (state.coachModeActive) {
                UciAnalysisEngine.generateCoachSuggestions(state.position, lines, style)
            } else {
                emptyList()
            }
            val updatedHint = if (state.progressiveHint.currentStep > 0) {
                UciAnalysisEngine.buildProgressiveHint(
                    pos = state.position,
                    bestLine = lines.firstOrNull(),
                    nextStep = state.progressiveHint.currentStep,
                    coachModeActive = state.coachModeActive,
                    playingStyle = style
                )
            } else {
                state.progressiveHint
            }
            val updatedLeaderboard = state.leaderboardEntries.map { entry ->
                if (entry.isCurrentUser) entry.copy(playingStyle = style) else entry
            }
            state.copy(
                userPlayingStyle = style,
                coachSuggestions = suggestions,
                progressiveHint = updatedHint,
                leaderboardEntries = updatedLeaderboard
            )
        }
    }

    fun setLeaderboardScope(scope: LeaderboardScope) {
        _uiState.update { it.copy(leaderboardScope = scope) }
    }

    fun setLeaderboardSortMetric(metric: LeaderboardSortMetric) {
        _uiState.update { it.copy(leaderboardSortMetric = metric) }
    }

    /**
     * Records a completed game result to the local Room database first with `pendingCloudSync = !isTaintedByEngine`.
     * If `isTaintedByEngine` is true, blocks Leaderboard submission. Otherwise updates local rankings and triggers
     * the offline-first Room-to-Firestore transaction sync worker.
     */
    fun recordUserGameVictory(accuracyEarned: Double = 93.4) {
        val snapshot = _uiState.value
        val isTainted = snapshot.isTaintedByEngine
        val ratingDelta = if (isTainted) 0 else 14

        viewModelScope.launch {
            try {
                repository.saveAnalyzedGame(
                    id = "ranked_${System.currentTimeMillis()}",
                    title = "Ranked Match vs ${snapshot.blackPlayerName}",
                    whitePlayer = snapshot.whitePlayerName,
                    blackPlayer = snapshot.blackPlayerName,
                    pgn = snapshot.moveHistory.joinToString(" ") { it.san },
                    finalFen = snapshot.position.toFen(),
                    whiteAccuracy = accuracyEarned.toInt(),
                    blackAccuracy = 84,
                    whiteAvgCpl = 16,
                    blackAvgCpl = 31,
                    mistakeCount = 1,
                    blunderCount = 0,
                    analyzed = true,
                    ratingDelta = ratingDelta,
                    isTaintedByEngine = isTainted,
                    pendingCloudSync = !isTainted
                )
                refreshPendingSyncCount()
                if (!isTainted) {
                    syncPendingCleanGamesToCloud()
                }
            } catch (_: Exception) {
            }
        }

        if (isTainted) {
            _uiState.update {
                it.copy(
                    statusBannerMessage = "Leaderboard Submission Blocked: Session was tainted by Engine/Hint telemetry. Saved to local archive only."
                )
            }
            return
        }

        _uiState.update { state ->
            val nextRating = state.userRating + ratingDelta
            val nextStreak = state.userWinStreak + 1
            val updatedEntries = state.leaderboardEntries.map { entry ->
                if (entry.isCurrentUser) {
                    val nextPlayed = entry.gamesPlayed + 1
                    val nextWon = entry.gamesWon + 1
                    val blendedAcc = ((entry.accuracyScore * entry.gamesPlayed) + accuracyEarned) / nextPlayed
                    entry.copy(
                        rating = entry.rating + ratingDelta,
                        gamesWon = nextWon,
                        gamesPlayed = nextPlayed,
                        winStreak = entry.winStreak + 1,
                        accuracyScore = kotlin.math.round(blendedAcc * 10.0) / 10.0
                    )
                } else {
                    entry
                }
            }
            state.copy(
                userRating = nextRating,
                userWinStreak = nextStreak,
                leaderboardEntries = updatedEntries,
                statusBannerMessage = "Recorded Clean Victory! Leaderboard updated (+1 Win, +$ratingDelta Rating, ${String.format("%.1f%%", accuracyEarned)} Accuracy)."
            )
        }
    }

    fun addFriendToLeaderboard(username: String, accuracyScore: Double = 89.6, gamesWon: Int = 19) {
        val cleanName = username.trim()
        if (cleanName.isEmpty()) return
        _uiState.update { state ->
            val exists = state.leaderboardEntries.any { it.username.equals(cleanName, ignoreCase = true) }
            val updated = if (exists) {
                state.leaderboardEntries.map {
                    if (it.username.equals(cleanName, ignoreCase = true)) it.copy(isFriend = true) else it
                }
            } else {
                state.leaderboardEntries + LeaderboardPlayerEntry(
                    id = "friend_${System.currentTimeMillis()}",
                    username = cleanName,
                    titleBadge = "FM",
                    rating = 2040,
                    accuracyScore = accuracyScore,
                    gamesWon = gamesWon,
                    gamesPlayed = (gamesWon * 1.4).toInt().coerceAtLeast(gamesWon),
                    winStreak = 2,
                    isFriend = true,
                    isCurrentUser = false,
                    playingStyle = PlayingStyleProfile.TACTICAL_AGGRESSIVE
                )
            }
            state.copy(
                leaderboardEntries = updated,
                leaderboardScope = LeaderboardScope.FRIENDS,
                statusBannerMessage = "Added $cleanName to Friends Leaderboard!"
            )
        }
    }

    fun playWhyBlunderRefutation(reviewItem: PlyReviewItem) {
        autoplayJob?.cancel()
        _uiState.update {
            it.copy(
                position = ChessPosition.fromFen(reviewItem.fenAfter),
                activeWhyBlunderLine = reviewItem.whyBlunderRefutationSan,
                isPlayingWhyBlunder = true,
                lastMove = reviewItem.playedMove,
                lastMoveStrength = reviewItem.strength,
                previousBestMove = reviewItem.bestMove
            )
        }
        autoplayJob = viewModelScope.launch {
            var simPos = ChessPosition.fromFen(reviewItem.fenAfter)
            for (refMove in reviewItem.whyBlunderRefutationMoves) {
                delay(_uiState.value.autoplaySpeedMs)
                simPos = simPos.applyMove(refMove)
                _uiState.update {
                    it.copy(
                        position = simPos,
                        lastMove = refMove,
                        lastMoveStrength = MoveStrength.BEST
                    )
                }
                startLiveUciEngineAnalysis()
            }
            _uiState.update { it.copy(isPlayingWhyBlunder = false) }
        }
    }

    fun loadPresetIntoBoard(preset: ArchiveStudyPreset, generateReportNow: Boolean = true) {
        var pos = ChessPosition.fromFen(ChessPosition.START_FEN)
        val moves = mutableListOf<ChessMove>()
        val fens = mutableListOf(ChessPosition.START_FEN)
        for (uci in preset.movesUci) {
            val from = com.example.engine.algebraicToSquare(uci.take(2)) ?: continue
            val to = com.example.engine.algebraicToSquare(uci.substring(2, 4)) ?: continue
            val legal = pos.generateLegalMoves().firstOrNull { it.from == from && it.to == to }
            if (legal != null) {
                moves.add(legal)
                pos = pos.applyMove(legal)
                fens.add(pos.toFen())
            }
        }

        val report = if (generateReportNow) {
            uciService?.generateGameReport(
                gameTitle = preset.title,
                whitePlayer = preset.whitePlayer,
                blackPlayer = preset.blackPlayer,
                moves = moves,
                config = _uiState.value.engineConfig
            ) ?: UciAnalysisEngine.generateDeepGameReport(
                gameTitle = preset.title,
                whitePlayer = preset.whitePlayer,
                blackPlayer = preset.blackPlayer,
                moves = moves,
                config = _uiState.value.engineConfig
            )
        } else {
            _uiState.value.deepReport
        }

        val strengths = report?.plyReviews?.map { it.strength } ?: moves.map { MoveStrength.GOOD }
        val shouldFlip = shouldAutoFlipForPlayers(preset.whitePlayer, preset.blackPlayer, _uiState.value.yourUsernames)
        val baseClock = _uiState.value.initialClockTimeMs
        val whiteClockSnaps = List(fens.size) { plyIdx ->
            val whiteMovesMade = (plyIdx + 1) / 2
            (baseClock - whiteMovesMade * 4_000L).coerceAtLeast(30_000L)
        }
        val blackClockSnaps = List(fens.size) { plyIdx ->
            val blackMovesMade = plyIdx / 2
            (baseClock - blackMovesMade * 4_000L).coerceAtLeast(30_000L)
        }

        _uiState.update {
            it.copy(
                position = pos,
                boardFlipped = shouldFlip,
                whitePlayerName = preset.whitePlayer,
                blackPlayerName = preset.blackPlayer,
                moveHistory = moves,
                moveStrengths = strengths,
                positionHistoryFens = fens,
                clockHistoryWhiteMs = whiteClockSnaps,
                clockHistoryBlackMs = blackClockSnaps,
                whiteTimeRemainingMs = whiteClockSnaps.lastOrNull() ?: baseClock,
                blackTimeRemainingMs = blackClockSnaps.lastOrNull() ?: baseClock,
                timedOutSide = null,
                currentPlyIndex = moves.size,
                pendingPromotionMove = null,
                lastMove = moves.lastOrNull(),
                lastMoveStrength = strengths.lastOrNull(),
                deepReport = report,
                isRetryMistakesMode = false
            )
        }
        startLiveUciEngineAnalysis()
    }

    private fun shouldAutoFlipForPlayers(white: String, black: String, usernamesCsv: String): Boolean {
        val handles = usernamesCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (handles.any { it == black.trim().lowercase() } && handles.none { it == white.trim().lowercase() }) {
            return true
        }
        return false
    }

    fun toggleArchiveGameSelection(id: String) {
        _uiState.update { state ->
            val next = if (id in state.selectedArchiveIds) state.selectedArchiveIds - id else state.selectedArchiveIds + id
            state.copy(selectedArchiveIds = next)
        }
    }

    fun analyzeMultipleGamesInBackground() {
        val state = _uiState.value
        val targets = state.archiveGames.filter { it.id in state.selectedArchiveIds }
        if (targets.isEmpty() || state.isBatchAnalyzingBackground) return

        _uiState.update {
            it.copy(
                isBatchAnalyzingBackground = true,
                batchAnalysisProgressText = "Starting background UCI Service batch analysis (${targets.size} games)..."
            )
        }

        viewModelScope.launch {
            targets.forEachIndexed { idx, preset ->
                _uiState.update {
                    it.copy(batchAnalysisProgressText = "UCI Service Analyzing ${idx + 1}/${targets.size}: ${preset.title}")
                }
                delay(400L)

                var pos = ChessPosition.fromFen(ChessPosition.START_FEN)
                val moves = mutableListOf<ChessMove>()
                for (uci in preset.movesUci) {
                    val from = com.example.engine.algebraicToSquare(uci.take(2)) ?: continue
                    val to = com.example.engine.algebraicToSquare(uci.substring(2, 4)) ?: continue
                    val legal = pos.generateLegalMoves().firstOrNull { it.from == from && it.to == to }
                    if (legal != null) {
                        moves.add(legal)
                        pos = pos.applyMove(legal)
                    }
                }
                val report = uciService?.generateGameReport(
                    gameTitle = preset.title,
                    whitePlayer = preset.whitePlayer,
                    blackPlayer = preset.blackPlayer,
                    moves = moves,
                    config = _uiState.value.engineConfig
                ) ?: UciAnalysisEngine.generateDeepGameReport(
                    gameTitle = preset.title,
                    whitePlayer = preset.whitePlayer,
                    blackPlayer = preset.blackPlayer,
                    moves = moves,
                    config = _uiState.value.engineConfig
                )
                val mistakes = report.plyReviews.count {
                    it.strength == MoveStrength.INACCURACY || it.strength == MoveStrength.MISTAKE
                }
                val blunders = report.plyReviews.count { it.strength == MoveStrength.BLUNDER }

                _uiState.update { cur ->
                    val updatedList = cur.archiveGames.map { item ->
                        if (item.id == preset.id) {
                            item.copy(
                                analyzed = true,
                                whiteAccuracy = report.whiteAccuracy,
                                blackAccuracy = report.blackAccuracy,
                                whiteAvgCpl = report.whiteAvgCpl,
                                blackAvgCpl = report.blackAvgCpl,
                                mistakeCount = mistakes.coerceAtLeast(1),
                                blunderCount = blunders
                            )
                        } else item
                    }
                    cur.copy(archiveGames = updatedList, deepReport = report)
                }

                repository.saveAnalyzedGame(
                    id = preset.id,
                    title = preset.title,
                    whitePlayer = preset.whitePlayer,
                    blackPlayer = preset.blackPlayer,
                    pgn = preset.movesSan.joinToString(" "),
                    finalFen = pos.toFen(),
                    whiteAccuracy = report.whiteAccuracy,
                    blackAccuracy = report.blackAccuracy,
                    whiteAvgCpl = report.whiteAvgCpl,
                    blackAvgCpl = report.blackAvgCpl,
                    mistakeCount = mistakes.coerceAtLeast(1),
                    blunderCount = blunders,
                    analyzed = true,
                    ratingDelta = 8,
                    isTaintedByEngine = _uiState.value.isTaintedByEngine,
                    pendingCloudSync = !_uiState.value.isTaintedByEngine
                )
            }

            refreshPendingSyncCount()
            syncPendingCleanGamesToCloud()

            _uiState.update {
                it.copy(
                    isBatchAnalyzingBackground = false,
                    batchAnalysisProgressText = "Completed background UCI Service analysis of ${targets.size} games."
                )
            }
        }
    }

    fun startRetryMistakesForMultipleGames() {
        val state = _uiState.value
        val selectedPresets = state.archiveGames.filter { it.id in state.selectedArchiveIds }
            .ifEmpty { state.archiveGames }

        val puzzles = mutableListOf<MistakePuzzleItem>()
        for (preset in selectedPresets) {
            var pos = ChessPosition.fromFen(ChessPosition.START_FEN)
            val moves = mutableListOf<ChessMove>()
            for (uci in preset.movesUci) {
                val from = com.example.engine.algebraicToSquare(uci.take(2)) ?: continue
                val to = com.example.engine.algebraicToSquare(uci.substring(2, 4)) ?: continue
                val legal = pos.generateLegalMoves().firstOrNull { it.from == from && it.to == to }
                if (legal != null) {
                    moves.add(legal)
                    pos = pos.applyMove(legal)
                }
            }
            val report = UciAnalysisEngine.generateDeepGameReport(
                gameTitle = preset.title,
                whitePlayer = preset.whitePlayer,
                blackPlayer = preset.blackPlayer,
                moves = moves,
                config = state.engineConfig
            )
            val mistakePlies = report.plyReviews.filter {
                it.strength == MoveStrength.BLUNDER ||
                    it.strength == MoveStrength.MISTAKE ||
                    it.strength == MoveStrength.INACCURACY
            }.ifEmpty { report.plyReviews.takeLast(2) }

            for (ply in mistakePlies) {
                puzzles.add(
                    MistakePuzzleItem(
                        id = "${preset.id}_ply_${ply.plyIndex}",
                        gameTitle = preset.title,
                        fenBefore = ply.fenBefore,
                        playedBadMoveSan = "${ply.playedMove.san}${ply.strength.symbol}",
                        bestMove = ply.bestMove,
                        explanation = ply.whyExplanation,
                        sideToMove = ply.side
                    )
                )
            }
        }

        if (puzzles.isEmpty()) return
        val first = puzzles.first()
        _uiState.update {
            it.copy(
                activeTab = StudioTab.PLAY,
                isRetryMistakesMode = true,
                mistakePuzzles = puzzles,
                currentPuzzleIndex = 0,
                position = ChessPosition.fromFen(first.fenBefore),
                boardFlipped = first.sideToMove == Side.BLACK,
                puzzleFeedback = "Find a stronger move than ${first.playedBadMoveSan} in ${first.gameTitle}",
                progressiveHint = ProgressiveHintState(),
                pendingPromotionMove = null
            )
        }
        startLiveUciEngineAnalysis()
    }

    private fun handlePuzzleMoveAttempt(move: ChessMove) {
        val state = _uiState.value
        val puzzle = state.mistakePuzzles.getOrNull(state.currentPuzzleIndex) ?: return
        val topMove = state.multiPvLines.firstOrNull()?.firstMove ?: puzzle.bestMove

        if ((move.from == puzzle.bestMove.from && move.to == puzzle.bestMove.to) ||
            (move.from == topMove.from && move.to == topMove.to)
        ) {
            val nextPos = state.position.applyMove(move)
            val updatedPuzzles = state.mistakePuzzles.mapIndexed { idx, item ->
                if (idx == state.currentPuzzleIndex) item.copy(solved = true) else item
            }
            _uiState.update {
                it.copy(
                    position = nextPos,
                    lastMove = move,
                    lastMoveStrength = MoveStrength.BEST,
                    mistakePuzzles = updatedPuzzles,
                    puzzleFeedback = "Correct! ${move.san}! solves the mistake (${puzzle.explanation})",
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    pendingPromotionMove = null
                )
            }
            playMoveFeedbackTone(move, MoveStrength.BEST)
        } else {
            _uiState.update {
                it.copy(
                    puzzleFeedback = "Not quite (${move.san}). Try again or tap Hint (Step 1→3) for guidance.",
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    pendingPromotionMove = null
                )
            }
        }
    }

    fun nextMistakePuzzle() {
        val state = _uiState.value
        if (state.mistakePuzzles.isEmpty()) return
        val nextIdx = (state.currentPuzzleIndex + 1) % state.mistakePuzzles.size
        val nextPuzzle = state.mistakePuzzles[nextIdx]
        _uiState.update {
            it.copy(
                currentPuzzleIndex = nextIdx,
                position = ChessPosition.fromFen(nextPuzzle.fenBefore),
                boardFlipped = nextPuzzle.sideToMove == Side.BLACK,
                puzzleFeedback = "Find a stronger move than ${nextPuzzle.playedBadMoveSan} in ${nextPuzzle.gameTitle}",
                progressiveHint = ProgressiveHintState(),
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                pendingPromotionMove = null
            )
        }
        startLiveUciEngineAnalysis()
    }

    fun updateEngineConfig(transform: (EngineConfig) -> EngineConfig) {
        _uiState.update { state ->
            val updated = transform(state.engineConfig)
            state.copy(engineConfig = updated)
        }
        val cfg = _uiState.value.engineConfig
        uciService?.sendUciCommand("setoption name Threads value ${cfg.threads}")
        uciService?.sendUciCommand("setoption name Hash value ${cfg.hashMb}")
        uciService?.sendUciCommand("setoption name MultiPV value ${cfg.multiPv}")
        startLiveUciEngineAnalysis()
    }

    fun updateVisualSettings(transform: (CheesseUiState) -> CheesseUiState) {
        val before = _uiState.value
        _uiState.update { state ->
            val updated = transform(state)
            val flip = shouldAutoFlipForPlayers(updated.whitePlayerName, updated.blackPlayerName, updated.yourUsernames)
            val syncedArrowMode = if (updated.drawArrowsOnBoard != state.drawArrowsOnBoard) {
                updated.drawArrowsOnBoard
            } else {
                updated.isArrowDrawMode
            }
            updated.copy(
                boardFlipped = if (updated.yourUsernames != state.yourUsernames) flip else updated.boardFlipped,
                isArrowDrawMode = syncedArrowMode
            )
        }
        val after = _uiState.value
        if (after.isUnassistedRankedSession && !after.isTaintedByEngine) {
            if ((!before.smartEvalBar && after.smartEvalBar) || (!before.showEngineArrows && after.showEngineArrows)) {
                markSessionTaintedByEngine("Visual Evaluation Overlay")
            }
        }
    }

    fun flipBoardManually() {
        _uiState.update { it.copy(boardFlipped = !it.boardFlipped) }
    }

    fun clearBoardDrawings() {
        _uiState.update {
            it.copy(
                drawnArrows = emptyList(),
                highlightedSquares = emptyList(),
                drawArrowStartSquare = null
            )
        }
    }

    /**
     * Fix 1: Board Editor Mutator updates the board and triggers sound immediately, then invokes
     * `uciService.evaluatePosition` asynchronously on `viewModelScope` without blocking the main thread.
     */
    fun onEditorSquareTapped(square: Int) {
        val state = _uiState.value
        val previousPiece = state.editorPosition.squares.getOrNull(square)
        val nextSquares = state.editorPosition.squares.toMutableList()
        nextSquares[square] = if (state.editorEraseMode) null else state.editorSelectedPiece
        if (state.editorEraseMode || previousPiece != null) {
            soundManager.playCapture(enabled = state.soundEnabled)
        } else {
            soundManager.playMove(enabled = state.soundEnabled)
        }
        val nextEditorPos = state.editorPosition.copy(squares = nextSquares)
        val staticEval = UciAnalysisEngine.evaluateStaticCentipawns(nextEditorPos, state.engineConfig.profile)
        _uiState.update {
            it.copy(
                editorPosition = nextEditorPos,
                editorEvalCp = staticEval,
                lastSoundEffectLabel = soundManager.lastPlayedEffect?.label
            )
        }

        val boundSvc = uciService
        if (boundSvc != null) {
            val cfg = state.engineConfig
            viewModelScope.launch {
                val asyncEval = boundSvc.evaluatePosition(nextEditorPos, cfg)
                _uiState.update { cur ->
                    if (cur.editorPosition == nextEditorPos) {
                        cur.copy(editorEvalCp = asyncEval)
                    } else cur
                }
            }
        }
    }

    fun setEditorSelectedPiece(piece: ChessPiece, eraseMode: Boolean = false) {
        _uiState.update { it.copy(editorSelectedPiece = piece, editorEraseMode = eraseMode) }
    }

    fun clearEditorBoard() {
        val emptySquares = List<ChessPiece?>(64) { sq ->
            when (sq) {
                4 -> ChessPiece(PieceType.KING, Side.BLACK)
                60 -> ChessPiece(PieceType.KING, Side.WHITE)
                else -> null
            }
        }
        val nextPos = ChessPosition(emptySquares, Side.WHITE, "", null, 0, 1)
        _uiState.update { it.copy(editorPosition = nextPos, editorEvalCp = 0) }
    }

    fun resetEditorToStart() {
        val startPos = ChessPosition.fromFen(ChessPosition.START_FEN)
        _uiState.update { it.copy(editorPosition = startPos, editorEvalCp = 20) }
    }

    fun applyEditorPositionToAnalysis() {
        val editorPos = _uiState.value.editorPosition
        val baseClock = _uiState.value.initialClockTimeMs
        _uiState.update {
            it.copy(
                activeTab = StudioTab.ANALYZE,
                position = editorPos,
                moveHistory = emptyList(),
                moveStrengths = emptyList(),
                positionHistoryFens = listOf(editorPos.toFen()),
                clockHistoryWhiteMs = listOf(baseClock),
                clockHistoryBlackMs = listOf(baseClock),
                whiteTimeRemainingMs = baseClock,
                blackTimeRemainingMs = baseClock,
                timedOutSide = null,
                currentPlyIndex = 0,
                pendingPromotionMove = null,
                lastMove = null
            )
        }
        startLiveUciEngineAnalysis()
    }

    fun toggleFenImportSheet(visible: Boolean? = null) {
        _uiState.update { state ->
            val targetVisible = visible ?: !state.showFenImportSheet
            if (targetVisible) {
                state.copy(
                    showFenImportSheet = true,
                    fenInputText = state.position.toFen(),
                    fenImportError = null
                )
            } else {
                state.copy(
                    showFenImportSheet = false,
                    fenImportError = null
                )
            }
        }
    }

    fun onFenInputTextChanged(newText: String) {
        val trimmed = newText.trim()
        val validationError = if (trimmed.isEmpty()) {
            null
        } else {
            ChessPosition.validateAndParseFen(trimmed).errorMessage
        }
        _uiState.update {
            it.copy(
                fenInputText = newText,
                fenImportError = validationError
            )
        }
    }

    fun importFenPosition(
        rawFen: String = _uiState.value.fenInputText,
        scenarioTitle: String? = null
    ): Boolean {
        val validation = ChessPosition.validateAndParseFen(rawFen)
        val parsed = validation.position
        if (!validation.isValid || parsed == null) {
            _uiState.update {
                it.copy(
                    fenInputText = rawFen,
                    fenImportError = validation.errorMessage ?: "Invalid FEN string."
                )
            }
            return false
        }

        val normalizedFen = parsed.toFen()
        val baseClock = _uiState.value.initialClockTimeMs
        val staticEval = UciAnalysisEngine.evaluateStaticCentipawns(parsed, _uiState.value.engineConfig.profile)
        val turnLabel = if (parsed.turn == Side.WHITE) "White to move" else "Black to move"
        val banner = if (!scenarioTitle.isNullOrBlank()) {
            "Loaded scenario: $scenarioTitle ($turnLabel)"
        } else {
            "Imported FEN position ($turnLabel)"
        }

        soundManager.playMove(enabled = _uiState.value.soundEnabled)
        _uiState.update {
            it.copy(
                activeTab = StudioTab.ANALYZE,
                position = parsed,
                editorPosition = parsed,
                editorEvalCp = staticEval,
                moveHistory = emptyList(),
                moveStrengths = emptyList(),
                positionHistoryFens = listOf(normalizedFen),
                clockHistoryWhiteMs = listOf(baseClock),
                clockHistoryBlackMs = listOf(baseClock),
                whiteTimeRemainingMs = baseClock,
                blackTimeRemainingMs = baseClock,
                timedOutSide = null,
                currentPlyIndex = 0,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                pendingPromotionMove = null,
                lastMove = null,
                lastMoveStrength = null,
                progressiveHint = ProgressiveHintState(),
                showFenImportSheet = false,
                fenInputText = normalizedFen,
                fenImportError = null,
                lastSoundEffectLabel = soundManager.lastPlayedEffect?.label,
                statusBannerMessage = banner
            )
        }
        startLiveUciEngineAnalysis()
        return true
    }

    fun selectFenScenarioPreset(preset: FenScenarioPreset): Boolean {
        return importFenPosition(rawFen = preset.fen, scenarioTitle = preset.label)
    }

    fun syncFromExternalFen(fen: String) {
        val validation = ChessPosition.validateAndParseFen(fen)
        val pos = validation.position ?: ChessPosition.fromFen(fen)
        val normalizedFen = pos.toFen()
        val baseClock = _uiState.value.initialClockTimeMs
        _uiState.update {
            it.copy(
                position = pos,
                editorPosition = pos,
                moveHistory = emptyList(),
                moveStrengths = emptyList(),
                positionHistoryFens = listOf(normalizedFen),
                clockHistoryWhiteMs = listOf(baseClock),
                clockHistoryBlackMs = listOf(baseClock),
                whiteTimeRemainingMs = baseClock,
                blackTimeRemainingMs = baseClock,
                timedOutSide = null,
                currentPlyIndex = 0,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                pendingPromotionMove = null,
                lastMove = null,
                fenInputText = normalizedFen,
                fenImportError = null,
                statusBannerMessage = "Synced board position."
            )
        }
        startLiveUciEngineAnalysis()
    }

    fun createLiveChallengeRoom() {
        val code = "CH" + (1000..9999).random()
        val hostHandle = _uiState.value.yourUsernames.substringBefore(",").trim().ifEmpty { "CheesseGM" }
        viewModelScope.launch {
            repository.createFriendChallenge(inviteCode = code, hostName = hostHandle)
            _uiState.update {
                it.copy(statusBannerMessage = "Created Challenge Room! Share Code: $code")
            }
        }
    }

    fun joinLiveChallengeRoom(challenge: FriendChallengeEntity) {
        val guestHandle = _uiState.value.yourUsernames.substringBefore(",").trim().ifEmpty { "Challenger" }
        viewModelScope.launch {
            repository.joinFriendChallenge(challenge, guestHandle)
            _uiState.update {
                it.copy(
                    activeChallenge = challenge,
                    activeTab = StudioTab.ANALYZE,
                    whitePlayerName = challenge.hostName,
                    blackPlayerName = guestHandle,
                    boardFlipped = true,
                    statusBannerMessage = "Joined match room ${challenge.inviteCode} vs ${challenge.hostName}!"
                )
            }
        }
    }
}
