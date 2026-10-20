package com.examenesdrones.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** BCP-47 tag, or empty for the system language. */
    val language: String = "",
    val practiceCount: Int = 20,
    val prioritizeWeak: Boolean = true,
    val shuffleOptions: Boolean = false,
    /** 0 means "use the official format from the PDF" when available. */
    val examCount: Int = 0,
    val examTimerEnabled: Boolean = true,
    /** 0 means "use the official time limit from the PDF" when available. */
    val examTimerMinutes: Int = 0,
    /** Negative means "use the official pass mark from the PDF" when available. */
    val passPercent: Double = -1.0,
    val penaltyPerWrong: Double = 0.0,
    val textScale: Float = 1f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val practiceCount = intPreferencesKey("practice_count")
        val prioritizeWeak = booleanPreferencesKey("prioritize_weak")
        val shuffleOptions = booleanPreferencesKey("shuffle_options")
        val examCount = intPreferencesKey("exam_count")
        val examTimerEnabled = booleanPreferencesKey("exam_timer_enabled")
        val examTimerMinutes = intPreferencesKey("exam_timer_minutes")
        val passPercent = doublePreferencesKey("pass_percent")
        val penalty = doublePreferencesKey("penalty")
        val textScale = doublePreferencesKey("text_scale")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = UserSettings(
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        language = this[Keys.language] ?: "",
        practiceCount = this[Keys.practiceCount] ?: 20,
        prioritizeWeak = this[Keys.prioritizeWeak] ?: true,
        shuffleOptions = this[Keys.shuffleOptions] ?: false,
        examCount = this[Keys.examCount] ?: 0,
        examTimerEnabled = this[Keys.examTimerEnabled] ?: true,
        examTimerMinutes = this[Keys.examTimerMinutes] ?: 0,
        passPercent = this[Keys.passPercent] ?: -1.0,
        penaltyPerWrong = this[Keys.penalty] ?: 0.0,
        textScale = (this[Keys.textScale] ?: 1.0).toFloat(),
    )

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[Keys.theme] = s.theme.name
            p[Keys.language] = s.language
            p[Keys.practiceCount] = s.practiceCount
            p[Keys.prioritizeWeak] = s.prioritizeWeak
            p[Keys.shuffleOptions] = s.shuffleOptions
            p[Keys.examCount] = s.examCount
            p[Keys.examTimerEnabled] = s.examTimerEnabled
            p[Keys.examTimerMinutes] = s.examTimerMinutes
            p[Keys.passPercent] = s.passPercent
            p[Keys.penalty] = s.penaltyPerWrong
            p[Keys.textScale] = s.textScale.toDouble()
        }
    }
}
