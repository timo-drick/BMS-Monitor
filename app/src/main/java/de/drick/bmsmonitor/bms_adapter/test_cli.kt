package de.drick.bmsmonitor.bms_adapter

@OptIn(ExperimentalStdlibApi::class)
private val data = """ 
55aaeb9002eb9f0d460d610d8c0d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000740d590000018200850079007c0000000000000000000000000000000000000000000000000000000000000000000000000000000000
""".trim().hexToByteArray()

private val testData = listOf(
    "55aaeb900238980d520d6c0d8d0d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000790d4600000179007e007900770000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "00000000000000000000000000000000e50000000000",
    "e33500000000000000000000c500cb000000000000000060db120000881300000000000001000000640000004e350b0001010000000000000000000000000000070001000000d1030000000046793f40000000006305000000010001000300008c05000000000000000000000000000000000000000000000000000000000000",
    "000000000000000000feff7fdc2f0101000000000099",
    "55aaeb900239980d520d6c0d8e0d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000790d4600000179007e007900770000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "00000000000000000000000000000000e50000000000",
    "e53500000000000000000000c500cb000000000000000060db120000881300000000000001000000640000004e350b0001010000000000000000000000000000070001000000d1030000000046793f40000000006305000000010001000300009105000000000000000000000000000000000000000000000000000000000000",
    "000000000000000000feff7fdc2f01010000000000a2",
    "55aaeb90027cfb0ca30cf40c1f0d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000ec0c7900030179007e007900770000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "0000000000000000000000000000000030f800000000",
    "b0330000000000000000000030f830f8000400000000005fa012000088130000000000003c0000006400000092510b0001010000000000000000000000000000000001000000d1030000000046793f40000000002b05000000010001000300003720010000000000000000000000000000000000000000000000000000000000",
    "000000000000000000feff7fdc2f0101000000000009",
    "55aaeb90027dfb0ca30cf40c1d0d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000ec0c7900030179007e007900770000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "0000000000000000000000000000000030f800000000",
    "af330000000000000000000030f830f8000400000000005fa012000088130000000000003c0000006400000092510b0001010000000000000000000000000000000001000000d1030000000046793f40000000002b05000000010001000300003c20010000000000000000000000000000000000000000000000000000000000",
    "000000000000000000feff7fdc2f010100000000000c",
    "55aaeb900293e60c8b0ce20c110d00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f000000d90c83000301000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "0000000000000000000000000000000030f800000000",
    "65330000000000000000000030f830f8040400000000005f981200008813000000000000440000006400000022550b0000000000000000000000000000000000000001000000d1030000000046793f4000000000230500000001000100030000dc43010000000000000000000000000000000000000000000000000000000000",
    "000000000000000000feff7fdc2f01010000000000ee"
)


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

var is32s: Boolean = false

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    //val decoder = JkBmsDecoder()
    //decoder.decode(testData)
    //val data = "9f0d".hexToByteArray()
    //println("Short: ${getShort(data, 0)} byte1: ${data[0].toUByte()} byte2: ${data[1].toUByte()}")
    is32s = true
    testData.forEach {
        addData(it.hexToByteArray())
    }
    val hexFormat = HexFormat {
        number.prefix = "0x"
    }
    val data = jk_b2a24s20p_jk02
        .split("\n")
        .map {line ->
            line
                .split(" ")
                .map {
                    if (it.startsWith("0x"))
                        it.hexToByte(hexFormat)
                    else it.hexToByte()
                }
                .toByteArray()
        }
    //println(data[0].toHexString(hexFormat))
    is32s = false
    /*data.forEach {
        addData(it)
    }*/
}

fun addData(newData: ByteArray) {
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
            decodeJk02(raw)
            frameBuffer.clear()
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun decodeJk02(data: ByteArray) {
    println("Data: ${data.toHexString()}")
    var offset = if (is32s) 16 else 0
    val cells = countBits(data[54 + offset], data[55 + offset], data[56 + offset])
    println("Cells: $cells")
    val voltages = FloatArray(cells, init = { i -> getShort(data, i * 2 + 6).toFloat() * 0.001f })
    println("Voltages: ${voltages.joinToString { "%.3f".format(it) }}")
    val avarage = getShort(data, 58 + offset).toFloat() * 0.001f
    val delta = getShort(data, 60 + offset).toFloat() * 0.001f
    val maxCell = data[62 + offset]
    val minCell = data[63 + offset]

    offset *= 2
    val voltage = getInt(data, 118 + offset).toFloat() * 0.001f
    println("Avarage: $avarage delta: $delta max cell: ${maxCell + 1} min cell: ${minCell + 1} v: $voltage")
    val current = getInt(data, 126 + offset).toFloat() * 0.001f
    println("Current: $current")
    //Temp
    val temp0 = getShort(data, 130 + offset).toFloat() * 0.1f
    val temp1 = getShort(data, 132 + offset).toFloat() * 0.1f
    val mosTemp = getShort(data, 134 + offset).toFloat() * 0.1f
    println("Temp1: $temp0 T2: $temp1 mos: $mosTemp")
    if (is32s) {
        val mask = data[134 + offset].toUByte().toInt() shl 8 or data[134 + 1 + offset].toUByte().toInt()
        println("Error bitmask: $mask")
        Errors.entries.forEachIndexed { index, error ->
            if (mask and (1 shl index) > 0) {
                println("Error: ${error.description}")
            }
        }
    }
    val soc = data[141].toInt()
}

enum class Errors(val description: String) {
    CHARGE_OVERTEMP("Charge Overtemperature"),           // 0000 0000 0000 0001
    CHARGE_UNDERTEMP("Charge Undertemperature"),         // 0000 0000 0000 0010
    CPUAUX_ANOMALY("CPUAUX Anomaly"),                    // 0000 0000 0000 0100
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


private fun countBits(vararg bytes: Byte): Int {
    var bits = 0
    for (i in 0 until bytes.size * 8) {
        if (bytes[i / 8].toInt() and (1 shl i % 8) > 0) bits++
    }
    return bits
}

private fun getShort(data: ByteArray, offset: Int): UShort =
    (data[offset].toUByte().toInt() + (data[offset + 1].toUByte().toInt() shl 8)).toUShort()
private fun getInt(data: ByteArray, offset: Int): UInt =
    getShort(data, offset + 2).toUInt() shl 16 or getShort(data, offset).toUInt()

fun crc(data: ByteArray, length: Int): Byte {
    var crc: Byte = 0
    for (i in 0 until length)  {
        crc = (crc + data[i]).toByte()
    }
    return crc
}