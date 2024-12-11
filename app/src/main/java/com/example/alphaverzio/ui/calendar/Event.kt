package com.example.alphaverzio.ui.calendar


import java.util.Date

data class Event(
    val id: Long = 0,
    val title: String,
    val description: String,
    val date: Date,
    val startTime: Date,
    val endTime: Date,
    var isCompleted: Boolean = false
)

