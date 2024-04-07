@file:OptIn(ExperimentalStdlibApi::class)

package de.drick.bmsmonitor.bms_adapter.yy_bms

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInterface
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class YYBmsAdapter(private val service: BluetoothLeConnectionService): BmsInterface {
    companion object {
        val serviceUUID =
            checkNotNull(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
        private val YY_BMS_BLE_RX_CHARACTERISTICS =
            checkNotNull(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
        private val YY_BMS_BLE_TX_CHARACTERISTICS =
            checkNotNull(UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"))
    }

    private val yyBmsDecoder = YYBmsDecoder()

    private val _cellInfoFlow = MutableStateFlow<GeneralCellInfo?>(null)
    override val cellInfoState: StateFlow<GeneralCellInfo?> = _cellInfoFlow
    private val _deviceInfoFlow = MutableStateFlow<GeneralDeviceInfo?>(null)
    override val deviceInfoState: StateFlow<GeneralDeviceInfo?> = _deviceInfoFlow

    private var running = false

    override suspend fun start() {
        service.subscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
        withContext(Dispatchers.IO) {
            launch {
                running = true
                service.writeCharacteristic(serviceUUID, YY_BMS_BLE_TX_CHARACTERISTICS, YYBmsDecoder.COMMAND_BMS_INFO_DATA)
                delay(500)
                while (isActive && running) {
                    if (deviceInfoState.value == null) {
                        service.writeCharacteristic(serviceUUID, YY_BMS_BLE_TX_CHARACTERISTICS, YYBmsDecoder.COMMAND_BMS_INFO_DATA)
                        delay(500)
                    }
                    service.writeCharacteristic(serviceUUID, YY_BMS_BLE_TX_CHARACTERISTICS, YYBmsDecoder.COMMAND_CELL_DATA)
                    delay(1000)
                }
            }
        }
    }

    override suspend fun stop() {
        running = false
        service.unSubscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS)
    }

    private val notificationCallback = { data: ByteArray ->
        when(val event = yyBmsDecoder.decodeData(data)) {
            is GeneralDeviceInfo -> {
                _deviceInfoFlow.value = event
            }
            is GeneralCellInfo -> {
                _cellInfoFlow.value = event
            }
        }
    }

}