package de.drick.bmsmonitor.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import de.drick.bmsmonitor.bms_adapter.DeviceMacPrefix
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.checkPermission
import de.drick.log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import java.util.UUID
import java.util.concurrent.TimeUnit


data class BTDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val lastSeen: Long
)

class BluetoothLeScanner(private val ctx: Context) {
    private val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner

    companion object {
        private val MAX_LIFESPAN = TimeUnit.SECONDS.toMillis(30)
        private val UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5)

        private val internalScanResults = mutableStateListOf<BTDeviceInfo>()
        private var lastUpdated = 0L

        private fun add(newEntry: BTDeviceInfo) {
            val index = internalScanResults.indexOfFirst { it.address == newEntry.address }
            if (index < 0) {
                internalScanResults.add(newEntry)
            } else {
                internalScanResults[index] = newEntry
            }
            log(newEntry)
            val tsNow = System.currentTimeMillis()
            if (tsNow - lastUpdated > UPDATE_INTERVAL) {
                lastUpdated = tsNow
                // Clean up old entries
                internalScanResults.removeAll { tsNow - it.lastSeen > MAX_LIFESPAN }
                internalScanResults.sortByDescending { it.rssi }
            }
        }
    }

    val scanResults: SnapshotStateList<BTDeviceInfo> = internalScanResults

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val tsNow = System.currentTimeMillis()
            val address = result.device.address
            val bmsType = DeviceMacPrefix.entries.find { address.startsWith(it.prefix) }
            log("type: $callbackType, bmsType: $bmsType result: $result")
            if (bmsType == null) return
            val name = if (ManifestPermission.BLUETOOTH_CONNECT.checkPermission(ctx))
                result.device.name ?: "-"
            else
                address
            add(BTDeviceInfo(name, address, result.rssi, tsNow))
        }
    }

    fun start(macAddressList: ImmutableList<String>) {
        val filterList = macAddressList.map {
            ScanFilter.Builder().apply {
                setDeviceAddress(it)
            }.build()
        }.toPersistentList()
        val settings = ScanSettings.Builder()
            .build()
        start(filterList, settings)
    }

    fun start(serviceFilter: UUID) {
        val settings = ScanSettings.Builder()
            .build()
        val filter = ScanFilter.Builder().apply {
            setServiceUuid(ParcelUuid(serviceFilter))
        }.build()
        start(persistentListOf(filter), settings)
    }

    fun start(filterList: ImmutableList<ScanFilter>, settings: ScanSettings) {
        if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
            log("Start scanning")
            bluetoothLeScanner.startScan(filterList, settings, scanCallback)
        }
    }

    fun stop() {
        if (ManifestPermission.BLUETOOTH_SCAN.checkPermission(ctx)) {
            log("Stop scanning")
            bluetoothLeScanner.stopScan(scanCallback)
        }
    }
}