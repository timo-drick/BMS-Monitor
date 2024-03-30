package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import kotlinx.coroutines.flow.StateFlow


data class BatteryInfo(
    val stateOfChard: Int, // in percent
    val maxCapacity: Float, // in Ah
    val current: Float, // in A
    val cellVoltages: FloatArray, // cell voltages in V
    val cellBalance: BooleanArray
)

data class BmsInfo(
    val name: String,
    val cells: Int
)

class BmsAdapter(ctx: Context) {
    private val service = BluetoothLeConnectionService(ctx)

    private val jkBmsAdapter = JKBmsAdapter(service)

    val batteryInfoState: StateFlow<BatteryInfo?> = jkBmsAdapter.batteryInfoState


    private var bmsInfo = BmsInfo(
        name = "-",
        cells = 24
    )
    
    suspend fun connect(deviceAddress: String) {
        service.connect(deviceAddress)
        service.discover()
        //TODO identify bms type
        jkBmsAdapter.start()
        //service.subscribeForNotification(YY_BMS_SERVICE, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
    }

    fun disconnect() {
        service.disconnect()
    }

    fun updateCellData() {
        jkBmsAdapter.requestCellData()
    }
    fun updateInfo() {
        jkBmsAdapter.requestDeviceInfo()
    }
}

fun stringFromBytes(bytes: ByteArray, offset: Int, maxLength: Int): String = buildString {
    for (i in 0 until maxLength) {
        val code = bytes[i + offset].toUShort()
        if (code == 0.toUShort()) break
        append(Char(code))
    }
}
