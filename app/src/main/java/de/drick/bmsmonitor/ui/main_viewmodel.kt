package de.drick.bmsmonitor.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.drick.bmsmonitor.bluetooth.KBluetoothLeScanner
import de.drick.bmsmonitor.bms_adapter.BmsAdapter
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.repository.BmsRepository
import de.drick.bmsmonitor.repository.DeviceInfoData
import de.drick.log
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screens {
    data class Main(
        val markedDevices: SnapshotStateList<UIDeviceItem>
    ): Screens
    data object Permission: Screens
    data object Scanner: Screens
    data class BmsDetail(val deviceAddress: String): Screens
    data object Finish: Screens
}

sealed interface MainUIAction {
    data object GoBack: MainUIAction
    data object ShowScan: MainUIAction
    data class ShowDetails(val deviceAddress: String): MainUIAction
    data class MarkDevice(val macAddress: String, val deviceInfo: GeneralDeviceInfo): MainUIAction
    data class UnMarkDevice(val macAddress: String): MainUIAction
    data object StartMarkedScan: MainUIAction
    data object StopMarkedScan: MainUIAction
}

class BmsProbeService(private val ctx: Context) {
    suspend fun probe(macAddressList: List<String>): List<Pair<String, GeneralCellInfo>> =
        coroutineScope {
            macAddressList.mapNotNull { macAddress ->
                val bmsAdapter = BmsAdapter(ctx, macAddress)
                log("Connect: $macAddress")
                bmsAdapter.connect()
                log("Connected")
                launch {
                    bmsAdapter.start()
                }
                log("Started")
                //TODO timeout
                log("Wait for first cell info")
                val cellInfo = bmsAdapter.cellInfoState.filterNotNull().first()
                log("cell info: $cellInfo")
                bmsAdapter.stop()
                bmsAdapter.disconnect()
                if (cellInfo != null) {
                    Pair(macAddress, cellInfo)
                } else {
                    null
                }
            }
        }
}

class MainViewModel(ctx: Application): AndroidViewModel(ctx) {
    private val bmsRepository = BmsRepository(ctx)
    private val liveData = false

    private val composeBluetoothLeScanner = KBluetoothLeScanner(ctx, onResult = { result ->
        _markedDevices.indexOfFirst { it.item.macAddress == result.address }.let { index ->
            if (index >= 0) {
                val item = _markedDevices[index]
                log("Scan: $item")
                _markedDevices[index] = item.copy(btDeviceInfo = result)
            }
        }
    })

    val bmsProbeService = BmsProbeService(ctx)

    private val _markedDevices = mutableStateListOf<UIDeviceItem>()
    val markedDevices: SnapshotStateList<UIDeviceItem> = _markedDevices

    var currentScreen: Screens by mutableStateOf(Screens.Main(markedDevices))
        private set

    private var bmsProbeJob: Job? = null

    init {
        updateMarkedDevices()
    }

    private fun startScanning() {
        log("Start scanning")
        composeBluetoothLeScanner.start(markedDevices.map { it.item.macAddress }.toPersistentList())
        bmsProbeJob?.cancel()
        if (liveData) {
            bmsProbeJob = viewModelScope.launch {
                while (isActive) {
                    val devices = markedDevices.mapNotNull { it.btDeviceInfo?.address }
                    log("probe all scanned bmses: ${devices.joinToString()}")
                    val cellInfoList = bmsProbeService.probe(devices)
                    log("probe finished")
                    cellInfoList.forEach { (macAddress, cellInfo) ->
                        _markedDevices.indexOfFirst { it.item.macAddress == macAddress }
                            .let { index ->
                                val item = _markedDevices[index]
                                log("insert cell info")
                                _markedDevices[index] = item.copy(cellInfo = cellInfo)
                            }
                    }
                    delay(5000)
                }
            }
        }
    }

    private fun stopScanning() {
        log("stop scanning")
        composeBluetoothLeScanner.stop()
        bmsProbeJob?.cancel()
        /*viewModelScope.launch {
            testConnection.stop()
            testConnection.disconnect()
        }*/
    }

    fun isDeviceMarked(macAddress: String) = markedDevices.find { it.item.macAddress == macAddress  } != null

    fun requestPermissions() {
        currentScreen = Screens.Permission
    }
    fun allPermissionsGranted() {
        if (currentScreen == Screens.Permission) {
            currentScreen = Screens.Main(markedDevices)
        }
    }

    fun action(action: MainUIAction) {
        when (action) {
            MainUIAction.ShowScan -> {
                currentScreen = Screens.Scanner
            }
            MainUIAction.GoBack -> back()
            is MainUIAction.ShowDetails -> {
                log("Add device ${action.deviceAddress}")
                currentScreen = Screens.BmsDetail(action.deviceAddress)
            }

            is MainUIAction.MarkDevice -> addMarkedDevice(action.macAddress, action.deviceInfo)
            is MainUIAction.UnMarkDevice -> removeMarkedDevice(action.macAddress)
            MainUIAction.StartMarkedScan -> startScanning()
            MainUIAction.StopMarkedScan -> stopScanning()
        }
    }

    private fun removeMarkedDevice(macAddress: String) {
        bmsRepository.removeDevice(macAddress)
        updateMarkedDevices()
    }
    private fun addMarkedDevice(
        macAddress: String,
        generalDeviceInfo: GeneralDeviceInfo
    ) {
        val data = DeviceInfoData(name = generalDeviceInfo.name, macAddress)
        bmsRepository.addDevice(data)
        updateMarkedDevices()
    }
    private fun updateMarkedDevices() {
        _markedDevices.clear()
        val newList = bmsRepository.getAll().map {
            UIDeviceItem(it, null)
        }
        _markedDevices.addAll(newList)
    }

    private fun showMainScreen() {
        currentScreen = Screens.Main(markedDevices)
    }

    fun back() {
        when (currentScreen) {
            is Screens.BmsDetail -> {
                showMainScreen()
            }
            Screens.Permission -> {
                showMainScreen()
            }
            Screens.Scanner -> {
                showMainScreen()
            }
            else -> {
                currentScreen = Screens.Finish
            }
        }
    }

    override fun onCleared() {

    }
}
