package com.zoliana.khampat.mizobible

import android.app.Application
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class MizoGoBibleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            // Avoid verbose logging that triggers and floods FCM logs
            OneSignal.Debug.logLevel = LogLevel.WARN

            // OneSignal Initialization
            val onesignalAppId = BuildConfig.ONESIGNAL_APP_ID
            if (!onesignalAppId.isNullOrEmpty()) {
                OneSignal.initWithContext(this, onesignalAppId)
                try {
                    OneSignal.InAppMessages.paused = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("MGB_DEBUG", "OneSignal initialization failed: ${e.message}")
        }
    }
}