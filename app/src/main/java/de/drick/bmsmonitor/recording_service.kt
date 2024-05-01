package de.drick.bmsmonitor

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.drick.bmsmonitor.bms_adapter.MonitorService
import de.drick.bmsmonitor.repository.RecordingRepository
import de.drick.bmsmonitor.ui.MainActivity
import de.drick.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val RECORDING_CHANNEL = "RECORDING_CHANNEL_1"
const val RECORDING_SERVICE_ID = 0xcafebabe.toInt()

class BackgroundRecordingService: LifecycleService() {
    companion object {

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow = _isRunningFlow.asStateFlow()

        private fun getIntent(ctx: Context, deviceAddress: String, recordLocation: Boolean) =
            Intent(ctx, BackgroundRecordingService::class.java).apply {
                putExtra("device_address", deviceAddress)
                putExtra("record_location", recordLocation)
            }
        private fun extractIntent(intent: Intent?): Pair<String?, Boolean> = intent?.let {
            Pair(
            first = it.getStringExtra("device_address"),
            second = it.getBooleanExtra("record_location", false)
            )
        } ?: Pair(null, false)


        fun start(ctx: Context, deviceAddress: String, recordLocation: Boolean) {
            log("Start service")
            val serviceIntent = getIntent(ctx, deviceAddress, recordLocation)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
        }

        fun stop(ctx: Context, deviceAddress: String) {
            ctx.stopService(getIntent(ctx, deviceAddress, false))
        }
    }

    private lateinit var notificationManager: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        log("Starting service ")
        val channel = NotificationChannelCompat.Builder(RECORDING_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Recording state")
            .setSound(null, null)
            .build()
        notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(channel)
        _isRunningFlow.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        log("Stopping service")
        notificationManager.cancel(RECORDING_SERVICE_ID)
        _isRunningFlow.value = false
        //coroutineJob.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        log("Start command received $intent")
        val notification = updateNotification()
        startForeground(RECORDING_SERVICE_ID, notification)
        val (deviceAddress, recordLocation) = extractIntent(intent)
        deviceAddress?.let { address ->
            lifecycleScope.launch {
                log("Start recording...")
                recordData(address, recordLocation)
            }
        } ?: log("No device address in intent!")
        return START_STICKY
    }

    private suspend fun recordData(deviceAddress: String, recordLocation: Boolean) {
        val ctx = this
        val recordingRepository = RecordingRepository(ctx)
        val monitor = MonitorService.getMonitor(ctx, deviceAddress)
        log("Start recording data location: $recordLocation")
        recordingRepository.startRecordingBMS(deviceAddress).use { recorder ->
            withContext(Dispatchers.IO) {
                launch {
                    monitor.bmsRawFlow.collect { data ->
                        recorder.add(data)
                    }
                }
                if (recordLocation) {
                    launch {
                        locationFlow(ctx).collect { location ->
                            recorder.add(location)
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, RECORDING_CHANNEL)
            .setContentTitle("Recording bms...")
            .setContentText("Recording bms data in progress")
            .setSmallIcon(R.drawable.baseline_downloading_24)
            .setContentIntent(pendingIntent)
            .build()
        try {
            notificationManager.notify(RECORDING_SERVICE_ID, notification)
        } catch (err: SecurityException) {
            log(err)
        }
        return notification
    }
}
