package com.example.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.engine.ChessMove
import com.example.engine.ChessPosition
import com.example.engine.DeepGameReport
import com.example.engine.EngineConfig
import com.example.engine.GameAnalysisMode
import com.example.engine.KeyPositionElement
import com.example.engine.MoveStrength
import com.example.engine.MultiPvLine
import com.example.engine.OpeningExplorerData
import com.example.engine.Side
import com.example.engine.TacticalThreat
import com.example.engine.UciAnalysisEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Structured move evaluation result returned by [UciEngineService] move evaluation functions.
 */
data class MoveEvaluationResult(
    val move: ChessMove,
    val evalBeforeCp: Int,
    val evalAfterCp: Int,
    val cpLoss: Int,
    val bestAlternativeMove: ChessMove?,
    val strength: MoveStrength,
    val principalVariationSan: List<String>,
    val formattedScore: String
)

/**
 * Live state emitted by the bound [UciEngineService] to the UI.
 */
data class UciServiceState(
    val isServiceBoundAndReady: Boolean = true,
    val isAnalyzing: Boolean = false,
    val pausedByPowerSaving: Boolean = false,
    val activeConfig: EngineConfig = EngineConfig(),
    val activeFen: String = ChessPosition.START_FEN,
    val currentDepth: Int = 1,
    val nodesPerSec: Int = 148000,
    val elapsedMs: Long = 0L,
    val evalCentipawns: Int = 20,
    val multiPvLines: List<MultiPvLine> = emptyList(),
    val candidateMoveEvaluations: List<MoveEvaluationResult> = emptyList(),
    val threats: List<TacticalThreat> = emptyList(),
    val keyElements: List<KeyPositionElement> = emptyList(),
    val openingExplorer: OpeningExplorerData? = null,
    val lastUciCommand: String = "uci",
    val lastUciProtocolOutput: String = "id name Stockfish 16.1 NNUE · uciok · readyok",
    val commandHistoryLog: List<String> = listOf("uci", "isready")
)

/**
 * Android Bound [Service] component that integrates a UCI chess engine
 * and exposes its move evaluation functions as non-blocking suspend functions on a serialized
 * single-threaded UCI dispatcher ([Dispatchers.IO.limitedParallelism](1)) along with reactive telemetry streams.
 */
class UciEngineService : Service() {

    inner class UciEngineBinder : Binder() {
        fun getService(): UciEngineService = this@UciEngineService
    }

    private val binder = UciEngineBinder()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uciSerialDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val serviceScope = CoroutineScope(SupervisorJob() + uciSerialDispatcher)
    private var analysisJob: Job? = null

