package de.drick.bmsmonitor.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.repository.BmsRepository
import de.drick.bmsmonitor.repository.DeviceInfoData
import de.drick.log

sealed interface Screens {
    data object Main: Screens
    data object Permission: Screens
    data object Scanner: Screens
    data class BmsDetail(val deviceAddress: String): Screens
}

class MainViewModel(ctx: Application): AndroidViewModel(ctx) {
    private val bmsRepository = BmsRepository(ctx)

    var currentScreen: Screens by mutableStateOf(Screens.Main)
        private set

    private val _markedDevices = mutableStateListOf<DeviceInfoData>()
    val markedDevices: SnapshotStateList<DeviceInfoData> = _markedDevices

    init {
        updateMarkedDevices()

    }


    fun isDeviceMarked(macAddress: String) = markedDevices.find { it.macAddress == macAddress  } != null

    fun removeMarkedDevice(macAddress: String) {
        bmsRepository.removeDevice(macAddress)
        updateMarkedDevices()
    }
    fun addMarkedDevice(
        macAddress: String,
        generalDeviceInfo: GeneralDeviceInfo
    ) {
        val data = DeviceInfoData(name = generalDeviceInfo.name, macAddress)
        bmsRepository.addDevice(data)
        updateMarkedDevices()
    }

    private fun updateMarkedDevices() {
        _markedDevices.clear()
        val newList = bmsRepository.getAll()
        _markedDevices.addAll(newList)
    }

    fun requestPermissions() {
        currentScreen = Screens.Permission
    }
    fun allPermissionsGranted() {
        if (currentScreen == Screens.Permission) {
            currentScreen = Screens.Main
        }
    }

    fun scanForDevices() {
        currentScreen = Screens.Scanner
    }

    fun showDeviceDetails(deviceAddress: String) {
        log("Add device $deviceAddress")
        currentScreen = Screens.BmsDetail(deviceAddress)
    }

    fun back(): Boolean {
        when (currentScreen) {
            is Screens.BmsDetail -> {
                currentScreen = Screens.Main
            }
            Screens.Permission -> {
                currentScreen = Screens.Main
            }
            Screens.Scanner -> {
                currentScreen = Screens.Main
            }
            else -> {
                return false
            }
        }
        return true
    }

    override fun onCleared() {

    }
}
