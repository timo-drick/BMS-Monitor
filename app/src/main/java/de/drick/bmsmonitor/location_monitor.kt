package de.drick.bmsmonitor

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.drick.compose.permission.ManifestPermission
import de.drick.compose.permission.checkPermission
import de.drick.log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


fun locationFlow(ctx: Context): Flow<Location> = callbackFlow {
    val listener = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { trySend(it) }
        }
    }
    if (ManifestPermission.ACCESS_FINE_LOCATION.checkPermission(ctx)) {
        val locationProvider = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(1000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setWaitForAccurateLocation(true)
            .build()
        locationProvider.requestLocationUpdates(request, listener, Looper.getMainLooper())
        awaitClose {
            locationProvider.removeLocationUpdates(listener) // Unregister listener on cancellation
        }
    } else {
        log("Location fine permission missing")
    }
}
