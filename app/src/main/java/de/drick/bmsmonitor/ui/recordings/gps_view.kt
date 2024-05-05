package de.drick.bmsmonitor.ui.recordings

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.White
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import de.drick.bmsmonitor.ui.compose_wrapper.CircleStyle
import de.drick.bmsmonitor.ui.compose_wrapper.MapBox
import de.drick.bmsmonitor.ui.compose_wrapper.MapBoxState
import de.drick.log

data class GpsData(
    val wayPoints: List<GpsRecord>
)

data class GpsRecord(
    val timeStamp: Long,
    val position: GeoPoint,
    val speed: Float
)

private val styleCurrentPoint = CircleStyle(
    radius = 4.0,
    color = Green,
    strokeColor = White,
    strokeWidth = 1.0
)
private val styleStartPoint = CircleStyle(
    radius = 4.0,
    color = White,
    strokeColor = Black,
    strokeWidth = 1.0
)
private val styleEndPoint = CircleStyle(
    radius = 4.0,
    color = Black,
    strokeColor = White,
    strokeWidth = 1.0
)

private class GpsViewState(private val mapBoxState: MapBoxState) {
    var currentPositionPoint: CircleAnnotation? = null
    suspend fun init(geoPoints: List<GeoPoint>) {
        mapBoxState.resetToDefault()
        if (geoPoints.size > 1) {
            mapBoxState.drawOverview(geoPoints)
            mapBoxState.drawPoint(geoPoints.first(), styleStartPoint)
            mapBoxState.drawPoint(geoPoints.last(), styleEndPoint)
        }
        currentPositionPoint = mapBoxState.drawPoint(geoPoints.first(), styleCurrentPoint)
        mapBoxState.updateCamera(geoPoints.first(), 0.0, false)
    }

    suspend fun update(currentPosition: GeoPoint) {
        currentPositionPoint?.let { pp ->
            log("update camera")
            mapBoxState.updatePoint(currentPosition, pp)
            mapBoxState.updateCamera(currentPosition, 0.0)
        }
    }
}

@Composable
fun GpsView(
    mapBoxState: MapBoxState,
    gpsData: GpsData,
    currentPosition: GpsRecord,
    modifier: Modifier = Modifier
) {
    val state = remember {
        GpsViewState(mapBoxState)
    }

    //val mapBoxState = rememberMapBoxState(frame.position)
    LaunchedEffect(gpsData) {
        log("launched effect start")
        state.init(gpsData.wayPoints.map { it.position })
    }
    LaunchedEffect(currentPosition) {
        log("New position: $currentPosition")
        state.update(currentPosition.position)
    }
    MapBox(
        modifier = modifier,
        state = mapBoxState,
        onDisableCameraFocus = { /*TODO*/ }
    )
}