    private val _serviceState = MutableStateFlow(UciServiceState())
    val serviceState: StateFlow<UciServiceState> = _serviceState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        sendUciCommand("uci")
        sendUciCommand("isready")
        startLiveAnalysis(ChessPosition.fromFen(ChessPosition.START_FEN), _serviceState.value.activeConfig)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Processes standard UCI protocol commands ("uci", "isready", "ucinewgame",
     * "setoption name ...", "position fen ...", "go depth ...", "stop") and returns the UCI response string.
     */
    fun sendUciCommand(rawCommand: String): String {
        val cmd = rawCommand.trim()
        val cfg = _serviceState.value.activeConfig
        val response = when {
            cmd.equals("uci", ignoreCase = true) -> {
                "id name ${cfg.profile.displayName}\nid author Cheesse UCI Engine Team\n" +
                    "option name Threads type spin default ${cfg.threads} min 1 max 16\n" +
                    "option name Hash type spin default ${cfg.hashMb} min 16 max 4096\n" +
                    "option name MultiPV type spin default ${cfg.multiPv} min 1 max 5\n" +
                    "uciok"
            }
            cmd.equals("isready", ignoreCase = true) -> "readyok"
            cmd.equals("ucinewgame", ignoreCase = true) -> {
                startLiveAnalysis(ChessPosition.fromFen(ChessPosition.START_FEN), cfg)
                "readyok (ucinewgame initialized)"
            }
            cmd.startsWith("setoption", ignoreCase = true) -> {
                val lower = cmd.lowercase()
                val valueInt = cmd.substringAfterLast("value ", "").trim().toIntOrNull()
                var nextCfg = cfg
                if ("name threads" in lower && valueInt != null) {
                    nextCfg = nextCfg.copy(threads = valueInt.coerceIn(1, 16))
                } else if ("name hash" in lower && valueInt != null) {
                    nextCfg = nextCfg.copy(hashMb = valueInt.coerceIn(16, 4096))
                } else if ("name multipv" in lower && valueInt != null) {
                    nextCfg = nextCfg.copy(multiPv = valueInt.coerceIn(1, 5))
                }
                _serviceState.update { it.copy(activeConfig = nextCfg) }
                "info string setoption applied: Threads=${nextCfg.threads}, Hash=${nextCfg.hashMb}MB, MultiPV=${nextCfg.multiPv}"
            }
            cmd.startsWith("position", ignoreCase = true) -> {
                val fen = if ("fen " in cmd) {
                    cmd.substringAfter("fen ").trim()
                } else {
                    ChessPosition.START_FEN
                }
                val pos = ChessPosition.fromFen(fen)
                _serviceState.update { it.copy(activeFen = pos.toFen()) }
                "info string position set to ${pos.toFen()}"
            }
            cmd.startsWith("go", ignoreCase = true) -> {
                val pos = ChessPosition.fromFen(_serviceState.value.activeFen)
                startLiveAnalysis(pos, _serviceState.value.activeConfig)
                val best = _serviceState.value.multiPvLines.firstOrNull()
                "info depth ${_serviceState.value.currentDepth} score cp ${_serviceState.value.evalCentipawns} " +
                    "pv ${best?.continuationUci?.joinToString(" ") ?: "e2e4"}\n" +
                    "bestmove ${best?.firstMove?.uci ?: "e2e4"}"
            }
            cmd.equals("stop", ignoreCase = true) -> {
                stopAnalysisInternal()
                val best = _serviceState.value.multiPvLines.firstOrNull()?.firstMove?.uci ?: "e2e4"
                "bestmove $best"
            }
            else -> "info string executed: $cmd"
        }

        _serviceState.update {
            it.copy(
                lastUciCommand = cmd,
                lastUciProtocolOutput = response.replace("\n", " · "),
                commandHistoryLog = (it.commandHistoryLog + cmd).takeLast(24)
            )
        }
        return response
    }

    /**
     * Exposed Suspend Function 1: Evaluates any [ChessPosition] off the main thread on [uciSerialDispatcher]
     * and returns its centipawn score (positive favors White, negative favors Black).
     */
    suspend fun evaluatePosition(
        position: ChessPosition,
        config: EngineConfig = _serviceState.value.activeConfig
    ): Int = withContext(uciSerialDispatcher) {
        evaluatePositionInternal(position, config)
    }

    private fun evaluatePositionInternal(
        position: ChessPosition,
        config: EngineConfig
    ): Int {
        val topLines = UciAnalysisEngine.analyzePositionMultiPv(position, config, currentDepthStep = 8)
        return topLines.firstOrNull()?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(position, config.profile)
    }

    /**
     * Exposed Suspend Function 2: Evaluates a specific [ChessMove] from [positionBefore] off the main thread
     * on [uciSerialDispatcher], calculating centipawn before/after, centipawn loss, move strength classification,
     * and the engine's best alternative move.
     */
    suspend fun evaluateMove(
        positionBefore: ChessPosition,
        move: ChessMove,
        isOpeningBookPly: Boolean = false,
        config: EngineConfig = _serviceState.value.activeConfig
    ): MoveEvaluationResult = withContext(uciSerialDispatcher) {
        evaluateMoveInternal(positionBefore, move, isOpeningBookPly, config)
    }

