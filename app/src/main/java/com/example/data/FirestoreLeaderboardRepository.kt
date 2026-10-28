package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.round

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
) {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerDataArray = JSONArray()
    currentUser?.providerData?.forEach { profile ->
        val providerObj = JSONObject().apply {
            put("providerId", profile.providerId)
            put("displayName", profile.displayName)
            put("email", profile.email)
            put("photoUrl", profile.photoUrl?.toString())
        }
        providerDataArray.put(providerObj)
    }

    val authInfo = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("isAnonymous", currentUser?.isAnonymous)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", providerDataArray)
    }

    val errorObj = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put("authInfo", authInfo)
    }

    val jsonString = errorObj.toString()
    Log.e("FirestoreError", jsonString, exception)
    throw RuntimeException(jsonString, exception)
}

@IgnoreExtraProperties
data class CloudLeaderboardDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "CheessePlayer",
    val titleBadge: String = "CM",
    val rating: Int = 1850,
    val accuracyScore: Double = 91.6,
    val gamesWon: Int = 38,
    val gamesPlayed: Int = 49,
    val winStreak: Int = 3,
    val playingStyle: String = "TACTICAL_AGGRESSIVE",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class LocalPlayerStatsSnapshot(
    val username: String = "CheessePlayer",
    val titleBadge: String = "CM",
    val rating: Int = 1850,
    val accuracyScore: Double = 91.6,
    val gamesWon: Int = 38,
    val gamesPlayed: Int = 49,
    val winStreak: Int = 3,
    val playingStyle: String = "TACTICAL_AGGRESSIVE"
)

