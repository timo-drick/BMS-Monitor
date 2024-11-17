package de.drick.bmsmonitor.ui.recordings

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.drick.bmsmonitor.bms_adapter.BmsType
import de.drick.bmsmonitor.repository.HeaderData
import de.drick.bmsmonitor.repository.RecordingInfo
import de.drick.bmsmonitor.ui.itemsIndexedCorner
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import java.io.File
import java.text.Format
import kotlin.random.Random

private val rnd = Random(124123)
private fun createMockEntry(): RecordingInfo {
    return RecordingInfo(
        id = "A${rnd.nextInt()}",
        timeStamp = System.currentTimeMillis(),
        name = "Niu Scooter",
        bmsType = BmsType.YY_BMS,
        header = HeaderData("XX:YY:ZZ"),
        soc = 13,
        voltage = 62.1f
    )
}
@Preview(showBackground = true)
@Composable
private fun PreviewRecordingsView() {
    val mockData = listOf(
        RecordingInfo(
            id = "A",
            timeStamp = System.currentTimeMillis(),
            name = "Niu Scooter",
            bmsType = BmsType.YY_BMS,
            header = HeaderData("XX:YY:ZZ"),
            soc = 13,
            voltage = 62.1f
        ),
        RecordingInfo(
            id = "B",
            timeStamp = System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 1,
            name = "Niu Scooter",
            bmsType = BmsType.YY_BMS,
            header = HeaderData("XX:YY:ZZ"),
            soc = 13,
            voltage = 62.1f
        )
    ).toPersistentList()
    RecordingsView(recordings = mockData, onSelected = {}, onBack = { })
}

val fullDateFormatter: Format = if (Build.VERSION.SDK_INT >= 24) {
    android.icu.text.SimpleDateFormat.getDateInstance(android.icu.text.SimpleDateFormat.FULL)
} else {
    java.text.SimpleDateFormat.getDateInstance()
}

val fullTimeFormatter: Format = if (Build.VERSION.SDK_INT >= 24) {
    android.icu.text.SimpleDateFormat.getTimeInstance(android.icu.text.SimpleDateFormat.MEDIUM)
} else {
    java.text.SimpleDateFormat.getTimeInstance(java.text.SimpleDateFormat.MEDIUM)
}

@Composable
fun RecordingsScreen(
    recordings: PersistentList<RecordingInfo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRecording by remember { mutableStateOf<RecordingInfo?>(null) }
    selectedRecording.let { record ->
        if (record == null) {
            RecordingsView(
                modifier = modifier,
                recordings = recordings,
                onSelected = { selectedRecording = it },
                onBack = onBack
            )
        } else {
            RecordingsDetailView(
                recordingInfo = record,
                onBack = { selectedRecording = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsView(
    recordings: PersistentList<RecordingInfo>,
    onSelected: (RecordingInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(recordings) {
        recordings.groupBy {
            fullDateFormatter.format(it.timeStamp)
        }
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            groups.forEach { (date, itemList) ->
                stickyHeader {
                    Box(Modifier
                        .background(MaterialTheme.colorScheme.background.copy(0.6f))
                        .fillMaxSize()) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = date,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                itemsIndexedCorner(itemList) { index, item, cornerShape ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = cornerShape
                    ) {
                        if (index > 0)
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 42.dp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        Row(
                            Modifier
                                .clickable(onClick = {
                                    onSelected(item)
                                })
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            //Spacer(modifier = Modifier.width(20.dp))
                            val dateText = remember(item) {
                                fullTimeFormatter.format(item.timeStamp)
                            }
                            Text(text = dateText)
                            Text(
                                text = "${item.bmsType.name} ${item.name}",
                                modifier = Modifier.weight(1f, true),
                            )
                            Text(
                                text = "%d%% %3.1fv".format(item.soc, item.voltage),
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}