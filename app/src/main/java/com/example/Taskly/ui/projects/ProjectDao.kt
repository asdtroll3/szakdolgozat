package com.example.Taskly.ui.projects

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)

    @Query("SELECT * FROM project_table WHERE ownerEmail = :email ORDER BY name ASC")
    fun getProjectsForUser(email: String): LiveData<List<Project>>

    // Added a non-LiveData version for use in spinners
    @Query("SELECT * FROM project_table WHERE ownerEmail = :email ORDER BY name ASC")
    suspend fun getProjectsForUserList(email: String): List<Project>
}