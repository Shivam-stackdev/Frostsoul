package dev.vxs.frostsoul.overlay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.overlayDataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_settings")

/**
 * User preferences for the floating lyrics overlay.
 */
@Singleton
class OverlaySettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.overlayDataStore

    companion object {
        val ENABLED = booleanPreferencesKey("overlay_enabled")
        val LOCKED = booleanPreferencesKey("overlay_locked")
        val TRANSPARENCY = floatPreferencesKey("overlay_transparency")
        val FONT_SIZE = intPreferencesKey("overlay_font_size")
        val TWO_LINE_MODE = booleanPreferencesKey("overlay_two_line_mode")
        val AUTO_HIDE_WHEN_PAUSED = booleanPreferencesKey("overlay_auto_hide_paused")
        val TOUCH_THROUGH_WHEN_LOCKED = booleanPreferencesKey("overlay_touch_through_locked")
        val X_POSITION = intPreferencesKey("overlay_x")
        val Y_POSITION = intPreferencesKey("overlay_y")
        val SHOW_TRANSLATION = booleanPreferencesKey("overlay_show_translation")
        val SHOW_ROMANIZED = booleanPreferencesKey("overlay_show_romanized")

        const val DEFAULT_TRANSPARENCY = 0.75f
        const val DEFAULT_FONT_SIZE = 18 // sp
        const val DEFAULT_X = 0
        const val DEFAULT_Y = 200
    }

    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }
    val locked: Flow<Boolean> = dataStore.data.map { it[LOCKED] ?: false }
    val transparency: Flow<Float> = dataStore.data.map { it[TRANSPARENCY] ?: DEFAULT_TRANSPARENCY }
    val fontSize: Flow<Int> = dataStore.data.map { it[FONT_SIZE] ?: DEFAULT_FONT_SIZE }
    val twoLineMode: Flow<Boolean> = dataStore.data.map { it[TWO_LINE_MODE] ?: true }
    val autoHideWhenPaused: Flow<Boolean> = dataStore.data.map { it[AUTO_HIDE_WHEN_PAUSED] ?: true }
    val touchThroughWhenLocked: Flow<Boolean> = dataStore.data.map { it[TOUCH_THROUGH_WHEN_LOCKED] ?: true }
    val xPosition: Flow<Int> = dataStore.data.map { it[X_POSITION] ?: DEFAULT_X }
    val yPosition: Flow<Int> = dataStore.data.map { it[Y_POSITION] ?: DEFAULT_Y }
    val showTranslation: Flow<Boolean> = dataStore.data.map { it[SHOW_TRANSLATION] ?: false }
    val showRomanized: Flow<Boolean> = dataStore.data.map { it[SHOW_ROMANIZED] ?: false }

    suspend fun setEnabled(value: Boolean) = dataStore.edit { it[ENABLED] = value }
    suspend fun setLocked(value: Boolean) = dataStore.edit { it[LOCKED] = value }
    suspend fun setTransparency(value: Float) = dataStore.edit { it[TRANSPARENCY] = value }
    suspend fun setFontSize(value: Int) = dataStore.edit { it[FONT_SIZE] = value }
    suspend fun setTwoLineMode(value: Boolean) = dataStore.edit { it[TWO_LINE_MODE] = value }
    suspend fun setAutoHideWhenPaused(value: Boolean) = dataStore.edit { it[AUTO_HIDE_WHEN_PAUSED] = value }
    suspend fun setTouchThroughWhenLocked(value: Boolean) = dataStore.edit { it[TOUCH_THROUGH_WHEN_LOCKED] = value }
    suspend fun setPosition(x: Int, y: Int) = dataStore.edit {
        it[X_POSITION] = x
        it[Y_POSITION] = y
    }
    suspend fun setShowTranslation(value: Boolean) = dataStore.edit { it[SHOW_TRANSLATION] = value }
    suspend fun setShowRomanized(value: Boolean) = dataStore.edit { it[SHOW_ROMANIZED] = value }
}
