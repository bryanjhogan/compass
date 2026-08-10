package net.bryanhogan.compass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

/** Snapshot of everything the compass and level screens need, derived from the device's sensors. */
data class CompassState(
    /** Magnetic heading in degrees, 0 = magnetic north, increasing clockwise. */
    val magneticAzimuthDegrees: Float = 0f,
    /** Device tilt front/back in degrees. */
    val pitchDegrees: Float = 0f,
    /** Device tilt left/right in degrees. */
    val rollDegrees: Float = 0f,
    /** Magnitude of the ambient magnetic field in microtesla (typical Earth field: 25-65 uT). */
    val magneticFieldMicroTesla: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val hasRotationSensor: Boolean = true,
    val hasMagnetometer: Boolean = true
)

/**
 * Wraps the Android sensor APIs into a single StateFlow of [CompassState]. Uses the fused
 * rotation-vector sensor (accelerometer + magnetometer, gyro-assisted when present) for
 * heading/pitch/roll since it is far more stable than deriving orientation from the raw
 * accelerometer and magnetometer directly, and reads the magnetometer separately only for
 * field-strength/calibration reporting.
 */
class CompassSensorManager(context: Context) {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val azimuthFilter = AngleLowPassFilter(alpha = 0.15f)
    private val pitchFilter = AngleLowPassFilter(alpha = 0.15f)
    private val rollFilter = AngleLowPassFilter(alpha = 0.15f)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val _state = MutableStateFlow(
        CompassState(
            hasRotationSensor = rotationSensor != null,
            hasMagnetometer = magnetometer != null
        )
    )
    val state: StateFlow<CompassState> = _state.asStateFlow()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event.values)
                Sensor.TYPE_MAGNETIC_FIELD -> handleMagneticField(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                _state.update { it.copy(accuracy = accuracy) }
            }
        }
    }

    private fun handleRotationVector(rotationVector: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val rawAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        val azimuth = azimuthFilter.smoothHeading(rawAzimuth)
        val pitch = pitchFilter.smooth(rawPitch)
        val roll = rollFilter.smooth(rawRoll)

        _state.update { it.copy(magneticAzimuthDegrees = azimuth, pitchDegrees = pitch, rollDegrees = roll) }
    }

    private fun handleMagneticField(magneticField: FloatArray) {
        val strength = sqrt(
            magneticField[0] * magneticField[0] +
                magneticField[1] * magneticField[1] +
                magneticField[2] * magneticField[2]
        )
        _state.update { it.copy(magneticFieldMicroTesla = strength) }
    }

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