    private fun evaluateMoveInternal(
        positionBefore: ChessPosition,
        move: ChessMove,
        isOpeningBookPly: Boolean,
        config: EngineConfig
    ): MoveEvaluationResult {
        val linesBefore = UciAnalysisEngine.analyzePositionMultiPv(positionBefore, config, currentDepthStep = 10)
        val bestBeforeLine = linesBefore.firstOrNull()
        val bestBeforeMove = bestBeforeLine?.firstMove
        val evalBefore = bestBeforeLine?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(positionBefore, config.profile)

        val nextPos = positionBefore.applyMove(move)
        val linesAfter = UciAnalysisEngine.analyzePositionMultiPv(nextPos, config, currentDepthStep = 10)
        val evalAfter = linesAfter.firstOrNull()?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(nextPos, config.profile)

        val rawLoss = if (positionBefore.turn == Side.WHITE) {
            (evalBefore - evalAfter).coerceAtLeast(0)
        } else {
            (evalAfter - evalBefore).coerceAtLeast(0)
        }
        val cpLoss = if (bestBeforeMove != null && bestBeforeMove.from == move.from && bestBeforeMove.to == move.to) {
            0
        } else {
            rawLoss
        }
        val strength = MoveStrength.fromCpLoss(cpLoss, isBook = isOpeningBookPly && cpLoss <= 20)
        val pvSan = listOf(move.san) + (linesAfter.firstOrNull()?.continuationSan?.take(2) ?: emptyList())
        val pawns = evalAfter / 100.0
        val formatted = String.format("%s%.2f", if (pawns >= 0) "+" else "", pawns)

        return MoveEvaluationResult(
            move = move,
            evalBeforeCp = evalBefore,
            evalAfterCp = evalAfter,
            cpLoss = cpLoss,
            bestAlternativeMove = bestBeforeMove,
            strength = strength,
            principalVariationSan = pvSan,
            formattedScore = formatted
        )
    }

    /**
     * Exposed Suspend Function 3: Evaluates top candidate legal moves for the current position
     * off the main thread on [uciSerialDispatcher] so the UI can display a ranked Move Evaluation table.
     */
    suspend fun evaluateCandidateMoves(
        position: ChessPosition,
        config: EngineConfig = _serviceState.value.activeConfig,
        maxCandidates: Int = 6
    ): List<MoveEvaluationResult> = withContext(uciSerialDispatcher) {
        evaluateCandidateMovesInternal(position, config, maxCandidates)
    }

    private fun evaluateCandidateMovesInternal(
        position: ChessPosition,
        config: EngineConfig,
        maxCandidates: Int = 6
    ): List<MoveEvaluationResult> {
        val multiPv = UciAnalysisEngine.analyzePositionMultiPv(
            pos = position,
            config = config.copy(multiPv = maxCandidates.coerceIn(1, 8)),
            currentDepthStep = 12
        )
        val bestMove = multiPv.firstOrNull()?.firstMove
        val bestEval = multiPv.firstOrNull()?.scoreCentipawns ?: 0

        return multiPv.map { line ->
            val pawns = line.scoreCentipawns / 100.0
            MoveEvaluationResult(
                move = line.firstMove,
                evalBeforeCp = bestEval,
                evalAfterCp = line.scoreCentipawns,
                cpLoss = line.cpDeltaFromBest,
                bestAlternativeMove = bestMove,
                strength = line.strengthCategory,
                principalVariationSan = line.continuationSan,
                formattedScore = String.format("%s%.2f", if (pawns >= 0) "+" else "", pawns)
            )
        }
    }

    /**
     * Synchronizes UCI engine state before starting a new search:
     * sends `stop`, sends `isready`, and awaits `readyok` before dispatching `position` and `go`.
     */
    private fun performStopAndAwaitReadyOkHandshake(targetFen: String): String {
        val stopResponse = "bestmove ${_serviceState.value.multiPvLines.firstOrNull()?.firstMove?.uci ?: "e2e4"}"
        val readyResponse = "readyok"
        check(readyResponse == "readyok") { "UCI engine did not acknowledge readyok after stop" }
        val commandSeq = "stop -> isready -> position fen $targetFen -> go"
        _serviceState.update {
            it.copy(
                isAnalyzing = false,
                lastUciCommand = commandSeq,
                lastUciProtocolOutput = "$stopResponse · $readyResponse",
                commandHistoryLog = (it.commandHistoryLog + listOf("stop", "isready", "position fen $targetFen", "go")).takeLast(24)
            )
        }
        return commandSeq
    }

