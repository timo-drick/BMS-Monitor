package de.drick.bmsmonitor.bms_adapter.yy_bms

import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.bms_adapter.crc16Modbus
import de.drick.bmsmonitor.bms_adapter.stringFromBytes
import de.drick.log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs


fun byteArray2BooleanArray(vararg bytes: Byte): BooleanArray {
    val bits = BooleanArray(bytes.size * 8)
    for (i in 0 until bytes.size * 8) {
        if (bytes[i / 8].toInt() and (1 shl i % 8) > 0) bits[i] = true
    }
    return bits
}

@OptIn(ExperimentalStdlibApi::class)
class YYBmsDecoder {
    companion object {
        private val writeCommand0 = "00030000000185db".hexToByteArray()
        private val prefix0 = "010302"

        private val writeCommand1 = "010300a000518414".hexToByteArray()
        private val prefix1 = "0103a2"

        private val writeCommand2 = "0103003f00047405".hexToByteArray()
        private val prefix2 = "010308"
        private var cellCount = 24

        private val prefixBmsInfoData = "01039e"
        val COMMAND_BMS_INFO_DATA = "01030001004f55fe".hexToByteArray()

        private val prefixCellData = "0103a0"
        val COMMAND_CELL_DATA = "01030050005045e7".hexToByteArray()
    }

    fun decodeData(data: ByteArray): Any? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val hex = data.toHexString()
        val crcCalculated = crc16Modbus(data, data.size - 2)
        val crcExpected = buffer.getShort(data.size - 2)
        if (crcCalculated != crcExpected) {
            log("Checksum error! calculated: $crcCalculated expected: $crcExpected")
            return null
        }
        //log(hex)
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
                val serialnumber = data.toHexString(5, 5 + 8)
                //log("Serial number: $serialnumber")
                //Offset 13

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
                // offset = 136

                val cells = buffer[151].toUByte().toInt()
                //log("Cells: $cells")
                cellCount = cells
                return GeneralDeviceInfo(
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
                val current =
                    currentSign * currentRaw.toFloat() * 0.014f // Not exactly the same like in the old app
                //But closer to the real value
                //log ("Current: ${"%.2f".format(current)}")
                val tmpMos = buffer[68].toUByte().toInt() - 40
                val tmp1 = buffer[69].toUByte().toInt() - 40
                val tmp2 = buffer[70].toUByte().toInt() - 40

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
                val balancing = balanceBits.find { it } ?: false
                val balanceState = if (balancing) "Balancing" else "Off"

                val testBits = byteArray2BooleanArray(buffer[147], buffer[148])
                val text = testBits.mapIndexed { index, b -> if (b) "$index [1]" else "$index [0]" }
                    .joinToString(" ")
                //log("Bits: $text")
                // bit 1 and 6 chraging disabled
                // bit 0 and 4 discharging disabled
                val bitsA = byteArray2BooleanArray(buffer[147])
                val dischargingEnabled = bitsA[0]  // also bit 4 flips when discharging is disabled
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

                return GeneralCellInfo(
                    stateOfCharge = soc,
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
                    temp0 = tmp1.toFloat(),
                    temp1 = tmp2.toFloat(),
                    tempMos = tmpMos.toFloat()
                )
            }

            else -> {
                //log(hex)
                //log("Unknown prefix: $hex")
            }
        }
        return null
    }
}