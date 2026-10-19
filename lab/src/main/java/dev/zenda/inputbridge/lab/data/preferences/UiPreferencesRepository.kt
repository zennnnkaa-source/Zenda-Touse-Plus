package dev.zenda.inputbridge.lab.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inputBridgeSettings by preferencesDataStore(name = "inputbridge_settings")

class UiPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.inputBridgeSettings

    val darkThemeEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true
    }

    suspend fun toggleDarkTheme() {
        dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = !(preferences[KEY_DARK_THEME] ?: true)
        }
    }

    private companion object {
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
    }
}
