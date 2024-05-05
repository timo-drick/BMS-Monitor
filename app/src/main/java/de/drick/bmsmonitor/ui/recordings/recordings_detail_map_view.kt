package de.drick.bmsmonitor.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.repository.LocationPoint
import de.drick.bmsmonitor.repository.RecordEntry
import de.drick.bmsmonitor.ui.compose_wrapper.rememberMapBoxState
import de.drick.bmsmonitor.ui.getSocIcon
import de.drick.log
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt


private val mockDataFlow = flow<GeneralCellInfo> {

}


@Preview(device = "id:pixel_8", showBackground = true)
@Composable
private fun PreviewRecordingsMapView() {
    val speed = MinMaxStartStop(
        start = 0f,
        stop = 0f,
        min = 0f,
        max = 46f
    ).format("Speed km/h", "%.1f")
    val data = UIDetailData(
        durationText = "Duration: 20:00",
        distanceText = "Distance: 22.5 km",
        maxSpeedText = "Speed: 46 km/h",
        stateOfChargeText = "Battery 100% - 80% diff -20%",
        gps = GpsData(
            listOf(
                GpsRecord(0L, GeoPoint(0.0, 0.0), 0f),
                GpsRecord(200L, GeoPoint(0.1, 0.2), 5f)
            )
        ),
        bmsRecords = listOf(
            RecordEntry(0L, 67.0f, 0f, 65f, 25f),
            RecordEntry(200L, 65.0f, 10f, 64f, 26f)
        )
    )
    MaterialTheme {
        RecordingsMapViewData(
            modifier = Modifier.fillMaxSize(),
            data = data
        )
    }
}

data class UIDetailData(
    val durationText: String,
    val distanceText: String,
    val maxSpeedText: String,
    val stateOfChargeText: String,
    val gps: GpsData,
    val bmsRecords: List<RecordEntry>
)

@Composable
fun RecordingsMapView(
    dataList: PersistentList<RecordEntry>,
    locationList: PersistentList<LocationPoint>,
    modifier: Modifier = Modifier
) {
    var uiDetailData by remember { mutableStateOf<UIDetailData?>(null) }

    LaunchedEffect(Unit) {
        val gpsList = locationList
            .map {
                GpsRecord(
                    timeStamp = it.timeStamp,
                    position = GeoPoint(it.latitude, it.longitude),
                    speed = it.speed ?: 0f
                )
            }
        val first = dataList.first()
        val last = dataList.last()
        val durationSeconds = (last.time - first.time) / 1000
        val timeText = formatDuration(durationSeconds)
        val distance = calculatePolylineDistance(gpsList.map { it.position })
        val maxSpeed = locationList.filter {
            (it.speedAccuracy ?: 10f) < .3f
        }.maxOf { it.speed ?: 0f }
        val avgSpeed = distance / durationSeconds
        val maxSpeedText = "Speed avg: %.0f km/h max:%.0f km/h".format(avgSpeed * 3.6f, maxSpeed * 3.6f)
        val socText = "Battery %.0f%% - %.0f%% diff: %.0f%%".format(
            first.soc, last.soc, last.soc - first.soc
        )

        uiDetailData = UIDetailData(
            durationText = "Duration: $timeText",
            distanceText = "Distance: %.1f km".format(distance / 1000f),
            maxSpeedText = maxSpeedText,
            stateOfChargeText = socText,
            gps = GpsData(gpsList),
            bmsRecords = dataList
        )
    }
    uiDetailData?.let { data ->
        RecordingsMapViewData(
            modifier = modifier,
            data = data
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingsMapViewData(
    data: UIDetailData,
    modifier: Modifier = Modifier
) {
    val playbackState = remember { PlaybackState(data.gps.wayPoints, data.bmsRecords) }
    LaunchedEffect(Unit) {
        while (isActive) {
            playbackState.clock()
            delay(500)
        }
    }
    Column(
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Statistics",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(data.durationText)
            Text(data.distanceText)
            Text(data.maxSpeedText)
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(data.stateOfChargeText)
        }

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Playback",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            val speedText = remember(playbackState.positionGps.speed) {
                "Speed: %.0f km/h".format(playbackState.positionGps.speed * 3.6f)
            }
            Text(speedText)
        }
        val record = playbackState.positionRecord
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val powerText = remember(record) {
                "Power: %.0f W".format(record.voltage * record.current)
            }
            val socText = "%.0f%%".format(record.soc)
            val socIcon = getSocIcon(record.soc.roundToInt())
            Icon(imageVector = socIcon, contentDescription = "Battery icon")
            Text(socText)
            Spacer(Modifier.width(8.dp))
            Text(powerText)
        }
        Spacer(Modifier.height(8.dp))
        val mapBoxState = rememberMapBoxState()
        GpsView(
            modifier = Modifier.weight(1f),
            mapBoxState = mapBoxState,
            gpsData = data.gps,
            currentPosition = playbackState.positionGps
        )
        PlayerControlBar(
            modifier = Modifier.padding(horizontal = 8.dp),
            controlButtonState = playbackState.controlState,
            progress = playbackState.progress,
            onTogglePlay = { playbackState.toggleState() },
            onSeek = { playbackState.seekProgress(it) }
        )
    }
}

