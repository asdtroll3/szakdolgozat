package com.example.Taskly.ui.login

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_table")
data class User(
    @PrimaryKey
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val email: String,
    val username: String,
    val password: String,
    val isDarkMode: Boolean = false
)