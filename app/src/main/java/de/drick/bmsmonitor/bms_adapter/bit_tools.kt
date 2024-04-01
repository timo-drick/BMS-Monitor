package de.drick.bmsmonitor.bms_adapter

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun crc8Add(data: ByteArray, length: Int): Byte {
    var crc: Byte = 0
    for (i in 0 until length)  {
        crc = (crc + data[i]).toByte()
    }
    return crc
}

/**
 * Online tool to find out crc:
 * https://www.lammertbies.nl/comm/info/crc-calculation
 *
 * Converted from c sourcecode from here:
 * https://github.com/lammertb/libcrc/blob/v2.0/src/crc16.c
 */
private const val CRC_POLY_16 = 0xA001
private const val CRC_START_MODBUS = 0xffff

private fun intCrc16Tab() = IntArray(256) { i ->
    var crc = 0
    var c = i
    for (j in 0 until 8) {
        if ((crc xor c) and 0x0001 > 0) {
            crc = (crc shr 1) xor CRC_POLY_16
        } else {
            crc = crc shr 1
        }
        c = c shr 1
    }
    crc
}

private val crcTab16 = intCrc16Tab()

fun crc16Modbus(data: ByteArray, length: Int): Short {
    var crc = CRC_START_MODBUS
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until length)  {
        val short_c = buffer[i].toInt()//0x00ff and buffer.getShort(i).toInt()
        val tmp = crc xor short_c
        crc = (crc shr 8) xor crcTab16[tmp and 0xff]
    }
    return crc.toShort()
}