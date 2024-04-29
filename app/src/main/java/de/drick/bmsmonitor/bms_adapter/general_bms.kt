package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.jk_bms.JKBmsAdapter
import de.drick.bmsmonitor.bms_adapter.yy_bms.YYBmsAdapter
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull


data class GeneralCellInfo(
    val deviceInfo: GeneralDeviceInfo?,
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

enum class BmsType(val prefix: String) {
    UNKNOWN(""),
    YY_BMS("C0:D6:3C"),
    JK_BMS("C8:47:8C");

    companion object {
        fun fromMacAddress(address: String) = when {
            address.startsWith(JK_BMS.prefix) -> JK_BMS
            address.startsWith(YY_BMS.prefix) -> YY_BMS
            else -> UNKNOWN
        }
    }
}

interface BmsInterface {
    suspend fun start()
    suspend fun stop()
    val bmsEventFlow: Flow<GeneralCellInfo>
    val bmsRawFlow: Flow<ByteArray>

    fun decodeRaw(data: ByteArray): GeneralCellInfo?
}

data class BmsInfo(
    val state: BluetoothLeConnectionService.State,
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
    val bmsType = BmsType.fromMacAddress(deviceAddress)

    //private val bmsAdapter = JKBmsAdapter(service)
    private val bmsAdapter: BmsInterface = when(bmsType) {
        BmsType.JK_BMS -> JKBmsAdapter(service)
        BmsType.YY_BMS -> YYBmsAdapter(service)
        else -> JKBmsAdapter(service)
    }

    val bmsInfo: Flow<BmsInfo> = combine(
        flow = service.connectionState,
        flow2 = bmsAdapter.bmsEventFlow
    ) { connectionState, event ->
        BmsInfo(connectionState, event)
    }

    val rawData = bmsAdapter.bmsRawFlow

    suspend fun connect() {
        val maxRetries = 3
        var connected = false
        for (i in 0 until maxRetries) {
            withTimeoutOrNull(10000) {
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

    fun decodeRaw(data: ByteArray): GeneralCellInfo? = bmsAdapter.decodeRaw(data)
}
