package de.drick.bmsmonitor.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bms_adapter.BatteryInfo
import de.drick.bmsmonitor.bms_adapter.BmsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("MissingPermission", "UnrememberedMutableState")
@Composable
fun BatteryDetailScreen(
    deviceAddress: String
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var batteryInfo by remember {
        mutableStateOf<BatteryInfo?>(null)
    }

    LifecycleResumeEffect(deviceAddress) {
        val bmsAdapter = BmsAdapter(ctx)
        scope.launch(Dispatchers.IO) {
            bmsAdapter.connect(deviceAddress)
            var counter = 0
            delay(200)
            bmsAdapter.updateInfo()
            delay(2000)
            bmsAdapter.updateCellData()

            /*while (isActive) {
                counter++
                delay(10000)
                bmsAdapter.updateCellData()
                delay(250)
            }*/
        }
        scope.launch {
            bmsAdapter.batteryInfoState.collect {
                batteryInfo = it
            }
        }
        onPauseOrDispose {
            bmsAdapter.disconnect()
        }
    }

    batteryInfo.let { batt ->
        if (batt != null) {
            BatteryView(
                batteryInfo = batt
            )
        } else {
            Text("No values received yet.")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun BatteryInfoPreview() {
    val mock = BatteryInfo(
        stateOfChard = 69,
        maxCapacity = 28f,
        current = 2.2f,
        cellVoltages = floatArrayOf(
            3.85f,
            3.89f,
            4.01f,
            4.15f,
            4.03f,
            3.69f
        ),
        cellBalance = booleanArrayOf(
            false,
            true,
            false,
            false,
            false,
            true
        )
    )
    BatteryView(
        modifier = Modifier.fillMaxSize(),
        batteryInfo = mock
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryView(
    batteryInfo: BatteryInfo,
    modifier: Modifier = Modifier
) {
    val voltageText = remember(batteryInfo) {
        val voltage = batteryInfo.cellVoltages.sum()
        "%.2f".format(voltage)
    }
    Column(
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "${voltageText}V",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%.1fA".format(batteryInfo.current),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.weight(1f))
        }
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "SOC: ${batteryInfo.stateOfChard}%",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "Cell voltages",
            style = MaterialTheme.typography.headlineSmall
        )
        val (hvIndex, hvValue) = remember(batteryInfo) {
            if (batteryInfo.cellVoltages.isNotEmpty()) {
                batteryInfo.cellVoltages.withIndex().maxBy { (_, v) -> v }
            } else
                IndexedValue(0, 0f)
        }
        val (lvIndex, lvValue) = remember(batteryInfo) {
            if (batteryInfo.cellVoltages.isNotEmpty()) {
                batteryInfo.cellVoltages.withIndex().minBy { (_, v) -> v }
            } else {
                IndexedValue(0, 0f)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "min"
                )
                Text("($lvIndex) %.3f".format(lvValue))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "max"
                )
                Text("($hvIndex) %.3f".format(hvValue))
            }
            Text("Δ %.0f mV".format(abs(lvValue-hvValue) * 1000f))
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            batteryInfo.cellVoltages.forEachIndexed { index, voltage ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        text = "%02d".format(index + 1)
                    )
                    Spacer(Modifier.width(8.dp))
                    val color = when (index) {
                        lvIndex -> MaterialTheme.colorScheme.inversePrimary
                        hvIndex -> MaterialTheme.colorScheme.primary
                        else -> LocalContentColor.current
                    }
                    val textModifier = if (batteryInfo.cellBalance[index]) {
                        Modifier
                            .background(MaterialTheme.colorScheme.secondary)
                            .padding(4.dp)
                    } else {
                        Modifier.padding(4.dp)
                    }
                    Text(
                        modifier = textModifier,
                        text = "%.3f".format(voltage),
                        color = color
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBatteryWidget() {
    val mock = BatteryInfo(
        stateOfChard = 69,
        maxCapacity = 28f,
        current = 2.2f,
        cellVoltages = floatArrayOf(
            3.85f,
            3.89f,
            4.01f,
            4.15f,
            4.03f,
            3.69f
        ),
        cellBalance = booleanArrayOf(
            false,
            true,
            false,
            false,
            false,
            true
        )
    )
    BatteryWidget(batteryInfo = mock)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryWidget(
    batteryInfo: BatteryInfo,
    modifier: Modifier = Modifier
) {
    val voltageText = remember(batteryInfo) {
        val voltage = batteryInfo.cellVoltages.sum()
        "%.2f".format(voltage)
    }
    Column(
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "${voltageText}V",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%.1fA".format(batteryInfo.current),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.weight(1f))
        }
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "SOC: ${batteryInfo.stateOfChard}%",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(12.dp))
        val (hvIndex, hvValue) = remember(batteryInfo) {
            if (batteryInfo.cellVoltages.isNotEmpty()) {
                batteryInfo.cellVoltages.withIndex().maxBy { (_, v) -> v }
            } else
                IndexedValue(0, 0f)
        }
        val (lvIndex, lvValue) = remember(batteryInfo) {
            if (batteryInfo.cellVoltages.isNotEmpty()) {
                batteryInfo.cellVoltages.withIndex().minBy { (_, v) -> v }
            } else {
                IndexedValue(0, 0f)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "min"
                )
                Text("($lvIndex) %.3f".format(lvValue))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "max"
                )
                Text("($hvIndex) %.3f".format(hvValue))
            }
            Text("Δ %.0f mV".format(abs(lvValue-hvValue) * 1000f))
        }
        Spacer(Modifier.height(12.dp))
    }
}
