package de.drick.compose.permission

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.*
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


private inline fun checkApiLevel(apiLevel: Int, block: () -> String): String {
    return if (Build.VERSION.SDK_INT >= apiLevel) block() else "NA"
}

enum class ManifestPermission(val permission: String, val minApiLevel: Int) {
    POST_NOTIFICATIONS(checkApiLevel(33) { Manifest.permission.POST_NOTIFICATIONS }, 33),
    ACCESS_FINE_LOCATION(Manifest.permission.ACCESS_FINE_LOCATION, 1),
    ACTIVITY_RECOGNITION(checkApiLevel(29) { Manifest.permission.ACTIVITY_RECOGNITION }, 29),
    BLUETOOTH_CONNECT(checkApiLevel(31) { Manifest.permission.BLUETOOTH_CONNECT }, 31),
    BLUETOOTH_SCAN(checkApiLevel(31) { Manifest.permission.BLUETOOTH_SCAN }, 31)
}

/**
 * Checks if the permission is granted
 *
 * Because of PermissionRequired linter check this method must
 * be named like check|enforce....Permission otherwise it will not be recognized as permission check.
 * See: https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/PermissionDetector.kt
 */
fun ManifestPermission.checkPermission(ctx: Context) = if (Build.VERSION.SDK_INT >= minApiLevel) {
    ContextCompat.checkSelfPermission(ctx, permission) == PermissionChecker.PERMISSION_GRANTED
} else {
    true
}

@Composable
fun rememberPermissionState(permission: ManifestPermission): PermissionState {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {} // already observed by lifecycle observer
    )
    val permissionState = remember { MutablePermissionState(ctx, permission, launcher) }

    DisposableEffect(lifecycle) {
        // observe permission
        val permissionCheckerObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState.hasPermission = permission.checkPermission(ctx)
            }
        }
        lifecycle.addObserver(permissionCheckerObserver)
        onDispose { lifecycle.removeObserver(permissionCheckerObserver) }
    }

    return permissionState
}

interface PermissionState {
    val hasPermission: Boolean
    fun launchPermissionRequest()
}

private class MutablePermissionState(
    ctx: Context,
    private val permission: ManifestPermission,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>
) : PermissionState {
    override var hasPermission by mutableStateOf(permission.checkPermission(ctx))
    override fun launchPermissionRequest() = launcher.launch(permission.permission)
}
