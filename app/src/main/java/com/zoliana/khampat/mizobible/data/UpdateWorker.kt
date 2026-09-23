package com.zoliana.khampat.mizobible.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import kotlinx.coroutines.tasks.await

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
        val dbUrl = "https://mizogobible-2c7f1-default-rtdb.firebaseio.com/"

        try {
            val snapshot = FirebaseDatabase.getInstance(dbUrl).reference.get().await()
            var rootMap = snapshot.value as? Map<String, Any> ?: return Result.failure()

            // Handle nested URL key if exists (common in Firebase imports)
            if (rootMap.size == 1 && rootMap.keys.first().startsWith("http")) {
                rootMap = rootMap[rootMap.keys.first()] as? Map<String, Any> ?: rootMap
            }

            val remoteVersions = if (rootMap.containsKey("versions")) {
                rootMap["versions"] as? Map<String, Any>
            } else if (rootMap.containsKey("MzOV")) {
                rootMap
            } else rootMap

            val versionsToCheck = listOf("MzOV", "KJV", "MzCL", "NIV")

            versionsToCheck.forEach { vCode ->
                val vData = remoteVersions?.get(vCode) as? Map<String, Any> ?: return@forEach
                val remote = (vData["version"] as? Number)?.toInt() ?: 0
                val local = prefs.getInt("version_$vCode", 1)
                val ignored = prefs.getInt("ignored_version_$vCode", 0)
                val url = (vData["url"] as? String)?.trim() ?: ""

                if (remote > local && remote > ignored && url.isNotEmpty() && !url.contains("VA_DAH_RAWH")) {
                    val message =
                        (vData["message"] as? String) ?: "$vCode Bible update thar a awm e."
                    showNotification(vCode, message)
                    // We only notify once per run to avoid spamming
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Error checking updates", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun showNotification(vCode: String, message: String) {
        val channelId = "BIBLE_UPDATE_CHANNEL"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bible Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            vCode.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Mizo Go Bible Update")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(vCode.hashCode(), builder.build())
    }
}
