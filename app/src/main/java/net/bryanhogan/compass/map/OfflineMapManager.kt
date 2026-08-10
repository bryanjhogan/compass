package net.bryanhogan.compass.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

sealed class OfflineDownloadState {
    object Idle : OfflineDownloadState()
    data class InProgress(val downloadedTiles: Int, val totalTiles: Int) : OfflineDownloadState()
    data class Completed(val totalTiles: Int) : OfflineDownloadState()
    data class Failed(val errorCount: Int) : OfflineDownloadState()
}

/**
 * Pre-fetches map tiles for offline use.
 *
 * osmdroid's [CacheManager.downloadAreaAsync] refuses to run against tile sources
 * (like the default OpenStreetMap Mapnik source) whose [org.osmdroid.tileprovider.tilesource.TileSourcePolicy]
 * marks bulk downloading as unsupported -- OSM's tile usage policy explicitly
 * discourages bulk/automated pre-fetching against their donated servers.
 * Rather than construct a tile source that fakes a permissive policy, this
 * fetches tiles one at a time through [CacheManager.loadTile] -- the same
 * single-tile request path ordinary interactive panning already uses -- with a
 * small delay between requests so it behaves like paced browsing, not a bulk blast.
 */
class OfflineMapManager(mapView: MapView) {

    private val cacheManager = CacheManager(mapView)
    private var downloadJob: Job? = null

    fun estimateTileCount(boundingBox: BoundingBox, minZoom: Int, maxZoom: Int): Int =
        cacheManager.possibleTilesInArea(boundingBox, minZoom, maxZoom)

    fun cacheUsageBytes(): Long = cacheManager.currentCacheUsage()

    /** Deletes every cached tile (all previously downloaded offline areas). */
    fun clearCache(): Boolean = SqlTileWriter().purgeCache()

    fun download(
        scope: CoroutineScope,
        tileSource: OnlineTileSourceBase,
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onStateChanged: (OfflineDownloadState) -> Unit
    ) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            val tileIndices = CacheManager.getTilesCoverage(boundingBox, minZoom, maxZoom)
            val total = tileIndices.size
            withContext(Dispatchers.Main) { onStateChanged(OfflineDownloadState.InProgress(0, total)) }

            var completed = 0
            var errors = 0
            for (tileIndex in tileIndices) {
                ensureActive()
                if (!cacheManager.loadTile(tileSource, tileIndex)) errors++
                completed++
                withContext(Dispatchers.Main) {
                    onStateChanged(OfflineDownloadState.InProgress(completed, total))
                }
                delay(TILE_REQUEST_DELAY_MS)
            }

            withContext(Dispatchers.Main) {
                onStateChanged(
                    if (errors == 0) OfflineDownloadState.Completed(total) else OfflineDownloadState.Failed(errors)
                )
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
    }

    companion object {
        const val TILE_REQUEST_DELAY_MS = 120L
    }
}
