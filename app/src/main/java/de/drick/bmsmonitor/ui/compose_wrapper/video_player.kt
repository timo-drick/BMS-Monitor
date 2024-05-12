package de.drick.bmsmonitor.ui.compose_wrapper

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import de.drick.bmsmonitor.R
import de.drick.log

class VideoPlayerState(ctx: Context) {
    enum class PlayBackState {
        PLAY, PAUSE, ENDED
    }

    private val player = ExoPlayer.Builder(ctx).build()

    var aspectRatio by mutableFloatStateOf(1.3f)
    var state by mutableStateOf(PlayBackState.PAUSE)
    val duration get() = player.duration
    val position get() = player.currentPosition
    private var currentVideoFile: Uri? = null

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                aspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                log("Set aspect ratio: $aspectRatio")
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            log("is playing: $isPlaying")
            if (state != PlayBackState.ENDED || isPlaying) {
                state = if (isPlaying) PlayBackState.PLAY else PlayBackState.PAUSE
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            log("state changed: $playbackState")
            state = when (playbackState) {
                Player.STATE_READY -> PlayBackState.PAUSE
                Player.STATE_ENDED -> PlayBackState.ENDED
                else -> state
            }
        }
    }
    init {
        player.addListener(listener)
    }

    internal fun bindSurfaceView(surfaceView: SurfaceView) {
        log("Bind surface view")
        //player.stop()
        player.setVideoSurfaceView(surfaceView)
        //currentVideoFile?.let { play(it) }
    }

    fun play(videoFile: Uri) {
        //if (currentVideoFile != videoFile) {
        player.stop()
        player.clearMediaItems()
        val item = MediaItem.fromUri(videoFile)
        player.addMediaItem(item)
        currentVideoFile = videoFile
        player.prepare()
        player.play()
        player.duration
        //}
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
    }

    fun seek(position: Long) {
        player.seekTo(position)
    }

    fun release() {
        player.release()
    }
}

@Composable
fun rememberVideoPlayerState(): VideoPlayerState {
    val ctx = LocalContext.current
    return remember { VideoPlayerState(ctx) }
}


@Composable
fun VideoPlayer(
    state: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        Image(
            modifier = Modifier.aspectRatio(state.aspectRatio).fillMaxSize(),
            painter = painterResource(id = R.drawable.video_thumb2),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                log("Create surfaceview")
                SurfaceView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    state.bindSurfaceView(this)
                    //exoPlayer.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
            },
            update = {

            }
        )
    }
}