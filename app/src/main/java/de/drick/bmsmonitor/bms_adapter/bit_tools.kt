package de.drick.bmsmonitor.bms_adapter

fun crc(data: ByteArray, length: Int): Byte {
    var crc: Byte = 0
    for (i in 0 until length)  {
        crc = (crc + data[i]).toByte()
    }
    return crc
}