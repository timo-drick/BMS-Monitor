package de.drick.bmsmonitor.ui.recordings

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.White
import com.mapbox.geojson.Point
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationState

data class GpsData(
    val wayPoints: List<GpsRecord>
)

data class GpsRecord(
    val timeStamp: Long,
    val position: GeoPoint,
    val speed: Float
)

fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
fun Location.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

val lineStyle = PolylineAnnotationState().apply {
    lineWidth = 10.0
    lineColor = Color(0xff90caf9)
    lineBorderColor = Color(0xff1976d2)
    lineBorderWidth = 2.0
}

val currentPosStyle = CircleAnnotationState().apply {
    circleRadius = 8.0
    circleColor = Green
    circleStrokeColor = White
    circleStrokeWidth = 2.0
}

val startPosStyle = CircleAnnotationState().apply {
    circleRadius = 8.0
    circleColor = White
    circleStrokeColor = Black
    circleStrokeWidth = 2.0
}

val endPosStyle = CircleAnnotationState().apply {
    circleRadius = 8.0
    circleColor = Black
    circleStrokeColor = White
    circleStrokeWidth = 2.0
}

@Composable
fun GpsView(
    gpsData: GpsData,
    currentPosition: GpsRecord,
    modifier: Modifier = Modifier
) {
    val mapViewPortState = rememberMapViewportState {
        setCameraOptions {
            zoom(14.0)
            center(currentPosition.position.toPoint())
        }
    }
    val wayPointsData = remember(gpsData) {
        gpsData.wayPoints.map { it.position.toPoint() }
    }
    val currentPoint = remember(currentPosition) {
        currentPosition.position.toPoint()
    }
    LaunchedEffect(currentPoint) {
        mapViewPortState.easeTo(
            cameraOptions { center(currentPoint) },
        )
    }
    val startPoint = remember(gpsData) {
        gpsData.wayPoints.first().position.toPoint()
    }
    val endPoint = remember(gpsData) {
        gpsData.wayPoints.last().position.toPoint()
    }
    MapboxMap(
        modifier = modifier,
        mapViewportState = mapViewPortState,
        scaleBar = {},
        logo = {
            Logo()
        },
        attribution = {
            Attribution()
        }
    ) {
        PolylineAnnotation(points = wayPointsData, lineStyle)
        CircleAnnotation(currentPoint, currentPosStyle)
        CircleAnnotation(startPoint, startPosStyle)
        CircleAnnotation(endPoint, endPosStyle)
    }
}
