package com.example.Taskly.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class ProjectWithCount(val project: Project, val eventCount: Int)
class HomeViewModel : ViewModel() {

    private val projectDao = App.database.projectDao()
    private val eventDao = App.database.eventDao()

    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val sharedPreferences = App.sharedPreferences
    companion object {
        const val PREF_QUOTE = "daily_quote"
        const val PREF_QUOTE_DATE = "quote_date"
    }

    private val _text = MutableLiveData<String>().apply {
        value = "Home"
    }
    val text: LiveData<String> = _text

    // LiveData for projects
    private val _projects = MutableLiveData<List<ProjectWithCount>>(emptyList())
    val projects: LiveData<List<ProjectWithCount>> = _projects

    private val _quote = MutableLiveData<String>()
    val quote: LiveData<String> = _quote

    // LiveData to hold projects for a specific user
    private var userProjectsLiveData: LiveData<List<Project>>? = null

    init {
        checkAndFetchQuote()
    }

    fun loadProjectsForUser(email: String) {
        // Remove any existing observer
        userProjectsLiveData?.removeObserver(projectObserver)

        // Get new LiveData from DAO
        val liveData = projectDao.getProjectsForUser(email)

        // Observe it
        liveData.observeForever(projectObserver)
        userProjectsLiveData = liveData
    }

    private val projectObserver = Observer<List<Project>> { projects ->
        // Got the list of projects, now get the count for each
        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher for DB calls
            val projectsWithCount = projects.map { project ->
                // Call the new DAO function
                val count = eventDao.getEventCountForProject(project.id, project.ownerEmail)
                ProjectWithCount(project, count)
            }
            _projects.postValue(projectsWithCount)
        }
    }

    fun clearProjects() {
        userProjectsLiveData?.removeObserver(projectObserver)
        _projects.postValue(emptyList())
    }

    fun addProject(projectName: String, iconName: String, ownerEmail: String) {
        if (projectName.isBlank() || ownerEmail.isBlank()) {
            // Handle error (e.g., with a LiveData event)
            return
        }

        viewModelScope.launch {
            val newProject = Project(
                ownerEmail = ownerEmail,
                name = projectName,
                iconName = iconName
            )
            projectDao.insert(newProject)
            // The LiveData will update automatically
        }
    }
    fun fetchQuoteOfTheDay() {
        _quote.postValue("Loading quote...") // Show loading state
        viewModelScope.launch(Dispatchers.IO) {
            val prompt = "Give me a short, inspirational or insightful quote for the day (max 2 sentences)."
            val result = makeGeminiApiCall(prompt)
            val quoteToDisplay = result ?: "Could not fetch quote."
            _quote.postValue(quoteToDisplay)

            // If fetch was successful (or even if not, to prevent constant retries on error),
            // store the quote and today's date
            if (result != null) {
                val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                sharedPreferences.edit()
                    .putString(PREF_QUOTE, quoteToDisplay)
                    .putString(PREF_QUOTE_DATE, todayDateString)
                    .apply()
            }
        }
    }
    private fun checkAndFetchQuote() {
        val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastFetchDate = sharedPreferences.getString(PREF_QUOTE_DATE, null)
        val storedQuote = sharedPreferences.getString(PREF_QUOTE, null)

        if (lastFetchDate == todayDateString && !storedQuote.isNullOrBlank()) {
            _quote.postValue(storedQuote!!)
        } else {
            fetchQuoteOfTheDay()
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
                        "API Error: ${response.code}"
                    }
                }
            } catch (e: IOException) {
                "Network error: ${e.message}"
            } catch (e: Exception) {
                "Error: ${e.message}"
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
                } else { "No text found." }
            } else {
                jsonResponse.optJSONObject("promptFeedback")?.optString("blockReason", "Blocked?") ?: "No candidates found."
            }
        } catch (e: Exception) {
            "Error parsing: ${e.message}"
        }
    }

    override fun onCleared() {
        // Clean up the observer when ViewModel is cleared
        userProjectsLiveData?.removeObserver(projectObserver)
        super.onCleared()
    }
}