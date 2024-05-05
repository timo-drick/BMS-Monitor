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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEndAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.of
import com.patrykandpatrick.vico.compose.common.shader.color
import com.patrykandpatrick.vico.core.cartesian.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.cartesian.axis.AxisPosition
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.shader.DynamicShader
import com.patrykandpatrick.vico.core.common.shape.Shape
import de.drick.bmsmonitor.bms_adapter.BmsType
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.repository.HeaderData
import de.drick.bmsmonitor.repository.LocationPoint
import de.drick.bmsmonitor.repository.RecordEntry
import de.drick.bmsmonitor.repository.RecordingInfo
import de.drick.bmsmonitor.repository.RecordingRepository
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File


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
                    RecordingsDetailChart(
                        modifier = Modifier.fillMaxSize(),
                        dataList = uiData.dataList
                    )
                }
            }
        }
    }
}
@Composable
fun RecordingsDetailChart(
    dataList: PersistentList<RecordEntry>,
    modifier: Modifier = Modifier
) {

    val modelProducer = remember { CartesianChartModelProducer.build() }

    var timeText by remember { mutableStateOf(formatDuration(0)) }

    LaunchedEffect(dataList) {
        val stepList = dataList.step(30000)
        val startTime = dataList.first().time
        val stepStart = stepList.first().time
        val time = stepList.map { (it.time - stepStart) / 1000 }
        val power = stepList.map { it.voltage * it.current }
        val voltage = stepList.map { it.voltage }
        val current = stepList.map { it.current }
        val soc = stepList.map { it.soc }
        timeText = formatDuration((dataList.last().time - startTime) / 1000)
        modelProducer.tryRunTransaction {
            lineSeries {
                series(x = time, y = voltage)
            }
            lineSeries {
                series(x = time, y = current)
            }
        }
    }
    Column(
        modifier = modifier
    ) {
        Row {
            Text("Duration: $timeText")
        }

        val valueFormatter = remember {
            CartesianValueFormatter { x, _, _ ->
                formatDuration(x.toLong())
            }
        }
        CartesianChartHost(
            modifier = Modifier.fillMaxSize(),
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    verticalAxisPosition = AxisPosition.Vertical.Start,
                    axisValueOverrider = remember {
                        AxisValueOverrider.fixed(minY = dataList.minOf { it.voltage })
                    },
                ),
                rememberLineCartesianLayer(
                    verticalAxisPosition = AxisPosition.Vertical.End,
                    lines = listOf(
                        LineCartesianLayer.LineSpec(
                            shader = DynamicShader.color(Color.Green),
                            backgroundShader = null
                        )
                    ),
                ),
                startAxis = rememberStartAxis(
                    titleComponent = rememberTextComponent(
                        color = Color.White,
                        background = rememberShapeComponent(Shape.Pill, Color.Black),
                        padding = Dimensions.of(horizontal = 8.dp, vertical = 2.dp),
                        margins = Dimensions.of(end = 4.dp),
                        typeface = Typeface.MONOSPACE,
                    ),
                    title = "V"
                ),
                //topAxis = rememberTopAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = valueFormatter,
                    itemPlacer =  AxisItemPlacer.Horizontal.default(
                        spacing = 4,
                        offset = 1
                    ),
                    guideline = null
                ),
                endAxis = rememberEndAxis(
                    label = rememberAxisLabelComponent(Color.Green),
                    titleComponent = rememberTextComponent(
                        color = Color.Green,
                        background = rememberShapeComponent(Shape.Pill, Color.Black),
                        padding = Dimensions.of(horizontal = 8.dp, vertical = 2.dp),
                        margins = Dimensions.of(end = 4.dp),
                        typeface = Typeface.MONOSPACE,
                    ),
                    title = "A"
                )
            ),
            modelProducer = modelProducer,
        )
    }
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