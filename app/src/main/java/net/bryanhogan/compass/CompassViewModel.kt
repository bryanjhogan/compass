package net.bryanhogan.compass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.bryanhogan.compass.location.LocationRepository
import net.bryanhogan.compass.location.LocationState
import net.bryanhogan.compass.sensor.CompassSensorManager
import net.bryanhogan.compass.sensor.CompassState
import kotlinx.coroutines.flow.StateFlow

/** Shared across all three screens so the compass keeps ticking while the user switches tabs. */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = CompassSensorManager(application)
    private val locationRepository = LocationRepository(application)

    val compassState: StateFlow<CompassState> = sensorManager.state
    val locationState: StateFlow<LocationState> = locationRepository.locationState

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
