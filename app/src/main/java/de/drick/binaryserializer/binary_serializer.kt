package de.drick.binaryserializer

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object BinarySerializer {
    inline fun <reified T> encode(value: T): ByteArray = encode(serializer(), value)

    fun <T> encode(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val output = ByteArrayOutputStream()
        encode(serializer, output, value)
        return output.toByteArray()
    }
    fun <T> encode(serializer: SerializationStrategy<T>, outputStream: OutputStream, value: T) {
        val encoder = BinaryEncoder(DataOutputStream(outputStream))
        encoder.encodeSerializableValue(serializer, value)
    }

    fun <T> decode(serializer: DeserializationStrategy<T>, data: ByteArray): T {
        val input = ByteArrayInputStream(data)
        return decode(serializer, input)
    }
    fun <T> decode(serializer: DeserializationStrategy<T>, inputStream: InputStream): T {
        val decoder = BinaryDecoder(DataInputStream(inputStream))
        return decoder.decodeSerializableValue(serializer)
    }
}


@OptIn(ExperimentalSerializationApi::class)
private class BinaryEncoder(
    val output: DataOutput,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractEncoder() {
    private val byteArraySerializer = serializer<ByteArray>()

    override fun encodeBoolean(value: Boolean) = output.writeByte(if (value) 1 else 0)
    override fun encodeByte(value: Byte) = output.writeByte(value.toInt())
    override fun encodeShort(value: Short) = output.writeShort(value.toInt())
    override fun encodeInt(value: Int) = output.writeInt(value)
    override fun encodeLong(value: Long) = output.writeLong(value)
    override fun encodeFloat(value: Float) = output.writeFloat(value)
    override fun encodeDouble(value: Double) = output.writeDouble(value)
    override fun encodeChar(value: Char) = output.writeChar(value.code)
    override fun encodeString(value: String) = output.writeUTF(value)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = output.writeInt(index)

    override fun encodeNull() = encodeBoolean(false)
    override fun encodeNotNullMark() = encodeBoolean(true)

    fun encodeByteArray(bytes: ByteArray) {
        encodeCompactSize(bytes.size)
        output.write(bytes)
    }

    private fun encodeCompactSize(value: Int) {
        if (value < 0xff) {
            output.writeByte(value)
        } else {
            output.writeByte(0xff)
            output.writeInt(value)
        }
    }


    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (serializer.descriptor == byteArraySerializer.descriptor) {
            encodeByteArray(value as ByteArray)
        } else {
            super.encodeSerializableValue(serializer, value)
        }
    }

    override fun beginCollection(
        descriptor: SerialDescriptor, collectionSize: Int
    ): CompositeEncoder {
        encodeInt(collectionSize)
        return this
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class BinaryDecoder(
    val input: DataInput,
    var elementsCount: Int = 0,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractDecoder() {
    private val byteArraySerializer = serializer<ByteArray>()
    private var elementIndex = 0

    override fun decodeBoolean(): Boolean = input.readBoolean()
    override fun decodeByte(): Byte = input.readByte()
    override fun decodeShort(): Short = input.readShort()
    override fun decodeInt(): Int = input.readInt()
    override fun decodeLong(): Long = input.readLong()
    override fun decodeFloat(): Float = input.readFloat()
    override fun decodeDouble(): Double = input.readDouble()
    override fun decodeChar(): Char = input.readChar()
    override fun decodeString(): String = input.readUTF()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeNotNullMark(): Boolean = decodeBoolean()

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val size = decodeInt()
        elementsCount = size
        return size
    }

    private fun decodeByteArray(): ByteArray {
        val bytes = ByteArray(decodeCompactSize())
        input.readFully(bytes)
        return bytes
    }
    private fun decodeCompactSize(): Int {
        val byte = input.readByte().toInt() and 0xff
        if (byte < 0xff) return byte
        return input.readInt()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>, previousValue: T?): T =
        if (deserializer.descriptor == byteArraySerializer.descriptor)
            decodeByteArray() as T
        else
            super.decodeSerializableValue(deserializer, previousValue)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = BinaryDecoder(input, descriptor.elementsCount, serializersModule)

    override fun decodeSequentially() = true
}