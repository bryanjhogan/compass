package net.bryanhogan.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.bryanhogan.compass.ui.CompassScreen
import net.bryanhogan.compass.ui.LevelScreen
import net.bryanhogan.compass.ui.MapScreen
import net.bryanhogan.compass.ui.theme.CompassTheme
import java.io.File
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: CompassViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger(this)
        super.onCreate(savedInstanceState)
        val crashLog = consumeCrashLog(this)
        setContent {
            CompassTheme {
                CompassApp(viewModel, crashLog)
            }
        }
    }
}

private const val CRASH_LOG_FILE_NAME = "crash_log.txt"

/**
 * There's no adb available when sideloading this app, so on an uncaught
 * exception we write the stack trace to a file and show it on the next
 * launch instead of relying on `adb logcat`.
 */
private fun installCrashLogger(context: Context) {
    val appContext = context.applicationContext
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            File(appContext.filesDir, CRASH_LOG_FILE_NAME).writeText(
                "Crashed at ${Date()}\n\n${Log.getStackTraceString(throwable)}"
            )
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}

private fun consumeCrashLog(context: Context): String? {
    val file = File(context.filesDir, CRASH_LOG_FILE_NAME)
    if (!file.exists()) return null
    val text = file.readText()
    file.delete()
    return text
}

private sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    object Compass : Destination("compass", "Compass", Icons.Filled.Explore)
    object Level : Destination("level", "Level", Icons.Filled.Straighten)
    object Map : Destination("map", "Map", Icons.Filled.Map)
}

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CompassApp(viewModel: CompassViewModel, crashLog: String? = null) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var visibleCrashLog by remember { mutableStateOf(crashLog) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results.values.any { it }
        if (hasLocationPermission) viewModel.onResume(true)
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(locationPermissions)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume(hasLocationPermission)
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val compassState by viewModel.compassState.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val useGpsBearing by viewModel.useGpsBearing.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val destinations = listOf(Destination.Compass, Destination.Level, Destination.Map)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Compass.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Compass.route) {
                CompassScreen(
                    compassState = compassState,
                    locationState = locationState,
                    useGpsBearing = useGpsBearing,
                    onUseGpsBearingChange = viewModel::setUseGpsBearing
                )
            }
            composable(Destination.Level.route) {
                LevelScreen(compassState)
            }
            composable(Destination.Map.route) {
                MapScreen(
                    locationState = locationState,
                    hasLocationPermission = hasLocationPermission,
                    onRequestPermission = { permissionLauncher.launch(locationPermissions) }
                )
            }
        }
    }

    visibleCrashLog?.let { log ->
        CrashLogDialog(log, onDismiss = { visibleCrashLog = null })
    }
}

@Composable
private fun CrashLogDialog(log: String, onDismiss: () -> Unit) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The app crashed last time") },
        text = {
            SelectionContainer {
                Text(
                    text = log,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(log))
                onDismiss()
            }) {
                Text("Copy & dismiss")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}
