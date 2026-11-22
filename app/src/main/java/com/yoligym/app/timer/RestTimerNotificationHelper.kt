package com.yoligym.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yoligym.app.MainActivity
import com.yoligym.app.R

object RestTimerNotificationHelper {
    const val CHANNEL_ONGOING = "yoligym_rest_ongoing_v1"
    const val CHANNEL_FINISHED_VIBRATE = "yoligym_rest_finished_v1"
    const val CHANNEL_FINISHED_NO_VIBRATE = "yoligym_rest_finished_novib_v1"

    const val NOTIFICATION_ID_ONGOING = 2001
    const val NOTIFICATION_ID_FINISHED = 2002

    private const val OPEN_APP_REQUEST_CODE = 100
    private const val ADD_15S_REQUEST_CODE = 101
    private const val SKIP_REQUEST_CODE = 102

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING,
            "Temporizador de descanso activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Muestra la cuenta atrás del descanso mientras usas otras apps"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val finishedVibrateChannel = NotificationChannel(
            CHANNEL_FINISHED_VIBRATE,
            "Fin de descanso (con vibración)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Aviso emergente con sonido y vibración al terminar el descanso"
            setSound(soundUri, audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 300L, 150L, 300L, 150L, 400L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val finishedNoVibrateChannel = NotificationChannel(
            CHANNEL_FINISHED_NO_VIBRATE,
            "Fin de descanso (sin vibración)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Aviso emergente con sonido al terminar el descanso"
            setSound(soundUri, audioAttrs)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(
            listOf(ongoingChannel, finishedVibrateChannel, finishedNoVibrateChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context.applicationContext,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showOngoingCountdownNotification(context: Context, snapshot: RestTimerSnapshot) {
        if (!snapshot.notifyInBackground || !hasNotificationPermission(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val add15Intent = Intent(context.applicationContext, RestTimerReceiver::class.java).apply {
            action = RestTimerCoordinator.ACTION_REST_ADD_15S
        }
        val add15PendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            ADD_15S_REQUEST_CODE,
            add15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context.applicationContext, RestTimerReceiver::class.java).apply {
            action = RestTimerCoordinator.ACTION_REST_SKIP
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            SKIP_REQUEST_CODE,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_yoligym)
            .setContentTitle("Descanso en curso")
            .setContentText("Toca para volver al entrenamiento")
            .setWhen(snapshot.endTimeMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, "+15s", add15PendingIntent)
            .addAction(0, "Saltar", skipPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_ONGOING, notification)
    }

    fun showRestFinishedNotification(context: Context, vibrate: Boolean) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = if (vibrate) CHANNEL_FINISHED_VIBRATE else CHANNEL_FINISHED_NO_VIBRATE
        val defaults = if (vibrate) {
            NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE
        } else {
            NotificationCompat.DEFAULT_SOUND
        }

        val notification = NotificationCompat.Builder(context.applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_yoligym)
            .setContentTitle("¡Descanso terminado!")
            .setContentText("Vuelve a tu siguiente serie en YoliGym")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(defaults)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .build()

        manager.notify(NOTIFICATION_ID_FINISHED, notification)
    }

    fun cancelOngoingNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(NOTIFICATION_ID_ONGOING)
    }

    fun cancelFinishedNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(NOTIFICATION_ID_FINISHED)
    }

    fun triggerVibration(context: Context, durationMs: Long = 400L) {
        try {
            val ctx = context.applicationContext
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
        }
    }
}
