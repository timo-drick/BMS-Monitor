package de.drick.bmsmonitor.ui.recordings

import android.content.Context
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.dimensions
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.shader.DynamicShader
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import de.drick.bmsmonitor.bms_adapter.BmsType
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.repository.HeaderData
import de.drick.bmsmonitor.repository.LocationPoint
import de.drick.bmsmonitor.repository.RecordEntry
import de.drick.bmsmonitor.repository.RecordingInfo
import de.drick.bmsmonitor.repository.RecordingRepository
import de.drick.bmsmonitor.ui.getSocIcon
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds


private val mockDataFlow = flow<GeneralCellInfo> {

}

class RecordingInfoState(ctx: Context, val info: RecordingInfo) {
    private val repository = RecordingRepository(ctx)

    suspend fun loadData() = repository
        .getDataFlow(info.id)
        .toList()
        .toPersistentList()
}

@Preview(device = "id:pixel_8", showBackground = true)
@Composable
private fun PreviewRecordingsDetailView() {
    val mockData = RecordingInfo(
        id = "a",
        timeStamp = System.currentTimeMillis(),
        name = "Niu Scooter",
        bmsType = BmsType.YY_BMS,
        header = HeaderData("XX:YY:ZZ"),
        soc = 13,
        voltage = 62.1f
    )
    MaterialTheme {
        RecordingsDetailView(
            modifier = Modifier.fillMaxSize(),
            recordingInfo = mockData,
            onBack = {},
            //dataFlow = mockDataFlow
        )
    }
}

data class UIData(
    val dataList: PersistentList<RecordEntry>,
    val locationList: PersistentList<LocationPoint>?
)

