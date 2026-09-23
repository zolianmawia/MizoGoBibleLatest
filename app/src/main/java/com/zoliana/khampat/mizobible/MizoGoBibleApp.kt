package com.zoliana.khampat.mizobible

import android.app.Application
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MizoGoBibleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            // Verbose Logging helps with debug issues!
            OneSignal.Debug.logLevel = LogLevel.VERBOSE

            // OneSignal Initialization
            OneSignal.initWithContext(this, "74866640-70e9-481a-8b42-f62c9d8c05aa")

            // requestPermission hi suspend function a nih tak avangin Coroutine chhungah kan dah a ngai e
            CoroutineScope(Dispatchers.IO).launch {
                OneSignal.Notifications.requestPermission(true)
            }
        } catch (e: Exception) {
            Log.e("MGB_DEBUG", "OneSignal initialization failed: ${e.message}")
        }
    }
}