package com.example.Taskly.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.login.User

@Database(entities = [Event::class, User::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao

}
