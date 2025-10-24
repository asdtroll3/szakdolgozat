package com.example.Taskly.ui.projects

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_table")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerEmail: String,
    val name: String,
    val iconName: String,
    val color: Int? = null
)