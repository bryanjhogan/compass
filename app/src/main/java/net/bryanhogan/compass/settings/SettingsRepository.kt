package net.bryanhogan.compass.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "compass_settings"
private const val KEY_USE_GPS_BEARING = "use_gps_bearing"

/** Persists user-facing toggles across launches via [android.content.SharedPreferences]. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _useGpsBearing = MutableStateFlow(prefs.getBoolean(KEY_USE_GPS_BEARING, false))
    val useGpsBearing: StateFlow<Boolean> = _useGpsBearing.asStateFlow()

    fun setUseGpsBearing(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_GPS_BEARING, enabled).apply()
        _useGpsBearing.value = enabled
    }
}
