package com.example.Taskly.ui.today

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import com.example.Taskly.NotificationScheduler
import com.example.Taskly.ui.calendar.Event
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
import java.util.Calendar

data class ChatMessage(
    val role: String,
    val content: String
)

class TodayViewModel(private val application: Application) : AndroidViewModel(application) {

    private val eventDao = App.database.eventDao()
    private val projectDao = App.database.projectDao()

    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val _currentUserEmail = MutableLiveData<String?>(null)
    private val _projects = MutableLiveData<List<Project>>(emptyList())
    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    private val _isSendingMessage = MutableLiveData<Boolean>(false)

    private val _todayEvents = MediatorLiveData<List<Event>>()

    val todayEvents: LiveData<List<Event>> = _todayEvents
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory
    val projects: LiveData<List<Project>> = _projects
    val isSendingMessage: LiveData<Boolean> = _isSendingMessage

    init {
        _todayEvents.addSource(_currentUserEmail) {
            loadTodayEvents()
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
                _todayEvents.value = emptyList()
            }
        }
    }

    private fun fetchUserProjects(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _projects.postValue(projectDao.getProjectsForUserList(email))
            } catch (e: Exception) {
                Log.e("TodayViewModel", "fetch proj", e)
                _projects.postValue(emptyList())
            }
        }
    }

    fun loadTodayEvents() {
        val email = _currentUserEmail.value
        if (email == null) {
            _todayEvents.postValue(emptyList())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val endOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                val events = eventDao.getEventsByDateRange(email, startOfDay.time, endOfDay.time)
                _todayEvents.postValue(events)
            } catch (e: Exception) {
                Log.e("TodayViewModel", "load events", e)
                _todayEvents.postValue(emptyList())
            }
        }
    }

    fun updateEventCompletion(event: Event, isCompleted: Boolean) {
        val updatedEvent = event.copy(isCompleted = isCompleted)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventDao.updateEvent(updatedEvent)
                val currentEvents = _todayEvents.value ?: emptyList()
                val newEvents = currentEvents.map { if (it.id == event.id) updatedEvent else it }
                _todayEvents.postValue(newEvents)
            } catch (e: Exception) {
                Log.e("TodayViewModel", "update event compl", e)
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NotificationScheduler.cancelNotification(application.applicationContext, event)
                eventDao.deleteEvent(event)
                loadTodayEvents()
            } catch (e: Exception) {
                Log.e("TodayViewModel", "del event", e)
            }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventDao.updateEvent(event)
                NotificationScheduler.cancelNotification(application.applicationContext, event)
                NotificationScheduler.scheduleNotification(application.applicationContext, event)
                loadTodayEvents()
            } catch (e: Exception) {
                Log.e("TodayViewModel", "update event", e)
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
                        Log.e("TodayViewModel", "API Error: ${response.code} - $errorBody")
                        "API Error: ${response.code}"
                    }
                }
            } catch (e: IOException) {
                Log.e("TodayViewModel", "network error: ${e.message}", e)
                "Network error: ${e.message}"
            } catch (e: Exception) {
                Log.e("TodayViewModel", "unexpected error: ${e.message}", e)
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