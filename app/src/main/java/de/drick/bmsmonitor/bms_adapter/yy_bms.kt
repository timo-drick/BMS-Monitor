@file:OptIn(ExperimentalStdlibApi::class)

package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

val YY_BMS_SERVICE = checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
private val YY_BMS_BLE_RX_CHARACTERISTICS = checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
private val YY_BMS_BLE_TX_CHARACTERISTICS = checkNotNull(UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"))

private val writeCommand0 = "00030000000185db".hexToByteArray()
private val prefix0 = "010302"

private val writeCommand1 = "010300a000518414".hexToByteArray()
private val prefix1 = "0103a2"

private val writeCommand2 = "0103003f00047405".hexToByteArray()
private val prefix2 = "010308"

private val prefixBmsInfoData = "01039e"
private val writeCommandBmsInfoData = "01030001004f55fe".hexToByteArray()

private val prefixCellData = "0103a0"
private val writeCommandCellData = "01030050005045e7".hexToByteArray()

fun byteArray2BitArray(vararg bytes: Byte): BooleanArray {
    val bits = BooleanArray(bytes.size * 8)
    for (i in 0 until bytes.size * 8) {
        if (bytes[i / 8].toInt() and (1 shl i % 8) > 0) bits[i] = true
    }
    return bits
}

fun intFromBytes(b1: Byte, b2: Byte) = b1.toUByte().toInt() + (b2.toUByte().toInt() shl 8)


class YYBmsAdapter(ctx: Context) {
    private val service = BluetoothLeConnectionService(ctx)

    private val _batteryInfoFlow = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfoState: StateFlow<BatteryInfo?> = _batteryInfoFlow

    private var bmsInfo = BmsInfo(
        name = "-",
        cells = 24
    )
    
    suspend fun connect(deviceAddress: String) {
        service.connect(deviceAddress)
        service.discover()
        service.subscribeForNotification(YY_BMS_SERVICE, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
    }

    fun disconnect() {
        service.disconnect()
    }

    fun updateCellData() {
        //service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_TX_CHARACTERISTICS, writeCommandCellData)
    }
    fun updateInfo() {
        //service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_TX_CHARACTERISTICS, writeCommandBmsInfoData)
        val data = "aa5590eb9700df5288679d0a096b9af6709a17fd".hexToByteArray()
        service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_RX_CHARACTERISTICS, data)
    }

    private val notificationCallback = { value: ByteArray ->
        val hex = value.toHexString()
        log(hex)
        when (hex.substring(0, prefix1.length)) {
            prefix1 -> {}
            prefix2 -> {}
            prefixBmsInfoData -> {
                val cells = value[151].toUByte().toInt()
                val modelTypeName = stringFromBytes(value, 41, 36)
                val bluetoothName = stringFromBytes(value, 83, 12)
                val pin = stringFromBytes(value, 95, 4)
                val year = value[131].toInt() + 2000
                val month = value[132].toInt()
                val day = value[133].toInt()
                val hour = value[134].toInt()
                val minutes = value[135].toInt()
                val seconds = value[136].toInt()
                log("$modelTypeName - $year.$month.$day $hour:$minutes:$seconds")
                log("$bluetoothName pin: $pin cells: $cells")
                bmsInfo = BmsInfo(
                    name = modelTypeName,
                    cells = cells
                )
            }
            prefixCellData -> {
                //log(hex)
                val soc = value[83].toInt()
                val capacity1 = intFromBytes(value[81], value[82]).toFloat() / 10f
                val capacity2 = intFromBytes(value[79], value[80]).toFloat() / 10f
                //log("Soc: $soc% cap1: $capacity1 Ah $capacity2 Ah cellCount: $cellCount")
                val currentRaw = intFromBytes(value[3], value[4])

                val current = currentRaw.toFloat() / 70f // Not exactly the same old app
                //But closer to the real value

                val byteOffset = 5
                val cellVoltage = FloatArray(bmsInfo.cells) {
                    val position = it * 2 + byteOffset
                    intFromBytes(value[position], value[position + 1]).toFloat() / 1000f
                }
                //Balance
                /**
                 * Balance:
                 * 59df01 -> 01011001 11011111 00000001
                 * 974900 -> 10010111 01001001 00000000
                 *
                 */
                //val balance = value.asList().subList(120, 124)
                //log("Balance: ${value[121].toUByte().toString(2)} ${value[122].toUByte().toString(2)} ${value[123].toUByte().toString(2)}")
                // 01011001 11011111 00000001 0

                val balanceBits = byteArray2BitArray(value[121], value[122], value[123])
                //log(balanceBits.joinToString("") { if (it) "1" else "0" })
                val new = BatteryInfo(
                    stateOfChard = soc,
                    maxCapacity = capacity1,
                    current = current,
                    cellVoltages = cellVoltage,
                    cellBalance = balanceBits
                )
                _batteryInfoFlow.value = new
            }
            else -> {
                log("Unknown prefix: $hex")
            }
        }
    }
}