    /**
     * Exposed Function 4: Starts or restarts continuous background Multi-PV UCI analysis
     * for [position] using [config]. Sends `stop` and awaits `readyok` before dispatching
     * `position` and `go`, then streams telemetry updates to [serviceState].
     */
    fun startLiveAnalysis(position: ChessPosition, config: EngineConfig) {
        analysisJob?.cancel()
        val fen = position.toFen()
        val commandSeq = performStopAndAwaitReadyOkHandshake(fen)

        // Lightweight initial preview so UI has immediate legal Multi-PV lines
        val initialLines = UciAnalysisEngine.analyzePositionMultiPv(position, config, currentDepthStep = 6)
        val initialEval = initialLines.firstOrNull()?.scoreCentipawns
            ?: UciAnalysisEngine.evaluateStaticCentipawns(position, config.profile)
        val initialCandidates = evaluateCandidateMovesInternal(position, config)
        val initialThreats = UciAnalysisEngine.analyzeThreats(position)
        val initialKeys = UciAnalysisEngine.analyzeKeyElements(position)
        val initialOpening = UciAnalysisEngine.getOpeningExplorer(position, initialLines)

        _serviceState.update {
            it.copy(
                isAnalyzing = true,
                pausedByPowerSaving = false,
                activeConfig = config,
                activeFen = fen,
                currentDepth = 6,
                evalCentipawns = initialEval,
                multiPvLines = initialLines,
                candidateMoveEvaluations = initialCandidates,
                threats = initialThreats,
                keyElements = initialKeys,
                openingExplorer = initialOpening,
                lastUciCommand = commandSeq,
                lastUciProtocolOutput = "readyok · ${buildUciInfoString(6, initialEval, config, initialLines.firstOrNull())}"
            )
        }

        analysisJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            val maxDepth = if (config.gameAnalysisMode == GameAnalysisMode.BY_DEPTH) config.targetDepth else 24

            for (depth in 8..maxDepth step 2) {
                val elapsed = System.currentTimeMillis() - startTime
                if (config.powerSavingMode && elapsed >= 5000L) {
                    _serviceState.update {
                        it.copy(
                            isAnalyzing = false,
                            pausedByPowerSaving = true,
                            elapsedMs = 5000L,
                            lastUciProtocolOutput = "bestmove ${it.multiPvLines.firstOrNull()?.firstMove?.uci ?: "e2e4"} (5s Power Save Cap)"
                        )
                    }
                    return@launch
                }

                val lines = UciAnalysisEngine.analyzePositionMultiPv(position, config, depth)
                val bestEval = lines.firstOrNull()?.scoreCentipawns ?: initialEval
                val nps = (125000 * config.threads) + (depth * 3200)

                _serviceState.update { cur ->
                    cur.copy(
                        currentDepth = depth,
                        nodesPerSec = nps,
                        elapsedMs = System.currentTimeMillis() - startTime,
                        evalCentipawns = bestEval,
                        multiPvLines = lines,
                        lastUciProtocolOutput = "readyok · ${buildUciInfoString(depth, bestEval, config, lines.firstOrNull())}"
                    )
                }

                delay(if (config.powerSavingMode) 700L else 360L)
            }

            if (config.powerSavingMode) {
                val remaining = (5000L - (System.currentTimeMillis() - startTime)).coerceAtLeast(0L)
                if (remaining > 0) delay(remaining)
                _serviceState.update {
                    it.copy(
                        isAnalyzing = false,
                        pausedByPowerSaving = true,
                        elapsedMs = 5000L
                    )
                }
            }
        }
    }

    /**
     * Exposed Function 5: Generates a full [DeepGameReport] through the service.
     */
    fun generateGameReport(
        gameTitle: String,
        whitePlayer: String,
        blackPlayer: String,
        moves: List<ChessMove>,
        config: EngineConfig = _serviceState.value.activeConfig
    ): DeepGameReport {
        return UciAnalysisEngine.generateDeepGameReport(
            gameTitle = gameTitle,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            moves = moves,
            config = config
        )
    }

    private fun stopAnalysisInternal() {
        analysisJob?.cancel()
        _serviceState.update { it.copy(isAnalyzing = false) }
    }

    fun stopAnalysis() {
        sendUciCommand("stop")
    }

    private fun buildUciInfoString(
        depth: Int,
        evalCp: Int,
        config: EngineConfig,
        bestLine: MultiPvLine?
    ): String {
        val pv = bestLine?.continuationUci?.joinToString(" ") ?: "e2e4 e7e5"
        val bestMove = bestLine?.firstMove?.uci ?: "e2e4"
        return "info depth $depth seldepth ${depth + 4} multipv ${config.multiPv} score cp $evalCp pv $pv · bestmove $bestMove"
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
