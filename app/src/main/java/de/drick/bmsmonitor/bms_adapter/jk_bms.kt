package de.drick.bmsmonitor.bms_adapter

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Protocol implementation used to understand the protocol data:
 * https://github.com/syssi/esphome-jk-bms/blob/main/components/jk_bms_ble/jk_bms_ble.cpp
 */

class JkBmsDecoder() {

    enum class Errors(val description: String) {
        CHARGE_OVERTEMP("Charge Overtemperature"),           // 0000 0000 0000 0001
        CHARGE_UNDERTEMP("Charge Undertemperature"),         // 0000 0000 0000 0010
        ERROR_0X04("Error 0x00 0x04"),                       // 0000 0000 0000 0100
        CELL_UNDERVOLT("Cell Undervoltage"),                 // 0000 0000 0000 1000
        ERROR_0X010("Error 0x00 0x10"),                      // 0000 0000 0001 0000
        ERROR_0X020("Error 0x00 0x20"),                      // 0000 0000 0010 0000
        ERROR_0X040("Error 0x00 0x40"),                      // 0000 0000 0100 0000
        ERROR_0X080("Error 0x00 0x80"),                      // 0000 0000 1000 0000
        ERROR_0X100("Error 0x01 0x00"),                      // 0000 0001 0000 0000
        ERROR_0X200("Error 0x02 0x00"),                      // 0000 0010 0000 0000
        CELL_COUNT("Cell count is not equal to settings"),   // 0000 0100 0000 0000
        CURRENT_SENSOR_ANOMALY("Current sensor anomaly"),    // 0000 1000 0000 0000
        CELL_OVERVOLTAGE("Cell Overvoltage"),                // 0001 0000 0000 0000
        ERROR_0X2000("Error 0x20 0x00"),                     // 0010 0000 0000 0000
        CHARE_OVERCURRENT("Charge overcurrent protection"),  // 0100 0000 0000 0000
        ERROR_0X8000("Error 0x80 0x00"),                     // 1000 0000 0000 0000
    }

    private fun crc(data: ByteArray, length: Int): Byte {
        var crc: Byte = 0
        for (i in 0 until length)  {
            crc = (crc + data[i]).toByte()
        }
        return crc
    }
    @OptIn(ExperimentalStdlibApi::class)
    fun decode(data: ByteArray): BatteryInfo? {
        val hex = data.toHexString()
        log(hex)
        val frameType = data[4]
        when (frameType) {
            0x01.toByte() -> {
                //TODO decode setting
            }
            0x02.toByte() -> {
                return decodeCellData(data)
            }
            0x03.toByte() -> {
                decodeDeviceInfo(data)
            }
            else -> {
                log("Unknown message type!")
            }
        }
        return null
    }
    private fun decodeDeviceInfo(data: ByteArray) {
        val vendorId = stringFromBytes(data, 6, 24)
        log("Vendor id: $vendorId")
    }

    private fun getShort(data: ByteArray, offset: Int): UShort =
        (data[offset].toUByte().toInt() + (data[offset + 1].toUByte().toInt() shl 8)).toUShort()

    private fun getInt(data: ByteArray, offset: Int): Int =
        getShort(data, offset + 2).toInt() shl 16 + getShort(data, offset).toInt()

    private fun intFromBytes(b1: Byte, b2: Byte) = b1.toUByte().toInt() + (b2.toUByte().toInt() shl 8)
    private enum class ProtocolVersion {
        JK02_24S, JK02_32S, JK04
    }
    private val protocolVersion = ProtocolVersion.JK02_24S

    @OptIn(ExperimentalStdlibApi::class)
    private fun decodeCellData(data: ByteArray): BatteryInfo {
        val offset = if (protocolVersion == ProtocolVersion.JK02_32S) 16 else 0

        val enableCellsBitmask = 0

        /*val c1 = getInt(data, i * 4 + 6)
        val cellVoltage1 = Float.fromBits(c1)

        val test = "C0615640".hexToByteArray()
        val testValue = Float.fromBits(getInt(test, 0))*/

        val voltages = FloatArray(16, init = { i -> getShort(data, i * 2 + 6).toFloat() * 0.001f })
        log("Voltages: ${voltages.joinToString { "%.3f".format(it) }}")
        val cellBalance = BooleanArray(24, init = { false })
        return BatteryInfo(
            stateOfChard = 0,
            maxCapacity = 0f,
            current = 0f,
            cellVoltages = voltages,
            cellBalance = cellBalance
        )
    }
    fun encode(address: Byte, value: Int, length: Byte): ByteArray {
        val data = ByteArray(20, init = { 0 })
        data[0] = 0xAA.toByte() // start sequence
        data[1] = 0x55.toByte() // start sequence
        data[2] = 0x90.toByte() // start sequence
        data[3] = 0xEB.toByte() // start sequence
        data[4] = address       // holding register
        data[5] = length        // size of the value in byte
        data[6] = (value shr 0).toByte()
        data[7] = (value shr 8).toByte()
        data[8] = (value shr 16).toByte()
        data[9] = (value shr 24).toByte()
        data[19] = crc(data, 19)
        return data
    }
}

class JKBmsAdapter(private val service: BluetoothLeConnectionService) {
    //private val serviceUUID = 0xFFE0
    //private val characteristicNotificationUUID = 0xFFE1
    //private val characteristicWriteCommandUUID = 0xFFE1
    private val serviceUUID = checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
    private val characteristicNotificationUUID = checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
    private val characteristicWriteCommandUUID = characteristicNotificationUUID
    private val _batteryInfoFlow = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfoState: StateFlow<BatteryInfo?> = _batteryInfoFlow

    private val decoder = JkBmsDecoder()

    private val COMMAND_CELL_INFO = 0x96.toByte()
    private val COMMAND_DEVICE_INFO = 0x97.toByte()


    fun start() {
        service.subscribeForNotification(serviceUUID, characteristicNotificationUUID, notificationCallback)
    }

    fun requestDeviceInfo() {
        writeCommand(COMMAND_DEVICE_INFO, 0, 0)
    }

    fun requestCellData() {
        writeCommand(COMMAND_CELL_INFO, 0, 0)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val notificationCallback: (ByteArray) -> Unit = { data: ByteArray ->
        val event = decoder.decode(data)
        when (event) {
            is BatteryInfo -> {
                _batteryInfoFlow.value = event
            }
            else -> {}
        }
    }

    private fun writeCommand(address: Byte, value: Int, length: Byte) {
        service.writeCharacteristic(serviceUUID, characteristicWriteCommandUUID,decoder.encode(address, value, length) )
    }

}
