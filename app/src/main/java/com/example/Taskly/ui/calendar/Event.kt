package com.example.Taskly.ui.calendar
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerEmail: String,
    val title: String,
    val description: String,
    val date: Date,
    val startTime: Date,
    val endTime: Date,
    var isCompleted: Boolean = false,
    val projectId: Int? = null
)