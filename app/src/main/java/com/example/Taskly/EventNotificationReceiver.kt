package com.example.Taskly

import android.util.Log
import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.Taskly.App.Companion.EVENT_CHANNEL_ID
import android.graphics.BitmapFactory

class EventNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("EventReceiver", "onReceive triggered!")
        val eventId = intent.getIntExtra("EXTRA_EVENT_ID", 0)
        val title = intent.getStringExtra("EXTRA_EVENT_TITLE") ?: "Event Reminder"
        val description = intent.getStringExtra("EXTRA_EVENT_DESCRIPTION") ?: "Your event is starting soon."
        val customTitle = "$title is in 1 hour"

        // Create an intent to open the app when the notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId, // Use event ID as request code to make it unique
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIconBitmap = BitmapFactory.decodeResource(
            context.resources,
            R.mipmap.ic_launcher_foreground
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, EVENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calendar)
            .setLargeIcon(largeIconBitmap)
            .setContentTitle(customTitle)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Set the tap action
            .setAutoCancel(true) // Dismiss notification on tap

        // Check for notification permission before showing
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification
                notify(eventId, builder.build())
            }
        }
    }
}