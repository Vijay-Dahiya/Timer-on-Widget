package com.example.tiimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Default).launch {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // Read persisted countdown state after reboot
            val prefs = runBlocking { context.countdownDataStore.data.first() }
            val wasActive = prefs[CountdownPrefs.ACTIVE] ?: false
            val endTime = prefs[CountdownPrefs.END_TIME] ?: 0L

            if (wasActive && endTime > System.currentTimeMillis()) {
                // There was an active countdown that still hasn't finished – restart the worker
                val workRequest = OneTimeWorkRequestBuilder<CountdownWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    CountdownPrefs.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }

            // Update the widget UI state on boot (so it shows correct time immediately)
                val glanceIds =
                    GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java)
                if (glanceIds.isNotEmpty()) {
                    glanceIds.forEach { id ->
                        updateAppWidgetState(context, id) { state ->
                            if (wasActive && endTime > System.currentTimeMillis()) {
                                // Still active countdown -> update remaining time display
                                val remaining = endTime - System.currentTimeMillis()
                                state[CountdownPrefs.ACTIVE] = true
                                state[CountdownPrefs.DISPLAY_TEXT] = formatTime(remaining)
                            } else {
                                // No active countdown -> ensure state is cleared
                                state[CountdownPrefs.ACTIVE] = false
                                state.remove(CountdownPrefs.DISPLAY_TEXT)
                            }
                        }
                    }
                    CountdownWidget().updateAll(context)  // trigger widget to redraw with updated state
                }
            }
        }
    }
}

