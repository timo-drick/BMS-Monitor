package de.drick.bmsmonitor.ui

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bms_adapter.DeviceMacPrefix
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.checkPermission
import de.drick.compose.permission.rememberBluetoothState
import de.drick.compose.permission.rememberPermissionState
import de.drick.log
import java.util.UUID


@Preview(showBackground = true)
@Composable
private fun PreviewBluetoothScanner() {
    val mockList = listOf(
        BTDeviceInfo("Test 1", "58:cb:52:a5:00:ff", 5),
        BTDeviceInfo(name="-", address="F2:46:D2:22:E8:74", rssi=-93),
        BTDeviceInfo(name="-", address="37:7D:01:AF:98:36", rssi=-96),
        BTDeviceInfo(name="-", address="7F:F8:81:AC:37:6E", rssi=-94),
        BTDeviceInfo(name="-", address="E3:BC:14:DC:48:41", rssi=-94),
        BTDeviceInfo(name="-", address="4A:B4:5C:B7:D2:38", rssi=-65),
        BTDeviceInfo(name="-", address="7C:64:56:95:A7:0D", rssi=-91),
        BTDeviceInfo(name="-", address="4E:AD:EA:38:42:19", rssi=-96),
    )
    BluetoothLEScannerView(
        scanResultList = mockList,
        onDeviceSelected = {}
    )
}

data class BTDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int
)

@Composable
fun BluetoothLEScannerScreen(
    modifier: Modifier = Modifier,
    onDeviceSelected: (deviceAddress: String) -> Unit
) {
    val scanResults = bluetoothLeScannerEffect()
    BluetoothLEScannerView(
        modifier = modifier,
        scanResultList = scanResults,
        onDeviceSelected = onDeviceSelected
    )
}

@Composable
fun BluetoothLEScannerView(
    modifier: Modifier = Modifier,
    scanResultList: List<BTDeviceInfo>,
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

val BMS_SERVICE_UUID = checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))


@Composable
fun bluetoothLeScannerEffect(): List<BTDeviceInfo> {
    val bluetoothState = rememberBluetoothState()
    val scanPermission = rememberPermissionState(ManifestPermission.BLUETOOTH_SCAN)
    val ctx = LocalContext.current
    val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val scanResults = remember {
        mutableStateListOf<BTDeviceInfo>()
    }
    LifecycleResumeEffect(bluetoothState.isEnabled, scanPermission.hasPermission) {
        val bluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                val bmsType = DeviceMacPrefix.entries.find { address.startsWith(it.prefix) }
                log("type: $callbackType, bmsType: $bmsType result: $result")
                if (bmsType == null) return
                val name = if (ManifestPermission.BLUETOOTH_CONNECT.checkPermission(ctx))
                    result.device.name ?: "-"
                else
                    address
                val newEntry = BTDeviceInfo(name, address, result.rssi)
                val index = scanResults.indexOfFirst { it.address == newEntry.address }
                if (index < 0) {
                    scanResults.add(newEntry)
                } else {
                    scanResults[index] = newEntry
                }
                log(newEntry)
                scanResults.sortByDescending { it.rssi }
            }
        }
        if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
            log("Start scanning")
            val filter1 = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BMS_SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .build()
            //bluetoothLeScanner.startScan(listOf(filter1), settings, scanCallback)
            bluetoothLeScanner.startScan(scanCallback)
        }
        onPauseOrDispose {
            if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
                log("Stop scanning")
                bluetoothLeScanner.stopScan(scanCallback)
            }
        }
    }
    return scanResults
}