package de.drick.bmsmonitor.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.drick.bmsmonitor.BackgroundRecordingService
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInfo
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.bmsmonitor.bms_adapter.MonitorService
import de.drick.bmsmonitor.locationFlow

@Composable
fun BatteryDetailScreen(
    deviceAddress: String,
    isMarked: Boolean,
    isRecording: Boolean,
    onAction: (MainUIAction) -> Unit
) {
    val ctx = LocalContext.current
    val bmsInfoFlow = remember(deviceAddress) {
        MonitorService.getBmsMonitor(ctx, deviceAddress)
    }
    val bmsInfo by bmsInfoFlow.collectAsStateWithLifecycle(BmsInfo(BluetoothLeConnectionService.State.Disconnected, null))
    BatteryViewNullable(
        connectionState = bmsInfo.state,
        cellInfo = bmsInfo.cellInfo,
        isMarked = isMarked,
        isRecording = isRecording,
        onMarkToggle = {
            if (isMarked) {
                onAction(MainUIAction.UnMarkDevice(deviceAddress))
            } else {
                bmsInfo.cellInfo?.deviceInfo?.let {
                    onAction(MainUIAction.MarkDevice(deviceAddress, it))
                }
            }
        },
        onRecordingToggle = {
            val activity = ctx as Activity
            if (BackgroundRecordingService.isRunningFlow.value) {
                BackgroundRecordingService.stop(activity, deviceAddress)
            } else {
                BackgroundRecordingService.start(activity, deviceAddress)
            }
        }
    )
}

@Composable
fun BatteryViewNullable(
    connectionState: BluetoothLeConnectionService.State,
    cellInfo: GeneralCellInfo?,
    isMarked: Boolean,
    isRecording: Boolean,
    onMarkToggle: () -> Unit,
    onRecordingToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cellInfo != null) {
        BatteryView(
            modifier = modifier,
            cellInfo = cellInfo,
            isMarked = isMarked,
            isRecording = isRecording,
            onMarkToggle = onMarkToggle,
            onRecordingToggle = onRecordingToggle
        )
    } else {
        Column(modifier) {
            Text("Connection state: ${connectionState.name}")
            Text("No values received yet.")
        }
    }
}


@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun BatteryInfoPreview() {
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
        balanceState = "Balancing",
        errorList = listOf("Under voltage protection"),
        chargingEnabled = true,
        dischargingEnabled = false,
        temp0 = 19.5f,
        temp1 = 21.1f,
        tempMos = 35.81f
    )
    BatteryView(
        modifier = Modifier.fillMaxSize(),
        cellInfo = mock,
        isMarked = true,
        isRecording = false,
        onMarkToggle = {},
        onRecordingToggle = {}
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryView(
    cellInfo: GeneralCellInfo,
    isMarked: Boolean,
    isRecording: Boolean,
    onMarkToggle: () -> Unit,
    onRecordingToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val voltageText = remember(cellInfo) {
        val voltage = cellInfo.cellVoltages.sum()
        "%.2f".format(voltage)
    }
    val deviceInfo = cellInfo.deviceInfo
    var speedText by remember { mutableStateOf("NA") }

    LaunchedEffect(Unit) {
        locationFlow(ctx).collect {
            speedText = "%3.0f km/h".format(it.speed / 360f * 1000f)
        }
    }

    Box(
        modifier = modifier.padding(8.dp)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = deviceInfo?.name ?: "NA",
                    style = MaterialTheme.typography.headlineLarge
                )
            }
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = deviceInfo?.longName ?: "NA",
                style = MaterialTheme.typography.bodyMedium
            )
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
                    text = "%.2fA".format(cellInfo.current),
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "SOC: ${cellInfo.stateOfCharge}%",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "C: ${cellInfo.maxCapacity} Ah",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chargingText = if (cellInfo.chargingEnabled) "On" else "Off"
                Text(
                    text = "Charging: $chargingText",
                    style = MaterialTheme.typography.bodyLarge
                )
                val dischargingText = if (cellInfo.dischargingEnabled) "On" else "Off"
                Text(
                    text = "Discharging: $dischargingText",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (cellInfo.errorList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val errorMessage = remember(cellInfo) {
                    cellInfo.errorList.joinToString()
                }
                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "T1: %.1f°C T2: %.1f°C Mos: %.1f°C".format(
                        cellInfo.temp0,
                        cellInfo.temp1,
                        cellInfo.tempMos
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = "Cell voltages",
                style = MaterialTheme.typography.titleMedium
            )
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
                    val cellMinIndex = cellInfo.cellMinIndex
                    val cellMinVoltage = cellInfo.cellVoltages[cellMinIndex]
                    Text("(%d) %.3f".format(cellMinIndex + 1, cellMinVoltage))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "max"
                    )
                    val cellMaxIndex = cellInfo.cellMaxIndex
                    val cellMaxVoltage = cellInfo.cellVoltages[cellMaxIndex]
                    Text("(%d) %.3f".format(cellMaxIndex + 1, cellMaxVoltage))
                }
                Text("Δ %.0f mV".format(cellInfo.cellDelta * 1000f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Balancer: ${cellInfo.balanceState}"
                )
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cellInfo.cellVoltages.forEachIndexed { index, voltage ->
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
                            cellInfo.cellMinIndex -> MaterialTheme.colorScheme.inversePrimary
                            cellInfo.cellMaxIndex -> MaterialTheme.colorScheme.primary
                            else -> LocalContentColor.current
                        }
                        val textModifier = if (cellInfo.cellBalance[index]) {
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
            Spacer(Modifier.height(60.dp))
        }
        Row(Modifier.align(Alignment.BottomStart)) {
            Spacer(Modifier.weight(.5f))
            val ctx = LocalContext.current
            Button(
                modifier = Modifier,
                onClick = onRecordingToggle
            ) {
                val text = if (isRecording) { "Stop recording" } else { "Start recording" }
                Text(text)
            }
            Spacer(
                Modifier
                    .weight(1f)
                    .width(12.dp))
            Button(
                modifier = Modifier,
                onClick = onMarkToggle
            ) {
                val icon = if (isMarked) Icons.Default.Star else Icons.Default.StarOutline
                Icon(
                    imageVector = icon,
                    contentDescription = "Mark / Unmark device"
                )
            }
            Spacer(Modifier.weight(.5f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBatteryWidget() {
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
    BatteryWidget(batteryInfo = mock)
}

@Composable
fun BatteryWidget(
    batteryInfo: GeneralCellInfo,
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
            text = "SOC: ${batteryInfo.stateOfCharge}%",
            style = MaterialTheme.typography.displaySmall
        )
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
                val cellMinIndex = batteryInfo.cellMinIndex + 1
                val cellMinVoltage = batteryInfo.cellVoltages[cellMinIndex]
                Text("($cellMinIndex) %.3f".format(cellMinVoltage))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "max"
                )
                val cellMaxIndex = batteryInfo.cellMaxIndex + 1
                val cellMaxVoltage = batteryInfo.cellVoltages[cellMaxIndex]
                Text("($cellMaxIndex) %.3f".format(cellMaxVoltage))
            }
            Text("Δ %.0f mV".format( batteryInfo.cellDelta * 1000f))
        }
        Spacer(Modifier.height(12.dp))
    }
}
