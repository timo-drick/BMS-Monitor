package de.drick.bmsmonitor.bms_adapter.yy_bms

import de.drick.bmsmonitor.bms_adapter.crc16Modbus
import de.drick.bmsmonitor.bms_adapter.stringFromBytes
import de.drick.log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min


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

    fun decodeData(data: ByteArray): YYBmsEvent? {
        if (data.size < 5) {
            log("Data length to short. Length: ${data.size} bytes.")
            return null
        }
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
                val serialnumber = data.toHexString(5, 5 + 8)
                //log("Serial number: $serialnumber")
                //Offset 13

                val modelTypeName = stringFromBytes(data, 41, 36)

                val hardVersion = buffer.getShort(74 + 3)

                val bluetoothName = stringFromBytes(data, 83, 12)
                val pin = stringFromBytes(data, 95, 4)

                val tempNum = buffer[103 + 3] // Temp sensors aux
                val mosNum = buffer[104 + 3]  // Temp sensors mosfet
                val balaNum = buffer[105 + 3] // Temp sensors balancer?

                val bootVersion = buffer[122 + 3]
                val softVersion = buffer[123 + 3]

                val year = buffer[131].toInt() + 2000
                val month = buffer[132].toInt()
                val day = buffer[133].toInt()
                val hour = buffer[134].toInt()
                val minutes = buffer[135].toInt()
                val seconds = buffer[136].toInt()
                //log("$modelTypeName - $year.$month.$day $hour:$minutes:$seconds")
                //log("$bluetoothName pin: $pin cells: $cells")
                // offset = 136

                val sysRuntime = buffer.getInt(140 + 3)
                val mcuResetCount = buffer.getShort(144 + 3)
                val cells = buffer[151].toUByte().toInt()
                val batteryType = when(buffer[149 + 3].toInt()) {
                    0 -> BatteryType.TernaryLi
                    1 -> BatteryType.Lfp
                    2 -> BatteryType.Lc
                    else -> BatteryType.Unknown
                }

                //log("Cells: $cells")

                val voltage = buffer.getInt(150 + 3).toFloat() / 100f
                val current = buffer.getInt(154 + 3).toFloat() / -100f
                //log("Voltage: $voltage Current: $current")
                //log("Runtime: ${sysRuntime.seconds.toString()}")
                cellCount = cells
                return YYBmsEvent.DeviceInfo(
                    name = bluetoothName,
                    modelName = modelTypeName,
                    serialNumber = serialnumber,
                    batteryType = batteryType,
                    voltage = voltage,
                    current = current,
                    systemRuntime = sysRuntime
                )
            }

            prefixCellData -> {
                //log(hex)

                // Offset 3 current 2 byte
                val power = buffer.getShort(3).toInt()

                // Offset 5 cell voltages 24x 2 byte shorts
                val cellVoltage = FloatArray(cellCount) {
                    val position = it * 2 + 5
                    buffer.getShort(position).toFloat() / 1000f
                }
                val maxVolIndex = min(24, buffer[62+3].toInt())
                val minVolIndex = min(24, buffer[63+3].toInt())

                // Offset end 53

                //val bits = byteArray2BooleanArray(buffer[65], buffer[66], buffer[67], buffer[68])
                //log(bits.joinToString("") { if (it) "1" else "0" })
                //val discharging = byteArray2BooleanArray(buffer[65])[0]

                //But closer to the real value
                //log ("Current: ${"%.2f".format(current)}")
                val tmpMos = buffer[64+3].toUByte().toInt() - 40
                val tmp1 = buffer[66+3].toUByte().toInt() - 40
                val tmp2 = buffer[67+3].toUByte().toInt() - 40

                val ratedCapacity = buffer.getShort(79).toFloat() / 10f //Rated capacity
                val capacity2 = buffer.getShort(81).toFloat() / 10f //Fact capacity ??
                val electricityCalibration = capacity2.toDouble() / ratedCapacity.toDouble()

                val soc = buffer[83].toInt()

                val remainingWh = buffer.getInt(82+3)
                val remainingChargeMins = buffer.getShort(86 + 3)
                val remainingDischargeMins = buffer.getShort(88 + 3)


                //log("Soc: $soc% cap1: $capacity1 Ah $capacity2 Ah cellCount: $cellCount")
                val learnedCapSucCnt = buffer.getShort(90 + 3)
                val learnedCapacity = buffer.getShort(92 + 3)
                val learnedCapState = buffer[94 + 3]
                //log("Remaining wh: $remainingWh  discharge min: $remainingDischargeMins")
                //log("Learned cap: $learnedCapacity state: $learnedCapState succnt: $learnedCapSucCnt")
                val cycleCount = buffer[95 + 3].toInt()
                val reqChrgCurt = buffer[98 + 3]
                val shuntGain = buffer.getShort(100 + 3).toInt()
                val shuntOffset = buffer.getShort(102 + 3).toInt()
                //log("reqChrgCurt: $reqChrgCurt Learned cap: $learnedCapacity shuntGain: $shuntGain offset: $shuntOffset")

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
                val balanceBitsInt = buffer.getInt(118 + 3)
                val balanceBits = byteArray2BooleanArray(buffer[121], buffer[122], buffer[123])
                val balancing = balanceBits.find { it } ?: false
                val balanceState = if (balancing) "Balancing" else "Off"
                val enBalceVol = buffer.getShort(122 + 3).toFloat() / 100f
                val enBalceDiffVol = buffer.getShort(124 + 3).toFloat() / 100f
                val enBalceDetaVol = buffer.getShort(126 + 3).toFloat() / 100f
                //log("Enable balance voltage: $enBalceVol diff: $enBalceDiffVol deta: $enBalceDetaVol")
                val staticBal = buffer.getInt(126 + 3)

                val testBits = byteArray2BooleanArray(buffer[147], buffer[148])
                val text = testBits.mapIndexed { index, b -> if (b) "$index [1]" else "$index [0]" }
                    .joinToString(" ")
                //log("Bits: $text")

                // bit 1 and 6 charging disabled
                // bit 0 and 4 discharging disabled
                val bitsA = byteArray2BooleanArray(buffer[144 + 3])
                val dischargingEnabled = bitsA[0]  // also bit 4 flips when discharging is disabled
                val chargingEnabled = bitsA[1]     // also bit 6 flips when charging is disabled
                val bitsB = byteArray2BooleanArray(buffer[147 + 3])
                val chargeState = bitsB[2]
                val dischargeState = bitsB[3]
                // charge && discharge false -> idle
                // charge true -> charging
                // discharge true -> discharging
                // both true undefined state

                //log("Charge state: $chargeState dis: $dischargeState")
                // Calculate highest and lowest cell
                /*val (hvIndex, hvValue) = if (cellVoltage.isNotEmpty()) {
                    cellVoltage.withIndex().maxBy { (_, v) -> v }
                } else {
                    IndexedValue(0, 0f)
                }
                val (lvIndex, lvValue) = if (cellVoltage.isNotEmpty()) {
                    cellVoltage.withIndex().minBy { (_, v) -> v }
                } else {
                    IndexedValue(0, 0f)
                }*/
                return YYBmsEvent.CellInfo(
                    power = power,
                    cellVoltage = cellVoltage,
                    maxVoltageIndex = maxVolIndex,
                    minVoltageIndex = minVolIndex,
                    tempMos = tmpMos.toFloat(),
                    temp1 = tmp1.toFloat(),
                    temp2 = tmp2.toFloat(),
                    ratedCapacity = ratedCapacity,
                    factCapacity = capacity2,
                    soc = soc,
                    remainingWh = remainingWh,
                    cycleCount = cycleCount,
                    shuntGain = shuntGain,
                    shuntOffset = shuntOffset,
                    cellBalance = balanceBits,
                    enableBalancingVoltage = enBalceVol,
                    enableBalancingDiffVoltage = enBalceDiffVol,
                    enableBalancingDetaVoltage = enBalceDetaVol,
                    chargingEnabled = chargingEnabled,
                    dischargingEnabled = dischargingEnabled
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