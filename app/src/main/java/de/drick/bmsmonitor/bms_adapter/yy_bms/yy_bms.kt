@file:OptIn(ExperimentalStdlibApi::class)

package de.drick.bmsmonitor.bms_adapter.yy_bms

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInterface
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID

enum class BatteryType {
    Unknown, TernaryLi, Lfp, Lc
}

sealed interface YYBmsEvent {
    data class DeviceInfo(
        val name: String,
        val modelName: String,
        val serialNumber: String,
        val batteryType: BatteryType,
        val voltage: Float,
        val current: Float,
        val systemRuntime: Int  //Seconds since 1970 unix timestamp
        ): YYBmsEvent
    data class CellInfo(
        val power: Int, //Watt (Not negative so it is absolute value)
        val cellVoltage: FloatArray,
        val maxVoltageIndex: Int,
        val minVoltageIndex: Int,
        val tempMos: Float,
        val temp1: Float,
        val temp2: Float,
        val ratedCapacity: Float,
        val factCapacity: Float,
        val soc: Int,
        val remainingWh: Int,
        val cycleCount: Int,
        val shuntGain: Int,
        val shuntOffset: Int,
        val cellBalance: BooleanArray,
        val enableBalancingVoltage: Float,
        val enableBalancingDiffVoltage: Float,
        val enableBalancingDetaVoltage: Float,
        val chargingEnabled: Boolean,
        val dischargingEnabled: Boolean,
    ): YYBmsEvent
}

class YYBmsAdapter(private val service: BluetoothLeConnectionService): BmsInterface {
    companion object {
        val serviceUUID =
            checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
        private val YY_BMS_BLE_RX_CHARACTERISTICS =
            checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
        private val YY_BMS_BLE_TX_CHARACTERISTICS =
            checkNotNull(UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"))
    }

    private val yyBmsDecoder = YYBmsDecoder()

    private val _eventFlow = MutableSharedFlow<GeneralCellInfo>(replay = 1)
    override val bmsEventFlow: Flow<GeneralCellInfo> = _eventFlow

    private val _rawFlow = MutableSharedFlow<ByteArray>(replay = 1)
    override val bmsRawFlow = _rawFlow.asSharedFlow()

    private var running = false
    private var lastDeviceInfo: YYBmsEvent.DeviceInfo? = null

    override suspend fun start() {
        log("start")
        service.subscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
        withContext(Dispatchers.IO) {
            running = true
            while (isActive && running) {
                service.writeCharacteristic(
                    serviceUUID,
                    YY_BMS_BLE_TX_CHARACTERISTICS,
                    YYBmsDecoder.COMMAND_BMS_INFO_DATA
                )
                delay(500)
                service.writeCharacteristic(
                    serviceUUID,
                    YY_BMS_BLE_TX_CHARACTERISTICS,
                    YYBmsDecoder.COMMAND_CELL_DATA
                )
                delay(500)
            }
        }
        log("Start finished")
    }

    override suspend fun stop() {
        running = false
        service.unSubscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS)
    }

    override fun decodeRaw(data: ByteArray): GeneralCellInfo? {
        yyBmsDecoder.decodeData(data)?.let { event ->
            if (event is YYBmsEvent.DeviceInfo) {
                lastDeviceInfo = event
            }
            if (event is YYBmsEvent.CellInfo) {
                val deviceInfo = lastDeviceInfo?.let {
                    GeneralDeviceInfo(
                        name = it.name,
                        longName = it.modelName
                    )
                }
                val cellDiffVolt = event.cellVoltage[event.maxVoltageIndex] - event.cellVoltage[event.minVoltageIndex]
                val balanceState = if (event.cellBalance.none()) "Off" else "On"
                return GeneralCellInfo(
                    deviceInfo = deviceInfo,
                    stateOfCharge = event.soc,
                    maxCapacity = event.ratedCapacity,
                    current = lastDeviceInfo?.current ?: (event.power.toFloat() / event.cellVoltage.sum()),
                    cellVoltages = event.cellVoltage,
                    cellBalance = event.cellBalance,
                    cellMinIndex = event.minVoltageIndex,
                    cellMaxIndex = event.maxVoltageIndex,
                    cellDelta = cellDiffVolt,
                    balanceState = balanceState,
                    errorList = emptyList(),
                    chargingEnabled = event.chargingEnabled,
                    dischargingEnabled = event.dischargingEnabled,
                    temp0 = event.temp1,
                    temp1 = event.temp2,
                    tempMos = event.tempMos
                )
            }
        }
        return null
    }

    private val notificationCallback: (ByteArray) -> Unit = { data: ByteArray ->
        val cellInfo = decodeRaw(data)
        if (cellInfo != null) {
            _eventFlow.tryEmit(cellInfo)
        }
        _rawFlow.tryEmit(data)
    }
}