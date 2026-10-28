package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analyzed_games")
data class AnalyzedGameEntity(
    @PrimaryKey val id: String,
    val title: String = "Standard Game",
    val whitePlayer: String = "White",
    val blackPlayer: String = "Black",
    val pgn: String = "",
    val finalFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val whiteAccuracy: Int = 90,
    val blackAccuracy: Int = 86,
    val whiteAvgCpl: Int = 22,
    val blackAvgCpl: Int = 28,
    val mistakeCount: Int = 1,
    val blunderCount: Int = 0,
    val analyzed: Boolean = true,
    val ratingDelta: Int = 12,
    val isTaintedByEngine: Boolean = false,
    val pendingCloudSync: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "friend_challenges")
data class FriendChallengeEntity(
    @PrimaryKey val id: String,
    val inviteCode: String = "",
    val hostName: String = "Host",
    val guestName: String = "",
    val currentFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val movesUci: String = "",
    val lastMoveStrength: String = "Book",
    val status: String = "open",
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface CheesseDao {
    @Query("SELECT * FROM analyzed_games ORDER BY updatedAtMs DESC")
    fun observeAnalyzedGames(): Flow<List<AnalyzedGameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalyzedGame(game: AnalyzedGameEntity)

    @Query("SELECT * FROM analyzed_games WHERE pendingCloudSync = 1 AND isTaintedByEngine = 0 ORDER BY updatedAtMs ASC")
    suspend fun getPendingCleanGamesForSync(): List<AnalyzedGameEntity>

    @Query("UPDATE analyzed_games SET pendingCloudSync = 0 WHERE id = :id")
    suspend fun markGameCloudSynced(id: String)

    @Query("SELECT * FROM friend_challenges ORDER BY updatedAtMs DESC")
    fun observeFriendChallenges(): Flow<List<FriendChallengeEntity>>

    @Query("SELECT * FROM friend_challenges WHERE id = :id LIMIT 1")
    fun observeChallengeById(id: String): Flow<FriendChallengeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFriendChallenge(challenge: FriendChallengeEntity)
}

@Database(
    entities = [AnalyzedGameEntity::class, FriendChallengeEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CheesseLocalDatabase : RoomDatabase() {
    abstract fun cheesseDao(): CheesseDao

    companion object {
        @Volatile
        private var INSTANCE: CheesseLocalDatabase? = null

        fun getInstance(context: Context): CheesseLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CheesseLocalDatabase::class.java,
                    "cheesse_local.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

class CheesseLocalRepository(private val dao: CheesseDao) {

    val analyzedGames: Flow<List<AnalyzedGameEntity>> = dao.observeAnalyzedGames()
    val friendChallenges: Flow<List<FriendChallengeEntity>> = dao.observeFriendChallenges()

    fun observeChallengeById(id: String): Flow<FriendChallengeEntity?> = dao.observeChallengeById(id)

    suspend fun saveAnalyzedGame(
        id: String = "game_${System.currentTimeMillis()}",
        title: String,
        whitePlayer: String,
        blackPlayer: String,
        pgn: String,
        finalFen: String,
        whiteAccuracy: Int,
        blackAccuracy: Int,
        whiteAvgCpl: Int,
        blackAvgCpl: Int,
        mistakeCount: Int,
        blunderCount: Int,
        analyzed: Boolean,
        ratingDelta: Int = 12,
        isTaintedByEngine: Boolean = false,
        pendingCloudSync: Boolean = !isTaintedByEngine
    ): String {
        val entity = AnalyzedGameEntity(
            id = id,
            title = title,
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            pgn = pgn,
            finalFen = finalFen,
            whiteAccuracy = whiteAccuracy,
            blackAccuracy = blackAccuracy,
            whiteAvgCpl = whiteAvgCpl,
            blackAvgCpl = blackAvgCpl,
            mistakeCount = mistakeCount,
            blunderCount = blunderCount,
            analyzed = analyzed,
            ratingDelta = ratingDelta,
            isTaintedByEngine = isTaintedByEngine,
            pendingCloudSync = pendingCloudSync,
            updatedAtMs = System.currentTimeMillis()
        )
        dao.upsertAnalyzedGame(entity)
        return id
    }

    suspend fun getPendingCleanGamesForSync(): List<AnalyzedGameEntity> =
        dao.getPendingCleanGamesForSync()

    suspend fun markGameCloudSynced(id: String) {
        dao.markGameCloudSynced(id)
    }

    suspend fun createFriendChallenge(
        inviteCode: String,
        hostName: String,
        initialFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    ): String {
        val id = "challenge_${System.currentTimeMillis()}"
        dao.upsertFriendChallenge(
            FriendChallengeEntity(
                id = id,
                inviteCode = inviteCode,
                hostName = hostName,
                guestName = "",
                currentFen = initialFen,
                movesUci = "",
                lastMoveStrength = "Book",
                status = "open",
                updatedAtMs = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun joinFriendChallenge(challenge: FriendChallengeEntity, guestName: String) {
        dao.upsertFriendChallenge(
            challenge.copy(
                guestName = guestName,
                status = "active",
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun pushChallengeMove(
        challenge: FriendChallengeEntity,
        newFen: String,
        movesUci: String,
        lastMoveStrength: String,
        status: String = "active"
    ) {
        dao.upsertFriendChallenge(
            challenge.copy(
                currentFen = newFen,
                movesUci = movesUci,
                lastMoveStrength = lastMoveStrength,
                status = status,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }
}
