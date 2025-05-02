package com.example.tiimer
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.padding
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class CountdownWidget : GlanceAppWidget() {
    // Use Preferences DataStore for widget state
    override var stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            val prefs = currentState<Preferences>()
            val active = prefs[CountdownPrefs.ACTIVE] ?: false
            if (!active) {
                actionRunCallback<StartCountdownAction>()
            } else {
                Text(text = prefs[CountdownPrefs.DISPLAY_TEXT].orEmpty(), style = TextStyle(fontWeight = FontWeight.Bold))
            }
        }
    }
}

// Receiver to attach the GlanceAppWidget to the system (required for Glance widgets)
class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

// Action to start the countdown (triggered by the Start button in the widget)
class StartCountdownAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val endTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(48)

        // Update persistent DataStore state
        context.countdownDataStore.edit { prefs ->
            prefs[CountdownPrefs.ACTIVE] = true
            prefs[CountdownPrefs.END_TIME] = endTime
        }

        // Initialize widget state: mark active and set initial display text
        val initialText = formatTime(endTime - System.currentTimeMillis())
        GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[CountdownPrefs.ACTIVE] = true
                prefs[CountdownPrefs.DISPLAY_TEXT] = initialText
            }
        }
        CountdownWidget().updateAll(context)  // Refresh the widget UI with new state:contentReference[oaicite:6]{index=6}

        // Schedule the WorkManager foreground worker (expedited) to run the countdown
        val workRequest = OneTimeWorkRequestBuilder<CountdownWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CountdownPrefs.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}

// Helper to format milliseconds into HH:MM:SS
internal fun formatTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0) / 1000  // avoid negative
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
