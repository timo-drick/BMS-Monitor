package de.drick.compose.permission

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.drick.log

/**
 * Official Android documentation about the bluetooth permissions:
 * https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
 *
 */

@Composable
fun rememberBluetoothState(): BluetoothState {
    val ctx = LocalContext.current
    val bluetoothConnectPermissionState = rememberPermissionState(ManifestPermission.BLUETOOTH_CONNECT)
    val bluetoothState = remember { MutableBluetoothState(ctx, bluetoothConnectPermissionState) }
    // observe bluetooth changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null) return
                if (intent == null) return
                log("broadcast received: $intent")
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    bluetoothState.update()
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            ctx.unregisterReceiver(receiver)
        }
    }

    return bluetoothState
}



interface BluetoothState {
    /**
     * Returns true when bluetooth is enabled
     */
    val isEnabled: Boolean

    /**
     * Since Android SDK 31 you need to request a permission to be able to request
     * to turn on bluetooth.
     */
    val isLaunchPermissionGranted: Boolean

    /**
     * Checks if the device hardware supports bluetooth
     */
    val isBluetoothSupported: Boolean
    /**
     * Checks if the device hardware supports bluetooth le
     */
    val isBluetoothLESupported: Boolean
    fun launchEnableBluetoothRequest()
}

private class MutableBluetoothState(
    private val ctx: Context,
    private val bluetoothConnectPermissionState: PermissionState
) : BluetoothState {
    override var isEnabled by mutableStateOf(isBluetoothEnabled())
    override val isLaunchPermissionGranted = bluetoothConnectPermissionState.hasPermission
    //private val packageManager =
    private val bluetoothAvailable = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    override val isBluetoothSupported = bluetoothAvailable
    private val bluetoothLEAvailable = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    override val isBluetoothLESupported = bluetoothLEAvailable

    override fun launchEnableBluetoothRequest() {
        val permissionGranted = ManifestPermission.BLUETOOTH_CONNECT.checkPermission(ctx)
        if (permissionGranted) {
            ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            log("Not allowed to enable bluetooth")
            bluetoothConnectPermissionState.launchPermissionRequest()
        }
    }
    fun update() {
        isEnabled = isBluetoothEnabled()
    }
    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled ?: false
    }
}
