package com.zoliana.khampat.mizobible.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.utils.NotificationHelper
import java.util.Calendar

class DailyVerseWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("daily_verse_notification", false)

        if (!isEnabled) {
            return Result.success()
        }

        try {
            val db = BibleDatabase.getDatabase(applicationContext)
            val dao = db.bibleDao()
            val totalCount = dao.getTotalVerseCount()

            if (totalCount > 0) {
                // Randomly select a verse based on date (similar to widget)
                val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                val year = Calendar.getInstance().get(Calendar.YEAR)
                val offset = (dayOfYear + year + System.currentTimeMillis() % 1000).toLong() // Add slight randomness for multiple notifications in a day

                val finalOffset = (Math.abs(offset) % totalCount).toInt()
                val verse = dao.getVerseByOffset(finalOffset)

                if (verse != null) {
                    val rawText = verse.text ?: ""
                    val plainText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(rawText).toString()
                    }

                    showNotification(
                        applicationContext,
                        "${verse.book} ${verse.chapter}:${verse.verse}",
                        plainText,
                        verse.id ?: 0,
                        verse.book ?: "",
                        verse.chapter ?: 1,
                        verse.type ?: "MzOV"
                    )
                }
            }

            // Schedule the NEXT notification
            NotificationHelper.scheduleNextNotification(applicationContext)
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Schedule the NEXT notification even if this one failed
            NotificationHelper.scheduleNextNotification(applicationContext)
            return Result.failure()
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        content: String,
        verseId: Int,
        book: String,
        chapter: Int,
        version: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("WIDGET_VERSE_ID", verseId)
            putExtra("WIDGET_BOOK", book)
            putExtra("WIDGET_CHAPTER", chapter)
            putExtra("WIDGET_VERSION", version)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Nitin Bible Chang - $title")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
            e.printStackTrace()
        }
    }
}
