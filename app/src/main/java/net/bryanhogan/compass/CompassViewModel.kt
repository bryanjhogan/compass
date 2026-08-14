package net.bryanhogan.compass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.bryanhogan.compass.location.LocationRepository
import net.bryanhogan.compass.location.LocationState
import net.bryanhogan.compass.sensor.CompassSensorManager
import net.bryanhogan.compass.sensor.CompassState
import net.bryanhogan.compass.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/** Shared across all screens so the compass keeps ticking while the user switches tabs. */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = CompassSensorManager(application)
    private val locationRepository = LocationRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val compassState: StateFlow<CompassState> = sensorManager.state
    val locationState: StateFlow<LocationState> = locationRepository.locationState
    val useGpsBearing: StateFlow<Boolean> = settingsRepository.useGpsBearing

    fun setUseGpsBearing(enabled: Boolean) {
        settingsRepository.setUseGpsBearing(enabled)
    }

    fun onResume(hasLocationPermission: Boolean) {
        sensorManager.start()
        if (hasLocationPermission) {
            locationRepository.start()
        }
    }

    fun onPause() {
        sensorManager.stop()
        locationRepository.stop()
    }

    override fun onCleared() {
        locationRepository.dispose()
    }
}

/** True heading = magnetic heading + declination at the current location, wrapped to [0, 360). */
fun trueHeadingDegrees(compassState: CompassState, locationState: LocationState): Float {
    val raw = compassState.magneticAzimuthDegrees + locationState.declinationDegrees
    return (raw + 360f) % 360f
}

fun headingToCardinal(headingDegrees: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((headingDegrees + 22.5f) / 45f).toInt()) % 8
    return directions[index]
}

enum class HeadingSource { GPS_BEARING, TRUE_NORTH, MAGNETIC }

data class HeadingResult(val degrees: Float, val source: HeadingSource)

/**
 * Picks what to show as the current heading: GPS bearing (direction of travel) only when the
 * user has opted in and a fresh, fast-enough fix supplies one; otherwise the usual
 * sensor-derived heading, true if we have a fix to correct for declination, magnetic otherwise.
 */
fun effectiveHeading(
    compassState: CompassState,
    locationState: LocationState,
    useGpsBearing: Boolean
): HeadingResult {
    val gpsBearing = locationState.gpsBearingDegrees
    return when {
        useGpsBearing && gpsBearing != null -> HeadingResult(gpsBearing, HeadingSource.GPS_BEARING)
        locationState.hasFix -> HeadingResult(trueHeadingDegrees(compassState, locationState), HeadingSource.TRUE_NORTH)
        else -> HeadingResult(compassState.magneticAzimuthDegrees, HeadingSource.MAGNETIC)
    }
}
