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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.repository.BmsRepository
import de.drick.bmsmonitor.repository.DeviceInfoData
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
    vm: MainViewModel = viewModel(),
    onFinish: () -> Unit
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
        } else {
            vm.allPermissionsGranted()
        }
    }

    BackHandler() {
        val handeled = vm.back()
        if (handeled.not()) {
            onFinish()
        }
    }
    AnimatedContent(
        modifier = modifier,
        targetState = vm.currentScreen,
        label = "Main screen switch animation"
    ) { screen ->
        when (screen) {
            Screens.Main -> MainView(
                markedDevices = vm.markedDevices,
                onAddDevice = { vm.scanForDevices() },
                onDeviceSelected = { vm.showDeviceDetails(it.macAddress) }
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
                onDeviceSelected = { vm.showDeviceDetails(it) }
            )
            is Screens.BmsDetail -> {
                val deviceAddress = screen.deviceAddress
                var isDeviceMarked by remember(deviceAddress) {
                    mutableStateOf(vm.isDeviceMarked(deviceAddress))
                }
                BatteryDetailScreen(
                    deviceAddress = deviceAddress,
                    isMarked = isDeviceMarked,
                    onSave = {
                        vm.addMarkedDevice(deviceAddress, it)
                        isDeviceMarked = vm.isDeviceMarked(deviceAddress)
                             },
                    onDelete = {
                        vm.removeMarkedDevice(deviceAddress)
                        isDeviceMarked = vm.isDeviceMarked(deviceAddress)
                               },
                )
            }
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