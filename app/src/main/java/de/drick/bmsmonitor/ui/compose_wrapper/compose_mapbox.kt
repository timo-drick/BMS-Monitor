package de.drick.bmsmonitor.ui.compose_wrapper

import android.content.Context
import android.location.Location
import android.view.Gravity
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.AttributionDialogManagerImpl
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.*
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import de.drick.bmsmonitor.R
import de.drick.bmsmonitor.ui.recordings.GeoPoint
import de.drick.log
import kotlinx.coroutines.CompletableDeferred

fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
fun Location.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

data class LineStyle(
    val width: Double,
    val color: Color,
    val strokeWidth: Double,
    val strokeColor: Color
)
data class CircleStyle(
    val radius: Double,
    val color: Color,
    val strokeColor: Color,
    val strokeWidth: Double
)

fun Color.toHexString() = String.format("#%06X", 0xffffff and toArgb())

val Ble2 = Color(0xff90caf9)
val Ble7 = Color(0xff1976d2)

private const val cameraAnimationDuration = 2000L

@Composable
fun rememberMapBoxState(initialPosition: GeoPoint? = null) = remember { MapBoxState(initialPosition, 15.0) }

private data class MapBoxStateData(
    val mapboxView: MapView,
    val mapboxMap: MapboxMap,
    val polylineManager: PolylineAnnotationManager,
    val circleManager: CircleAnnotationManager
)

class MapBoxState(val initialPosition: GeoPoint?, initialZoom: Double = 4.0) {
    private var sync = CompletableDeferred<MapBoxStateData>()
    var zoomLevel = initialZoom
        private set

    fun init(mapView: MapView) {
        if (sync.isCompleted) sync = CompletableDeferred()
        val data = MapBoxStateData(
            mapboxView = mapView,
            mapboxMap = mapView.getMapboxMap(),
            polylineManager = mapView.annotations.createPolylineAnnotationManager(),
            circleManager = mapView.annotations.createCircleAnnotationManager()
        )
        sync.complete(data)
    }

    private suspend fun <T>useState(block: suspend MapBoxStateData.() -> T): T = block(sync.await())

    suspend fun showAttributionDialog(ctx: Context) {
        useState {
            AttributionDialogManagerImpl(ctx).showAttribution(mapboxView.attribution.getMapAttributionDelegate())
        }
    }

    private val overviewStyle = LineStyle(
        width = 2.0,
        color = Ble2,
        strokeWidth = .5,
        strokeColor = Ble7
    )

    private suspend fun clearAll() {
        useState {
            polylineManager.deleteAll()
            circleManager.deleteAll()
        }
    }

    suspend fun zoomIn() {
        useState {
            if (zoomLevel < 20) {
                zoomLevel += 1
            }
            val cameraOptions = CameraOptions.Builder()
                .zoom(zoomLevel)
                .pitch(0.0)
                .build()
            mapboxMap.setCamera(cameraOptions)
        }
    }
    suspend fun zoomOut() {
        useState {
            if (zoomLevel > 0) {
                zoomLevel -= 1
            }
            val cameraOptions = CameraOptions.Builder()
                .zoom(zoomLevel)
                .pitch(0.0)
                .build()
            mapboxMap.setCamera(cameraOptions)
        }
    }

    suspend fun drawPoint(point: GeoPoint, circleStyle: CircleStyle): CircleAnnotation = useState {
        val options = CircleAnnotationOptions()
            .withPoint(point.toPoint())
            .withCircleRadius(circleStyle.radius)
            .withCircleColor(circleStyle.color.toHexString())
            .withCircleStrokeColor(circleStyle.strokeColor.toHexString())
            .withCircleStrokeWidth(circleStyle.strokeWidth)
        circleManager.create(options)
    }

    suspend fun updatePoint(point: GeoPoint, annotation: CircleAnnotation) {
        useState {
            annotation.point = point.toPoint()
            circleManager.update(annotation)
        }
    }

    suspend fun drawOverview(points: List<GeoPoint>) {
        draw(points, overviewStyle)
    }

    suspend fun drawColor(color: Color, points: List<GeoPoint>) {
        val style = LineStyle(
            width = 3.0,
            color = color,
            strokeWidth = 1.0,
            strokeColor = color
        )
        draw(points, style)
    }

    suspend fun zoomCamera(points: List<GeoPoint>) {
        moveBoundingCamera(points, 0f, 0.01f)
    }

    suspend fun resetToDefault() {
        log("reset")
        useState {
            clearAll()
            val cameraOptions = CameraOptions.Builder().apply {
                initialPosition?.let { center(it.toPoint()) }
                bearing(0.0)
                zoom(zoomLevel)
                pitch(0.0)
            }.build()
            mapboxMap.setCamera(cameraOptions)
        }
    }

    private suspend fun draw(points: List<GeoPoint>, style: LineStyle) {
        useState {
            drawLines(
                lineManager = polylineManager,
                lineGeometry = points.map { it.toPoint() },
                lineStyle = style
            )
        }
    }

