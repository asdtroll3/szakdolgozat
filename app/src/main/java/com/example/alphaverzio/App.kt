package com.example.alphaverzio

import android.app.Application
import androidx.room.Room
import com.example.alphaverzio.data.AppDatabase

class App : Application() {

    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize the Room database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "alpha_verzio_db"
        ).fallbackToDestructiveMigration().build()
    }
}
