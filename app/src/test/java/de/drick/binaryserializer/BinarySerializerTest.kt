package de.drick.binaryserializer

import junit.framework.TestCase
import kotlinx.serialization.Serializable
import org.junit.Test


class BinarySerializerTest {

    @Serializable
    data class PrimitiveTestData(
        val char: Char,
        val string: String,
        val boolean: Boolean,
        val byte: Byte,
        val short: Short,
        val integer: Int,
        val long: Long,
        val float: Float,
        val double: Double
    )

    @Test
    fun encodeDecodePrimitiveData() {
        val testData = PrimitiveTestData(
            char = 'A',
            string = "Unit test",
            boolean = true,
            byte = 124.toByte(),
            short = 12000.toShort(),
            integer = 343577787,
            long = 23134353345L,
            float = 44.12342f,
            double = 3.1423244354
        )
        val encodedBytes = BinarySerializer.encode(PrimitiveTestData.serializer(), testData)

        val decoded = BinarySerializer.decode(PrimitiveTestData.serializer(), encodedBytes)
        TestCase.assertEquals(decoded, testData)
    }

}