enum class ControlButtonState(val icon: ImageVector) {
    PLAY(Icons.Default.PlayArrow),
    PAUSE(Icons.Default.Pause),
    REPLAY(Icons.Default.Replay)
}

@Composable
fun PlayerControlBar(
    controlButtonState: ControlButtonState,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.size(64.dp),
            onClick = onTogglePlay,
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                imageVector = controlButtonState.icon,
                contentDescription = "Play/Pause",
            )
        }
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = progress,
            onValueChange = onSeek
        )
    }
}

class PlaybackState(
    private val wayPoints: List<GpsRecord>,
    private val bmsRecords: List<RecordEntry>
) {
    private val startTS = bmsRecords.first().time
    private val stopTS = bmsRecords.last().time
    private val duration = stopTS - startTS

    var progress by mutableFloatStateOf(0f)
        private set
    var controlState by mutableStateOf(ControlButtonState.PLAY)
        private set

    var positionGps by mutableStateOf(wayPoints.first())
        private set
    var positionRecord by mutableStateOf(bmsRecords.first())
        private set

    private var clockPlayStartTS: Long = 0L
    private var clockPausePosition: Long = 0L
    private var playing = false
    private var currentGPSIndex = 0
    private var currentRecordIndex = 0

    fun clock() {
        if (playing) {
            val position = System.currentTimeMillis() - clockPlayStartTS
            progress = position.toFloat() / duration.toFloat()
            val timePosition = position + startTS
            seekLocation(timePosition)
            seekRecord(timePosition)
            if (position >= duration) {
                playing = false
                controlState = ControlButtonState.REPLAY
            }
            log("Current play position: $position")
        }
    }

    fun toggleState() {
        when (controlState) {
            ControlButtonState.PLAY -> {
                clockPlayStartTS = System.currentTimeMillis() - clockPausePosition
                playing = true
                controlState = ControlButtonState.PAUSE
            }
            ControlButtonState.PAUSE -> {
                playing = false
                clockPausePosition = System.currentTimeMillis() - clockPlayStartTS
                controlState = ControlButtonState.PLAY
            }
            ControlButtonState.REPLAY -> {
                clockPlayStartTS = System.currentTimeMillis()
                clockPausePosition = 0L
                playing = true
                controlState = ControlButtonState.PAUSE
                clock()
            }
        }
    }

    fun seekProgress(progress: Float) {
        val seekPosition = ((stopTS - startTS) * progress).toLong()
        this.progress = progress
        clockPlayStartTS = System.currentTimeMillis() - seekPosition
        clockPausePosition = seekPosition
        val pos = seekPosition + startTS
        seekRecord(pos)
        seekLocation(pos)
    }

    private fun gpsTS(index: Int) = wayPoints[index].timeStamp

    private fun seekLocation(position: Long) {
        var nextIndex: Int
        if (gpsTS(currentGPSIndex) > position) {
            nextIndex = currentGPSIndex - 1
            while (nextIndex > 0) {
                if (gpsTS(nextIndex) < position) break
                nextIndex -= 1
            }
            nextIndex++
        } else {
            nextIndex = currentGPSIndex + 1
            while (nextIndex < wayPoints.size) {
                val nextPos = gpsTS(nextIndex)
                if (nextPos > position) break
                nextIndex += 1
            }
            nextIndex--
        }
        if (nextIndex != currentGPSIndex) {
            currentGPSIndex = nextIndex
            positionGps = wayPoints[currentGPSIndex]
            log("Current gps index: $currentGPSIndex")
        }
    }

    private fun recordTS(index: Int) = bmsRecords[index].time

    private fun seekRecord(position: Long) {
        var nextIndex: Int
        if (recordTS(currentRecordIndex) > position) {
            nextIndex = currentRecordIndex - 1
            while (nextIndex > 0) {
                if (recordTS(nextIndex) < position) break
                nextIndex -= 1
            }
            nextIndex++
        } else {
            nextIndex = currentRecordIndex + 1
            while (nextIndex < bmsRecords.size) {
                val nextPos = recordTS(nextIndex)
                if (nextPos > position) break
                nextIndex += 1
            }
            nextIndex--
        }
        if (nextIndex != currentRecordIndex) {
            currentRecordIndex = nextIndex
            positionRecord = bmsRecords[currentRecordIndex]
            log("Current record index: $currentRecordIndex")
        }
    }
}
