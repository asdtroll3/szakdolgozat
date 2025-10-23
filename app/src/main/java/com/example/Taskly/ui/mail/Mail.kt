package com.example.Taskly.ui.mail

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "mail_table")
data class Mail(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderEmail: String,
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)