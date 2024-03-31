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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.BmsAdapter
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission", "UnrememberedMutableState")
@Composable
fun BatteryDetailScreen(
    deviceAddress: String
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bmsAdapter = remember {
        BmsAdapter(ctx, deviceAddress)
    }


    LifecycleResumeEffect(deviceAddress) {
        scope.launch(Dispatchers.IO) {
            bmsAdapter.connect()
            bmsAdapter.start()
        }
        onPauseOrDispose {
            //bmsAdapter.stop()
            bmsAdapter.disconnect()
        }
    }

    val deviceInfo by bmsAdapter.deviceInfoState.collectAsState()
    val cellInfo by bmsAdapter.cellInfoState.collectAsState()
    val connectionState by bmsAdapter.connectionState.collectAsState()
    BatteryViewNullable(
        connectionState = connectionState,
        deviceInfo = deviceInfo,
        cellInfo = cellInfo
    )
}

@Composable
fun BatteryViewNullable(
    connectionState: BluetoothLeConnectionService.State,
    deviceInfo: GeneralDeviceInfo?,
    cellInfo:GeneralCellInfo?
) {
    if (deviceInfo != null && cellInfo != null) {
        BatteryView(
            deviceInfo = deviceInfo,
            cellInfo = cellInfo
        )
    } else {
        Column {
            Text("Connection state: ${connectionState.name}")
            Text("No values received yet.")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun BatteryInfoPreview() {
    val mock = GeneralCellInfo(
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
        deviceInfo = GeneralDeviceInfo("Test name", "Long test name"),
        cellInfo = mock
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryView(
    deviceInfo: GeneralDeviceInfo,
    cellInfo: GeneralCellInfo,
    modifier: Modifier = Modifier
) {
    val voltageText = remember(cellInfo) {
        val voltage = cellInfo.cellVoltages.sum()
        "%.2f".format(voltage)
    }
    Column(
        modifier = modifier.padding(8.dp)
    ) {
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = deviceInfo.name,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = deviceInfo.longName,
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
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "SOC: ${cellInfo.stateOfChard}%",
            style = MaterialTheme.typography.displaySmall
        )
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
            Text("T1: %.1f°C T2: %.1f°C Mos: %.1f°C".format(cellInfo.temp0, cellInfo.temp1, cellInfo.tempMos))
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
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBatteryWidget() {
    val mock = GeneralCellInfo(
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
            text = "SOC: ${batteryInfo.stateOfChard}%",
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
