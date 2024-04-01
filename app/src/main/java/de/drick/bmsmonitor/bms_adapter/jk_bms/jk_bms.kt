package de.drick.bmsmonitor.bms_adapter.jk_bms

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInterface
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    ERROR_0X100("Error 0x01 0x00"),                       // 0000 0001 0000 0000
    ERROR_0X200("Error 0x02 0x00"),                       // 0000 0010 0000 0000
    CELL_COUNT("Cell count is not equal to settings"),    // 0000 0100 0000 0000
    CURRENT_SENSOR_ANOMALY("Current sensor anomaly"),     // 0000 1000 0000 0000
    CELL_OVER_VOLTAGE("Cell over voltage"),               // 0001 0000 0000 0000
    ERROR_0X2000("Error 0x20 0x00"),                      // 0010 0000 0000 0000
    CHARGE_OVER_CURRENT("Charge over current protection"),// 0100 0000 0000 0000
    ERROR_0X8000("Error 0x80 0x00"),                      // 1000 0000 0000 0000
}

class JKBmsAdapter(private val service: BluetoothLeConnectionService): BmsInterface {
    //private val serviceUUID = 0xFFE0
    //private val characteristicNotificationUUID = 0xFFE1
    //private val characteristicWriteCommandUUID = 0xFFE1
    private val serviceUUID = checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
    private val characteristicNotificationUUID = checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
    private val characteristicWriteCommandUUID = characteristicNotificationUUID

    private val _cellInfoFlow = MutableStateFlow<GeneralCellInfo?>(null)
    override val cellInfoState: StateFlow<GeneralCellInfo?> = _cellInfoFlow
    private val _deviceInfoFlow = MutableStateFlow<GeneralDeviceInfo?>(null)
    override val deviceInfoState: StateFlow<GeneralDeviceInfo?> = _deviceInfoFlow

    private val decoder = JkBmsDecoder()

    private val COMMAND_CELL_INFO = 0x96.toByte()
    private val COMMAND_DEVICE_INFO = 0x97.toByte()


    override suspend fun start() {
        service.subscribeForNotification(serviceUUID, characteristicNotificationUUID, notificationCallback)
        delay(500)
        writeCommand(COMMAND_DEVICE_INFO, 0, 0)
        delay(500)
        writeCommand(COMMAND_CELL_INFO, 0, 0)
    }

    override suspend fun stop() {
        service.unSubscribeForNotification(serviceUUID, characteristicNotificationUUID)
    }

    private val notificationCallback: (ByteArray) -> Unit = { data: ByteArray ->
        when (val event = decoder.addData(data)) {
            is JKBmsEvent.DeviceInfo -> {
                _deviceInfoFlow.value = GeneralDeviceInfo(
                    name = event.name,
                    longName = event.longName
                )
            }
            is JKBmsEvent.CellInfo -> {
                _cellInfoFlow.value = GeneralCellInfo(
                    stateOfChard = event.soc,
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
            else -> {}
        }
    }

    private fun writeCommand(address: Byte, value: Int, length: Byte) {
        service.writeCharacteristic(serviceUUID, characteristicWriteCommandUUID,decoder.encode(address, value, length) )
    }

}
