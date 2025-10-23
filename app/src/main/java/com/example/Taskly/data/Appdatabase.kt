package com.example.Taskly.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.login.User
import com.example.Taskly.ui.mail.Mail
import com.example.Taskly.ui.mail.MailDao

@Database(entities = [Event::class, User::class, Mail::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao
    abstract fun mailDao(): MailDao

}
