package de.drick.bmsmonitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.drick.bmsmonitor.ui.MainActivity
import de.drick.log

const val RECORDING_CHANNEL = "RECORDING_CHANNEL_1"
const val RECORDING_SERVICE_ID = 0xcafebabe.toInt()

class RecordingService: Service() {
    companion object {
        private fun getIntent(ctx: Context) = Intent(ctx, RecordingService::class.java)

        fun start(ctx: Context) {
            val serviceIntent = getIntent(ctx)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(getIntent(ctx))
        }
    }

    private lateinit var notificationManager: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        log("Starting service")
        val channel = NotificationChannelCompat.Builder(RECORDING_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Recording state")
            .setSound(null, null)
            .build()
        notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("Stopping service")
        notificationManager.cancel(RECORDING_SERVICE_ID)
        //coroutineJob.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Start command received")
        val notification = updateNotification()
        startForeground(RECORDING_SERVICE_ID, notification)
        return START_STICKY
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

    override fun onBind(intent: Intent?): IBinder? {
        log("Service binding received")
        throw UnsupportedOperationException("Binding is not supported and also not needed!")
    }
}