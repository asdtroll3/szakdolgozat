package com.example.Taskly.ui.calendar

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import com.example.Taskly.NotificationScheduler
import com.example.Taskly.ui.projects.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.Calendar
import java.util.Date

data class ChatMessage(
    val role: String,
    val content: String
)

class CalendarViewModel(private val application: Application) : AndroidViewModel(application) {

    private val eventDao = App.database.eventDao()
    private val projectDao = App.database.projectDao()

    private val client = OkHttpClient()
    private val apiKey = ""
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val _selectedDate = MutableLiveData(LocalDate.now())
    private val _currentUserEmail = MutableLiveData<String?>(null)
    private val _projects = MutableLiveData<List<Project>>(emptyList())
    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    private val _isSendingMessage = MutableLiveData<Boolean>(false)

    val projects: LiveData<List<Project>> = _projects
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory
    val isSendingMessage: LiveData<Boolean> = _isSendingMessage

    val eventsForSelectedDate = MediatorLiveData<List<Event>>()

    init {
        eventsForSelectedDate.addSource(_currentUserEmail) {
            loadEventsForSelectedDate()
        }
        eventsForSelectedDate.addSource(_selectedDate) {
            loadEventsForSelectedDate()
        }
    }


    fun setUserEmail(email: String?) {
        if (_currentUserEmail.value != email) {
            _chatHistory.value = emptyList()
            _currentUserEmail.value = email
            if (email != null) {
                fetchUserProjects(email)
            } else {
                _projects.value = emptyList()
            }
        }
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    private fun fetchUserProjects(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _projects.postValue(projectDao.getProjectsForUserList(email))
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "fetchUserProjects", e)
                _projects.postValue(emptyList())
            }
        }
    }

    fun addNewEvent(ownerEmail: String, title: String, description: String, startTime: Date, endTime: Date, date: Date, projectId: Int?) {
        val event = Event(
            ownerEmail = ownerEmail,
            title = title,
            description = description,
            date = date,
            startTime = startTime,
            endTime = endTime,
            isCompleted = false,
            projectId = projectId
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newId = eventDao.insertEvent(event)
                val newEvent = event.copy(id = newId.toInt())
                NotificationScheduler.scheduleNotification(application.applicationContext, newEvent)
                loadEventsForSelectedDate()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "addNewEvent", e)
            }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventDao.updateEvent(event)

                //notification cuccli
                NotificationScheduler.cancelNotification(application.applicationContext, event)
                NotificationScheduler.scheduleNotification(application.applicationContext, event)
                loadEventsForSelectedDate()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "updateevent", e)
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NotificationScheduler.cancelNotification(application.applicationContext, event)
                eventDao.deleteEvent(event)
                loadEventsForSelectedDate()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "deleteevent", e)
            }
        }
    }

    fun updateEventCompletion(event: Event, isCompleted: Boolean) {
        val updatedEvent = event.copy(isCompleted = isCompleted)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventDao.updateEvent(updatedEvent)
                val currentEvents = eventsForSelectedDate.value ?: emptyList()
                val newEvents = currentEvents.map { if (it.id == event.id) updatedEvent else it }
                eventsForSelectedDate.postValue(newEvents)
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "update event comp", e)
            }
        }
    }

    private fun loadEventsForSelectedDate() {
        val email = _currentUserEmail.value
        val date = _selectedDate.value
        if (email == null || date == null) {
            eventsForSelectedDate.postValue(emptyList())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
                }
                val endOfDay = Calendar.getInstance().apply {
                    set(date.year, date.monthValue - 1, date.dayOfMonth, 23, 59, 59)
                }
                val events = eventDao.getEventsByDateRange(email, startOfDay.time, endOfDay.time)
                eventsForSelectedDate.postValue(events)
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "load event", e)
                eventsForSelectedDate.postValue(emptyList())
            }
        }
    }


    private fun addMessageToChat(role: String, content: String): List<ChatMessage> {
        val currentHistory = _chatHistory.value ?: emptyList()
        val newHistory = currentHistory + ChatMessage(role, content)
        _chatHistory.postValue(newHistory)
        return newHistory
    }

    fun sendMessage(message: String) {
        if (_isSendingMessage.value == true) return

        _isSendingMessage.postValue(true)
        val newHistory = addMessageToChat("user", message)

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { makeApiCall(newHistory) }
                response?.let { aiResponse ->
                    addMessageToChat("model", aiResponse)
                } ?: run {
                    addMessageToChat("System", "Sorry, I couldn't get a response. Please try again.")
                }
            } catch (e: Exception) {
                addMessageToChat("System", "Error: ${e.message}")
            } finally {
                _isSendingMessage.postValue(false)
            }
        }
    }

    private suspend fun makeApiCall(history: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    val contentsArray = JSONArray()
                    history.forEach { msg ->
                        val content = JSONObject().apply {
                            put("role", msg.role)
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", msg.content)
                                })
                            }
                            put("parts", partsArray)
                        }
                        contentsArray.put(content)
                    }
                    put("contents", contentsArray)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$apiUrl?key=$apiKey")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { parseGeminiResponse(it) }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("CalendarViewModel", "API Error: ${response.code} - $errorBody")
                        "API Error: ${response.code}"
                    }
                }
            } catch (e: IOException) {
                Log.e("CalendarViewModel", "network error: ${e.message}", e)
                "Network error: ${e.message}"
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "unexpected error: ${e.message}", e)
                "Unexpected error: ${e.message}"
            }
        }
    }

    private fun parseGeminiResponse(responseBody: String): String? {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    parts.getJSONObject(0).getString("text").trim()
                } else {
                    "No response text found."
                }
            } else {
                val promptFeedback = jsonResponse.optJSONObject("promptFeedback")
                if (promptFeedback != null) {
                    val safetyRatings = promptFeedback.optJSONArray("safetyRatings")
                    if (safetyRatings != null && safetyRatings.length() > 0) {
                        val firstRating = safetyRatings.getJSONObject(0)
                        "Blocked due to safety concerns: ${firstRating.getString("category")}"
                    } else {
                        "Response was empty. It might have been blocked."
                    }
                } else {
                    "No candidates found in response."
                }
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }
}