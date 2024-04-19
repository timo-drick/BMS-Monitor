package de.drick.bmsmonitor.bluetooth

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.checkPermission
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Sources:
 * https://punchthrough.com/android-ble-guide/
 * https://github.com/syssi/esphome-jk-bms/blob/main/docs/protocol-design.md
 * Bluetooth analyzing: https://source.android.com/docs/core/connect/bluetooth/verifying_debugging#debugging-with-bug-reports
 * Official Android documentation:
 * https://source.android.com/docs/core/connect/bluetooth/ble
 */

@SuppressLint("MissingPermission")
class BluetoothLeConnectionService(private val ctx: Context) {
    enum class State {
        Connected, Connecting, Disconnecting, Disconnected
    }
    private var connectedGatt: BluetoothGatt? = null
    private val _connectionState = MutableStateFlow(State.Disconnected)
    val connectionState: StateFlow<State> = _connectionState

    private val discoveryResult = MutableStateFlow<List<BluetoothGattService>?>(null)

    private val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val clientCharacteristicConfigurationDescriptor = checkNotNull(UUID.fromString("2902-0000-1000-8000-00805f9b34fb"))

    suspend fun connect(deviceAddress: String) = withContext(Dispatchers.IO) {
        log("Connecting...")
        if (connectAsync(deviceAddress)) {
            //Wait until connected
            connectionState.first { it == State.Connected }
            log("Connected")
        }
    }
    fun connectAsync(deviceAddress: String): Boolean {
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress).not()) {
            log("provided deviceAddress: $deviceAddress is not valid!")
            return false
        }
        val adapter = bluetoothManager.adapter
        val device = adapter.getRemoteDevice(deviceAddress)
        if (ManifestPermission.BLUETOOTH_CONNECT.checkPermission(ctx).not()) {
            log("BLUETOOTH_CONNECT permission not granted")
            return false
        }
        device.connectGattCompat(ctx, false, CompatTransportType.TRANSPORT_LE, bluetoothGattCallback)
        return true
    }

    fun disconnect() {
        connectedGatt?.disconnect()
    }

    suspend fun discover() {
        log("Discovering...")
        if (discoverAsync()) {
            discoveryResult.filter { it != null }.first()
            log("Discovered")
        }
    }

    fun discoverAsync(): Boolean {
        connectedGatt?.let { gatt ->
            return gatt.discoverServices()
        } ?: log("Not connected!")
        return false
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, value: ByteArray) {
        connectedGatt?.let { gatt ->
            val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
            if (characteristic != null) {
                //log("is readable : ${characteristic.isReadable()}")
                //log("is writeable: ${characteristic.isWritable()}")
                //log("Write values: ${value.toHexString()}")
                if (characteristic.isWritable()) {
                    val result = gatt.writeCharacteristicCompat(
                        characteristic, value,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                    //log("Result: $result")
                } else {
                    log("Characteristic not writeable!")
                }
            } else {
                log("Unable to find characteristic!")
            }
        }
    }

    data class CharacteristicSubscription(
        val uuid: UUID,
        val callback: (ByteArray) -> Unit
    )

    private val subscriptionMap = mutableMapOf<UUID, CharacteristicSubscription>()
    fun subscribeForNotification(
        serviceUUID: UUID, characteristicUUID: UUID, callback: (ByteArray) -> Unit
    ) {
        val subscriptionData = CharacteristicSubscription(characteristicUUID, callback)
        subscriptionMap[subscriptionData.uuid] = subscriptionData
        unSubscribeForNotificationInternal(true, serviceUUID, characteristicUUID)
    }
    fun unSubscribeForNotification(serviceUUID: UUID, characteristicUUID: UUID) {
        unSubscribeForNotificationInternal(false, serviceUUID, characteristicUUID)
        subscriptionMap.remove(characteristicUUID)
    }

    private fun unSubscribeForNotificationInternal(
        subscribe: Boolean,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        connectedGatt?.let { gatt ->
            val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
            if (characteristic != null) {
                log("is readable   : ${characteristic.isReadable()}")
                log("is writeable  : ${characteristic.isWritable()}")
                log("is indicatable: ${characteristic.isIndicatable()}")
                log("is notifiable : ${characteristic.isNotifiable()}")
                val success = gatt.setCharacteristicNotification(characteristic, true)
                log("result: $success")
                characteristic.getDescriptor(clientCharacteristicConfigurationDescriptor)
                    ?.let { cccDescriptor ->
                        val result = gatt.writeDescriptorCompat(
                            descriptor = cccDescriptor,
                            value = if (subscribe)
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            else
                                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        )
                        log("write ccc descriptor: $result")
                    }
            } else {
                log("Unable to find service or characteristc!")
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected")
                    connectedGatt = gatt
                    _connectionState.value = State.Connected
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Disconnected")
                    _connectionState.value = State.Disconnected
                    connectedGatt = null
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    log("Connecting...")
                    _connectionState.value = State.Connecting
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    log("Disconnecting...")
                    _connectionState.value = State.Disconnecting
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status ==  BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                services.forEach { gattService ->
                    val uuid = gattService.uuid.toString()
                    val gattCharacteristics = gattService.characteristics
                    val serviceName = AllGattServices.lookup(uuid)
                    log("Service: $uuid type: ${gattService.type} name: $serviceName")
                    gattCharacteristics.forEach { gattCharacteristic ->
                        val characteristicUuid = gattCharacteristic.uuid.toString()
                        val characteristicName = AllGattCharacteristics.lookup(characteristicUuid)
                        log("   Characteristics: $characteristicUuid $characteristicName")
                    }
                }
                discoveryResult.value = services
            } else {
                log("Error during service discovery status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            log("onWrite: $status")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            log("descriptor write status: $status")
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            log("descriptor read")
        }

        /**
         * Backwards compatibility used on devices api < 33
         */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { value ->
                //log("Characteristic changed:\n${value.toHexString()}")
                subscriptionMap[characteristic.uuid]?.callback?.invoke(value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            //log("Characteristic changed:\n${value.toHexString()}")
            subscriptionMap[characteristic.uuid]?.callback?.invoke(value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        log("Read characteristic $uuid:\n${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        log("Read not permitted for $uuid!")
                    }
                    else -> {
                        log("Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }
    }

    private fun getCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): BluetoothGattCharacteristic? {
        connectedGatt?.let { gatt ->
            discoveryResult.value?.let { discoveryResult ->
                // Search characteristic in discovered result
                val service = discoveryResult.find { it.uuid == serviceUUID }
                if (service != null) {
                    val characteristic = service.characteristics.find { it.uuid == characteristicUUID }
                    if (characteristic != null) {
                        return characteristic
                    }
                }
            }
            // Not found in discoverd services. Check if we can connect directly
            gatt.getService(serviceUUID)?.let { service ->
                service.getCharacteristic(characteristicUUID)?.let { characteristic ->
                    log("is readable   : ${characteristic.isReadable()}")
                    log("is writeable  : ${characteristic.isWritable()}")
                    log("is indicatable: ${characteristic.isIndicatable()}")
                    log("is notifiable : ${characteristic.isNotifiable()}")
                    return characteristic
                } ?: log("Characteristics not found!")
            } ?: log("Service not found!")
        } ?: log("not connected!")
        return null
    }
}

enum class CompatTransportType {
    TRANSPORT_AUTO,
    TRANSPORT_BREDR,
    TRANSPORT_LE,
}

@RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
fun BluetoothDevice.connectGattCompat(
    ctx: Context,
    autoConnect: Boolean = false,
    transportType: CompatTransportType,
    bluetoothGattCallback: BluetoothGattCallback
) {
    if (Build.VERSION.SDK_INT >= 23) {
        val type = when(transportType) {
            CompatTransportType.TRANSPORT_AUTO -> BluetoothDevice.TRANSPORT_AUTO
            CompatTransportType.TRANSPORT_BREDR -> BluetoothDevice.TRANSPORT_BREDR
            CompatTransportType.TRANSPORT_LE -> BluetoothDevice.TRANSPORT_LE
        }
        connectGatt(ctx, autoConnect, bluetoothGattCallback, type)
    } else {
        connectGatt(ctx, autoConnect, bluetoothGattCallback)
    }
}

@RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
fun BluetoothGatt.writeDescriptorCompat(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
) = if (Build.VERSION.SDK_INT >= 33) {
    writeDescriptor(descriptor, value)
} else {
    descriptor.value = value
    writeDescriptor(descriptor)
}

@RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
fun BluetoothGatt.writeCharacteristicCompat(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: Int
) {
    if (Build.VERSION.SDK_INT >= 33) {
        writeCharacteristic(characteristic, value, writeType)
    } else {
        characteristic.value = value
        characteristic.writeType = writeType
        writeCharacteristic(characteristic)
    }
}

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return properties and property != 0
}