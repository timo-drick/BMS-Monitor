package de.drick.bmsmonitor.repository

import android.content.Context
import de.drick.binaryserializer.BinarySerializer
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

@Serializable
data class BinaryDataChunk(
    val timeStamp: Long,
    val data: ByteArray
)

@Serializable
data class HeaderData(
    val deviceAddress: String,
)

data class RecordingInfo(
    val file: File,
    val name: String
)

class BinaryRepository(ctx: Context) {
    private val path = File(ctx.filesDir, "raw_recordings")

    fun startRecording(deviceAddress: String): BinaryRecorder {
        val id = UUID.randomUUID().toString()
        path.mkdirs()
        val file = File(path, id)
        file.createNewFile()
        return BinaryRecorder(file, HeaderData(deviceAddress))
    }

    fun listRecordings(): List<RecordingInfo> {
        return path.listFiles()?.map {
            RecordingInfo(
                file = it,
                name = it.name //TODO
            )
        } ?: emptyList()
    }
}

class BinaryRecorder(outputFile: File, headerData: HeaderData): Closeable {
    private val output = DataOutputStream(outputFile.outputStream())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        log("Start")
        BinarySerializer.encode(HeaderData.serializer(), output, headerData)
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun add(data: ByteArray) = withContext(singleThreadDispatcher) {
        log("Add: ${data.toHexString()}")
        BinarySerializer.encode(BinaryDataChunk.serializer(), output, BinaryDataChunk(System.currentTimeMillis(), data))
    }

    override fun close() {
        log("Close")
        output.close()
    }

}
