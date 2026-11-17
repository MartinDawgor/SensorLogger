package com.salihov.sensorlogger.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.salihov.sensorlogger.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RecordingServiceBridge {
    sealed interface ServiceCommand {
        data object StopRecording : ServiceCommand
        data object InsertMarker : ServiceCommand
    }

    private val _commands = MutableSharedFlow<ServiceCommand>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    fun emitCommand(cmd: ServiceCommand) {
        _commands.tryEmit(cmd)
    }
}

class SensorRecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FROM_NOTIFICATION -> {
                RecordingServiceBridge.emitCommand(RecordingServiceBridge.ServiceCommand.StopRecording)
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_MARKER_FROM_NOTIFICATION -> {
                RecordingServiceBridge.emitCommand(RecordingServiceBridge.ServiceCommand.InsertMarker)
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            else -> {
                val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "sensors.csv"
                val sampleCount = intent?.getLongExtra(EXTRA_SAMPLE_COUNT, 0L) ?: 0L
                val acquireWakeLock = intent?.getBooleanExtra(EXTRA_WAKE_LOCK, true) ?: true
                if (acquireWakeLock && wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "SensorLogger::RecordingWakeLock"
                    )?.apply { acquire(6 * 60 * 60 * 1000L) }
                }
                startForeground(NOTIFICATION_ID, buildNotification(fileName, sampleCount))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновая запись датчиков",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает статус активной записи логов датчиков"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(fileName: String, sampleCount: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SensorRecordingService::class.java).apply { action = ACTION_STOP_FROM_NOTIFICATION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markerIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, SensorRecordingService::class.java).apply { action = ACTION_MARKER_FROM_NOTIFICATION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Запись датчиков: $fileName")
            .setContentText("Записано строк: $sampleCount • Фон активен")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "+ Метка", markerIntent)
            .addAction(0, "Стоп", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sensor_logger_recording"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_SAMPLE_COUNT = "extra_sample_count"
        private const val EXTRA_WAKE_LOCK = "extra_wake_lock"

        const val ACTION_START_OR_UPDATE = "com.salihov.sensorlogger.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.salihov.sensorlogger.ACTION_STOP"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.salihov.sensorlogger.ACTION_NOTIF_STOP"
        const val ACTION_MARKER_FROM_NOTIFICATION = "com.salihov.sensorlogger.ACTION_NOTIF_MARKER"

        fun startOrUpdate(context: Context, fileName: String, sampleCount: Long) {
            val intent = Intent(context, SensorRecordingService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_SAMPLE_COUNT, sampleCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SensorRecordingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching { context.startService(intent) }
        }
    }
}
