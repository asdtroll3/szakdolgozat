package com.example.alphaverzio.data
import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.alphaverzio.ui.calendar.Event
import java.util.Date

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event)

    @Update
    suspend fun updateEvent(event: Event)

    @Query("SELECT * FROM Event WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getEventsByDateRange(startDate: Date, endDate: Date): List<Event>
}