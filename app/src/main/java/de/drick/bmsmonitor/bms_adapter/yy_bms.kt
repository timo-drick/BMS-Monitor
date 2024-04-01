@file:OptIn(ExperimentalStdlibApi::class)

package de.drick.bmsmonitor.bms_adapter

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs


private val writeCommand0 = "00030000000185db".hexToByteArray()
private val prefix0 = "010302"

private val writeCommand1 = "010300a000518414".hexToByteArray()
private val prefix1 = "0103a2"

private val writeCommand2 = "0103003f00047405".hexToByteArray()
private val prefix2 = "010308"


fun byteArray2BooleanArray(vararg bytes: Byte): BooleanArray {
    val bits = BooleanArray(bytes.size * 8)
    for (i in 0 until bytes.size * 8) {
        if (bytes[i / 8].toInt() and (1 shl i % 8) > 0) bits[i] = true
    }
    return bits
}

fun intFromBytes(b1: Byte, b2: Byte) = b1.toUByte().toInt() + (b2.toUByte().toInt() shl 8)


class YYBmsAdapter(private val service: BluetoothLeConnectionService): BmsInterface {
    val YY_BMS_SERVICE = checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
    private val YY_BMS_BLE_RX_CHARACTERISTICS = checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
    private val YY_BMS_BLE_TX_CHARACTERISTICS = checkNotNull(UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"))

    private val _cellInfoFlow = MutableStateFlow<GeneralCellInfo?>(null)
    override val cellInfoState: StateFlow<GeneralCellInfo?> = _cellInfoFlow
    private val _deviceInfoFlow = MutableStateFlow<GeneralDeviceInfo?>(null)
    override val deviceInfoState: StateFlow<GeneralDeviceInfo?> = _deviceInfoFlow

    private var cellCount = 24

    private val prefixBmsInfoData = "01039e"
    private val writeCommandBmsInfoData = "01030001004f55fe".hexToByteArray()

    private val prefixCellData = "0103a0"
    private val writeCommandCellData = "01030050005045e7".hexToByteArray()

    private var running = false

    override suspend fun start() {
        service.subscribeForNotification(YY_BMS_SERVICE, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
        withContext(Dispatchers.IO) {
            launch {
                running = true
                service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_TX_CHARACTERISTICS, writeCommandBmsInfoData)
                delay(500)
                while (isActive && running) {
                    if (deviceInfoState.value == null) {
                        service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_TX_CHARACTERISTICS, writeCommandBmsInfoData)
                        delay(500)
                    }
                    service.writeCharacteristic(YY_BMS_SERVICE, YY_BMS_BLE_TX_CHARACTERISTICS, writeCommandCellData)
                    delay(1000)
                }
            }
        }
    }

    override suspend fun stop() {
        running = false
        service.unSubscribeForNotification(YY_BMS_SERVICE, YY_BMS_BLE_RX_CHARACTERISTICS)
    }

