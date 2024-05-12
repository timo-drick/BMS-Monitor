package de.drick.bmsmonitor.ui

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.locationFlow
import kotlin.math.roundToInt

data class MotionData(
    val speed: Float?, // meter/second
)

@Preview(device = "spec:parent=pixel_5", showBackground = true, showSystemUi = false)
@Composable
private fun PreviewVehicleView() {
    val mock = GeneralCellInfo(
        deviceInfo = GeneralDeviceInfo("Test name", "Long test name"),
        stateOfCharge = 69,
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
        cellMinIndex = 5,
        cellMaxIndex = 3,
        cellDelta = 0.46f,
        cellBalance = booleanArrayOf(
            false,
            true,
            false,
            false,
            false,
            true
        ),
        balanceState = "Discharge",
        errorList = emptyList(),
        chargingEnabled = true,
        dischargingEnabled = true,
        temp0 = 19.5f,
        temp1 = 21.1f,
        tempMos = 35.81f
    )
    MaterialTheme {
        VehicleView(
            modifier = Modifier.fillMaxSize(),
            batteryInfo = mock,
            motionData = MotionData(50.5f),
        )
    }
}

private val batteryIcons = arrayOf(
    Icons.Default.Battery0Bar,
    Icons.Default.Battery1Bar,
    Icons.Default.Battery2Bar,
    Icons.Default.Battery3Bar,
    Icons.Default.Battery4Bar,
    Icons.Default.Battery5Bar,
    Icons.Default.Battery6Bar,
    Icons.Default.BatteryFull
)
fun getSocIcon(soc: Float): ImageVector = getSocIcon(soc.roundToInt())
fun getSocIcon(soc: Int): ImageVector {
    val index = ((batteryIcons.size - 1).toFloat() / 100f) * soc
    return batteryIcons[index.roundToInt()]
}

@Composable
fun VehicleScreen(
    batteryInfo: GeneralCellInfo,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val locationFlow = remember {
        locationFlow(ctx)
    }
    val location: Location? by locationFlow.collectAsState(initial = null)
    val motionData = remember(location) {
        location?.let {
            MotionData(it.speed)
        } ?: MotionData(null)
    }
    val view = LocalView.current
    LifecycleResumeEffect {
        view.keepScreenOn = true
        onPauseOrDispose {
            view.keepScreenOn = false
        }
    }
    VehicleView(
        modifier = modifier,
        batteryInfo = batteryInfo,
        motionData = motionData,
    )
}

@Composable
fun VehicleView(
    batteryInfo: GeneralCellInfo,
    motionData: MotionData,
    modifier: Modifier = Modifier
) {
    val voltageSum = remember(batteryInfo) { batteryInfo.cellVoltages.sum() }
    val voltageText = remember(batteryInfo) {
        "%.2f".format(voltageSum)
    }
    val powerText = remember(batteryInfo) {
        "%.0f W".format(voltageSum * batteryInfo.current * -1f)
    }
    val socText = remember(batteryInfo) {
        "%3d%%".format(batteryInfo.stateOfCharge)
    }
    val isMetric = true
    val speedText = remember(motionData) {
        motionData.speed?.let {
            if (isMetric) {
                "%4.0f km/h".format(it * 3.6f)
            } else {
                "%4.0f mi/h".format(it * 2.23694f)
            }
        } ?: "  ------"
    }

    Column(
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(42.dp),
                imageVector = getSocIcon(batteryInfo.stateOfCharge),
                contentDescription = "Battery icon"
            )
            Text(
                text = socText,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = speedText,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = powerText,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.weight(1f))
        }
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
        Spacer(Modifier.height(12.dp))
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
                val cellMinIndex = batteryInfo.cellMinIndex
                val cellMinVoltage = batteryInfo.cellVoltages[cellMinIndex]
                Text("(%d) %.3f".format(cellMinIndex + 1, cellMinVoltage))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "max"
                )
                val cellMaxIndex = batteryInfo.cellMaxIndex
                val cellMaxVoltage = batteryInfo.cellVoltages[cellMaxIndex]
                Text("(%d) %.3f".format(cellMaxIndex + 1, cellMaxVoltage))
            }
            Text("Δ %.0f mV".format( batteryInfo.cellDelta * 1000f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "T1: %.1f°C T2: %.1f°C Mos: %.1f°C".format(
                    batteryInfo.temp0,
                    batteryInfo.temp1,
                    batteryInfo.tempMos
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}