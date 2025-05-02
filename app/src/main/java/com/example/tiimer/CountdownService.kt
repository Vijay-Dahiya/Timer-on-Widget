package com.example.tiimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CountdownService : Service() {
    private val CHANNEL_ID = "CountdownChannel"
    private var countdownJob: Job? = null
    private var endTime: Long = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        // Create a notification channel for the foreground service (Android 8+)
        val channel = NotificationChannel(
            CHANNEL_ID, "Countdown Timer",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle explicit stop requests
        if (intent?.action == "STOP") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        // Determine the countdown end time
        val passedEnd = intent?.getLongExtra("endTime", 0L) ?: 0L
        if (passedEnd > 0) {
            endTime = passedEnd  // Received a new countdown end time
        } else {
            // If no end time passed (e.g., service restarted), load from DataStore
            runBlocking {
                val prefs = applicationContext.countdownDataStore.data.first()
                endTime = prefs[CountdownPrefs.END_TIME] ?: 0L
            }
        }
        if (endTime <= System.currentTimeMillis()) {
            // No valid countdown (maybe already finished) – stop service
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground service with a persistent notification (Android 12+ requires this immediately)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // Use your app's icon here
            .setContentTitle("48-Hour Countdown")
            .setContentText("Countdown in progress")
            .setOngoing(true)
            .build()

        startForeground(1, notification)  // Foreground service started (keeps service alive)

        // Launch the countdown loop if not already running
        if (countdownJob == null) {
            countdownJob = CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    val remainingMs = endTime - now
                    if (remainingMs <= 0) {
                        // Time is up – update widget to show 00:00:00
                        updateAllWidgets(0)
                        // Clear the saved end time since countdown is finished
                        applicationContext.countdownDataStore.edit { it.remove(CountdownPrefs.END_TIME) }
                        break
                    }
                    // Compute remaining seconds and update all widget instances
                    val secondsLeft = (remainingMs / 1000).toInt()
                    updateAllWidgets(secondsLeft)
                    delay(1000)  // Wait 1 second before the next tick
                }
                // Countdown finished – stop the service
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY  // If the system kills the service, it will be restarted with a null intent
    }

    // Helper to update all instances of the AppWidget with the new time
    private suspend fun updateAllWidgets(secondsLeft: Int) {
        val context = this@CountdownService
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java)
        for (glanceId in glanceIds) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[CountdownPrefs.END_TIME] = secondsLeft.toLong()
            }
            CountdownWidget().update(context, glanceId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null  // Not used (no binding)
}
