package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.jk_bms.JKBmsAdapter
import de.drick.bmsmonitor.bms_adapter.yy_bms.YYBmsAdapter
import de.drick.log
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull


data class GeneralCellInfo(
    val stateOfCharge: Int, // in percent
    val maxCapacity: Float, // in Ah
    val current: Float, // in A
    val cellVoltages: FloatArray, // cell voltages in V
    val cellBalance: BooleanArray,// cells which are getting balanced
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

data class BmsInfo(
    val state: BluetoothLeConnectionService.State,
    val deviceInfo: GeneralDeviceInfo?,
    val cellInfo: GeneralCellInfo?
)

class BmsAdapter(
    ctx: Context,
    private val deviceAddress: String
) {
    private val service = BluetoothLeConnectionService(ctx)

    companion object {
        val BMS_SERVICE_UUIDs = persistentSetOf(
            JKBmsAdapter.serviceUUID,
            YYBmsAdapter.serviceUUID
        ).toImmutableSet()
    }

    //private val bmsAdapter = JKBmsAdapter(service)
    private val bmsAdapter: BmsInterface = when {
        deviceAddress.startsWith(DeviceMacPrefix.JK_BMS.prefix) -> JKBmsAdapter(service)
        deviceAddress.startsWith(DeviceMacPrefix.YY_BMS.prefix) -> YYBmsAdapter(service)
        else -> JKBmsAdapter(service)
    }

    val bmsInfo: Flow<BmsInfo> = combine(
        flow = service.connectionState,
        flow2 = bmsAdapter.deviceInfoState,
        flow3 = bmsAdapter.cellInfoState
    ) { flow, flow2, flow3 ->
        BmsInfo(flow, flow2, flow3)
    }

    suspend fun connect() {
        val maxRetries = 3
        var connected = false
        for (i in 0 until maxRetries) {
            withTimeoutOrNull(10000) {
                log("try to connect try: $i")
                service.connect(deviceAddress)
                service.discover()
                connected = true
            }
            if (connected) break
        }
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
