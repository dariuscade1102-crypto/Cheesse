package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
abstract class FirestoreEmulatorTestBase {
    lateinit var db: FirebaseFirestore
    lateinit var auth: FirebaseAuth
    private val projectId = "demo-test-project"
    private lateinit var databaseId: String

    @Before
    fun setupEmulator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseId = context.getString(R.string.firestore_database_id)
        FirebaseApp.clearInstancesForTest()

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId("1:1234567890:android:abcdef")
            .setApiKey("fake-api-key-for-emulator")
            .build()
        val app = FirebaseApp.initializeApp(context, options)

        auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)

        db = FirebaseFirestore.getInstance(app, databaseId)
        db.useEmulator("127.0.0.1", 8085)

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    @After
    fun tearDownEmulator() = runBlocking {
        try {
            withContext(Dispatchers.IO) {
                val url = URL("http://127.0.0.1:8085/emulator/v1/projects/$projectId/databases/$databaseId/documents")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.responseCode
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        try {
            awaitTask(db.terminate())
        } catch (_: Exception) {
        }
        FirebaseApp.clearInstancesForTest()
    }

    protected fun <T> awaitTask(task: Task<T>): T = runBlocking {
        withTimeout(10_000) {
            while (!task.isComplete) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            if (task.isSuccessful) {
                task.result
            } else {
                throw task.exception ?: RuntimeException("Task failed without exception")
            }
        }
    }

    protected fun signInTestUser(
        email: String = "user_${System.nanoTime()}@example.com",
        password: String = "password123"
    ): String {
        val result = awaitTask(auth.createUserWithEmailAndPassword(email, password))
        return result.user!!.uid
    }
}
