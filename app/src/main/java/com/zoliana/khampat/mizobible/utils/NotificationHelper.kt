package com.zoliana.khampat.mizobible.utils

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zoliana.khampat.mizobible.worker.DailyVerseWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationHelper {

    const val CHANNEL_ID = "daily_verse_channel"
    private const val WORK_NAME = "daily_verse_work"

    fun scheduleNextNotification(context: Context) {
        val delayMs = calculateDelayToNextNotification()

        val workRequest = OneTimeWorkRequestBuilder<DailyVerseWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Always replace with the exact next time calculation
            workRequest
        )
    }

    fun cancelNotification(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun calculateDelayToNextNotification(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // By default, target is 8:00 AM today
        target.set(Calendar.HOUR_OF_DAY, 8)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // Determine next trigger time
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        if (dayOfWeek == Calendar.SUNDAY) {
            // It is Sunday. Check if we missed 8 AM
            if (now.after(target)) {
                // Next target is 12:30 PM today
                target.set(Calendar.HOUR_OF_DAY, 12)
                target.set(Calendar.MINUTE, 30)

                // Check if we missed 12:30 PM too
                if (now.after(target)) {
                    // Next is 8 AM Monday
                    target.add(Calendar.DAY_OF_YEAR, 1)
                    target.set(Calendar.HOUR_OF_DAY, 8)
                    target.set(Calendar.MINUTE, 0)
                }
            }
        } else {
            // It is not Sunday
            if (now.after(target)) {
                // We missed 8 AM today. Next target is 8 AM tomorrow.
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }
}
