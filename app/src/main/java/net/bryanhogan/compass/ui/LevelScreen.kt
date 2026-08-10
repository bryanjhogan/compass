package net.bryanhogan.compass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import net.bryanhogan.compass.sensor.CompassState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val LEVEL_THRESHOLD_DEGREES = 1f
private const val MAX_BUBBLE_ANGLE_DEGREES = 45f

@Composable
fun LevelScreen(compassState: CompassState, modifier: Modifier = Modifier) {
    val pitch = compassState.pitchDegrees
    val roll = compassState.rollDegrees
    val tiltMagnitude = sqrt(pitch * pitch + roll * roll)
    val isLevel = abs(pitch) < LEVEL_THRESHOLD_DEGREES && abs(roll) < LEVEL_THRESHOLD_DEGREES

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BubbleLevel(
            pitchDegrees = pitch,
            rollDegrees = roll,
            isLevel = isLevel,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.85f)
        )

        Text(
            text = if (isLevel) "LEVEL" else "${tiltMagnitude.roundToInt()}° off level",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isLevel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground
        )

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValueRow("Pitch (front/back tilt)", "${pitch.roundToInt()}°")
                LabeledValueRow("Roll (left/right tilt)", "${roll.roundToInt()}°")
            }
        }
    }
}

@Composable
private fun LabeledValueRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BubbleLevel(
    pitchDegrees: Float,
    rollDegrees: Float,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val targetColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val bubbleColor = if (isLevel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f * 0.9f
        val dialCenter = center
        val targetRadius = 16.dp.toPx()
        val bubbleRadius = 20.dp.toPx()
        val maxTravel = radius - bubbleRadius

        drawCircle(color = ringColor, radius = radius, center = dialCenter, style = Stroke(width = 2.dp.toPx()))
        drawLine(ringColor, Offset(dialCenter.x - radius, dialCenter.y), Offset(dialCenter.x + radius, dialCenter.y), strokeWidth = 1.dp.toPx())
        drawLine(ringColor, Offset(dialCenter.x, dialCenter.y - radius), Offset(dialCenter.x, dialCenter.y + radius), strokeWidth = 1.dp.toPx())
        drawCircle(color = targetColor, radius = targetRadius, center = dialCenter, style = Stroke(width = 2.dp.toPx()))

        val rollFraction = (rollDegrees / MAX_BUBBLE_ANGLE_DEGREES).coerceIn(-1f, 1f)
        val pitchFraction = (pitchDegrees / MAX_BUBBLE_ANGLE_DEGREES).coerceIn(-1f, 1f)
        val bubbleCenter = Offset(
            x = dialCenter.x + sin(Math.toRadians(rollFraction * 90.0)).toFloat() * maxTravel,
            y = dialCenter.y + sin(Math.toRadians(pitchFraction * 90.0)).toFloat() * maxTravel
        )

        drawCircle(color = bubbleColor.copy(alpha = 0.25f), radius = bubbleRadius * 1.4f, center = bubbleCenter)
        drawCircle(color = bubbleColor, radius = bubbleRadius, center = bubbleCenter)
    }
}
