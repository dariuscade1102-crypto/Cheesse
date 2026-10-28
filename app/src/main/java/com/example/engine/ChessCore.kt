package com.example.engine

import kotlin.math.abs

enum class Side(val code: Char) {
    WHITE('w'),
    BLACK('b');

    fun opposite(): Side = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(
    val sanChar: Char,
    val whiteFigurine: String,
    val blackFigurine: String,
    val materialValue: Int
) {
    PAWN('P', "♙", "♟", 1),
    KNIGHT('N', "♘", "♞", 3),
    BISHOP('B', "♗", "♝", 3),
    ROOK('R', "♖", "♜", 5),
    QUEEN('Q', "♕", "♛", 9),
    KING('K', "♔", "♚", 0);

    fun figurine(side: Side): String = if (side == Side.WHITE) whiteFigurine else blackFigurine
}

data class ChessPiece(
    val type: PieceType,
    val side: Side
) {
    fun toFenChar(): Char {
        val c = type.sanChar
        return if (side == Side.WHITE) c.uppercaseChar() else c.lowercaseChar()
    }

    companion object {
        fun fromFenChar(ch: Char): ChessPiece? {
            val side = if (ch.isUpperCase()) Side.WHITE else Side.BLACK
            val type = when (ch.uppercaseChar()) {
                'P' -> PieceType.PAWN
                'N' -> PieceType.KNIGHT
                'B' -> PieceType.BISHOP
                'R' -> PieceType.ROOK
                'Q' -> PieceType.QUEEN
                'K' -> PieceType.KING
                else -> return null
            }
            return ChessPiece(type, side)
        }
    }
}

enum class MoveStrength(
    val label: String,
    val symbol: String,
    val cpLossThreshold: Int
) {
    BEST("Best", "!", 12),
    GOOD("Good", "", 35),
    BOOK("Book", "", 15),
    INACCURACY("Inaccuracy", "?!", 85),
    MISTAKE("Mistake", "?", 180),
    BLUNDER("Blunder", "??", 9999);

    companion object {
        fun fromCpLoss(cpLoss: Int, isBook: Boolean = false): MoveStrength {
            if (isBook && cpLoss <= 20) return BOOK
            return when {
                cpLoss <= 12 -> BEST
                cpLoss <= 38 -> GOOD
                cpLoss <= 90 -> INACCURACY
                cpLoss <= 185 -> MISTAKE
                else -> BLUNDER
            }
        }
    }
}

data class ChessMove(
    val from: Int, // 0..63 (row 0 = rank 8, col 0 = file a)
    val to: Int,
    val piece: ChessPiece,
    val captured: ChessPiece? = null,
    val promotion: PieceType? = null,
    val isCastling: Boolean = false,
    val san: String = "",
    val uci: String = "${squareToAlgebraic(from)}${squareToAlgebraic(to)}${promotion?.sanChar?.lowercaseChar() ?: ""}"
) {
    fun formatNotation(useFigurine: Boolean, showSymbols: Boolean, strength: MoveStrength? = null): String {
        val base = if (useFigurine && piece.type != PieceType.PAWN && san.isNotEmpty() && san[0] == piece.type.sanChar) {
            piece.type.figurine(piece.side) + san.drop(1)
        } else {
            san.ifEmpty { uci }
        }
        val sym = if (showSymbols && strength != null && strength.symbol.isNotEmpty()) strength.symbol else ""
        return base + sym
    }

    fun withPromotionPiece(promoType: PieceType): ChessMove {
        val destAlg = squareToAlgebraic(to)
        val fromFile = ('a' + (from % 8))
        val capStr = if (captured != null) "x" else ""
        val promoStr = "=${promoType.sanChar}"
        val updatedSan = if (captured != null) "$fromFile$capStr$destAlg$promoStr" else "$destAlg$promoStr"
        val updatedUci = "${squareToAlgebraic(from)}$destAlg${promoType.sanChar.lowercaseChar()}"
        return copy(
            promotion = promoType,
            san = updatedSan,
            uci = updatedUci
        )
    }
}

data class CapturedSummary(
    val capturedByWhite: List<PieceType>, // Black pieces captured by White
    val capturedByBlack: List<PieceType>, // White pieces captured by Black
    val whiteMaterialLead: Int,           // >0 means White is ahead
    val blackMaterialLead: Int            // >0 means Black is ahead
)

data class ChessPosition(
    val squares: List<ChessPiece?>, // size 64: index = rankFromTop * 8 + file
    val turn: Side = Side.WHITE,
    val castlingRights: String = "KQkq",
    val enPassantSquare: Int? = null,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1
) {
    fun pieceAt(sq: Int): ChessPiece? = if (sq in 0..63) squares[sq] else null

    fun toFen(): String {
        val sb = StringBuilder()
        for (row in 0..7) {
            var emptyCount = 0
            for (col in 0..7) {
                val p = squares[row * 8 + col]
                if (p == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(p.toFenChar())
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            if (row < 7) sb.append('/')
        }
        val ep = enPassantSquare?.let { squareToAlgebraic(it) } ?: "-"
        val castle = castlingRights.ifEmpty { "-" }
        return "$sb ${turn.code} $castle $ep $halfMoveClock $fullMoveNumber"
    }

    fun computeCapturedSummary(): CapturedSummary {
        val startingCounts = mapOf(
            PieceType.QUEEN to 1,
            PieceType.ROOK to 2,
            PieceType.BISHOP to 2,
            PieceType.KNIGHT to 2,
            PieceType.PAWN to 8
        )
        val whiteRemaining = mutableMapOf<PieceType, Int>()
        val blackRemaining = mutableMapOf<PieceType, Int>()
        var whiteScore = 0
        var blackScore = 0

        for (p in squares) {
            if (p != null && p.type != PieceType.KING) {
                if (p.side == Side.WHITE) {
                    whiteRemaining[p.type] = (whiteRemaining[p.type] ?: 0) + 1
                    whiteScore += p.type.materialValue
                } else {
                    blackRemaining[p.type] = (blackRemaining[p.type] ?: 0) + 1
                    blackScore += p.type.materialValue
                }
            }
        }

        val capturedByWhite = mutableListOf<PieceType>()
        val capturedByBlack = mutableListOf<PieceType>()
        for ((type, startCount) in startingCounts) {
            val missingBlack = (startCount - (blackRemaining[type] ?: 0)).coerceAtLeast(0)
            repeat(missingBlack) { capturedByWhite.add(type) }

            val missingWhite = (startCount - (whiteRemaining[type] ?: 0)).coerceAtLeast(0)
            repeat(missingWhite) { capturedByBlack.add(type) }
        }

        val diff = whiteScore - blackScore
        return CapturedSummary(
            capturedByWhite = capturedByWhite,
            capturedByBlack = capturedByBlack,
            whiteMaterialLead = if (diff > 0) diff else 0,
            blackMaterialLead = if (diff < 0) -diff else 0
        )
    }

    fun applyMove(move: ChessMove): ChessPosition {
        val nextSquares = squares.toMutableList()
        val movingPiece = nextSquares[move.from] ?: move.piece
        nextSquares[move.from] = null

        // En passant capture cleanup
        if (movingPiece.type == PieceType.PAWN && move.to == enPassantSquare && squares[move.to] == null) {
            val epCapturedSq = if (movingPiece.side == Side.WHITE) move.to + 8 else move.to - 8
            if (epCapturedSq in 0..63) nextSquares[epCapturedSq] = null
        }

        // Castling rook shift
        if (movingPiece.type == PieceType.KING && abs((move.from % 8) - (move.to % 8)) == 2) {
            val row = move.from / 8
            if (move.to % 8 == 6) {
                // Kingside
                nextSquares[row * 8 + 5] = nextSquares[row * 8 + 7]
                nextSquares[row * 8 + 7] = null
            } else if (move.to % 8 == 2) {
                // Queenside
                nextSquares[row * 8 + 3] = nextSquares[row * 8 + 0]
                nextSquares[row * 8 + 0] = null
            }
        }

        val placedPiece = if (move.promotion != null) {
            ChessPiece(move.promotion, movingPiece.side)
        } else {
            movingPiece
        }
        nextSquares[move.to] = placedPiece

        var updatedCastling = castlingRights
        if (movingPiece.type == PieceType.KING) {
            updatedCastling = if (movingPiece.side == Side.WHITE) {
                updatedCastling.replace("K", "").replace("Q", "")
            } else {
                updatedCastling.replace("k", "").replace("q", "")
            }
        }
        if (move.from == 63 || move.to == 63) updatedCastling = updatedCastling.replace("K", "")
        if (move.from == 56 || move.to == 56) updatedCastling = updatedCastling.replace("Q", "")
        if (move.from == 7 || move.to == 7) updatedCastling = updatedCastling.replace("k", "")
        if (move.from == 0 || move.to == 0) updatedCastling = updatedCastling.replace("q", "")

        val nextEp = if (movingPiece.type == PieceType.PAWN && abs((move.from / 8) - (move.to / 8)) == 2) {
            (move.from + move.to) / 2
        } else {
            null
        }

        val nextHalf = if (movingPiece.type == PieceType.PAWN || move.captured != null) 0 else halfMoveClock + 1
        val nextFull = if (turn == Side.BLACK) fullMoveNumber + 1 else fullMoveNumber

        return ChessPosition(
            squares = nextSquares,
            turn = turn.opposite(),
            castlingRights = updatedCastling,
            enPassantSquare = nextEp,
            halfMoveClock = nextHalf,
            fullMoveNumber = nextFull
        )
    }

    fun isKingInCheck(side: Side): Boolean {
        val kingSq = squares.indexOfFirst { it?.type == PieceType.KING && it.side == side }
        if (kingSq == -1) return false
        return isSquareAttackedBy(kingSq, side.opposite())
    }

    fun isSquareAttackedBy(targetSq: Int, attackerSide: Side): Boolean {
        val targetRow = targetSq / 8
        val targetCol = targetSq % 8

        for (sq in 0..63) {
            val p = squares[sq] ?: continue
            if (p.side != attackerSide) continue
            val r = sq / 8
            val c = sq % 8
            val dr = targetRow - r
            val dc = targetCol - c

            when (p.type) {
                PieceType.PAWN -> {
                    val forward = if (attackerSide == Side.WHITE) -1 else 1
                    if (dr == forward && abs(dc) == 1) return true
                }
                PieceType.KNIGHT -> {
                    if ((abs(dr) == 2 && abs(dc) == 1) || (abs(dr) == 1 && abs(dc) == 2)) return true
                }
                PieceType.KING -> {
                    if (abs(dr) <= 1 && abs(dc) <= 1 && (dr != 0 || dc != 0)) return true
                }
                PieceType.BISHOP -> {
                    if (abs(dr) == abs(dc) && dr != 0 && isPathClear(r, c, targetRow, targetCol)) return true
                }
                PieceType.ROOK -> {
                    if ((dr == 0 || dc == 0) && (dr != 0 || dc != 0) && isPathClear(r, c, targetRow, targetCol)) return true
                }
                PieceType.QUEEN -> {
                    if (((abs(dr) == abs(dc)) || dr == 0 || dc == 0) && (dr != 0 || dc != 0) &&
                        isPathClear(r, c, targetRow, targetCol)
                    ) return true
                }
            }
        }
        return false
    }

    private fun isPathClear(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val stepR = (r2 - r1).coerceIn(-1, 1)
        val stepC = (c2 - c1).coerceIn(-1, 1)
        var curR = r1 + stepR
        var curC = c1 + stepC
        while (curR != r2 || curC != c2) {
            if (squares[curR * 8 + curC] != null) return false
            curR += stepR
            curC += stepC
        }
        return true
    }

    fun generateLegalMoves(forSide: Side = turn): List<ChessMove> {
        val pseudo = mutableListOf<ChessMove>()
        for (sq in 0..63) {
            val p = squares[sq] ?: continue
            if (p.side != forSide) continue
            val r = sq / 8
            val c = sq % 8

            when (p.type) {
                PieceType.PAWN -> {
                    val dir = if (forSide == Side.WHITE) -1 else 1
                    val startRow = if (forSide == Side.WHITE) 6 else 1
                    val promoRow = if (forSide == Side.WHITE) 0 else 7
                    val promoChoices = listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP)
                    val nextR = r + dir
                    if (nextR in 0..7) {
                        val oneStep = nextR * 8 + c
                        if (squares[oneStep] == null) {
                            if (nextR == promoRow) {
                                for (promoType in promoChoices) {
                                    pseudo.add(buildMove(sq, oneStep, p, null, promoType))
                                }
                            } else {
                                pseudo.add(buildMove(sq, oneStep, p, null, null))
                            }
                            if (r == startRow) {
                                val twoStep = (r + dir * 2) * 8 + c
                                if (squares[twoStep] == null) {
                                    pseudo.add(buildMove(sq, twoStep, p, null, null))
                                }
                            }
                        }
                        for (dc in listOf(-1, 1)) {
                            val nc = c + dc
                            if (nc in 0..7) {
                                val targetSq = nextR * 8 + nc
                                val occ = squares[targetSq]
                                if (occ != null && occ.side != forSide) {
                                    if (nextR == promoRow) {
                                        for (promoType in promoChoices) {
                                            pseudo.add(buildMove(sq, targetSq, p, occ, promoType))
                                        }
                                    } else {
                                        pseudo.add(buildMove(sq, targetSq, p, occ, null))
                                    }
                                } else if (targetSq == enPassantSquare) {
                                    val epPiece = ChessPiece(PieceType.PAWN, forSide.opposite())
                                    pseudo.add(buildMove(sq, targetSq, p, epPiece, null))
                                }
                            }
                        }
                    }
                }
                PieceType.KNIGHT -> {
                    val jumps = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                    for ((dr, dc) in jumps) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val target = nr * 8 + nc
                            val occ = squares[target]
                            if (occ == null || occ.side != forSide) {
                                pseudo.add(buildMove(sq, target, p, occ))
                            }
                        }
                    }
                }
                PieceType.KING -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0..7 && nc in 0..7) {
                                val target = nr * 8 + nc
                                val occ = squares[target]
                                if (occ == null || occ.side != forSide) {
                                    pseudo.add(buildMove(sq, target, p, occ))
                                }
                            }
                        }
                    }
                    // Castling
                    val homeRow = if (forSide == Side.WHITE) 7 else 0
                    if (r == homeRow && c == 4 && !isKingInCheck(forSide)) {
                        val ksRight = if (forSide == Side.WHITE) 'K' else 'k'
                        val qsRight = if (forSide == Side.WHITE) 'Q' else 'q'
                        if (castlingRights.contains(ksRight) &&
                            squares[homeRow * 8 + 5] == null &&
                            squares[homeRow * 8 + 6] == null &&
                            !isSquareAttackedBy(homeRow * 8 + 5, forSide.opposite()) &&
                            !isSquareAttackedBy(homeRow * 8 + 6, forSide.opposite())
                        ) {
                            pseudo.add(
                                ChessMove(
                                    from = sq,
                                    to = homeRow * 8 + 6,
                                    piece = p,
                                    isCastling = true,
                                    san = "O-O"
                                )
                            )
                        }
                        if (castlingRights.contains(qsRight) &&
                            squares[homeRow * 8 + 3] == null &&
                            squares[homeRow * 8 + 2] == null &&
                            squares[homeRow * 8 + 1] == null &&
                            !isSquareAttackedBy(homeRow * 8 + 3, forSide.opposite()) &&
                            !isSquareAttackedBy(homeRow * 8 + 2, forSide.opposite())
                        ) {
                            pseudo.add(
                                ChessMove(
                                    from = sq,
                                    to = homeRow * 8 + 2,
                                    piece = p,
                                    isCastling = true,
                                    san = "O-O-O"
                                )
                            )
                        }
                    }
                }
                PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN -> {
                    val dirs = mutableListOf<Pair<Int, Int>>()
                    if (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN) {
                        dirs.addAll(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
                    }
                    if (p.type == PieceType.ROOK || p.type == PieceType.QUEEN) {
                        dirs.addAll(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
                    }
                    for ((dr, dc) in dirs) {
                        var nr = r + dr
                        var nc = c + dc
                        while (nr in 0..7 && nc in 0..7) {
                            val target = nr * 8 + nc
                            val occ = squares[target]
                            if (occ == null) {
                                pseudo.add(buildMove(sq, target, p, null))
                            } else {
                                if (occ.side != forSide) {
                                    pseudo.add(buildMove(sq, target, p, occ))
                                }
                                break
                            }
                            nr += dr
                            nc += dc
                        }
                    }
                }
            }
        }

        return pseudo.filter { candidate ->
            val nextPos = applyMove(candidate)
            !nextPos.isKingInCheck(forSide)
        }
    }

    private fun buildMove(
        from: Int,
        to: Int,
        piece: ChessPiece,
        captured: ChessPiece?,
        promotion: PieceType? = null
    ): ChessMove {
        val destAlg = squareToAlgebraic(to)
        val fromFile = ('a' + (from % 8))
        val capStr = if (captured != null) "x" else ""
        val promoStr = if (promotion != null) "=${promotion.sanChar}" else ""
        val san = if (piece.type == PieceType.PAWN) {
            if (captured != null) "$fromFile$capStr$destAlg$promoStr" else "$destAlg$promoStr"
        } else {
            "${piece.type.sanChar}$capStr$destAlg$promoStr"
        }
        return ChessMove(
            from = from,
            to = to,
            piece = piece,
            captured = captured,
            promotion = promotion,
            san = san
        )
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val QUICK_FEN_SCENARIOS: List<FenScenarioPreset> = listOf(
            FenScenarioPreset(
                id = "start_pos",
                label = "Starting Position",
                category = "Standard",
                fen = START_FEN,
                description = "Classical starting chess setup"
            ),
            FenScenarioPreset(
                id = "opera_game",
                label = "Morphy's Opera Sacrifice",
                category = "Tactics",
                fen = "4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4 w k - 1 16",
                description = "White to move: find Qb8+! Nxb8 Rd8#"
            ),
            FenScenarioPreset(
                id = "lucena_bridge",
                label = "Lucena Rook Endgame",
                category = "Endgame",
                fen = "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1",
                description = "White to move: build the classic 4th-rank bridge"
            ),
            FenScenarioPreset(
                id = "najdorf_sharp",
                label = "Sicilian Najdorf Clash",
                category = "Opening",
                fen = "r1b1kb1r/1pqn1ppp/p2ppn2/6B1/3NPP2/2N2Q2/PPP3PP/R3KB1R b KQkq - 2 9",
                description = "Black to move in the sharp 6.Bg5 Najdorf"
            ),
            FenScenarioPreset(
                id = "saavedra_study",
                label = "Saavedra Promotion Study",
                category = "Study",
                fen = "8/2P5/8/8/8/8/2K2r2/2k5 w - - 0 1",
                description = "White to move: famous underpromotion to Rook"
            ),
            FenScenarioPreset(
                id = "kp_opposition",
                label = "King & Pawn Opposition",
                category = "Endgame",
                fen = "8/8/4k3/8/4P3/4K3/8/8 w - - 0 1",
                description = "White to move: distant & direct opposition drill"
            )
        )

        fun validateAndParseFen(rawFen: String): FenValidationResult {
            val trimmed = rawFen.trim()
            if (trimmed.isEmpty()) {
                return FenValidationResult(
                    isValid = false,
                    errorMessage = "FEN string cannot be empty."
                )
            }

            val parts = trimmed.split(Regex("\\s+"))
            val boardPart = parts[0]
            val ranks = boardPart.split('/')
            if (ranks.size != 8) {
                return FenValidationResult(
                    isValid = false,
                    errorMessage = "Invalid FEN board layout: expected 8 ranks separated by '/', found ${ranks.size}."
                )
            }

            val squares = MutableList<ChessPiece?>(64) { null }
            var whiteKings = 0
            var blackKings = 0

            for ((rowIdx, rankStr) in ranks.withIndex()) {
                var col = 0
                for (ch in rankStr) {
                    if (ch.isDigit()) {
                        val step = ch.digitToInt()
                        if (step !in 1..8) {
                            return FenValidationResult(
                                isValid = false,
                                errorMessage = "Invalid empty square digit '$ch' on rank ${8 - rowIdx}."
                            )
                        }
                        col += step
                    } else {
                        val piece = ChessPiece.fromFenChar(ch)
                            ?: return FenValidationResult(
                                isValid = false,
                                errorMessage = "Invalid FEN piece character '$ch' on rank ${8 - rowIdx}."
                            )
                        if (col >= 8) {
                            return FenValidationResult(
                                isValid = false,
                                errorMessage = "Rank ${8 - rowIdx} exceeds 8 squares."
                            )
                        }
                        if (piece.type == PieceType.KING) {
                            if (piece.side == Side.WHITE) whiteKings++ else blackKings++
                        }
                        squares[rowIdx * 8 + col] = piece
                        col++
                    }
                }
                if (col != 8) {
                    return FenValidationResult(
                        isValid = false,
                        errorMessage = "Rank ${8 - rowIdx} has $col squares instead of 8."
                    )
                }
            }

            if (whiteKings != 1 || blackKings != 1) {
                return FenValidationResult(
                    isValid = false,
                    errorMessage = "Invalid king count: FEN must contain exactly 1 White King and 1 Black King (found $whiteKings White, $blackKings Black)."
                )
            }

            val turnToken = parts.getOrNull(1)?.lowercase() ?: "w"
            if (turnToken != "w" && turnToken != "b") {
                return FenValidationResult(
                    isValid = false,
                    errorMessage = "Invalid active color '${parts[1]}': expected 'w' or 'b'."
                )
            }
            val turn = if (turnToken == "b") Side.BLACK else Side.WHITE

            val castlingToken = parts.getOrNull(2) ?: "-"
            if (castlingToken != "-" && !castlingToken.all { it in "KQkq" }) {
                return FenValidationResult(
                    isValid = false,
                    errorMessage = "Invalid castling rights '$castlingToken': only 'K', 'Q', 'k', 'q', or '-' allowed."
                )
            }
            val castling = if (castlingToken == "-") "" else castlingToken

            val epToken = parts.getOrNull(3) ?: "-"
            val epSquare = if (epToken == "-") {
                null
            } else {
                algebraicToSquare(epToken)
                    ?: return FenValidationResult(
                        isValid = false,
                        errorMessage = "Invalid en-passant target square '$epToken'."
                    )
            }

            val halfMove = parts.getOrNull(4)?.let {
                it.toIntOrNull()?.takeIf { n -> n >= 0 }
                    ?: return FenValidationResult(
                        isValid = false,
                        errorMessage = "Invalid halfmove clock '$it': must be a non-negative integer."
                    )
            } ?: 0

            val fullMove = parts.getOrNull(5)?.let {
                it.toIntOrNull()?.takeIf { n -> n >= 1 }
                    ?: return FenValidationResult(
                        isValid = false,
                        errorMessage = "Invalid fullmove number '$it': must be an integer >= 1."
                    )
            } ?: 1

            val position = ChessPosition(
                squares = squares,
                turn = turn,
                castlingRights = castling,
                enPassantSquare = epSquare,
                halfMoveClock = halfMove,
                fullMoveNumber = fullMove
            )
            return FenValidationResult(
                isValid = true,
                position = position,
                errorMessage = null,
                normalizedFen = position.toFen()
            )
        }

        fun fromFen(fen: String): ChessPosition {
            val validated = validateAndParseFen(fen)
            if (validated.isValid && validated.position != null) {
                return validated.position
            }
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty()) return validateAndParseFen(START_FEN).position!!
            val boardPart = parts[0]
            val squares = MutableList<ChessPiece?>(64) { null }
            var row = 0
            var col = 0
            for (ch in boardPart) {
                when {
                    ch == '/' -> {
                        row++
                        col = 0
                    }
                    ch.isDigit() -> {
                        col += ch.digitToInt()
                    }
                    else -> {
                        if (row in 0..7 && col in 0..7) {
                            squares[row * 8 + col] = ChessPiece.fromFenChar(ch)
                        }
                        col++
                    }
                }
            }
            val turn = if (parts.getOrNull(1) == "b") Side.BLACK else Side.WHITE
            val castling = parts.getOrNull(2)?.takeIf { it != "-" } ?: ""
            val ep = parts.getOrNull(3)?.takeIf { it != "-" }?.let { algebraicToSquare(it) }
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            return ChessPosition(squares, turn, castling, ep, half, full)
        }
    }
}

data class FenValidationResult(
    val isValid: Boolean,
    val position: ChessPosition? = null,
    val errorMessage: String? = null,
    val normalizedFen: String = ""
)

data class FenScenarioPreset(
    val id: String,
    val label: String,
    val category: String,
    val fen: String,
    val description: String
)

fun squareToAlgebraic(sq: Int): String {
    if (sq !in 0..63) return "a1"
    val file = 'a' + (sq % 8)
    val rank = 8 - (sq / 8)
    return "$file$rank"
}

fun algebraicToSquare(alg: String): Int? {
    if (alg.length < 2) return null
    val file = alg[0].lowercaseChar() - 'a'
    val rank = alg[1].digitToIntOrNull() ?: return null
    if (file !in 0..7 || rank !in 1..8) return null
    return (8 - rank) * 8 + file
}
