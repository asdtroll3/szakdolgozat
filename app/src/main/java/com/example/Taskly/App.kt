package com.example.Taskly

import android.app.Application
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.example.Taskly.data.AppDatabase

class App : Application() {

    companion object {
        lateinit var database: AppDatabase
            private set

        lateinit var sharedPreferences: SharedPreferences
            private set

        const val EVENT_CHANNEL_ID = "event_reminder_channel"
    }

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "taskly_db"
        ).fallbackToDestructiveMigration().build()

        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)

        createNotificationChannel()
    }
    private fun createNotificationChannel() {
        val name = "Event Reminders"
        val descriptionText = "Notifications for upcoming events"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(EVENT_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}