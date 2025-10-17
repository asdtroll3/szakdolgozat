package com.example.alphaverzio

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
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

        // Set the theme based on saved preference
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}