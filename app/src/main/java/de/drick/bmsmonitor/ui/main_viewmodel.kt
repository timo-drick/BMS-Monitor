package de.drick.bmsmonitor.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.drick.bmsmonitor.BackgroundRecordingService
import de.drick.bmsmonitor.bluetooth.KBluetoothLeScanner
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.repository.BmsRepository
import de.drick.bmsmonitor.repository.DeviceInfoData
import de.drick.bmsmonitor.repository.RecordingInfo
import de.drick.bmsmonitor.repository.RecordingRepository
import de.drick.log
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screens {
    data class Main(
        val markedDevices: SnapshotStateList<UIDeviceItem>
    ): Screens
    data object Permission: Screens
    data object Scanner: Screens
    data class BmsDetail(val deviceAddress: String): Screens
    data object Recordings: Screens
    data object Finish: Screens
}

sealed interface MainUIAction {
    data object GoBack: MainUIAction
    data object ShowScan: MainUIAction
    data object StartMarkedScan: MainUIAction
    data object StopMarkedScan: MainUIAction
    data object ShowRecordings: MainUIAction
    data class ShowDetails(val deviceAddress: String): MainUIAction
    data class MarkDevice(val macAddress: String, val deviceInfo: GeneralDeviceInfo): MainUIAction
    data class UnMarkDevice(val macAddress: String): MainUIAction
    data class ToggleRecording(val macAddress: String, val mode: BatteryViewMode): MainUIAction
}

class MainViewModel(private val ctx: Application): AndroidViewModel(ctx) {
    private val bmsRepository = BmsRepository(ctx)
    private val recordingRepository = RecordingRepository(ctx)

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

    private val _markedDevices = mutableStateListOf<UIDeviceItem>()
    val markedDevices: SnapshotStateList<UIDeviceItem> = _markedDevices

    var currentScreen: Screens by mutableStateOf(Screens.Main(markedDevices))
        private set

    var isRecording = BackgroundRecordingService.isRunningFlow

    //private var bmsProbeJob: Job? = null
    var recordings by mutableStateOf(persistentListOf<RecordingInfo>())

    init {
        updateMarkedDevices()
        updateRecordings()
    }

    private fun startScanning() {
        log("Start scanning")
        composeBluetoothLeScanner.start(markedDevices.map { it.item.macAddress }.toPersistentList())
    }

    private fun stopScanning() {
        log("stop scanning")
        composeBluetoothLeScanner.stop()
    }

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
            is MainUIAction.ShowRecordings -> {
                currentScreen = Screens.Recordings
                updateRecordings()
            }
            is MainUIAction.MarkDevice -> addMarkedDevice(action.macAddress, action.deviceInfo)
            is MainUIAction.UnMarkDevice -> removeMarkedDevice(action.macAddress)
            MainUIAction.StartMarkedScan -> startScanning()
            MainUIAction.StopMarkedScan -> stopScanning()
            is MainUIAction.ToggleRecording -> toggleRecording(action.macAddress, action.mode)
        }
    }

    private fun updateRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedRecordingList = recordingRepository.listRecordings()
            log("recordings:")
            updatedRecordingList.forEach { log("   ${it.name} -> ${it.file}") }
            withContext(Dispatchers.Main) {
                recordings = updatedRecordingList.toPersistentList()
            }
        }
    }

    private fun toggleRecording(deviceAddress: String, mode: BatteryViewMode) {
        log("Toggle recording: $deviceAddress")
        if (BackgroundRecordingService.isRunningFlow.value) {
            BackgroundRecordingService.stop(ctx, deviceAddress)
        } else {
            BackgroundRecordingService.start(ctx, deviceAddress, mode == BatteryViewMode.Vehicle)
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
        val newList = bmsRepository.getAll().map {
            UIDeviceItem(it, null)
        }
        _markedDevices.clear()
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
            Screens.Recordings -> {
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
