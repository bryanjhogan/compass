package net.bryanhogan.compass.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bryanhogan.compass.location.LocationState
import net.bryanhogan.compass.map.OfflineDownloadState
import net.bryanhogan.compass.map.OfflineMapManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_ZOOM = 17.0
private const val MIN_SUPPORTED_ZOOM = 1
private const val MAX_SUPPORTED_ZOOM = 19
private const val MAX_REASONABLE_TILES = 4000
private const val BYTES_PER_TILE_ESTIMATE = 15_000L

@Composable
fun MapScreen(
    locationState: LocationState,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        configureOsmdroid(context)
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
        }
    }

    val marker = remember { Marker(mapView) }

    DisposableEffect(Unit) {
        mapView.overlays.add(marker)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(locationState.latitude, locationState.longitude) {
        val lat = locationState.latitude
        val lon = locationState.longitude
        if (lat != null && lon != null) {
            val point = GeoPoint(lat, lon)
            marker.position = point
            mapView.controller.animateTo(point)
        }
    }

    val offlineMapManager = remember(mapView) { OfflineMapManager(mapView) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<OfflineDownloadState>(OfflineDownloadState.Idle) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(offlineMapManager) {
        onDispose { offlineMapManager.cancel() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (!hasLocationPermission) {
            PermissionPrompt(onRequestPermission, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        } else {
            CoordinatesOverlay(
                locationState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }

        FloatingActionButton(
            onClick = { showDownloadDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.DownloadForOffline, contentDescription = "Download map area for offline use")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (showDownloadDialog) {
        OfflineDownloadDialog(
            mapView = mapView,
            offlineMapManager = offlineMapManager,
            downloadState = downloadState,
            onDownloadStateChanged = { downloadState = it },
            onDismiss = {
                if (downloadState !is OfflineDownloadState.InProgress) {
                    showDownloadDialog = false
                    downloadState = OfflineDownloadState.Idle
                }
            },
            onFinished = { message ->
                showDownloadDialog = false
                downloadState = OfflineDownloadState.Idle
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}

@Composable
private fun OfflineDownloadDialog(
    mapView: MapView,
    offlineMapManager: OfflineMapManager,
    downloadState: OfflineDownloadState,
    onDownloadStateChanged: (OfflineDownloadState) -> Unit,
    onDismiss: () -> Unit,
    onFinished: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentZoom = mapView.zoomLevelDouble.roundToInt().coerceIn(MIN_SUPPORTED_ZOOM, MAX_SUPPORTED_ZOOM)
    var extraZoomLevels by remember { mutableStateOf(2f) }
    val minZoom = remember { (currentZoom - 1).coerceAtLeast(MIN_SUPPORTED_ZOOM) }
    val maxZoom = (currentZoom + extraZoomLevels.roundToInt()).coerceAtMost(MAX_SUPPORTED_ZOOM)
    val boundingBox = remember { mapView.boundingBox ?: mapView.projection.boundingBox }

    val estimatedTiles = remember(minZoom, maxZoom) {
        offlineMapManager.estimateTileCount(boundingBox, minZoom, maxZoom)
    }
    val estimatedMegabytes = (estimatedTiles * BYTES_PER_TILE_ESTIMATE) / (1024.0 * 1024.0)
    val cacheUsedMegabytes = remember { offlineMapManager.cacheUsageBytes() / (1024.0 * 1024.0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save map area for offline use") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val state = downloadState) {
                    is OfflineDownloadState.Idle -> {
                        Text(
                            "Downloads the currently visible map area so it's available without a network connection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Detail level: +${extraZoomLevels.roundToInt()} zoom levels", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = extraZoomLevels,
                            onValueChange = { extraZoomLevels = it },
                            valueRange = 0f..4f,
                            steps = 3
                        )
                        val estimatedSeconds = estimatedTiles * OfflineMapManager.TILE_REQUEST_DELAY_MS / 1000
                        Text(
                            text = "Estimated: %d tiles (~%.1f MB, ~%d sec)".format(
                                Locale.US, estimatedTiles, estimatedMegabytes, estimatedSeconds
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Tiles are fetched one at a time, paced to be considerate of the free OpenStreetMap servers — larger areas take longer.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (estimatedTiles > MAX_REASONABLE_TILES) {
                            Text(
                                "That's a large area. Zoom in on the map or lower the detail level before downloading.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider()
                        Text(
                            "Offline storage used: %.1f MB".format(Locale.US, cacheUsedMegabytes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is OfflineDownloadState.InProgress -> {
                        val progress = if (state.totalTiles > 0) state.downloadedTiles.toFloat() / state.totalTiles else 0f
                        Text("Downloading tiles…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${state.downloadedTiles} / ${state.totalTiles} tiles", style = MaterialTheme.typography.bodySmall)
                    }
                    is OfflineDownloadState.Completed -> {
                        Text("Saved ${state.totalTiles} tiles for offline use.", style = MaterialTheme.typography.bodyMedium)
                    }
                    is OfflineDownloadState.Failed -> {
                        Text(
                            "Download finished with ${state.errorCount} failed tile(s). You may be offline or the map server is unreachable.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is OfflineDownloadState.Idle -> {
                    Button(
                        enabled = estimatedTiles in 1..MAX_REASONABLE_TILES,
                        onClick = {
                            offlineMapManager.download(
                                scope = coroutineScope,
                                tileSource = TileSourceFactory.MAPNIK,
                                boundingBox = boundingBox,
                                minZoom = minZoom,
                                maxZoom = maxZoom
                            ) { state ->
                                onDownloadStateChanged(state)
                                if (state is OfflineDownloadState.Completed) {
                                    onFinished("Saved ${state.totalTiles} tiles for offline use")
                                } else if (state is OfflineDownloadState.Failed) {
                                    onFinished("Offline download finished with ${state.errorCount} error(s)")
                                }
                            }
                        }
                    ) {
                        Text("Download")
                    }
                }
                is OfflineDownloadState.InProgress -> {
                    TextButton(onClick = {
                        offlineMapManager.cancel()
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Cancel download")
                    }
                }
                else -> {
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        },
        dismissButton = {
            if (downloadState is OfflineDownloadState.Idle) {
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        val cleared = withContext(Dispatchers.IO) { offlineMapManager.clearCache() }
                        if (cleared) {
                            onFinished("Cleared all offline map data")
                        }
                    }
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Clear offline data")
                }
            }
        }
    )
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
    config.userAgentValue = context.packageName
    // Use filesDir (not cacheDir) so downloaded offline map tiles aren't
    // silently evicted by the OS when it needs to reclaim cache space.
    config.osmdroidBasePath = File(context.filesDir, "osmdroid")
    config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Location permission is needed to show your position on the map.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("Grant location permission")
            }
        }
    }
}

@Composable
private fun CoordinatesOverlay(locationState: LocationState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!locationState.hasFix) {
                Text("Waiting for GPS fix…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = "%.5f, %.5f".format(locationState.latitude, locationState.longitude),
                    style = MaterialTheme.typography.titleMedium
                )
                locationState.altitudeMeters?.let {
                    Text("Altitude: ${it.roundToInt()} m", style = MaterialTheme.typography.bodyMedium)
                }
                locationState.accuracyMeters?.let {
                    Text("Accuracy: ±${it.roundToInt()} m", style = MaterialTheme.typography.bodyMedium)
                }
                locationState.address?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
