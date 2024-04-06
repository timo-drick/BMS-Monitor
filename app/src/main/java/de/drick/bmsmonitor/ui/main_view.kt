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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.drick.bmsmonitor.repository.DeviceInfoData
import de.drick.bmsmonitor.ui.theme.BMSMonitorTheme

private val mockDevices = listOf(
    DeviceInfoData("Test device 1", "A"),
    DeviceInfoData("Test device 2", "B"),
    DeviceInfoData("Test device 3", "C"),
)
private val mockDevicesOffline = listOf(
    DeviceInfoData("Test device offline", "A"),
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun PreviewMainView() {
    BMSMonitorTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainView(
                mockDevices,
                onAddDevice = {},
                onDeviceSelected = {}
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
    markedDevices: List<DeviceInfoData>,
    onAddDevice: () -> Unit,
    onDeviceSelected: (DeviceInfoData) -> Unit,
    modifier: Modifier = Modifier
) {
    val btDevices = remember(markedDevices) {
        markedDevices.map { it.macAddress }
    }
    val scanResults = bluetoothLeScannerEffect(btDevices)

    val orderedList by remember {
        derivedStateOf {
            markedDevices.map { deviceInfo ->
                val btDeviceInfo = scanResults.find { it.address == deviceInfo.macAddress }
                UIDeviceItem(deviceInfo, btDeviceInfo)
            }
        }
    }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            item {
                Button(onClick = onAddDevice) {
                    Text("Add new device")
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(orderedList) { index, item ->
                val cornerRadius = 8.dp
                val cornerShape = when (index) {
                    0 -> if (orderedList.size > 1)
                        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    else
                        RoundedCornerShape(cornerRadius)
                    orderedList.size - 1 -> RoundedCornerShape(
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
                            .clickable(onClick = { onDeviceSelected(item.item) }),
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = "${data.item.name} btstate: ${data.btDeviceInfo}",
            modifier = Modifier.weight(1f, true),
            //color = contentColor
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
        )
    }
}

