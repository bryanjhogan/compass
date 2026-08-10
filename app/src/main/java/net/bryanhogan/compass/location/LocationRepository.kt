package net.bryanhogan.compass.location

import android.content.Context
import android.hardware.GeomagneticField
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Everything the map and heading UI need about where the device currently is. */
data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val address: String? = null,
    /**
     * Degrees to add to a magnetic heading to get a true heading at this location
     * (east declination positive, west negative). Zero until a location fix arrives,
     * which is an acceptable approximation for most of the globe.
     */
    val declinationDegrees: Float = 0f,
    val hasFix: Boolean = false
)

private const val MIN_GEOCODE_DISTANCE_METERS = 100f

/**
 * Streams device location via the platform [LocationManager] (no Google Play Services
 * dependency needed), reverse-geocodes it to a human-readable address, and computes the
 * magnetic declination for the current fix so the compass can show a true-north heading.
 */
class LocationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val geocoder by lazy { Geocoder(appContext, Locale.getDefault()) }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastGeocodedLocation: Location? = null

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val listener = LocationListener { location -> handleNewLocation(location) }

    /** Caller is responsible for having already obtained the location permission. */
    fun start() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            try {
                locationManager.requestLocationUpdates(provider, 2000L, 5f, listener)
            } catch (_: SecurityException) {
                // Permission not granted; caller will retry start() once it is.
            }
        }

        providers
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { handleNewLocation(it) }
    }

    fun stop() {
        locationManager.removeUpdates(listener)
    }

    fun dispose() {
        stop()
        ioScope.cancel()
    }

    private fun handleNewLocation(location: Location) {
        val declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            if (location.hasAltitude()) location.altitude.toFloat() else 0f,
            location.time
        ).declination

        _locationState.update {
            it.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                declinationDegrees = declination,
                hasFix = true
            )
        }

        maybeReverseGeocode(location)
    }

    private fun maybeReverseGeocode(location: Location) {
        val last = lastGeocodedLocation
        if (last != null && last.distanceTo(location) < MIN_GEOCODE_DISTANCE_METERS) return
        lastGeocodedLocation = location

        ioScope.launch {
            val address = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let(::formatAddress)
            }.getOrNull()

            if (address != null) {
                _locationState.update { it.copy(address = address) }
            }
        }
    }

    private fun formatAddress(address: Address): String {
        address.getAddressLine(0)?.let { return it }
        return listOfNotNull(address.locality, address.adminArea, address.countryName)
            .joinToString(", ")
            .ifBlank { "Unknown location" }
    }
}
