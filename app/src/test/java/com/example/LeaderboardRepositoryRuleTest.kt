package com.example

import android.os.Looper
import com.example.data.CloudLeaderboardDoc
import com.example.data.FirestoreLeaderboardRepository
import com.example.data.LocalPlayerStatsSnapshot
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class LeaderboardRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun <T> runWithLooper(block: suspend CoroutineScope.() -> T): T = runBlocking {
        val deferred = async(Dispatchers.Default) { block() }
        while (!deferred.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        deferred.await()
    }

    @Test
    fun writeLeaderboardEntry_whenUnauthenticated_throwsPermissionDenied() {
        val repo = FirestoreLeaderboardRepository(db = db, authProvider = { auth })
        val entry = CloudLeaderboardDoc(
            id = "some-uid",
            userId = "some-uid",
            username = "AnonPlayer",
            titleBadge = "CM",
            rating = 1850,
            accuracyScore = 91.6,
            gamesWon = 10,
            gamesPlayed = 15,
            winStreak = 2,
            playingStyle = "TACTICAL_AGGRESSIVE",
            visibility = "public",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        try {
            runWithLooper { repo.writeLeaderboardEntry(entry) }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            assertTrue(
                "Expected PERMISSION_DENIED or FirestoreError, got: ${e.message}",
                e.message?.contains("PERMISSION_DENIED") == true
            )
        }
    }

    @Test
    fun syncCleanGamesAndMergeProfile_whenAuthenticatedAsOwner_succeedsAndMergesMaxRating() {
        val uid = signInTestUser()
        val repo = FirestoreLeaderboardRepository(db = db, authProvider = { auth })

        val initialStats = LocalPlayerStatsSnapshot(
            username = "CheesseHero",
            titleBadge = "IM",
            rating = 1920,
            accuracyScore = 93.2,
            gamesWon = 42,
            gamesPlayed = 55,
            winStreak = 4,
            playingStyle = "TACTICAL_AGGRESSIVE"
        )

        val synced = runWithLooper {
            repo.syncCleanGamesAndMergeProfile(uid = uid, localStats = initialStats)
        }
        assertNotNull(synced)
        assertEquals(uid, synced?.userId)
        assertEquals(1920, synced?.rating)

        val fetched = runWithLooper { repo.getLeaderboardEntry(uid) }
        assertNotNull(fetched)
        assertEquals("CheesseHero", fetched?.username)
        assertEquals(1920, fetched?.rating)
    }

    @Test
    fun writeLeaderboardEntry_forAnotherUsersDocument_throwsPermissionDenied() {
        val myUid = signInTestUser()
        val repo = FirestoreLeaderboardRepository(db = db, authProvider = { auth })

        // Attempting to write to "other-user-id" while signed in as myUid
        val spoofedEntry = CloudLeaderboardDoc(
            id = "other-user-id",
            userId = myUid,
            username = "Spoofer",
            titleBadge = "CM",
            rating = 2500,
            accuracyScore = 99.0,
            gamesWon = 99,
            gamesPlayed = 99,
            winStreak = 99,
            playingStyle = "TACTICAL_AGGRESSIVE",
            visibility = "public",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        try {
            runWithLooper { repo.writeLeaderboardEntry(spoofedEntry) }
            fail("Expected PERMISSION_DENIED when writing to another user's leaderboard doc")
        } catch (e: Exception) {
            assertTrue(
                "Expected PERMISSION_DENIED, got: ${e.message}",
                e.message?.contains("PERMISSION_DENIED") == true
            )
        }
    }
}
