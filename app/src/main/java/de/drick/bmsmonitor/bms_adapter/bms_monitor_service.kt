package de.drick.bmsmonitor.bms_adapter

import android.content.Context
import de.drick.bmsmonitor.bluetooth.BluetoothLeConnectionService
import de.drick.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object MonitorService {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val bmsMonitorMap = mutableMapOf<String, BmsMonitor>()

    fun getBmsMonitor(ctx: Context, deviceAddress: String): StateFlow<BmsInfo> {
        log("Get monitor: $deviceAddress")
        val bmsMonitor = bmsMonitorMap.getOrPut(deviceAddress) { BmsMonitor(scope, ctx, deviceAddress) }
        return bmsMonitor.bmsInfoFlow
    }
}

class BmsMonitor(
    private val scope: CoroutineScope,
    ctx: Context,
    deviceAddress: String
) {
    private val _bmsInfoFlow = MutableStateFlow(BmsInfo(BluetoothLeConnectionService.State.Disconnected, null, null))
    val bmsInfoFlow: StateFlow<BmsInfo> = _bmsInfoFlow

    private val bmsAdapter = BmsAdapter(ctx, deviceAddress)

    private val test = MutableSharedFlow<BmsInfo>()

    init {
        log("Created instance: $this")
        scope.launch {
            _bmsInfoFlow.subscriptionCount
                .map { count ->
                    count > 0
                }
                .collect { isActive ->
                    log("$deviceAddress is active: $isActive")
                    if (isActive) {
                        start()
                    } else {
                        stop()
                    }
                }
        }
        scope.launch {
            bmsAdapter.bmsInfo.collect {
                _bmsInfoFlow.emit(it)
            }
        }
    }
    private fun start() {
        scope.launch {
            log("Connecting")
            bmsAdapter.connect()
            log("Connected")
            bmsAdapter.start()
        }
    }
    private fun stop() {
        scope.launch {
            log("Stopping")
            bmsAdapter.stop()
            bmsAdapter.disconnect()
            log("disconnected")
        }
    }
}