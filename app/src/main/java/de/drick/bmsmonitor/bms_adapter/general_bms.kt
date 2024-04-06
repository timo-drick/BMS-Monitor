package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.jk_bms.JKBmsAdapter
import de.drick.bmsmonitor.bms_adapter.yy_bms.YYBmsAdapter
import kotlinx.coroutines.flow.StateFlow


data class GeneralCellInfo(
    val stateOfCharge: Int, // in percent
    val maxCapacity: Float, // in Ah
    val current: Float, // in A
    val cellVoltages: FloatArray, // cell voltages in V
    val cellBalance: BooleanArray,// cell which are getting balanced
    val cellMinIndex: Int,
    val cellMaxIndex: Int,
    val cellDelta: Float,
    val balanceState: String,
    val errorList: List<String>,
    val chargingEnabled: Boolean,
    val dischargingEnabled: Boolean,
    val temp0: Float,
    val temp1: Float,
    val tempMos: Float
)

data class GeneralDeviceInfo(
    val name: String,
    val longName: String
)

enum class DeviceMacPrefix(val prefix: String) {
    YY_BMS("C0:D6:3C"),
    JK_BMS("C8:47:8C")
}

interface BmsInterface {
    suspend fun start()
    suspend fun stop()
    val cellInfoState: StateFlow<GeneralCellInfo?>
    val deviceInfoState: StateFlow<GeneralDeviceInfo?>
}

class BmsAdapter(
    ctx: Context,
    private val deviceAddress: String
) {
    private val service = BluetoothLeConnectionService(ctx)

    //private val bmsAdapter = JKBmsAdapter(service)
    private val bmsAdapter: BmsInterface = when {
        deviceAddress.startsWith(DeviceMacPrefix.JK_BMS.prefix) -> JKBmsAdapter(service)
        deviceAddress.startsWith(DeviceMacPrefix.YY_BMS.prefix) -> YYBmsAdapter(service)
        else -> JKBmsAdapter(service)
    }

    val cellInfoState: StateFlow<GeneralCellInfo?> = bmsAdapter.cellInfoState
    val deviceInfoState: StateFlow<GeneralDeviceInfo?> = bmsAdapter.deviceInfoState
    val connectionState = service.connectionState

    suspend fun connect() {
        service.connect(deviceAddress)
        service.discover()
    }

    suspend fun start() {
        bmsAdapter.start()
    }

    suspend fun stop() {
        bmsAdapter.stop()
    }

    fun disconnect() {
        service.disconnect()
    }
}

fun stringFromBytes(bytes: ByteArray, offset: Int, maxLength: Int): String = buildString {
    for (i in 0 until maxLength) {
        val code = bytes[i + offset].toUShort()
        if (code == 0.toUShort()) break
        append(Char(code))
    }
}
