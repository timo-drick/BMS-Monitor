package de.drick.bmsmonitor.ui.recordings

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.drick.bmsmonitor.ui.compose_wrapper.VideoPlayer
import de.drick.bmsmonitor.ui.compose_wrapper.VideoPlayerState
import de.drick.bmsmonitor.ui.compose_wrapper.rememberVideoPlayerState
import de.drick.bmsmonitor.ui.compose_wrapper.UriFile
import de.drick.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


@Composable
fun RecordingsVideoView(
    data: UIDetailData,
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val videoState = rememberVideoPlayerState()
    val playbackState = remember { PlaybackState(data.gps.wayPoints, data.bmsRecords) }
    val ctx = LocalContext.current
    val videoOffset: Long = remember(videoState.duration) {
        val uriFile = UriFile.fromUri(ctx, videoUri)
        uriFile?.let {
            log("Video modified: ${uriFile?.lastModified}")
            log("Gps start     : ${data.gps.wayPoints.first().timeStamp}")
            log("Bms data      : ${data.bmsRecords.first().time}")
            log("Video duration: ${videoState.duration}")
            log("Video start   : ${it.lastModified - 60*60*1000}")
            it.lastModified - 60L*60_1000L
        } ?: 0L
    }
    var progress by remember { mutableFloatStateOf(0f) }
    LifecycleResumeEffect(videoUri) {
        videoState.play(videoUri)
        onPauseOrDispose {
            videoState.stop()
        }
    }
    LaunchedEffect(videoState.state) {
        while (isActive && videoState.state == VideoPlayerState.PlayBackState.PLAY) {
            playbackState.seekPosition(videoState.position + videoOffset)
            progress = videoState.position.toFloat() / videoState.duration.toFloat()
            delay(1000)
            log("Seek position: ${videoState.position} offset: $videoOffset")
        }
    }
    val view = LocalView.current
    LaunchedEffect(videoState.state) {
        view.keepScreenOn = (videoState.state == VideoPlayerState.PlayBackState.PLAY)
    }
    val controlButtonState by remember {
        derivedStateOf {
            when (videoState.state) {
                VideoPlayerState.PlayBackState.PLAY -> ControlButtonState.PAUSE
                VideoPlayerState.PlayBackState.PAUSE -> ControlButtonState.PLAY
                VideoPlayerState.PlayBackState.ENDED -> ControlButtonState.REPLAY
            }
        }
    }
    Box(
        modifier = modifier
    ) {
        VideoPlayer(
            modifier = Modifier.fillMaxSize(),
            state = videoState
        )
        Column {
            val voltage = remember(playbackState.positionRecord) {
                "%.1f".format(playbackState.positionRecord.voltage)
            }
            val speed = remember(playbackState.positionGps) {
                "%.0f".format(playbackState.positionGps.speed)
            }
            Text(voltage)
            Text(speed)
            Spacer(modifier = Modifier.weight(1f))
            PlayerControlBar(
                modifier = Modifier.padding(horizontal = 8.dp),
                controlButtonState = controlButtonState,
                progress = progress,
                onTogglePlay = {
                    when (videoState.state) {
                        VideoPlayerState.PlayBackState.PLAY -> {
                            videoState.pause()
                        }
                        VideoPlayerState.PlayBackState.PAUSE -> {
                            videoState.play()
                        }
                        VideoPlayerState.PlayBackState.ENDED -> {
                            videoState.play(videoUri)
                        }
                    }
                },
                onSeek = {
                    val position = (videoState.duration * it).toLong()
                    videoState.seek(position)
                    //TODO calculate offset
                    playbackState.seekPosition(position + videoOffset)
                }
            )
        }
    }
}