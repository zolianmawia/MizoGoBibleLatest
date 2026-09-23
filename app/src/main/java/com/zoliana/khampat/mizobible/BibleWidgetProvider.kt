package com.zoliana.khampat.mizobible

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import android.widget.RemoteViews
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleVerse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class BibleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            for (appWidgetId in appWidgetIds) {
                performUpdate(context, appWidgetManager, appWidgetId)
            }
            pendingResult.finish()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_NEXT || action == ACTION_PREV || action == ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    var offset = prefs.getLong("offset_$appWidgetId", -1L)

                    if (offset == -1L) {
                        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                        val year = Calendar.getInstance().get(Calendar.YEAR)
                        offset = (dayOfYear + year).toLong()
                    }

                    when (action) {
                        ACTION_NEXT -> offset += 1
                        ACTION_PREV -> offset = if (offset > 0) offset - 1 else 0L
                        ACTION_REFRESH -> {
                            offset = (0..1000000).random().toLong()
                        }
                    }
                    prefs.edit().putLong("offset_$appWidgetId", offset).apply()
                    performUpdate(context, AppWidgetManager.getInstance(context), appWidgetId)
                    pendingResult.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_NEXT = "com.zoliana.khampat.mizobible.ACTION_NEXT"
        const val ACTION_PREV = "com.zoliana.khampat.mizobible.ACTION_PREV"
        const val ACTION_REFRESH = "com.zoliana.khampat.mizobible.ACTION_REFRESH"

        suspend fun performUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_bible)
            
            views.setOnClickPendingIntent(R.id.btn_widget_next, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btn_widget_prev, getPendingSelfIntent(context, appWidgetId, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.btn_widget_refresh, getPendingSelfIntent(context, appWidgetId, ACTION_REFRESH))

            try {
                val db = BibleDatabase.getDatabase(context.applicationContext)
                val dao = db.bibleDao()
                val totalCount = dao.getTotalVerseCount()

                if (totalCount > 0) {
                    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    var offset = prefs.getLong("offset_$appWidgetId", -1L)
                    if (offset == -1L) {
                        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                        val year = Calendar.getInstance().get(Calendar.YEAR)
                        offset = (dayOfYear + year).toLong()
                    }
                    
                    val finalOffset = (Math.abs(offset) % totalCount).toInt()
                    val verse = dao.getVerseByOffset(finalOffset)
                    
                    if (verse != null) {
                        val formattedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(verse.text ?: "", Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(verse.text ?: "")
                        }
                        
                        views.setTextViewText(R.id.text_widget_verse, formattedText)
                        views.setTextViewText(R.id.text_widget_reference, "${verse.book} ${verse.chapter}:${verse.verse}")
                        
                        val openAppIntent = Intent(context, MainActivity::class.java).apply {
                            putExtra("WIDGET_VERSE_ID", verse.id)
                            putExtra("WIDGET_BOOK", verse.book)
                            putExtra("WIDGET_CHAPTER", verse.chapter)
                            putExtra("WIDGET_VERSION", verse.type)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingOpenApp = PendingIntent.getActivity(context, appWidgetId, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widget_root, pendingOpenApp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, BibleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(context, appWidgetId + action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
