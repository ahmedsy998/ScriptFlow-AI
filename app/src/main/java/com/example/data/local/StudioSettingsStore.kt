package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ArabicDialect
import com.example.domain.model.VideoResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "scriptflow_settings")

data class AppPreferences(
    val isDarkTheme: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val defaultResolution: VideoResolution = VideoResolution.HD_720P,
    val defaultDialect: ArabicDialect = ArabicDialect.FUSHA,
    val activeProjectId: String? = null
)

class StudioSettingsStore(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val DEFAULT_DIALECT = stringPreferencesKey("default_dialect")
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
    }

    val preferencesFlow: Flow<AppPreferences> = context.studioDataStore.data.map { prefs ->
        AppPreferences(
            isDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            autoSaveEnabled = prefs[Keys.AUTO_SAVE] ?: true,
            defaultResolution = VideoResolution.entries.firstOrNull {
                it.id == prefs[Keys.DEFAULT_RESOLUTION]
            } ?: VideoResolution.HD_720P,
            defaultDialect = ArabicDialect.entries.firstOrNull {
                it.name == prefs[Keys.DEFAULT_DIALECT]
            } ?: ArabicDialect.FUSHA,
            activeProjectId = prefs[Keys.ACTIVE_PROJECT_ID]
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun setDefaultResolution(resolution: VideoResolution) {
        context.studioDataStore.edit { it[Keys.DEFAULT_RESOLUTION] = resolution.id }
    }

    suspend fun setDefaultDialect(dialect: ArabicDialect) {
        context.studioDataStore.edit { it[Keys.DEFAULT_DIALECT] = dialect.name }
    }

    suspend fun setActiveProjectId(projectId: String?) {
        context.studioDataStore.edit { prefs ->
            if (projectId == null) prefs.remove(Keys.ACTIVE_PROJECT_ID)
            else prefs[Keys.ACTIVE_PROJECT_ID] = projectId
        }
    }
}
