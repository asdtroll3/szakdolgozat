package com.example.alphaverzio.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.alphaverzio.ui.calendar.Event
import com.example.alphaverzio.data.EventDao
import com.example.alphaverzio.data.Converters

@Database(entities = [Event::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    // Add more DAOs here when needed in the future
}
