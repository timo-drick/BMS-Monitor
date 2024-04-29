package de.drick.bmsmonitor.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.patrykandpatrick.vico.compose.common.shader.color
import com.patrykandpatrick.vico.core.cartesian.axis.AxisPosition
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.DynamicShader
import de.drick.bmsmonitor.bms_adapter.BmsType
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.repository.HeaderData
import de.drick.bmsmonitor.repository.RecordEntry
import de.drick.bmsmonitor.repository.RecordingInfo
import de.drick.bmsmonitor.repository.RecordingRepository
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.io.File


private val mockDataFlow = flow<GeneralCellInfo> {

}

class RecordingInfoState(ctx: Context, val info: RecordingInfo) {
    private val repository = RecordingRepository(ctx)

    suspend fun loadData() = repository
        .getDataFlow(info.file)
        .toList()
        .toPersistentList()
}

@Preview(device = "id:pixel_8", showBackground = true)
@Composable
private fun PreviewRecordingsDetailView() {
    val mockData = RecordingInfo(
        file = File(""),
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

@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T> Iterable<T>.averageOf(selector: (T) -> Int): Int {
    var sum = 0
    var counter = 0
    for (element in this) {
        sum += selector(element)
        counter++
    }
    return sum / counter
}

@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T> Iterable<T>.averageOf(selector: (T) -> Float): Float {
    var sum = 0f
    var counter = 0
    for (element in this) {
        sum += selector(element)
        counter++
    }
    return sum / counter
}

@Composable
fun RecordingsDetailView(
    recordingInfo: RecordingInfo,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val repository = RecordingRepository(ctx)

    LaunchedEffect(Unit) {
        val dataList = repository
            .getDataFlow(recordingInfo.file)
            .toList()
            .groupBy { it.time / 10000L }
            .map { (ts, list) ->
                RecordEntry(
                    time = ts * 10000L,
                    voltage = list.maxOf { it.voltage },
                    current = list.maxOf { it.current },
                    soc = list.averageOf { it.soc },
                    temp = list.averageOf { it.temp }
                )
            }
            .toPersistentList()
        val startTime = dataList.first().time
        val time = dataList.map { (it.time - startTime) / 1000 }
        val power = dataList.map { it.voltage * it.current }
        val soc = dataList.map { it.soc }
        modelProducer.tryRunTransaction {
            lineSeries {
                series(x = time, y = soc)
            }
            lineSeries {
                series(x = time, y = power)
            }
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
        CartesianChartHost(
            modifier = Modifier.fillMaxSize(),
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    verticalAxisPosition = AxisPosition.Vertical.Start,
                ),
                rememberLineCartesianLayer(
                    verticalAxisPosition = AxisPosition.Vertical.End,
                    lines = listOf(LineCartesianLayer.LineSpec(
                        shader = DynamicShader.color(Color.Green),
                        backgroundShader = null
                    )),
                ),
                startAxis = rememberStartAxis(),
                //topAxis = rememberTopAxis(),
                bottomAxis = rememberBottomAxis(),
                endAxis = rememberEndAxis(
                    label = rememberAxisLabelComponent(Color.Green)
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