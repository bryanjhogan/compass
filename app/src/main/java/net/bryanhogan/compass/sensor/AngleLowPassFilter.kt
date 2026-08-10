package net.bryanhogan.compass.sensor

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooths a stream of angles (degrees) with an exponential moving average applied to the
 * angle's sin/cos components, so smoothing behaves correctly across the 0/360 wraparound
 * instead of producing a jump when the raw signal crosses it.
 */
class AngleLowPassFilter(private val alpha: Float = 0.15f) {

    private var smoothedSin = 0f
    private var smoothedCos = 1f
    private var initialized = false

    /** Feeds a new raw angle in degrees, returns the smoothed angle in the range [-180, 180). */
    fun smooth(newDegrees: Float): Float {
        val rad = Math.toRadians(newDegrees.toDouble())
        val newSin = sin(rad).toFloat()
        val newCos = cos(rad).toFloat()
        if (!initialized) {
            smoothedSin = newSin
            smoothedCos = newCos
            initialized = true
        } else {
            smoothedSin += alpha * (newSin - smoothedSin)
            smoothedCos += alpha * (newCos - smoothedCos)
        }
        return Math.toDegrees(atan2(smoothedSin.toDouble(), smoothedCos.toDouble())).toFloat()
    }

    /** Same as [smooth] but wraps the result into the compass-friendly range [0, 360). */
    fun smoothHeading(newDegrees: Float): Float {
        val angle = smooth(newDegrees)
        return (angle + 360f) % 360f
    }
}
