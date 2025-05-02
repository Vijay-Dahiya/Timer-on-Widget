package com.example.tiimer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object CountdownPrefs {
    // Keys for DataStore (Preferences)
    val ACTIVE = booleanPreferencesKey("countdown_active")
    val END_TIME = longPreferencesKey("countdown_end_time")
    val DISPLAY_TEXT = stringPreferencesKey("countdown_text")

    // Unique name for WorkManager job
    const val WORK_NAME = "CountdownWork"
}

// Extension property to get the DataStore instance
val Context.countdownDataStore by preferencesDataStore(name = "countdown_prefs")