@Composable
fun RecordingsDetailView(
    recordingInfo: RecordingInfo,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val repository = RecordingRepository(ctx)
    var data: UIData? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dataList = repository.getDataFlow(recordingInfo.id).toList().toPersistentList()
            val locationList =
                repository.getLocationFlow(recordingInfo.id).toList().toPersistentList()
            data = UIData(
                dataList = dataList,
                locationList = if (locationList.size > 3) locationList else null
            )
        }
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = recordingInfo.name,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium
        )
        val dateTimeText = remember(recordingInfo) {
            val date = fullDateFormatter.format(recordingInfo.timeStamp)
            val time = fullTimeFormatter.format(recordingInfo.timeStamp)
            "$date $time"
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = dateTimeText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        AnimatedContent(
            targetState = data,
            label = "Loading animation"
        ) { uiData ->
            if (uiData == null) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else {
                if (uiData.locationList != null) {
                    RecordingsMapView(
                        modifier = Modifier.fillMaxSize(),
                        dataList = uiData.dataList,
                        locationList = uiData.locationList
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        RecordingsDetailData(
                            dataList = uiData.dataList
                        )
                        RecordingsDetailChart(
                            modifier = Modifier.weight(1f),
                            dataList = uiData.dataList
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewRecordingsDetailData() {
    val dataList = persistentListOf(
        RecordEntry(
            time = 10L,
            voltage = 69.3f,
            current = 5f,
            soc = 69f,
            temp = 20f
        ),
        RecordEntry(
            time = 461_000L,
            voltage = 60.3f,
            current = -24.4f,
            soc = 65f,
            temp = 25f
        ),
        RecordEntry(
            time = 565_000L,
            voltage = 59.3f,
            current = 5f,
            soc = 60f,
            temp = 28f
        )
    )
    MaterialTheme {
        RecordingsDetailData(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            dataList = dataList
        )
    }
}

@Composable
fun RecordingsDetailData(
    dataList: PersistentList<RecordEntry>,
    modifier: Modifier = Modifier
) {
    val first = remember(dataList) { dataList.first() }
    val last = remember(dataList) { dataList.last() }

    Column(
        modifier = modifier
    ) {
        Row {
            val timeText = remember {
                //formatDuration((last.time - first.time) / 1000)
                ((last.time - first.time) / 1000).seconds.toString()
            }
            Icon(
                imageVector = Icons.Default.HourglassFull,
                contentDescription = null
            )
            Text(timeText)
        }
        Row {
            val firstSocText = remember {
                "%.0f%%"
                    .format(first.soc)
            }
            Icon(
                imageVector = getSocIcon(first.soc),
                contentDescription = null
            )
            Text(firstSocText)
            Icon(
                imageVector = getSocIcon(last.soc),
                contentDescription = null
            )
            val lastSocText = remember {
                "%.0f%% Δ %.0f%%"
                    .format(last.soc, last.soc - first.soc)
            }
            Text(lastSocText)

        }
        Row {
            val voltageText = remember {
                val min = dataList.minOf { it.voltage }
                val max = dataList.maxOf { it.voltage }
                val avg = dataList.averageOf { it.voltage }
                "%.1f V - %.1f V Δ %.1f V min: %.1f V max: %.1f V avg: %.1f V"
                    .format(
                        first.voltage,
                        last.voltage,
                        last.voltage - first.voltage,
                        min,
                        max,
                        avg
                    )
            }
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null
            )
            Text(voltageText)
            //Icon(imageVector = Icons.Default., contentDescription = )
        }
        Row {
            val currentText = remember {
                val avg = dataList.averageOf { it.current }
                val min = dataList.minOf { it.current }
                val max = dataList.maxOf { it.current }
                "Current min: %.1f A max: %.1f A  avg %.1f A"
                    .format(min, max, avg)
            }
            Text(currentText)
        }
        Row {
            val currentText = remember {
                val avgW = dataList.averageOf { it.current * it.voltage }
                val minW = dataList.minOf { it.current * it.voltage }
                val maxW = dataList.maxOf { it.current * it.voltage }
                "Power min: %.0f W max: %.0f W avg: %.0f W"
                    .format(minW,  maxW, avgW)
            }
            Text(currentText)
        }
    }
}

@Composable
fun RecordingsDetailChart(
    dataList: PersistentList<RecordEntry>,
    modifier: Modifier = Modifier
) {

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dataList) {
        val stepList = dataList.step(30000)

        val stepStart = stepList.first().time
        val time = stepList.map { (it.time - stepStart) / 1000 }
        val power = stepList.map { it.voltage * it.current }
        val voltage = stepList.map { it.voltage }
        val current = stepList.map { it.current }
        val soc = stepList.map { it.soc }
        modelProducer.runTransaction {
            lineSeries {
                series(x = time, y = voltage)
            }
            lineSeries {
                series(x = time, y = current)
            }
        }
    }
    val valueFormatter = remember {
        CartesianValueFormatter { x, _, _ ->
            //formatDuration(x.toLong())
            x.toString()
        }
    }
    CartesianChartHost(
        modifier = modifier,
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                verticalAxisPosition = Axis.Position.Vertical.End,
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        remember { LineCartesianLayer.LineFill.single(fill(Color.Green)) }
                    )
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
                titleComponent = rememberTextComponent(
                    color = Color.White,
                    background = rememberShapeComponent(Color.Black, CorneredShape.Pill),
                    padding = dimensions(horizontal = 8.dp, vertical = 2.dp),
                    margins = dimensions(end = 4.dp),
                    typeface = Typeface.MONOSPACE,
                ),
                title = "Volt"
            ),
            //topAxis = rememberTopAxis(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = valueFormatter,
                itemPlacer = remember {
                    HorizontalAxis.ItemPlacer.aligned(offset = 1, spacing = 4)
                },
            ),
            endAxis = VerticalAxis.rememberEnd(
                label = rememberAxisLabelComponent(Color.Green),
                titleComponent = rememberTextComponent(
                    color = Color.Green,
                    background = rememberShapeComponent(Color.Black, CorneredShape.Pill),
                    padding = dimensions(horizontal = 8.dp, vertical = 2.dp),
                    margins = dimensions(end = 4.dp),
                    typeface = Typeface.MONOSPACE,
                ),
                title = "Amp"
            )
        ),
        modelProducer = modelProducer,
    )

}

data class DataPoint(val x: Float, val y: Float)

@Composable
fun ChartView(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCircle(color = Color.Red)
    }
}

@Preview
@Composable
private fun PreviewChartView() {
    ChartView(
        modifier = Modifier.size(200.dp)
    )
}