package com.example.tiimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CountdownWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val context = appContext

    override suspend fun doWork(): Result {
        // Load the countdown target end time from DataStore
        val prefs = context.countdownDataStore.data.first()
        val endTime = prefs[CountdownPrefs.END_TIME] ?: 0L
        if (endTime <= System.currentTimeMillis()) {
            return Result.success() // No valid countdown to run
        }

        // Create and start foreground notification
        setForeground(createForegroundInfo(formatTime(endTime - System.currentTimeMillis())))

        try {
            // Loop until the countdown is finished or work is cancelled
            while (System.currentTimeMillis() < endTime) {
                // Compute remaining time and format it
                val now = System.currentTimeMillis()
                val remainingMs = endTime - now
                val remainingText = formatTime(remainingMs)

                // Update the widget's DataStore state with new remaining time
                GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java).forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[CountdownPrefs.ACTIVE] = true
                        prefs[CountdownPrefs.DISPLAY_TEXT] = remainingText
                    }
                }
                // Request widget UI update
                CountdownWidget().updateAll(context)

                // Refresh the notification text every minute or on significant changes to reduce overhead
                if (Build.VERSION.SDK_INT >= 24) {
                    // Update notification text periodically (e.g., every 60 seconds or if less than 60s remaining)
                    if (remainingMs < 60000 || remainingMs % 60000L < 1000L) {
                        setForeground(createForegroundInfo(remainingText))
                    }
                } else {
                    // For older devices, just update every loop
                    setForeground(createForegroundInfo(remainingText))
                }

                // Wait 1 second before next update
                delay(1000)
                if (isStopped) break  // exit if work was cancelled
            }
        } finally {
            // Cleanup/ending: mark countdown as stopped in DataStore (if not already)
            context.countdownDataStore.edit { prefs ->
                prefs[CountdownPrefs.ACTIVE] = false
            }
            // Ensure widget shows final state (00:00:00 or stopped)
            GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[CountdownPrefs.ACTIVE] = false
                    // Optionally set final time to "00:00:00"
                    prefs[CountdownPrefs.DISPLAY_TEXT] = "00:00:00"
                }
            }
            CountdownWidget().updateAll(context)
        }

        return Result.success()
    }

    // Create ForegroundInfo for the ongoing countdown notification
    private fun createForegroundInfo(timeRemainingText: String): ForegroundInfo {
        val channelId = "countdown_timer_channel"
        val title = "Countdown Timer"
        val cancelText = "Stop"

        // Create notification channel for Android O+
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Countdown Timer", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows the remaining time for the countdown widget"
                    }
                )
            }
        }

        // Create an intent to cancel the worker (stop the countdown) from notification
        val cancelIntent = WorkManager.getInstance(context)
            .createCancelPendingIntent(id)  // cancels this work when clicked

        // Build the persistent notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // TODO: replace with your own icon resource
            .setContentTitle(title)
            .setContentText("Time remaining: $timeRemainingText")
            .setOngoing(true)               // make it persistent (user cannot swipe it away)
            .addAction(android.R.drawable.ic_delete, cancelText, cancelIntent)  // action to stop
            .build()

        // Specify that this WorkManager task uses the "dataSync" foreground service type (Android 14+)
        return ForegroundInfo(1, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