class FirestoreLeaderboardRepository(
    private val db: FirebaseFirestore? = null,
    private val authProvider: () -> FirebaseAuth? = {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }
) {
    constructor(context: Context) : this(
        db = try {
            val appContext = context.applicationContext
            val databaseId = appContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(databaseId)
        } catch (_: Exception) {
            null
        }
    )

    val currentUserId: String?
        get() = authProvider()?.currentUser?.uid

    val currentUserDisplayName: String?
        get() = authProvider()?.currentUser?.displayName

    /**
     * Real-time listener for public leaderboard entries.
     * Filter matches security rules: `.whereEqualTo("visibility", "public")`.
     */
    fun observePublicLeaderboard(): Flow<List<CloudLeaderboardDoc>> = callbackFlow {
        val firestore = db
        val auth = authProvider()
        if (firestore == null || auth?.currentUser == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection("leaderboard")
            .whereEqualTo("visibility", "public")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    try {
                        handleFirestoreError(error, OperationType.LIST, "leaderboard")
                    } catch (_: Exception) {
                    }
                    return@addSnapshotListener
                }
                val docs = snapshot?.toObjects(CloudLeaderboardDoc::class.java).orEmpty()
                trySend(docs)
            }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Atomically creates or updates `leaderboard/{uid}` using `runTransaction`, merging local Room
     * profile stats (`max(localScore, cloudScore)`) and applying any un-synced clean games.
     */
    suspend fun syncCleanGamesAndMergeProfile(
        uid: String,
        localStats: LocalPlayerStatsSnapshot,
        cleanGamesToSync: List<AnalyzedGameEntity> = emptyList()
    ): CloudLeaderboardDoc? {
        val firestore = db ?: return null
        val docRef = firestore.collection("leaderboard").document(uid)
        val path = "leaderboard/$uid"
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val existing = if (snapshot.exists()) {
                    snapshot.toObject(CloudLeaderboardDoc::class.java)
                } else {
                    null
                }

                val cleanCount = cleanGamesToSync.count { !it.isTaintedByEngine }
                val deltaRating = cleanGamesToSync.filter { !it.isTaintedByEngine }.sumOf { it.ratingDelta }
                val avgCleanAcc = if (cleanCount > 0) {
                    cleanGamesToSync.filter { !it.isTaintedByEngine }
                        .map { it.whiteAccuracy.toDouble() }
                        .average()
                } else {
                    localStats.accuracyScore
                }

                val mergedRating = max(
                    localStats.rating + deltaRating,
                    (existing?.rating ?: localStats.rating) + deltaRating
                ).coerceIn(100, 3500)

                val mergedGamesWon = max(
                    localStats.gamesWon + cleanCount,
                    (existing?.gamesWon ?: localStats.gamesWon) + cleanCount
                )
                val mergedGamesPlayed = max(
                    localStats.gamesPlayed + cleanCount,
                    (existing?.gamesPlayed ?: localStats.gamesPlayed) + cleanCount
                ).coerceAtLeast(mergedGamesWon)

                val mergedWinStreak = max(
                    localStats.winStreak + cleanCount,
                    (existing?.winStreak ?: localStats.winStreak) + cleanCount
                )

                val rawAccuracy = max(
                    max(localStats.accuracyScore, existing?.accuracyScore ?: 0.0),
                    avgCleanAcc
                ).coerceIn(0.0, 100.0)
                val mergedAccuracy = round(rawAccuracy * 10.0) / 10.0

                val updatedDoc = CloudLeaderboardDoc(
                    id = uid,
                    userId = uid,
                    username = localStats.username.take(40).ifEmpty { existing?.username ?: "CheessePlayer" },
                    titleBadge = localStats.titleBadge.take(10).ifEmpty { existing?.titleBadge ?: "CM" },
                    rating = mergedRating,
                    accuracyScore = mergedAccuracy,
                    gamesWon = mergedGamesWon,
                    gamesPlayed = mergedGamesPlayed,
                    winStreak = mergedWinStreak,
                    playingStyle = localStats.playingStyle.take(40),
                    visibility = "public",
                    createdAt = existing?.createdAt ?: Timestamp.now(),
                    updatedAt = Timestamp.now()
                )

                transaction.set(docRef, updatedDoc)
                updatedDoc
            }.await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            null
        }
    }

    /**
     * Links an existing Firebase user session or signs in with Google Credential (`idToken`),
     * handling `FirebaseAuthUserCollisionException` by signing into the existing Google account
     * and merging local Room stats with the cloud Firestore profile (`max(localScore, cloudScore)`).
     */
    suspend fun linkOrSignInWithGoogleIdToken(
        idToken: String,
        localStats: LocalPlayerStatsSnapshot,
        cleanGames: List<AnalyzedGameEntity>
    ): CloudLeaderboardDoc? {
        val auth = authProvider() ?: return null
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val existingUser = auth.currentUser

        val authenticatedUser = try {
            if (existingUser != null) {
                existingUser.linkWithCredential(credential).await().user
            } else {
                auth.signInWithCredential(credential).await().user
            }
        } catch (collision: FirebaseAuthUserCollisionException) {
            // Account already linked to an existing UID: sign in and merge local Room stats with cloud profile
            auth.signInWithCredential(credential).await().user
        }

        val uid = authenticatedUser?.uid ?: return null
        val resolvedName = authenticatedUser.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: localStats.username

        return syncCleanGamesAndMergeProfile(
            uid = uid,
            localStats = localStats.copy(username = resolvedName),
            cleanGamesToSync = cleanGames
        )
    }

    /**
     * Direct helper for emulator rules tests and explicit leaderboard document writes.
     */
    suspend fun writeLeaderboardEntry(entry: CloudLeaderboardDoc) {
        val firestore = db ?: return
        val docId = entry.id.ifEmpty { entry.userId }
        val path = "leaderboard/$docId"
        try {
            val payload = hashMapOf(
                "userId" to entry.userId,
                "username" to entry.username,
                "titleBadge" to entry.titleBadge,
                "rating" to entry.rating,
                "accuracyScore" to entry.accuracyScore,
                "gamesWon" to entry.gamesWon,
                "gamesPlayed" to entry.gamesPlayed,
                "winStreak" to entry.winStreak,
                "playingStyle" to entry.playingStyle,
                "visibility" to entry.visibility,
                "createdAt" to (entry.createdAt ?: Timestamp.now()),
                "updatedAt" to (entry.updatedAt ?: Timestamp.now())
            )
            firestore.collection("leaderboard").document(docId).set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getLeaderboardEntry(docId: String): CloudLeaderboardDoc? {
        val firestore = db ?: return null
        val path = "leaderboard/$docId"
        return try {
            val snap = firestore.collection("leaderboard").document(docId).get().await()
            snap.toObject(CloudLeaderboardDoc::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }
}
