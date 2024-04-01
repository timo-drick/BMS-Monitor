package de.drick.bmsmonitor.ui

import android.app.Application
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.drick.bmsmonitor.ui.theme.BMSMonitorTheme
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.rememberBluetoothState
import de.drick.compose.permission.rememberPermissionState
import de.drick.log


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BMSMonitorTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

sealed interface Screens {
    data object Main: Screens
    data object Permission: Screens
    data object Scanner: Screens
    data class BmsDetail(val deviceAddress: String): Screens
}

class MainViewModel(ctx: Application): AndroidViewModel(ctx) {
    var currentScreen: Screens by mutableStateOf(Screens.Main)
        private set

    var backHandlerEnabled by mutableStateOf(true)
        private set

    fun requestPermissions() {
        currentScreen = Screens.Permission
    }
    fun allPermissionsGranted() {
        currentScreen = Screens.Main
    }

    fun scanForDevices() {
        currentScreen = Screens.Scanner
    }

    fun addDevice(deviceAddress: String) {
        log("Add device $deviceAddress")
        currentScreen = Screens.BmsDetail(deviceAddress)
    }

    fun back() {
        when (currentScreen) {
            is Screens.BmsDetail -> {
                currentScreen = Screens.Scanner
            }
            Screens.Main -> {
                log("Nothing we can do here!")
            }
            Screens.Permission -> {
                currentScreen = Screens.Main
            }
            Screens.Scanner -> {
                currentScreen = Screens.Main
            }
        }
        backHandlerEnabled = currentScreen != Screens.Main
    }

    override fun onCleared() {

    }
}

@Composable
fun PermissionView(
    isBluetoothEnabled: Boolean,
    hasScanPermission: Boolean,
    hasConnectPermission: Boolean,
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
            else -> {
                Text("All permissions granted :-D")
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel()
) {
    val bluetoothState = rememberBluetoothState()

    val scanPermission = if (Build.VERSION.SDK_INT >=31)
        rememberPermissionState(ManifestPermission.BLUETOOTH_SCAN)
    else
        rememberPermissionState(ManifestPermission.ACCESS_FINE_LOCATION)
    val connectPermission = rememberPermissionState(ManifestPermission.BLUETOOTH_CONNECT)


    LaunchedEffect(bluetoothState.isEnabled, scanPermission.hasPermission, connectPermission.hasPermission) {
        if (bluetoothState.isEnabled.not() || scanPermission.hasPermission.not() || connectPermission.hasPermission.not()) {
            vm.requestPermissions()
        } /*else {
            vm.allPermissionsGranted()
        }*/
    }

    BackHandler(enabled = vm.backHandlerEnabled) {
        vm.back()
    }
    AnimatedContent(
        modifier = modifier,
        targetState = vm.currentScreen,
        label = "Main screen switch animation"
    ) { screen ->
        when (screen) {
            Screens.Main -> MainView(
                onAddDevice = { vm.scanForDevices() }
            )
            Screens.Permission -> PermissionView(
                isBluetoothEnabled = bluetoothState.isEnabled,
                hasScanPermission = scanPermission.hasPermission,
                hasConnectPermission = connectPermission.hasPermission,
                onEnableBluetooth = { bluetoothState.launchEnableBluetoothRequest() },
                onRequestPermissions = {
                    scanPermission.launchPermissionRequest()
                    connectPermission.launchPermissionRequest()
                },
            )
            Screens.Scanner -> BluetoothLEScannerScreen(
                onDeviceSelected = { vm.addDevice(it) }
            )
            is Screens.BmsDetail -> BatteryDetailScreen(
                deviceAddress = screen.deviceAddress
            )
        }
    }
}

@Preview
@Composable
fun PreviewMainView() {
    BMSMonitorTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
            /*MainView(
                isBluetoothEnabled = true,
                hasScanPermission = true,
                onEnableBluetooth = { },
                onRequestPermission = { })*/
        }
    }
}

@Composable
fun MainView(
    onAddDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(text = "Hello World!")
        Button(onClick = onAddDevice) {
            Text("Add new device")
        }
    }
}


/*@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BMSMonitorTheme {
        MainView(
            isBluetoothEnabled = true,
            hasScanPermission = false,
            onEnableBluetooth = {},
            onRequestPermission = {}
        )
    }
}*/