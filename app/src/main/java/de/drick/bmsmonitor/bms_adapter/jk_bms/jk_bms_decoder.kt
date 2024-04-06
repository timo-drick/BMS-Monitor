package de.drick.bmsmonitor.bms_adapter.jk_bms

import de.drick.bmsmonitor.bms_adapter.stringFromBytes
import de.drick.log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Protocol implementation used to understand the protocol data:
 * https://github.com/syssi/esphome-jk-bms/blob/main/components/jk_bms_ble/jk_bms_ble.cpp
 */

class JkBmsDecoder() {
    enum class Command(
        val code: Byte
    ) {
        COMMAND_CELL_INFO(0x96.toByte()),
        COMMAND_DEVICE_INFO(0x97.toByte())
    }

    fun addData(newData: ByteArray): JKBmsEvent? {
        var result: JKBmsEvent? = null
        if (frameBuffer.size > MAX_RESPONSE_SIZE) {
            frameBuffer.clear()
        }
        // Flush buffer on every preamble
        if (newData[0] == 0x55.toByte() && newData[1] == 0xAA.toByte() && newData[2] == 0xEB.toByte() && newData[3] == 0x90.toByte()) {
            frameBuffer.clear()
        }
        frameBuffer.insert(newData)
        if (frameBuffer.size >= MIN_RESPONSE_SIZE) {
            val frameSize = 300
            val raw = frameBuffer.build()
            val computedCrc = crc(raw, frameSize - 1)
            val remoteCrc = raw[frameSize - 1]
            if (computedCrc != remoteCrc) {
                println("CRC error crc: $remoteCrc expected: $computedCrc")
            } else {
                result = decodeJk02(raw)
                frameBuffer.clear()
            }
        }
        return result
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun decodeJk02(data: ByteArray): JKBmsEvent? {
        //val hex = data.toHexString()
        //log(hex)
        val frameType = data[4]
        return when (frameType) {
            0x01.toByte() -> {
                //TODO decode setting
                null
            }
            0x02.toByte() -> {
                return decodeCellData(data)
            }
            0x03.toByte() -> {
                return decodeDeviceInfo(data)
            }
            else -> {
                log("Unknown message type!")
                null
            }
        }
    }
    private fun decodeDeviceInfo(data: ByteArray): JKBmsEvent.DeviceInfo {
        val vendorId = stringFromBytes(data, 6, 24)
        log("Vendor id: $vendorId")
        return JKBmsEvent.DeviceInfo(
            name = vendorId,
            longName = ""
        )
    }


    private enum class ProtocolVersion {
        JK02_24S, JK02_32S, JK04
    }
    private val protocolVersion = ProtocolVersion.JK02_24S

    private fun decodeCellData(data: ByteArray): JKBmsEvent.CellInfo {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val is32s = true
        var offset = if (is32s) 16 else 0
        val cells = countBits(data[54 + offset], data[55 + offset], data[56 + offset])
        val cellVoltages = FloatArray(cells, init = { i -> buffer.getShort(i * 2 + 6).toFloat() * 0.001f })
        val cellAverage = buffer.getShort(58 + offset).toFloat() * 0.001f
        val cellDelta = buffer.getShort(60 + offset).toFloat() * 0.001f
        val cellMaxIndex = buffer[62 + offset].toInt()
        val cellMinIndex = buffer[63 + offset].toInt()

        val cellBalanceDischargeIndex = buffer[62 + offset].toInt()
        val cellBalanceChargeIndex = buffer[63 + offset].toInt()

        offset *= 2

        val balancingCurrent = buffer.getShort(138 + offset).toFloat() * 0.001f
        val balancingState = when(buffer[140 + offset]) {
            0x01.toByte() -> BalanceState.Charging
            0x02.toByte() -> BalanceState.Discharging
            else -> BalanceState.Off
        }
        val cellBalanceActive = BooleanArray(cells) { index ->
            when (balancingState) {
                BalanceState.Off -> false
                BalanceState.Charging -> cellBalanceChargeIndex == index
                BalanceState.Discharging -> cellBalanceDischargeIndex == index
            }
        }

        val voltage = buffer.getInt(118 + offset).toFloat() * 0.001f
        val current = buffer.getInt(126 + offset).toFloat() * 0.001f
        val tempMosIndex = if (is32s) 112 else 134
        val tempMos = buffer.getShort(tempMosIndex + offset).toFloat() * 0.1f
        val temp0 = buffer.getShort(130 + offset).toFloat() * 0.1f
        val temp1 = buffer.getShort(132 + offset).toFloat() * 0.1f
        val errorOffset = if (is32s) 134 else 136
        val mask = data[errorOffset + offset].toUByte().toInt() shl 8 or data[errorOffset + 1 + offset].toUByte().toInt()
        //val mask = buffer.getShort(134 + offset).toUShort().toInt()
        val errors = JKBmsErrors.entries.mapIndexedNotNull { index, error ->
            if (mask and (1 shl index) > 0) {
                error
            } else {
                null
            }
        }

        val soc = buffer[141 + offset].toInt()
        val capacityRemaining = buffer.getInt(142 + offset).toFloat() * 0.001f
        val capacityTotal = buffer.getInt(146 + offset).toFloat() * 0.001f
        val cycleCount = buffer.getInt(150 + offset)
        val cycleCapacity = buffer.getInt(154 + offset).toFloat() * 0.001f
        val totalRuntime = buffer.getInt(162 + offset)
        val chargingEnabled = buffer[166 + offset] > 0
        val dischargingEnabled = buffer[167 + offset] > 0
        val heatingEnabled = buffer[192 + offset] > 0
        val heatingCurrent = buffer.getShort(204 + offset).toFloat() * 0.001f

        return JKBmsEvent.CellInfo(
            totalVoltage = voltage,
            current = current,
            soc = soc,
            capacity = capacityTotal,
            capacityRemaining = capacityRemaining,
            cellVoltageList = cellVoltages,
            cellAverage = cellAverage,
            cellDelta = cellDelta,
            cellMaxIndex = cellMaxIndex,
            cellMinIndex = cellMinIndex,
            balanceState = balancingState,
            balanceCurrent = balancingCurrent,
            cellBalanceActive = cellBalanceActive,
            temp0 = temp0,
            temp1 = temp1,
            tempMos = tempMos,
            cycleCount = cycleCount,
            cycleCapacity = cycleCapacity,
            totalRuntime = totalRuntime.toUInt(),
            chargingEnabled = chargingEnabled,
            dischargingEnabled = dischargingEnabled,
            heatingEnabled = heatingEnabled,
            heatingCurrent = heatingCurrent,
            errors = errors
        )
    }

    fun encode(command: Command, value: Int, length: Byte): ByteArray {
        val data = ByteArray(20, init = { 0 })
        data[0] = 0xAA.toByte() // start sequence
        data[1] = 0x55.toByte() // start sequence
        data[2] = 0x90.toByte() // start sequence
        data[3] = 0xEB.toByte() // start sequence
        data[4] = command.code  // holding register
        data[5] = length        // size of the value in byte
        data[6] = (value shr 0).toByte()
        data[7] = (value shr 8).toByte()
        data[8] = (value shr 16).toByte()
        data[9] = (value shr 24).toByte()
        data[19] = crc(data, 19)
        return data
    }
    private fun countBits(vararg bytes: Byte): Int {
        var bits = 0
        for (i in 0 until bytes.size * 8) {
            if (bytes[i / 8].toInt() and (1 shl i % 8) > 0) bits++
        }
        return bits
    }

    private fun crc(data: ByteArray, length: Int): Byte {
        var crc: Byte = 0
        for (i in 0 until length)  {
            crc = (crc + data[i]).toByte()
        }
        return crc
    }
}

class FrameBuffer() {
    private val frameBuffer = mutableListOf<ByteArray>()
    val size get() = frameBuffer.sumOf { it.size }
    fun insert(data: ByteArray) {
        frameBuffer.add(data)
    }
    fun clear() {
        frameBuffer.clear()
    }
    fun build(): ByteArray {
        val buffer = frameBuffer
        var data = byteArrayOf()
        buffer.forEach {
            data += it
        }
        return data
    }
}
val frameBuffer = FrameBuffer()
const val MIN_RESPONSE_SIZE = 300
const val MAX_RESPONSE_SIZE = 320
