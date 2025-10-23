package com.example.Taskly.ui.today

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.App
import com.example.Taskly.R
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.calendar.EventAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import com.example.Taskly.NotificationScheduler
import androidx.fragment.app.activityViewModels
import com.example.Taskly.ui.login.LoginViewModel

class TodayFragment : Fragment() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView

    // Views for today's events
    private lateinit var todayEventsRecyclerView: RecyclerView
    private lateinit var noEventsTextView: TextView
    private lateinit var eventAdapter: EventAdapter

    private val chatHistory = mutableListOf<ChatMessage>()
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val loginViewModel: LoginViewModel by activityViewModels()

    data class ChatMessage(
        val role: String, // "user" or "model"
        val content: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        // Load events every time the fragment is shown to keep the list up-to-date
        loadTodayEvents()
    }

    private fun initializeViews(view: View) {
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatDisplay = view.findViewById(R.id.text_today)
        chatScrollView = view.findViewById(R.id.chatScrollView)

        // Initialize new views
        todayEventsRecyclerView = view.findViewById(R.id.todayEventsRecyclerView)
        noEventsTextView = view.findViewById(R.id.noEventsTextView)
    }

    private fun setupRecyclerView() {
        // Reuse the existing EventAdapter
        eventAdapter = EventAdapter(
            onEventCheckedChange = { event, isChecked ->
                event.isCompleted = isChecked
                lifecycleScope.launch {
                    try {
                        App.database.eventDao().updateEvent(event)
                        Toast.makeText(
                            requireContext(),
                            if (isChecked) "Task marked as completed!" else "Task marked as incomplete!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e("TodayFragment", "Error updating event", e)
                        Toast.makeText(requireContext(), "Error updating task", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEventDeleteClick = { event ->
                showDeleteConfirmationDialog(event)
            }
        )
        todayEventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }

    private fun showDeleteConfirmationDialog(event: Event) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogTheme)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { dialog, _ ->
                deleteEvent(event)
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteEvent(event: Event) {
        lifecycleScope.launch {
            try {
                NotificationScheduler.cancelNotification(requireContext(), event)
                App.database.eventDao().deleteEvent(event)
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                loadTodayEvents() // Refresh the list
            } catch (e: Exception) {
                Log.e("TodayFragment", "Error deleting event", e)
                Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTodayEvents() {
        val currentUser = loginViewModel.loggedInUser.value
        if (currentUser == null) {
            noEventsTextView.visibility = View.VISIBLE
            todayEventsRecyclerView.visibility = View.GONE
            eventAdapter.submitList(emptyList())
            return
        }
        lifecycleScope.launch {
            try {
                // Get the start and end of the current day
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

                // Query the database for events within today's range
                val events = App.database.eventDao().getEventsByDateRange(
                    currentUser.email,
                    startOfDay.time,
                    endOfDay.time
                )

                // Update the UI on the main thread
                withContext(Dispatchers.Main) {
                    if (events.isEmpty()) {
                        noEventsTextView.visibility = View.VISIBLE
                        todayEventsRecyclerView.visibility = View.GONE
                    } else {
                        noEventsTextView.visibility = View.GONE
                        todayEventsRecyclerView.visibility = View.VISIBLE
                        eventAdapter.submitList(events)
                    }
                }
            } catch (e: Exception) {
                Log.e("TodayFragment", "Error loading today's events", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading events", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            handleSendMessage()
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                handleSendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun scrollToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun handleSendMessage() {
        val message = userInput.text.toString().trim()

        if (message.isEmpty()) {
            Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Add user message to history and display
        addMessageToChat("You", message)
        userInput.text.clear()

        // Disable send button while processing
        sendButton.isEnabled = false
        sendButton.text = getString(R.string.sending)

        // Send message to Gemini
        sendMessageToGemini(message)
    }

    private fun addMessageToChat(sender: String, message: String) {
        val currentText = chatDisplay.text.toString()
        val newText = if (currentText.isEmpty()) {
            "$sender: $message"
        } else {
            "$currentText\n\n$sender: $message"
        }
        chatDisplay.text = newText

        // Scroll to bottom
        scrollToBottom()
    }

    private fun sendMessageToGemini(message: String) {
        lifecycleScope.launch {
            try {
                // Add user message to chat history for context
                chatHistory.add(ChatMessage("user", message))

                val response = withContext(Dispatchers.IO) {
                    makeApiCall()
                }

                response?.let { aiResponse ->
                    // Add AI response to history and display
                    chatHistory.add(ChatMessage("model", aiResponse))
                    addMessageToChat("Gemini", aiResponse)
                } ?: run {
                    addMessageToChat("System", "Sorry, I couldn't get a response. Please try again.")
                }

            } catch (e: Exception) {
                addMessageToChat("System", "Error: ${e.message}")
            } finally {
                // Re-enable send button
                sendButton.isEnabled = true
                sendButton.text = getString(R.string.send)
            }
        }
    }

    private suspend fun makeApiCall(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Build the request body from the entire chat history
                val json = JSONObject().apply {
                    val contentsArray = JSONArray()
                    chatHistory.forEach { msg ->
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

                client.newCall(request).execute().use { response -> // Use .use for automatic resource closing
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let { parseGeminiResponse(it) }
                    } else {
                        "API Error: ${response.code} - ${response.message}"
                    }
                }

            } catch (e: IOException) {
                "Network error: ${e.message}"
            } catch (e: Exception) {
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
                // Check for safety ratings or other reasons for no candidates
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up resources if needed
    }
}