    private fun drawLines(
        lineManager: PolylineAnnotationManager,
        lineGeometry: List<Point>,
        lineStyle: LineStyle
    ) {
        val lineStrokeOptions = PolylineAnnotationOptions()
            .withPoints(lineGeometry)
            .withLineColor(lineStyle.strokeColor.toHexString())
            .withLineWidth(lineStyle.width + lineStyle.strokeWidth)
            .withLineJoin(LineJoin.ROUND)
        lineManager.create(lineStrokeOptions)
        val lineOptions = PolylineAnnotationOptions()
            .withPoints(lineGeometry)
            .withLineColor(lineStyle.color.toHexString())
            .withLineWidth(lineStyle.width)
            .withLineJoin(LineJoin.ROUND)
        lineManager.create(lineOptions)
    }

    suspend fun updateCamera(center: GeoPoint, bearing: Double, smooth: Boolean = true) {
        //log("update bearing: $bearing")
        useState {
            val cameraOptions = CameraOptions.Builder()
                .center(center.toPoint())
                .bearing(bearing)
                //.pitch(0.0)
                .zoom(zoomLevel)
                .build()
            //mapboxMap.setCamera(cameraOptions)
            if (smooth) {
                mapboxMap.easeTo(cameraOptions)
            } else {
                mapboxMap.setCamera(cameraOptions)
            }
            //mapboxMap.flyTo(cameraOptions, MapAnimationOptions.mapAnimationOptions { duration(cameraAnimationDuration) })
        }
    }

    private suspend fun moveBoundingCamera(points: List<GeoPoint>, overlayBottomOffset: Float, padding: Float) {
        log("move bounding")
        useState {
            val nBound = points.maxOf { it.latitude }//boundingBox.northernBoundary
            val eBound = points.minOf { it.longitude }//boundingBox.easternBoundary
            val sBound = points.minOf { it.latitude }//boundingBox.southernBoundary
            val wBound = points.maxOf { it.longitude }//boundingBox.westernBoundary
            val paddingLatitude = (nBound - sBound) * padding
            val paddingLongitude = (eBound - wBound) * padding
            val northEastBounds = Point.fromLngLat(
                eBound + paddingLongitude,
                nBound + paddingLatitude
            )
            val latitudeOffset = sBound - (nBound - sBound + paddingLatitude * 2) *
                    overlayBottomOffset / (1 - overlayBottomOffset)
            val southWestBounds = Point.fromLngLat(
                wBound - paddingLongitude,
                latitudeOffset - paddingLatitude
            )
            val cameraOptions = mapboxMap.cameraForCoordinateBounds(
                bounds = CoordinateBounds(
                    southWestBounds,
                    northEastBounds,
                    false
                )
            ).toBuilder().let {
                it.bearing(0.0)
                it.pitch(0.0)
                it.build()
            }
            log("Calculate bounding zoom level ${cameraOptions.zoom}")
            mapboxMap.setCamera(cameraOptions)
        }
    }
}

@Composable
fun MapBox(
    state: MapBoxState,
    onDisableCameraFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onNewPosition: (GeoPoint) -> Unit = {}
) {
    val mapClickListener = remember {
        OnMapClickListener {
            onNewPosition(
                GeoPoint(
                    latitude = it.latitude(),
                    longitude = it.longitude()
                )
            )
            false
        }
    }
    if (LocalInspectionMode.current) {
        Image(
            modifier = modifier,
            painter = painterResource(R.drawable.preview_map),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                log("Mapbox view created")
                val cameraOptions = state.initialPosition?.let { position ->
                    CameraOptions.Builder().apply {
                        center(position.toPoint())
                        bearing(0.0)
                        zoom(state.zoomLevel)
                        pitch(0.0)
                    }.build()
                }
                val options = MapInitOptions(
                    context = context,
                    textureView = true,
                    cameraOptions = cameraOptions
                )
                val view = MapView(context, mapInitOptions = options)
                val map = view.mapboxMap
                state.init(view)
                view.compass.enabled = false
                view.scalebar.enabled = false
                map.loadStyle(
                    styleExtension = style(Style.SATELLITE_STREETS) {},
                    onStyleLoaded = {}
                )
                view.attribution.enabled = false
                view.attribution.clickable = false
                view.attribution.position = Gravity.TOP
                view.logo.position = Gravity.TOP
                log("Set initial position")
                state.initialPosition?.let { position ->
                    val cameraOptions = CameraOptions.Builder().apply {
                        center(position.toPoint())
                        bearing(0.0)
                        zoom(state.zoomLevel)
                        pitch(0.0)
                    }.build()
                    map.flyTo(
                        cameraOptions,
                        MapAnimationOptions.mapAnimationOptions { duration(cameraAnimationDuration) }
                    )
                    //setCamera(cameraOptions)
                }
                map.addOnMapClickListener(mapClickListener)
                view
            },
            update = {
                log("Update")
                /*it.getMapboxMap().apply {
                    removeOnFlingListener(flingListener)
                    removeOnMoveListener(moveListener)
                    removeOnRotateListener(rotateListener)
                    removeOnScaleListener(scaleListener)
                    removeOnShoveListener(shoveListener)
                }*/
            },
            onReset = {
                log("onReset")
            },
            onRelease = {
                log("Release")
            }
        )
    }
}
