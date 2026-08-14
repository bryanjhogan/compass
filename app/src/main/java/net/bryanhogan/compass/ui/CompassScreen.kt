package net.bryanhogan.compass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.hardware.SensorManager
import net.bryanhogan.compass.HeadingSource
import net.bryanhogan.compass.effectiveHeading
import net.bryanhogan.compass.headingToCardinal
import net.bryanhogan.compass.sensor.CompassState
import net.bryanhogan.compass.location.LocationState
import net.bryanhogan.compass.trueHeadingDegrees
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassScreen(
    compassState: CompassState,
    locationState: LocationState,
    useGpsBearing: Boolean,
    onUseGpsBearingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val magneticHeading = compassState.magneticAzimuthDegrees
    val trueHeading = trueHeadingDegrees(compassState, locationState)
    val heading = effectiveHeading(compassState, locationState, useGpsBearing)
    val displayHeading = heading.degrees

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!compassState.hasRotationSensor) {
            Text(
                text = "This device has no orientation sensor",
                color = MaterialTheme.colorScheme.error
            )
        } else if (isUncalibrated(compassState.accuracy)) {
            CalibrationBanner()
        }

        CompassDial(
            headingDegrees = displayHeading,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.85f)
        )

        Text(
            text = "${displayHeading.roundToInt()}°",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = headingToCardinal(displayHeading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = headingSourceLabel(heading.source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Magnetic heading", "${magneticHeading.roundToInt()}°")
                InfoRow(
                    "True heading",
                    if (locationState.hasFix) "${trueHeading.roundToInt()}°" else "Waiting for GPS fix"
                )
                InfoRow(
                    "GPS bearing",
                    locationState.gpsBearingDegrees?.let { "${it.roundToInt()}°" } ?: "Not moving fast enough"
                )
                InfoRow("Magnetic field", "${compassState.magneticFieldMicroTesla.roundToInt()} µT")
                InfoRow("Sensor accuracy", accuracyLabel(compassState.accuracy))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        GpsBearingToggleRow(
            useGpsBearing = useGpsBearing,
            onUseGpsBearingChange = onUseGpsBearingChange
        )
    }
}

private fun headingSourceLabel(source: HeadingSource): String = when (source) {
    HeadingSource.GPS_BEARING -> "Showing GPS bearing (direction of travel)"
    HeadingSource.TRUE_NORTH -> "Showing true heading"
    HeadingSource.MAGNETIC -> "Showing magnetic heading"
}

@Composable
private fun GpsBearingToggleRow(
    useGpsBearing: Boolean,
    onUseGpsBearingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onUseGpsBearingChange(!useGpsBearing) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Use GPS bearing while moving",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = useGpsBearing, onCheckedChange = onUseGpsBearingChange)
    }
}

@Composable
private fun CalibrationBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text = "Compass needs calibration — wave your device in a figure-8",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun isUncalibrated(accuracy: Int): Boolean =
    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

private fun accuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
    else -> "Unreliable"
}

/**
 * Draws the rotating compass rose: tick marks every 15°, cardinal/intercardinal labels, and a
 * north/south needle, all rotated together so North always points at true/magnetic north. A
 * fixed triangle at the top marks the direction the top edge of the phone is pointing.
 */
@Composable
private fun CompassDial(headingDegrees: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val needleNorthColor = MaterialTheme.colorScheme.primary
    val needleSouthColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val fixedMarkerColor = MaterialTheme.colorScheme.secondary
    val labelStyle = remember(onSurfaceColor) { TextStyle(color = onSurfaceColor, fontSize = 16.sp) }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f * 0.88f
        val dialCenter = center

        fun pointOnCircle(compassDegrees: Float, r: Float): Offset {
            val angleRad = Math.toRadians((compassDegrees - 90).toDouble())
            return Offset(
                x = dialCenter.x + r * cos(angleRad).toFloat(),
                y = dialCenter.y + r * sin(angleRad).toFloat()
            )
        }

        rotate(degrees = -headingDegrees, pivot = dialCenter) {
            drawCircle(color = ringColor, radius = radius, center = dialCenter, style = Stroke(width = 2.dp.toPx()))

            for (deg in 0 until 360 step 15) {
                val isCardinal = deg % 90 == 0
                val isIntercardinal = deg % 45 == 0
                val tickLength = when {
                    isCardinal -> 20.dp.toPx()
                    isIntercardinal -> 14.dp.toPx()
                    else -> 8.dp.toPx()
                }
                val outer = pointOnCircle(deg.toFloat(), radius)
                val inner = pointOnCircle(deg.toFloat(), radius - tickLength)
                drawLine(
                    color = if (isCardinal) onSurfaceColor else ringColor,
                    start = outer,
                    end = inner,
                    strokeWidth = if (isCardinal) 3.dp.toPx() else 1.5.dp.toPx()
                )
            }

            val labelRadius = radius - 36.dp.toPx()
            listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (deg, label) ->
                val layout = textMeasurer.measure(label, style = labelStyle.copy(color = if (label == "N") needleNorthColor else onSurfaceColor))
                val position = pointOnCircle(deg, labelRadius)
                drawText(
                    layout,
                    topLeft = Offset(position.x - layout.size.width / 2f, position.y - layout.size.height / 2f)
                )
            }

            // North/south needle through the center.
            val needleWidth = 10.dp.toPx()
            val northTip = pointOnCircle(0f, radius - 30.dp.toPx())
            val southTip = pointOnCircle(180f, radius - 30.dp.toPx())
            val leftBase = pointOnCircle(270f, needleWidth)
            val rightBase = pointOnCircle(90f, needleWidth)

            drawPath(
                path = Path().apply {
                    moveTo(northTip.x, northTip.y)
                    lineTo(leftBase.x, leftBase.y)
                    lineTo(rightBase.x, rightBase.y)
                    close()
                },
                color = needleNorthColor
            )
            drawPath(
                path = Path().apply {
                    moveTo(southTip.x, southTip.y)
                    lineTo(leftBase.x, leftBase.y)
                    lineTo(rightBase.x, rightBase.y)
                    close()
                },
                color = needleSouthColor
            )
            drawCircle(color = onSurfaceColor, radius = 5.dp.toPx(), center = dialCenter)
        }

        // Fixed heading-reference marker, drawn outside the rotating block.
        val markerTip = Offset(dialCenter.x, dialCenter.y - radius - 4.dp.toPx())
        val markerLeft = Offset(dialCenter.x - 8.dp.toPx(), dialCenter.y - radius - 20.dp.toPx())
        val markerRight = Offset(dialCenter.x + 8.dp.toPx(), dialCenter.y - radius - 20.dp.toPx())
        drawPath(
            path = Path().apply {
                moveTo(markerTip.x, markerTip.y)
                lineTo(markerLeft.x, markerLeft.y)
                lineTo(markerRight.x, markerRight.y)
                close()
            },
            color = fixedMarkerColor
        )
    }
}
