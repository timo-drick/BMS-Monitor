package de.drick.bmsmonitor.repository

import android.content.Context
import android.location.Location
import android.os.Build
import de.drick.binaryserializer.BinarySerializer
import de.drick.bmsmonitor.bms_adapter.BmsAdapter
import de.drick.bmsmonitor.bms_adapter.BmsType
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    val timeStamp: Long,
    val name: String,
    val bmsType: BmsType,
    val header: HeaderData,
    val soc: Int?,
    val voltage: Float?
)


fun main() {
    val testRecordings = File("/home/timo/projects/android/BMS-Monitor/infos/recordings")
    /*val repo = RecordingRepository(testRecordings)
    val recording = repo.listRecordings()[5]
    val info = repo.getHeader(recording.file)
    println(info)*/
    /*runBlocking {
        val list = getDataFlow(recording.file).map {
            val temp = min(it.tempMos, min(it.temp0, it.temp1))
            Analytics(
                soc = it.stateOfCharge,
                voltage = it.cellVoltages.sum(),
                current = it.current,
                temp = temp
            )
        }.toList()
        list.forEach {
            val power = it.current * it.voltage
            println("    %d%% %4.1fv %4.1fA %5.0fW %3.0f°C".format(it.soc, it.voltage, it.current, power, it.temp))
        }
        val soc: Int = list.maxOf { it.soc }
        val voltage = list.maxOf { it.voltage }
        val current = list.maxOf { it.current }
        val power = list.maxOf { it.voltage * it.current }
        val temp = list.maxOf { it.temp }
        print("max %d%% %4.1fv %4.1fA %5.0fW %3.0f°C".format(soc, voltage, current, power, temp))
    }*/
}

data class RecordEntry(
    val time: Long,
    val voltage: Float,
    val current: Float,
    val soc: Int,
    val temp: Float
)

class RecordingRepository(private val ctx: Context) {
    private val path = File(ctx.filesDir, "raw_recordings")
    private val pathLocation = File(ctx.filesDir, "location_recordings")

    fun startRecordingBMS(deviceAddress: String): BMSRecorder {
        val id = UUID.randomUUID().toString()
        val rawBmsDatafile = File(path, id)
        val locationFile = File(pathLocation, id)
        path.mkdirs()
        rawBmsDatafile.createNewFile()
        pathLocation.mkdirs()
        locationFile.createNewFile()
        return BMSRecorder(rawBmsDatafile, locationFile, HeaderData(deviceAddress))
    }

    fun listRecordings(): List<RecordingInfo> {
        return path.listFiles()?.mapNotNull {
            try {
                getHeader(it)
            } catch (err: Throwable) {
                log(err)
                null
            }
        }?.sortedByDescending { it.timeStamp } ?: emptyList()
    }

    private fun getHeader(file: File): RecordingInfo = file.inputStream().use { inputStream ->
        val header = BinarySerializer.decode(HeaderData.serializer(), inputStream)
        val type = BmsType.fromMacAddress(header.deviceAddress)
        val adapter = BmsAdapter(ctx, header.deviceAddress)
        var cellInfo: GeneralCellInfo? = null
        var timeStamp: Long = 0
        while (cellInfo == null && inputStream.available() > 0) {
            val data = BinarySerializer.decode(BinaryDataChunk.serializer(), inputStream)
            if (timeStamp == 0L) timeStamp = data.timeStamp
            val decoded = adapter.decodeRaw(data.data)
            if (decoded is GeneralCellInfo) {
                cellInfo = decoded
            }
        }
        RecordingInfo(
            file = file,
            timeStamp = timeStamp,
            name = cellInfo?.deviceInfo?.name ?: header.deviceAddress.substring(9),
            bmsType = type,
            header = header,
            soc = cellInfo?.stateOfCharge,
            voltage = cellInfo?.cellVoltages?.sum()
        )
    }

    fun getDataFlow(file: File): Flow<RecordEntry> = flow {
        file.inputStream().use { inputStream ->
            val header = BinarySerializer.decode(HeaderData.serializer(), inputStream)
            println(header)
            val adapter = BmsAdapter(ctx, header.deviceAddress)
            while (inputStream.available() > 0) {
                val data = BinarySerializer.decode(BinaryDataChunk.serializer(), inputStream)
                val decoded = adapter.decodeRaw(data.data)
                if (decoded is GeneralCellInfo) {
                    val entry = RecordEntry(
                        time = data.timeStamp,
                        voltage = decoded.cellVoltages.sum(),
                        current = decoded.current,
                        soc = decoded.stateOfCharge,
                        temp = (decoded.tempMos + decoded.temp0 + decoded.temp1) / 3f
                    )
                    emit(entry)
                }
            }
        }
    }
}

@Serializable
data class LocationPoint(
    val timeStamp: Long,
    val latitude: Double, // Latitude in degrees
    val longitude: Double,// Longitude in degrees
    /**
     * Estimated horizontal accuracy radius in meters of this location at the 68th percentile
     * confidence level. This means that there is a 68% chance that the true location of the device
     * is within a distance of this uncertainty of the reported location. Another way of putting
     * this is that if a circle with a radius equal to this accuracy is drawn around the reported
     * location, there is a 68% chance that the true location falls within this circle.
     * This accuracy value is only valid for horizontal positioning, and not vertical positioning.
     */
    val accuracy: Float?,
    val bearing: Float?, // Bearing at the time of this location in degrees. Range [0, 360)
    val bearingAccuracy: Float?, // Estimated bearing accuracy in degrees of this location at the 68th percentile confidence level.
    val speed: Float?, // Speed at the time of this location in meters per second.
    val speedAccuracy: Float?, // Estimated speed accuracy in meters per second of this location at the 68th percentile confidence level.
    val altitude: Double?, // The altitude of this location in meters above the WGS84 reference ellipsoid.
    val verticalAccuracy: Float? // Estimated altitude accuracy in meters of this location at the 68th percentile confidence level.
)

class BMSRecorder(rawBmsDataFile: File, locationFile: File, headerData: HeaderData): Closeable {

    private val outputRawBmsData = DataOutputStream(rawBmsDataFile.outputStream())
    private val outputLocationData = DataOutputStream(locationFile.outputStream())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        log("Start")
        BinarySerializer.encode(HeaderData.serializer(), outputRawBmsData, headerData)
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun add(data: ByteArray) = withContext(singleThreadDispatcher) {
        log("Add: ${data.toHexString()}")
        BinarySerializer.encode(BinaryDataChunk.serializer(), outputRawBmsData, BinaryDataChunk(System.currentTimeMillis(), data))
    }

    fun add(location: Location) {
        val point = LocationPoint(
            timeStamp = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            bearingAccuracy = if (Build.VERSION.SDK_INT >= 26 && location.hasBearingAccuracy())
                location.bearingAccuracyDegrees else null,
            speed = if (location.hasSpeed()) location.speed else null,
            speedAccuracy = if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy())
                location.speedAccuracyMetersPerSecond else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy())
                location.verticalAccuracyMeters else null
        )
        BinarySerializer.encode(LocationPoint.serializer(), outputLocationData, point)
    }

    override fun close() {
        log("Close")
        try {
            outputRawBmsData.close()
        } catch (err: Throwable) {
            log(err)
        }
        try {
            outputLocationData.close()
        } catch (err: Throwable) {
            log(err)
        }
    }

}
