package de.drick.bmsmonitor.bms_adapter

fun ByteArray.getShort(offset: Int): Short =
    (this[offset].toInt() + (this[offset + 1].toInt() shl 8)).toShort()
fun ByteArray.getUShort(offset: Int): UShort =
    (this[offset].toUByte().toInt() + (this[offset + 1].toUByte().toInt() shl 8)).toUShort()
fun ByteArray.getUInt(offset: Int): UInt =
    getUShort(offset + 2).toUInt() shl 16 or getUShort(offset).toUInt()

fun crc(data: ByteArray, length: Int): Byte {
    var crc: Byte = 0
    for (i in 0 until length)  {
        crc = (crc + data[i]).toByte()
    }
    return crc
}