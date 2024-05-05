package de.drick.bmsmonitor.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapbox.common.MapboxOptions
import de.drick.bmsmonitor.BuildConfig
import de.drick.bmsmonitor.ui.recordings.RecordingsScreen
import de.drick.bmsmonitor.ui.theme.BMSMonitorTheme
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.rememberBluetoothState
import de.drick.compose.permission.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapboxOptions.accessToken = BuildConfig.MAPBOX_TOKEN
        setContent {
            BMSMonitorTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(onFinish = { finish() })
                }
            }
        }
    }
}

@Composable
fun PermissionView(
    isBluetoothEnabled: Boolean,
    hasScanPermission: Boolean,
    hasConnectPermission: Boolean,
    hasLocationPermission: Boolean,
    onEnableBluetooth: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(text = "Hello World!")
        Text("Bluetooth is enabled: $isBluetoothEnabled")
        when {
            isBluetoothEnabled.not() -> {
                Button(onClick = onEnableBluetooth) {
                    Text("Enable bluetooth")
                }
            }
            hasScanPermission.not() -> {
                Button(onClick = onRequestPermissions) {
                    Text("Allow scanning for bluetooth devices")
                }
            }
            hasConnectPermission.not() -> {
                Button(onClick = onRequestPermissions) {
                    Text("Allow connecting bluetooth devices")
                }
            }
            hasLocationPermission.not() -> {
                Button(onClick = onRequestPermissions) {
                    Text("Allow fine location permission to record location")
                }
            }
            else -> {
                Text("All permissions granted :-D")
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel(),
    onFinish: () -> Unit
) {
    val bluetoothState = rememberBluetoothState()

    val scanPermission = if (Build.VERSION.SDK_INT >=31)
        rememberPermissionState(ManifestPermission.BLUETOOTH_SCAN)
    else
        rememberPermissionState(ManifestPermission.ACCESS_FINE_LOCATION)

    val connectPermission = rememberPermissionState(ManifestPermission.BLUETOOTH_CONNECT)

    val locationPermission = rememberPermissionState(ManifestPermission.ACCESS_FINE_LOCATION)
    //TODO make sure gps is turned on

    LaunchedEffect(
        bluetoothState.isEnabled,
        scanPermission.hasPermission,
        connectPermission.hasPermission,
        locationPermission.hasPermission
    ) {
        if (bluetoothState.isEnabled.not() ||
            scanPermission.hasPermission.not() ||
            connectPermission.hasPermission.not() ||
            locationPermission.hasPermission.not()
            ) {
            vm.requestPermissions()
        } else {
            vm.allPermissionsGranted()
        }
    }

    BackHandler {
        vm.back()
    }
    AnimatedContent(
        modifier = modifier,
        targetState = vm.currentScreen,
        label = "Main screen switch animation"
    ) { screen ->
        when (screen) {
            Screens.Finish -> {
                onFinish()
            }
            is Screens.Main -> MainView(
                markedDevices = screen.markedDevices,
                onAction = { vm.action(it) },
            )
            Screens.Permission -> PermissionView(
                isBluetoothEnabled = bluetoothState.isEnabled,
                hasScanPermission = scanPermission.hasPermission,
                hasConnectPermission = connectPermission.hasPermission,
                hasLocationPermission = locationPermission.hasPermission,
                onEnableBluetooth = { bluetoothState.launchEnableBluetoothRequest() },
                onRequestPermissions = {
                    scanPermission.launchPermissionRequest()
                    connectPermission.launchPermissionRequest()
                    locationPermission.launchPermissionRequest()
                },
            )
            Screens.Scanner -> BluetoothLEScannerScreen(
                onDeviceSelected = { vm.action(MainUIAction.ShowDetails(it)) }
            )
            is Screens.BmsDetail -> {
                val deviceAddress = screen.deviceAddress
                val isDeviceMarked by remember {
                    derivedStateOf {
                        vm.markedDevices.firstOrNull { it.item.macAddress == deviceAddress } != null
                    }
                }
                val isRecording by vm.isRecording.collectAsState()

                BatteryDetailScreen(
                    deviceAddress = deviceAddress,
                    isMarked = isDeviceMarked,
                    isRecording = isRecording,
                    onAction = { vm.action(it) }
                )
            }
            Screens.Recordings -> RecordingsScreen(
                recordings = vm.recordings,
                onBack = { vm.action(MainUIAction.GoBack) }
            )
        }
    }
}
