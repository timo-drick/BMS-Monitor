@file:OptIn(ExperimentalStdlibApi::class)

package de.drick.bmsmonitor.bms_adapter.yy_bms

import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.bmsmonitor.bms_adapter.BmsInterface
import de.drick.bmsmonitor.bms_adapter.GeneralCellInfo
import de.drick.bmsmonitor.bms_adapter.GeneralDeviceInfo
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
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

    private val _eventFlow = MutableSharedFlow<GeneralCellInfo>(replay = 1)
    override val bmsEventFlow: Flow<GeneralCellInfo> = _eventFlow

    private val _rawFlow = MutableSharedFlow<ByteArray>(replay = 1)
    override val bmsRawFlow = _rawFlow.asSharedFlow()

    private var running = false
    private var lastDeviceInfo: GeneralDeviceInfo? = null

    override suspend fun start() {
        log("start")
        service.subscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS, notificationCallback)
        withContext(Dispatchers.IO) {
            running = true
            while (isActive && running) {
                if (lastDeviceInfo == null) {
                    service.writeCharacteristic(
                        serviceUUID,
                        YY_BMS_BLE_TX_CHARACTERISTICS,
                        YYBmsDecoder.COMMAND_BMS_INFO_DATA
                    )
                } else {
                    service.writeCharacteristic(
                        serviceUUID,
                        YY_BMS_BLE_TX_CHARACTERISTICS,
                        YYBmsDecoder.COMMAND_CELL_DATA
                    )
                }
                delay(1000)
            }
        }
        log("Start finished")
    }

    override suspend fun stop() {
        running = false
        service.unSubscribeForNotification(serviceUUID, YY_BMS_BLE_RX_CHARACTERISTICS)
    }

    override fun decodeRaw(data: ByteArray): GeneralCellInfo? {
        yyBmsDecoder.decodeData(data)?.let { event ->
            if (event is GeneralDeviceInfo) {
                lastDeviceInfo = event
            }
            if (event is GeneralCellInfo) {
                return event.copy(deviceInfo = lastDeviceInfo)
            }
        }
        return null
    }

    private val notificationCallback: (ByteArray) -> Unit = { data: ByteArray ->
        val cellInfo = decodeRaw(data)
        if (cellInfo != null) {
            _eventFlow.tryEmit(cellInfo)
        }
        _rawFlow.tryEmit(data)
    }
}