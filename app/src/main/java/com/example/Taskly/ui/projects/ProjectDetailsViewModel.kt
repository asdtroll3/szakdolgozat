package com.example.Taskly.ui.projects

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.login.User
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiEventSuggestion(
    val title: String,
    val description: String,
    val date: String,
    val startTime: String,
    val endTime: String
)

class ProjectDetailsViewModel : ViewModel() {

    private val eventDao = App.database.eventDao()

    // Holds the current project ID and user for fetching
    private val _requestData = MutableLiveData<Pair<Int, User>>()

    // LiveData for the events, automatically updated when _requestData changes
    val projectEvents: LiveData<List<Event>> = _requestData.switchMap { (projectId, user) ->
        eventDao.getEventsForProject(projectId, user.email)
    }
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val _isLoadingAi = MutableLiveData<Boolean>()
    val isLoadingAi: LiveData<Boolean> = _isLoadingAi

    private val _aiEventSuggestion = MutableLiveData<AiEventSuggestion?>()
    val aiEventSuggestion: LiveData<AiEventSuggestion?> = _aiEventSuggestion

    private var aiSuggestionTriggered = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Function to trigger loading events for a specific project and user
    fun loadEvents(projectId: Int, user: User) {
        _requestData.value = Pair(projectId, user)
    }
    fun clearAiSuggestion() {
        _aiEventSuggestion.value = null // Use setValue as this should be called on main thread
    }
    fun triggerAiSuggestion(events: List<Event>) {
        if (aiSuggestionTriggered || events.size < 3) return // Don't run if already triggered or not enough events
        aiSuggestionTriggered = true
        _isLoadingAi.postValue(true)

        // Format the prompt
        val eventListString = events.joinToString("\n") {
            "- Title: ${it.title}, Date: ${dateFormat.format(it.date)}, Start: ${timeFormat.format(it.startTime)}, Description: ${it.description.take(50)}..."
        }

        val prompt = """
        Here is a list of existing events in a project:
        $eventListString

        Based on these events, suggest one new, complementary event (not a copy) that follows a similar theme or logical progression.
        The event must be in the future. Assume today is ${dateFormat.format(Date())}.
        Provide the new event's title, description, a suitable future date (YYYY-MM-DD), startTime (HH:mm), and endTime (HH:mm) in a single, minified JSON object like this:
        {"title": "...", "description": "...", "date": "...", "startTime": "...", "endTime": "..."}
        Ensure the output contains ONLY the JSON object, with no extra text or markdown formatting.
        """.trimIndent()


        viewModelScope.launch(Dispatchers.IO) {
            val result = makeGeminiApiCall(prompt)
            if (result != null) {
                try {
                    var jsonString = result.trim()
                    val firstBrace = jsonString.indexOf('{')
                    val lastBrace = jsonString.lastIndexOf('}')

                    if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                        // Extract the substring between the braces
                        jsonString = jsonString.substring(firstBrace, lastBrace + 1)
                        Log.d("ProjectDetailsVM", "Cleaned JSON String: $jsonString") // Log cleaned string
                    } else {
                        // If braces aren't found correctly, log an error
                        Log.e("ProjectDetailsVM", "Could not find valid JSON object braces in raw response: $result")
                        // Attempt parsing the original string anyway, it might be correct already or will fail predictably
                        jsonString = result
                    }
                    // *************************************

                    // Parse the potentially cleaned jsonString
                    val json = JSONObject(jsonString)
                    val suggestion = AiEventSuggestion(
                        title = json.getString("title"),
                        description = json.getString("description"),
                        date = json.getString("date"),
                        startTime = json.getString("startTime"),
                        endTime = json.getString("endTime")
                    )
                    _aiEventSuggestion.postValue(suggestion)
                } catch (e: Exception) {
                    Log.e("ProjectDetailsVM", "Error parsing AI response: $result", e)
                    _aiEventSuggestion.postValue(null) // Failed parsing
                }
            } else {
                _aiEventSuggestion.postValue(null) // Failed API call
            }
            _isLoadingAi.postValue(false)
        }
    }
    private suspend fun makeGeminiApiCall(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    }
                    put("contents", contentsArray)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("$apiUrl?key=$apiKey").post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { parseGeminiResponse(it) }
                    } else {
                        Log.e("ProjectDetailsVM", "API Error: ${response.code} - ${response.body?.string()}")
                        null
                    }
                }
            } catch (e: IOException) {
                Log.e("ProjectDetailsVM", "Network error: ${e.message}", e)
                null
            } catch (e: Exception) {
                Log.e("ProjectDetailsVM", "Error: ${e.message}", e)
                null
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
                    parts.getJSONObject(0).getString("text").trim() // This should be the JSON string
                } else { null }
            } else { null }
        } catch (e: Exception) {
            Log.e("ProjectDetailsVM", "Error parsing wrapper: ${e.message}", e)
            null
        }
    }
}