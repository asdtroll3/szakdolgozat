package com.example.Taskly.ui.today

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Spinner
import java.text.SimpleDateFormat
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
import com.example.Taskly.ui.projects.Project
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class TodayFragment : Fragment() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView

    private lateinit var todayEventsRecyclerView: RecyclerView
    private lateinit var noEventsTextView: TextView
    private lateinit var eventAdapter: EventAdapter

    private val chatHistory = mutableListOf<ChatMessage>()
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val loginViewModel: LoginViewModel by activityViewModels()
    private var userProjects: List<Project> = emptyList()

    data class ChatMessage(
        val role: String,
        val content: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        setupRecyclerView()

        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                fetchUserProjects(user.email)
            } else {
                userProjects = emptyList()
            }
        }
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }
    private fun fetchUserProjects(email: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                userProjects = App.database.projectDao().getProjectsForUserList(email)
            } catch (e: Exception) {
                Log.e("TodayFragment", "projekt", e)
                userProjects = emptyList()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTodayEvents()
    }

    private fun initializeViews(view: View) {
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatDisplay = view.findViewById(R.id.text_today)
        chatScrollView = view.findViewById(R.id.chatScrollView)

        todayEventsRecyclerView = view.findViewById(R.id.todayEventsRecyclerView)
        noEventsTextView = view.findViewById(R.id.noEventsTextView)
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            showDate = false,
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
                        //Log.e("TodayFragment", "", e)
                        Toast.makeText(requireContext(), "Error updating task", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEventDeleteClick = { event ->
                showDeleteConfirmationDialog(event)
            },
            onEventEditClick = { event ->
                showEditEventDialog(event)
            },
            onEventHelpClick = { event ->
                val helpPrompt = "Help me with this task: \nTitle: ${event.title}\nDescription: ${event.description}"
                userInput.setText(helpPrompt)
                handleSendMessage()
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
                loadTodayEvents()
            } catch (e: Exception) {
                Log.e("TodayFragment", "deleteEvent", e)
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

                val events = App.database.eventDao().getEventsByDateRange(
                    currentUser.email,
                    startOfDay.time,
                    endOfDay.time
                )


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
                Log.e("TodayFragment", "loadTodayEvents", e)
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
    private fun hideKeyboard() {
        val view = activity?.currentFocus
        if (view != null) {
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
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

        addMessageToChat("You", message)
        userInput.text.clear()

        sendButton.isEnabled = false
        sendButton.text = getString(R.string.sending)


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

        scrollToBottom()
    }

    private fun sendMessageToGemini(message: String) {
        lifecycleScope.launch {
            try {
                chatHistory.add(ChatMessage("user", message))

                val response = withContext(Dispatchers.IO) {
                    makeApiCall()
                }

                response?.let { aiResponse ->
                    chatHistory.add(ChatMessage("model", aiResponse))
                    addMessageToChat("Gemini", aiResponse)
                } ?: run {
                    addMessageToChat("System", "Sorry, I couldn't get a response. Please try again.")
                }

            } catch (e: Exception) {
                addMessageToChat("System", "Error: ${e.message}")
            } finally {
                sendButton.isEnabled = true
                sendButton.text = getString(R.string.send)
            }
        }
    }

    private suspend fun makeApiCall(): String? {
        return withContext(Dispatchers.IO) {
            try {
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

                client.newCall(request).execute().use { response ->
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
    private fun showEditEventDialog(eventToEdit: Event) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null, false)
        val dialogHeader = dialogView.findViewById<TextView>(R.id.dialogHeaderTitle)
        val titleEdit = dialogView.findViewById<EditText>(R.id.eventTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.eventDescriptionEdit)
        val startTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.startTimeEdit)
        val endTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.endTimeEdit)
        val projectSpinner = dialogView.findViewById<Spinner>(R.id.projectSpinner)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        dialogHeader.text = "Edit Event"

        val projectNames = mutableListOf("No Project")
        projectNames.addAll(userProjects.map { it.name })
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            projectNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        projectSpinner.adapter = spinnerAdapter

        val startCalendar = Calendar.getInstance().apply { time = eventToEdit.startTime }
        val endCalendar = Calendar.getInstance().apply { time = eventToEdit.endTime }


        titleEdit.setText(eventToEdit.title)
        descEdit.setText(eventToEdit.description)
        startTimeEdit.setText(timeFormat.format(startCalendar.time))
        endTimeEdit.setText(timeFormat.format(endCalendar.time))

        val projectIndex = userProjects.indexOfFirst { it.id == eventToEdit.projectId }
        projectSpinner.setSelection(if (projectIndex != -1) projectIndex + 1 else 0)


        startTimeEdit.setOnClickListener {
            showTimePickerDialog(startCalendar) { newCalendar ->
                startCalendar.time = newCalendar.time
                startTimeEdit.setText(timeFormat.format(startCalendar.time))
            }
        }
        endTimeEdit.setOnClickListener {
            showTimePickerDialog(endCalendar) { newCalendar ->
                endCalendar.time = newCalendar.time
                endTimeEdit.setText(timeFormat.format(endCalendar.time))
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleEdit.text.toString().trim()
                val description = descEdit.text.toString().trim()

                if (title.isEmpty()) {
                    titleEdit.error = "Title cannot be empty"
                    return@setOnClickListener
                }
                val selectedSpinnerPosition = projectSpinner.selectedItemPosition
                val selectedProjectId: Int? = if (selectedSpinnerPosition == 0) {
                    null
                } else {
                    userProjects[selectedSpinnerPosition - 1].id
                }


                if (endCalendar.after(startCalendar)) {
                    val updatedEvent = eventToEdit.copy(
                        title = title,
                        description = description,
                        startTime = startCalendar.time,
                        endTime = endCalendar.time,
                        projectId = selectedProjectId
                    )

                    lifecycleScope.launch {
                        try {
                            App.database.eventDao().updateEvent(updatedEvent)

                            NotificationScheduler.cancelNotification(requireContext(), updatedEvent)
                            NotificationScheduler.scheduleNotification(requireContext(), updatedEvent)

                            Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show()
                            loadTodayEvents()
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Log.e("TodayFragment", "showEditEventDialog", e)
                            Toast.makeText(requireContext(), "Error updating event", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "End time must be after start time",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            val cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            cancelButton.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
        dialog.show()
    }

    private fun showTimePickerDialog(
        calendar: Calendar,
        onTimeSet: (Calendar) -> Unit
    ) {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val newCalendar = Calendar.getInstance().apply {
                    time = calendar.time
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                onTimeSet(newCalendar)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}