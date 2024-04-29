package de.drick.bmsmonitor.bms_adapter.jk_bms

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInterface
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

sealed interface JKBmsEvent {
    data class DeviceInfo(
        val name: String,
        val longName: String
    ): JKBmsEvent
    data class CellInfo(
        val totalVoltage: Float,
        val current: Float,
        val soc: Int,
        val capacity: Float,
        val capacityRemaining: Float,
        val cellVoltageList: FloatArray,
        val cellAverage: Float,
        val cellDelta: Float,
        val cellMaxIndex: Int,
        val cellMinIndex: Int,
        val balanceState: BalanceState,
        val balanceCurrent: Float,
        val cellBalanceActive: BooleanArray,
        val temp0: Float,
        val temp1: Float,
        val tempMos: Float,
        val cycleCount: Int,
        val cycleCapacity: Float,
        val totalRuntime: UInt,
        val chargingEnabled: Boolean,
        val dischargingEnabled: Boolean,
        //val balancingEnabled: Boolean,
        val heatingEnabled: Boolean,
        val heatingCurrent: Float,
        val errors: List<JKBmsErrors>,
    ): JKBmsEvent
}

enum class BalanceState {
    Off, Charging, Discharging
}

enum class JKBmsErrors(val description: String) {
    CHARGE_OVER_TEMP("Charge over temperature"),          // 0000 0000 0000 0001
    CHARGE_UNDER_TEMP("Charge under temperature"),        // 0000 0000 0000 0010
    CPU_AUX_ANOMALY("CPU AUX Anomaly"),                   // 0000 0000 0000 0100
    CELL_UNDER_VOLTAGE("Cell under voltage"),             // 0000 0000 0000 1000
    ERROR_0X010("Error 0x00 0x10"),                       // 0000 0000 0001 0000
    ERROR_0X020("Error 0x00 0x20"),                       // 0000 0000 0010 0000
    ERROR_0X040("Error 0x00 0x40"),                       // 0000 0000 0100 0000
    ERROR_0X080("Error 0x00 0x80"),                       // 0000 0000 1000 0000
    ERROR_0X100("Sample-wire resistance too large"),      // 0000 0001 0000 0000
    ERROR_0X200("Error 0x02 0x00"),                       // 0000 0010 0000 0000
    CELL_COUNT("Cell count is not equal to settings"),    // 0000 0100 0000 0000
    CURRENT_SENSOR_ANOMALY("Current sensor anomaly"),     // 0000 1000 0000 0000
    CELL_OVER_VOLTAGE("Cell over voltage"),               // 0001 0000 0000 0000
    ERROR_0X2000("Error 0x20 0x00"),                      // 0010 0000 0000 0000
    CHARGE_OVER_CURRENT("Charge over current protection"),// 0100 0000 0000 0000
    ERROR_0X8000("Error 0x80 0x00"),                      // 1000 0000 0000 0000
}

class JKBmsAdapter(private val service: BluetoothLeConnectionService): BmsInterface {
    companion object {
        val serviceUUID =
            checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
        private val characteristicNotificationUUID =
            checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
        private val characteristicWriteCommandUUID = characteristicNotificationUUID
    }
    private val _eventFlow = MutableSharedFlow<GeneralCellInfo>(replay = 1)
    override val bmsEventFlow = _eventFlow.asSharedFlow()
    private val _rawFlow = MutableSharedFlow<ByteArray>(replay = 1)
    override val bmsRawFlow = _rawFlow.asSharedFlow()

    private val decoder = JkBmsDecoder()

    override suspend fun start() {
        service.subscribeForNotification(serviceUUID, characteristicNotificationUUID, notificationCallback)
        delay(500)
        writeCommand(JkBmsDecoder.Command.COMMAND_DEVICE_INFO)
        delay(500)
        writeCommand(JkBmsDecoder.Command.COMMAND_CELL_INFO)
    }

    override suspend fun stop() {
        service.unSubscribeForNotification(serviceUUID, characteristicNotificationUUID)
    }

    private var lastDeviceInfo: GeneralDeviceInfo? = null

    override fun decodeRaw(data: ByteArray): GeneralCellInfo? {
        when (val event = decoder.addData(data)) {
            is JKBmsEvent.DeviceInfo -> {
                lastDeviceInfo = GeneralDeviceInfo(
                    name = event.name,
                    longName = event.longName
                )
            }
            is JKBmsEvent.CellInfo -> {
                return GeneralCellInfo(
                    deviceInfo = lastDeviceInfo,
                    stateOfCharge = event.soc,
                    maxCapacity = event.capacity,
                    current = event.current,
                    cellVoltages = event.cellVoltageList,
                    cellMinIndex = event.cellMinIndex,
                    cellMaxIndex = event.cellMaxIndex,
                    cellDelta = event.cellDelta,
                    cellBalance = event.cellBalanceActive,
                    balanceState = "${event.balanceState.name} ${"%.3f".format(event.balanceCurrent)} A",
                    errorList = event.errors.map { it.description },
                    chargingEnabled = event.chargingEnabled,
                    dischargingEnabled = event.dischargingEnabled,
                    temp0 = event.temp0,
                    temp1 = event.temp1,
                    tempMos = event.tempMos
                )
            }
            null -> {
                //No data available
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

    private fun writeCommand(command: JkBmsDecoder.Command) {
        service.writeCharacteristic(serviceUUID, characteristicWriteCommandUUID, decoder.encode(command, 0, 0) )
    }

}
