package de.drick.bmsmonitor.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.bluetooth.BTDeviceInfo
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.repository.DeviceInfoData
import de.drick.bmsmonitor.ui.theme.BMSMonitorTheme

private val mockDevices = listOf(
    DeviceInfoData("Test device 1", "A"),
    DeviceInfoData("Test device 2", "B"),
    DeviceInfoData("Test device 3", "C"),
).map { UIDeviceItem(it, BTDeviceInfo(it.name, it.macAddress, -1, 0)) }
private val mockDevicesOffline = listOf(
    DeviceInfoData("Test device offline", "A"),
).map { UIDeviceItem(it, null) }

private val mockUiDevices = mockDevices + mockDevicesOffline

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun PreviewMainView() {
    BMSMonitorTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainView(
                mockUiDevices.toMutableStateList(),
                onAction = {}
            )
        }
    }
}

data class UIDeviceItem(
    val item: DeviceInfoData,
    val btDeviceInfo: BTDeviceInfo?
)

@Composable
fun MainView(
    markedDevices: SnapshotStateList<UIDeviceItem>,
    onAction: (MainUIAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LifecycleResumeEffect(Unit) {
        onAction(MainUIAction.StartMarkedScan)
        onPauseOrDispose {
            onAction(MainUIAction.StopMarkedScan)
        }
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            item {
                Button(onClick = {
                    onAction(MainUIAction.ShowScan)
                }) {
                    Text("Add new device")
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(markedDevices) { index, item ->
                val cornerRadius = 8.dp
                val cornerShape = when (index) {
                    0 -> if (markedDevices.size > 1)
                        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    else
                        RoundedCornerShape(cornerRadius)
                    markedDevices.size - 1 -> RoundedCornerShape(
                        bottomStart = cornerRadius,
                        bottomEnd = cornerRadius
                    )
                    else -> RectangleShape
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = cornerShape
                ) {
                    if (index > 0)
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 42.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    SavedDeviceItem(
                        modifier = Modifier
                            .clickable(onClick = {
                                onAction(MainUIAction.ShowDetails(item.item.macAddress))
                            }),
                        data = item
                    )
                }
            }
        }
    }
}

@Composable
fun SavedDeviceItem(
    data: UIDeviceItem,
    modifier: Modifier = Modifier
) {
    val isOnline = data.btDeviceInfo != null
    val additionalModifier = if (isOnline) Modifier else Modifier.alpha(0.6f)
    /*val ctx = LocalContext.current
    val cellFlow = remember {
        derivedStateOf {
            data.btDeviceInfo?.let {
                MonitorService.getBmsMonitor(ctx, it.address)
            }
        }
    }
    val cellInfo = cellFlow.value?.collectAsState()
    */
    Row(
        modifier
            .fillMaxWidth()
            .then(additionalModifier)
            .padding(10.dp)
    ) {
        /*val socText = cellInfo?.value?.cellInfo?.let {
            val voltage = it.cellVoltages.sum()
            "%d%% %.1f v".format(it.stateOfCharge, voltage)
        } ?: "?"
        Text(socText)*/
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = data.item.name,
            modifier = Modifier.weight(1f, true),
            //color = contentColor
        )
        if (isOnline) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
            )
        }
    }
}

