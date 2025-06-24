package com.project.smartcoastercompanion

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_ALARM = "com.project.smartcoastercompanion.STOP_ALARM"
        private const val WAKE_LOCK_TIMEOUT = 60000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartCoaster:AlarmWakelock"
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT)

        val alarmType = intent.getStringExtra("alarm_type")

        try {
            // Check the action to decide between starting or stopping the alarm
            when (intent.action) {
                ACTION_STOP_ALARM -> handleStopAlarm(context)
                else -> handleStartAlarm(context, intent)
            }
        } finally {
            // Release the wake lock after handling the alarm
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }


    private fun rescheduleIntervalAlarm(
        context: Context,
        intervalMillis: Long,
        requestCode: Int
    ) {
        val alarmScheduler = AlarmScheduler(context)
        alarmScheduler.setIntervalAlarm(intervalMillis, requestCode)
    }

    // Start the alarm, handle interval rescheduling and regular alarm removal
    private fun handleStartAlarm(context: Context, intent: Intent) {
        val alarmType = intent.getStringExtra("alarm_type")
        val requestCode = intent.getIntExtra("requestCode", 0)

        // Play alarm sound and start vibrating the device
        AlarmSoundManager.playAlarmSound(context)
        vibratePhone(context)

        // Show the notification with a "Stop Alarm" action button
        showAlarmNotification(context, alarmType ?: "Alarm", requestCode)

        // Remove regular alarms from the schedule after they complete
        if (alarmType == "regular") {
            AlarmScheduler(context).cancelAlarm(requestCode)
            Log.d("AlarmReceiver", "Regular alarm canceled: requestCode=$requestCode")
        }

        // If this is an interval alarm, reschedule it to repeat after the specified interval
        if (alarmType == "interval") {
            val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
            rescheduleIntervalAlarm(context, intervalMillis, requestCode)
        }

        // Send a completion broadcast for tracking purposes
        sendCompletionBroadcast(context, intent, requestCode, alarmType)
    }

    private fun handleStopAlarm(context: Context) {
        AlarmSoundManager.stopAlarmSound()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun vibratePhone(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val vibrationPattern = longArrayOf(0, 1000, 500, 1000)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1)
            )
        } else {
            vibrator.vibrate(3000)
        }
    }

    // Display a notification with an action to stop the alarm when tapped
    @SuppressLint("LaunchActivityFromNotification")
    private fun showAlarmNotification(context: Context, alarmType: String, requestCode: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure the notification channel is created (required for API 26+)
        createNotificationChannel(notificationManager)

        // Define the stop action intent for the notification
        val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, AlarmReceiver::class.java)
        val fullScreenPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with the stop action
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$alarmType Alarm")
            .setContentText("Tap to stop alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true) // Keep notification persistent
            .addAction(android.R.drawable.ic_media_pause, "Stop Alarm", stopPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Set the full-screen intent
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Create a notification channel for alarm notifications (API 26+ requirement)
    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for alarm alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Send a broadcast indicating that an alarm has completed
    private fun sendCompletionBroadcast(context: Context, intent: Intent, requestCode: Int, alarmType: String?) {
        val completionIntent = Intent("com.project.smartcoastercompanion.ALARM_COMPLETED").apply {
            putExtra("requestCode", requestCode)
            putExtra("alarm_type", alarmType)
            putExtra("intervalMillis", intent.getLongExtra("intervalMillis", 0L))
        }
        context.sendBroadcast(completionIntent)
    }
}