    private val notificationCallback = { data: ByteArray ->
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val hex = data.toHexString()
        val crcCalculated = crc16Modbus(data, data.size - 2)
        val crcExpected = buffer.getShort(data.size - 2)
        if (crcCalculated != crcExpected) {
            log("Checksum error! calculated: $crcCalculated expected: $crcExpected")
        }
        log(hex)
        when (hex.substring(0, prefix1.length)) {
            prefix1 -> {
                //log(hex)
            }
            prefix2 -> {
                //log(hex)
            }
            prefixBmsInfoData -> {
                //log(hex)
                val ascii = data.map { Char(it.toUByte().toInt()) }
                val modelTypeName = stringFromBytes(data, 41, 36)
                val bluetoothName = stringFromBytes(data, 83, 12)
                val pin = stringFromBytes(data, 95, 4)
                val year = buffer[131].toInt() + 2000
                val month = buffer[132].toInt()
                val day = buffer[133].toInt()
                val hour = buffer[134].toInt()
                val minutes = buffer[135].toInt()
                val seconds = buffer[136].toInt()
                //log("$modelTypeName - $year.$month.$day $hour:$minutes:$seconds")
                //log("$bluetoothName pin: $pin cells: $cells")
                val cells = buffer[151].toUByte().toInt()
                //log("Cells: $cells")
                cellCount = cells
                _deviceInfoFlow.value = GeneralDeviceInfo(
                    name = bluetoothName,
                    longName = modelTypeName
                )
            }
            prefixCellData -> {
                //log(hex)

                // Offset 3 current 2 byte
                val currentRaw = buffer.getShort(3)

                // Offset 5 cell voltages 24x 2 byte shorts
                val byteOffset = 5
                val cellVoltage = FloatArray(cellCount) {
                    val position = it * 2 + byteOffset
                    buffer.getShort(position).toFloat() / 1000f
                }
                // Offset end 53

                //val bits = byteArray2BooleanArray(buffer[65], buffer[66], buffer[67], buffer[68])
                //log(bits.joinToString("") { if (it) "1" else "0" })
                val discharging = byteArray2BooleanArray(buffer[65])[0]
                val currentSign = 1f //if (discharging) -1f else 1f // not working
                val current = currentSign * currentRaw.toFloat() * 0.014f // Not exactly the same like in the old app
                //But closer to the real value
                //log ("Current: ${"%.2f".format(current)}")


                val capacity1 = buffer.getShort(79).toFloat() / 10f
                val capacity2 = buffer.getShort(81).toFloat() / 10f
                val soc = buffer[83].toInt()

                //log("Soc: $soc% cap1: $capacity1 Ah $capacity2 Ah cellCount: $cellCount")


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

                val balanceBits = byteArray2BooleanArray(buffer[121], buffer[122], buffer[123])
                //log(balanceBits.joinToString("") { if (it) "1" else "0" })
                val balancing = buffer[121] + buffer[122] + buffer[123] > 0
                val balanceState = if (balancing) "Balancing" else "Off"

                val testBits = byteArray2BooleanArray(buffer[147], buffer[148])
                val text = testBits.mapIndexed { index, b -> if (b) "$index [1]" else "$index [0]" }.joinToString(" ")
                //log("Bist: $text")
                // bit 1 und 6 chraging disabled
                // bit 0 und 4 discharging disabled
                val bitsA = byteArray2BooleanArray(buffer[147])
                val dischargingEnabled = bitsA[0]  // also bit 4 flips when dischraging is disabled
                val chargingEnabled = bitsA[1]     // also bit 6 flips when charging is disabled

                // Calculate highest and lowest cell
                val (hvIndex, hvValue) = if (cellVoltage.isNotEmpty()) {
                    cellVoltage.withIndex().maxBy { (_, v) -> v }
                } else {
                    IndexedValue(0, 0f)
                }
                val (lvIndex, lvValue) = if (cellVoltage.isNotEmpty()) {
                    cellVoltage.withIndex().minBy { (_, v) -> v }
                } else {
                    IndexedValue(0, 0f)
                }

                val new = GeneralCellInfo(
                    stateOfChard = soc,
                    maxCapacity = capacity1,
                    current = current,
                    cellVoltages = cellVoltage,
                    cellMinIndex = lvIndex,
                    cellMaxIndex = hvIndex,
                    cellDelta = abs(lvValue - hvValue),
                    cellBalance = balanceBits,
                    balanceState = balanceState,
                    errorList = emptyList(), //TODO,
                    chargingEnabled = chargingEnabled,
                    dischargingEnabled = dischargingEnabled,
                    temp0 = 0f,  //TODO
                    temp1 = 0f,  //TODO
                    tempMos = 0f //TODO
                )
                _cellInfoFlow.value = new
            }
            else -> {
                //log(hex)
                //log("Unknown prefix: $hex")
            }
        }
    }
}