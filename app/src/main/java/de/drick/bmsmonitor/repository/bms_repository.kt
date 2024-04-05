package de.drick.bmsmonitor.repository

import android.content.Context
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoData(
    val name: String,
    val macAddress: String
)

class BmsRepository(ctx: Context) {
    private val keyValueStore = ctx.keyValuePrefStorage<DeviceInfoData>("bms_repository")

    fun getAll() = keyValueStore.loadAll()

    fun addDevice(data: DeviceInfoData) {
        keyValueStore.save(data.macAddress, data)
    }

    fun removeDevice(macAddress: String) {
        keyValueStore.remove(macAddress)
    }
}
