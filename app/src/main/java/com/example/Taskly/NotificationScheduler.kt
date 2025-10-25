package com.example.Taskly

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.Taskly.ui.calendar.Event

object NotificationScheduler {

    private const val ONE_HOUR_IN_MILLIS = 3_600_000L

    fun scheduleNotification(context: Context, event: Event) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    Toast.makeText(context, "Please grant permission to schedule alarms", Toast.LENGTH_LONG).show()
                    context.startActivity(it)
                }
                return
            }
        }

        val triggerTime = event.startTime.time - ONE_HOUR_IN_MILLIS

        if (triggerTime > System.currentTimeMillis()) {
            val intent = Intent(context, EventNotificationReceiver::class.java).apply {
                putExtra("EXTRA_EVENT_ID", event.id)
                putExtra("EXTRA_EVENT_TITLE", event.title)
                putExtra("EXTRA_EVENT_DESCRIPTION", event.description)
                putExtra("EXTRA_EVENT_OWNER_EMAIL", event.ownerEmail)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                event.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d("NotificationScheduler", "event: ${event.id} idő: $triggerTime")
            } catch (e: SecurityException) {
                Log.e("NotificationScheduler", "FAIL", e)
                Toast.makeText(context, "Could not schedule reminder.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelNotification(context: Context, event: Event) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, EventNotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d("NotificationScheduler", "delete event: ${event.id}")
    }
}