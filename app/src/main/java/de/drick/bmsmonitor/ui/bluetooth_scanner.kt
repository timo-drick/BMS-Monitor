package de.drick.bmsmonitor.ui

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bluetooth.BTDeviceInfo
import de.drick.bmsmonitor.bluetooth.BluetoothLeScanner
import de.drick.bmsmonitor.bms_adapter.BmsAdapter
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.checkPermission
import de.drick.compose.permission.rememberBluetoothState
import de.drick.compose.permission.rememberPermissionState
import de.drick.log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import java.util.UUID


@Preview(showBackground = true)
@Composable
private fun PreviewBluetoothScanner() {
    val mockList = persistentListOf(
        BTDeviceInfo("Test 1", "58:cb:52:a5:00:ff", 5, 0),
        BTDeviceInfo(name="-", address="F2:46:D2:22:E8:74", rssi=-93, 0),
        BTDeviceInfo(name="-", address="37:7D:01:AF:98:36", rssi=-96, 0),
        BTDeviceInfo(name="-", address="7F:F8:81:AC:37:6E", rssi=-94, 0),
        BTDeviceInfo(name="-", address="E3:BC:14:DC:48:41", rssi=-94, 0),
        BTDeviceInfo(name="-", address="4A:B4:5C:B7:D2:38", rssi=-65, 0),
        BTDeviceInfo(name="-", address="7C:64:56:95:A7:0D", rssi=-91, 0),
        BTDeviceInfo(name="-", address="4E:AD:EA:38:42:19", rssi=-96, 0),
    ).toMutableStateList()
    BluetoothLEScannerView(
        scanResultList = mockList,
        onDeviceSelected = {}
    )
}

@Composable
fun BluetoothLEScannerScreen(
    modifier: Modifier = Modifier,
    onDeviceSelected: (deviceAddress: String) -> Unit
) {
    val scanResults = bluetoothLeScannerServiceEffect(BmsAdapter.BMS_SERVICE_UUIDs)
    BluetoothLEScannerView(
        modifier = modifier,
        scanResultList = scanResults,
        onDeviceSelected = onDeviceSelected
    )
}

@Composable
fun BluetoothLEScannerView(
    scanResultList: SnapshotStateList<BTDeviceInfo>,
    modifier: Modifier = Modifier,
    onDeviceSelected: (deviceAddress: String) -> Unit
) {
    Column(modifier) {
        Text("Scanning...")
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(scanResultList) { deviceInfo ->
                BluetoothDeviceInfoView(
                    modifier = Modifier,
                    deviceInfo = deviceInfo,
                    onClick = { onDeviceSelected(deviceInfo.address) }
                )
            }
        }
    }
}

@Composable
fun BluetoothDeviceInfoView(
    deviceInfo: BTDeviceInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
        text = "${deviceInfo.name} (${deviceInfo.address}) rssi: ${deviceInfo.rssi}"
    )
}

@Composable
fun bluetoothLeScannerEffect() : SnapshotStateList<BTDeviceInfo> {
    val settings = ScanSettings.Builder().build()
    val filterList = persistentListOf<ScanFilter>()
    return bluetoothLeScannerEffect(filterList, settings)
}

@Composable
fun bluetoothLeScannerMacEffect(
    macAddressList: ImmutableSet<String>
) : SnapshotStateList<BTDeviceInfo> {
    val filterList = macAddressList.map {
        ScanFilter.Builder().apply {
            setDeviceAddress(it)
        }.build()
    }.toPersistentList()
    val settings = ScanSettings.Builder()
        .build()
    return bluetoothLeScannerEffect(filterList, settings)
}

@Composable
fun bluetoothLeScannerServiceEffect(
    serviceFilter: ImmutableSet<UUID>
) : SnapshotStateList<BTDeviceInfo> {
    val settings = ScanSettings.Builder()
        .build()
    val filter = ScanFilter.Builder().apply {
        serviceFilter.forEach {
            setServiceUuid(ParcelUuid(it))
        }
    }.build()
    return bluetoothLeScannerEffect(persistentListOf(filter), settings)
}

@Composable
fun bluetoothLeScannerEffect(
    filterList: ImmutableList<ScanFilter>, settings: ScanSettings
): SnapshotStateList<BTDeviceInfo> {
    val bluetoothState = rememberBluetoothState()
    val scanPermission = rememberPermissionState(ManifestPermission.BLUETOOTH_SCAN)
    val ctx = LocalContext.current
    val scanner = remember {
        BluetoothLeScanner(ctx)
    }
    LifecycleResumeEffect(bluetoothState.isEnabled, scanPermission.hasPermission) {
        if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
            log("Start scanning")
            scanner.start(filterList, settings)
        }
        onPauseOrDispose {
            if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
                log("Stop scanning")
                scanner.stop()
            }
        }
    }
    return scanner.scanResults
}