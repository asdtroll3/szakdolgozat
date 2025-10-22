package com.example.Taskly.data
import androidx.room.*
import com.example.Taskly.ui.calendar.Event
import java.util.Date

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Query("SELECT * FROM Event WHERE ownerEmail = :ownerEmail AND date BETWEEN :startDate AND :endDate ORDER BY startTime ASC")
    suspend fun getEventsByDateRange(ownerEmail: String, startDate: Date, endDate: Date): List<Event>
}