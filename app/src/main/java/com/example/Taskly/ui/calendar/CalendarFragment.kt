package com.example.Taskly.ui.calendar

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.Taskly.databinding.FragmentCalendarBinding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.Taskly.R
import com.example.Taskly.NotificationScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.*
import com.example.Taskly.App
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.fragment.app.activityViewModels
import com.example.Taskly.ui.login.LoginViewModel
import com.example.Taskly.ui.projects.Project

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private val binding get() = _binding!!
    private lateinit var eventAdapter: EventAdapter

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView
    private val chatHistory = mutableListOf<ChatMessage>()

    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    data class ChatMessage(
        val role: String,
        val content: String
    )

    private val loginViewModel: LoginViewModel by activityViewModels()
    private var userProjects: List<Project> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendarView()
        setupRecyclerView()
        setupAddEventButton()
        loadEventsForSelectedDate()
        updateEventsTitle(selectedDate)
        initializeChatViews(view)
        setupChatListeners()

        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                fetchUserProjects(user.email)
                loadEventsForSelectedDate()
            } else {
                userProjects = emptyList()
                loadEventsForSelectedDate()
            }
        }

        val currentMonth = YearMonth.now()
        val title = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}"
        binding.monthYearText.text = title

        binding.calendarView.monthScrollListener = { month ->
            val title = "${month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.yearMonth.year}"
            binding.monthYearText.text = title
        }
    }
    private fun fetchUserProjects(email: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                userProjects = App.database.projectDao().getProjectsForUserList(email)
            } catch (e: Exception) {
                Log.e("CalendarFragment", "eeeee baj?", e)
                userProjects = emptyList()
            }
        }
    }

    private fun initializeChatViews(view: View) {
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatDisplay = view.findViewById(R.id.chatDisplay)
        chatScrollView = view.findViewById(R.id.chatScrollView)
    }

    private fun setupChatListeners() {
        sendButton.setOnClickListener {
            handleSendMessage()
        }
    }

    private fun handleSendMessage() {
        val message = userInput.text.toString().trim()
        if (message.isNotEmpty()) {
            addMessageToChat("You", message)
            sendMessageToGemini(message)
            userInput.text.clear()
        }
    }

    private fun addMessageToChat(sender: String, message: String) {
        val currentText = chatDisplay.text.toString()
        chatDisplay.text = if (currentText.isEmpty()) "$sender: $message" else "$currentText\n\n$sender: $message"
        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
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


    private fun updateEventsTitle(date: LocalDate) {
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val dayOfMonth = date.dayOfMonth
        val suffix = getDayOfMonthSuffix(dayOfMonth)
        binding.eventsTitle.text = "Events for $dayOfWeek, $month ${dayOfMonth}$suffix"
    }

    private fun getDayOfMonthSuffix(n: Int): String {
        if (n in 11..13) {
            return "th"
        }
        return when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    private fun setupCalendarView() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(12)
        val endMonth = currentMonth.plusMonths(12)

        binding.calendarView.setup(startMonth, endMonth, DayOfWeek.MONDAY)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()

                when (data.position) {
                    DayPosition.MonthDate -> {
                        container.textView.visibility = View.VISIBLE
                        container.textView.alpha = 1.0f

                        if (data.date == selectedDate) {
                            container.view.setBackgroundResource(R.drawable.selected_date_background)
                        } else {
                            container.view.background = null
                        }

                        container.view.setOnClickListener {
                            val previousSelection = selectedDate
                            selectedDate = data.date
                            updateEventsTitle(selectedDate)

                            binding.calendarView.notifyCalendarChanged()

                            updateEventsForDate(selectedDate)
                        }
                    }
                    DayPosition.InDate, DayPosition.OutDate -> {
                        container.textView.visibility = View.VISIBLE
                        container.textView.alpha = 0.3f
                        container.view.background = null

                        container.view.setOnClickListener {
                            val previousSelection = selectedDate
                            selectedDate = data.date
                            updateEventsTitle(selectedDate)

                            if (data.position == DayPosition.InDate) {
                                binding.calendarView.findFirstVisibleMonth()?.let { month ->
                                    binding.calendarView.scrollToMonth(month.yearMonth.minusMonths(1))
                                }
                            } else {
                                binding.calendarView.findFirstVisibleMonth()?.let { month ->
                                    binding.calendarView.scrollToMonth(month.yearMonth.plusMonths(1))
                                }
                            }

                            updateEventsForDate(selectedDate)
                        }
                    }
                }
            }
        }
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
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
                        Log.e("CalendarFragment", "recyclerview", e)
                        Toast.makeText(
                            requireContext(),
                            "Error updating task",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onEventDeleteClick = { event ->
                showDeleteConfirmationDialog(event)
            },
            onEventEditClick = { event ->
                showAddEventDialog(event)
            }
        )
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }

    private fun setupAddEventButton() {
        binding.calendarAddEvent.setOnClickListener {
            showAddEventDialog(null)
        }
    }

    private fun showTimePickerDialog(
        context: android.content.Context,
        calendar: Calendar,
        onTimeSet: (Calendar) -> Unit
    ) {
        val timePickerDialog = TimePickerDialog(
            context,
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

    private fun showAddEventDialog(eventToEdit: Event?) {
        val isEditMode = eventToEdit != null

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null, false)
        val dialogHeader = dialogView.findViewById<TextView>(R.id.dialogHeaderTitle)
        val titleEdit = dialogView.findViewById<EditText>(R.id.eventTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.eventDescriptionEdit)
        val startTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.startTimeEdit)
        val endTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.endTimeEdit)
        val projectSpinner = dialogView.findViewById<Spinner>(R.id.projectSpinner)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val projectNames = mutableListOf("No Project")
        projectNames.addAll(userProjects.map { it.name })

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            projectNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        projectSpinner.adapter = spinnerAdapter

        val dialogTitle = if (isEditMode) "Edit Event" else "Add New Event"
        val positiveButtonText = if (isEditMode) "Save" else "Add"
        dialogHeader.text = dialogTitle

        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance()

        if (isEditMode) {
            titleEdit.setText(eventToEdit!!.title)
            descEdit.setText(eventToEdit.description)
            startCalendar.time = eventToEdit.startTime
            endCalendar.time = eventToEdit.endTime
            val projectIndex = userProjects.indexOfFirst { it.id == eventToEdit.projectId }
            projectSpinner.setSelection(if (projectIndex != -1) projectIndex + 1 else 0)
        } else {
            startCalendar.time = Date()
            startCalendar.add(Calendar.HOUR_OF_DAY, 1)
            endCalendar.time = startCalendar.time
            endCalendar.add(Calendar.HOUR_OF_DAY, 1)
            projectSpinner.setSelection(0)
        }

        startTimeEdit.setText(timeFormat.format(startCalendar.time))
        endTimeEdit.setText(timeFormat.format(endCalendar.time))

        startTimeEdit.setOnClickListener {
            showTimePickerDialog(requireContext(), startCalendar) { newCalendar ->
                startCalendar.time = newCalendar.time
                startTimeEdit.setText(timeFormat.format(startCalendar.time))
            }
        }
        endTimeEdit.setOnClickListener {
            showTimePickerDialog(requireContext(), endCalendar) { newCalendar ->
                endCalendar.time = newCalendar.time
                endTimeEdit.setText(timeFormat.format(endCalendar.time))
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(positiveButtonText, null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleEdit.text.toString().trim()
                val description = descEdit.text.toString().trim()

                val currentUser = loginViewModel.loggedInUser.value
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "You must be logged in", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

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

                val startTime = Calendar.getInstance().apply {
                    set(
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                        startCalendar.get(Calendar.HOUR_OF_DAY),
                        startCalendar.get(Calendar.MINUTE),
                        0
                    )
                }
                val endTime = Calendar.getInstance().apply {
                    set(
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                        endCalendar.get(Calendar.HOUR_OF_DAY),
                        endCalendar.get(Calendar.MINUTE),
                        0
                    )
                }

                val selectedDateCalendar = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                }

                if (endTime.after(startTime)) {

                    if (isEditMode) {
                        val updatedEvent = eventToEdit!!.copy(
                            title = title,
                            description = description,
                            date = selectedDateCalendar.time,
                            startTime = startTime.time,
                            endTime = endTime.time,
                            projectId = selectedProjectId
                        )
                        updateEvent(updatedEvent)
                    } else {
                        addNewEvent(
                            currentUser.email,
                            title,
                            description,
                            startTime.time,
                            endTime.time,
                            selectedDateCalendar.time,
                            selectedProjectId
                        )
                    }
                    dialog.dismiss()
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

    private fun addNewEvent(ownerEmail: String, title: String, description: String, startTime: Date, endTime: Date, date: Date, projectId: Int?) {
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

        lifecycleScope.launch {
            try {
                val newId = App.database.eventDao().insertEvent(event)
                val newEvent = event.copy(id = newId.toInt())

                NotificationScheduler.scheduleNotification(requireContext(), newEvent)

                loadEventsForSelectedDate()
                Toast.makeText(
                    requireContext(),
                    "Event added successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("CalendarFragment", "addnewevent", e)
                Toast.makeText(
                    requireContext(),
                    "Error adding event",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                loadEventsForSelectedDate()
            } catch (e: Exception) {
                Log.e("CalendarFragment", "ilyen nem kene tortenjen", e)
                Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun loadEventsForSelectedDate() {
        updateEventsForDate(selectedDate)
    }
    private fun updateEvent(event: Event) {
        lifecycleScope.launch {
            try {
                App.database.eventDao().updateEvent(event)

                //notifikacio cuccli
                NotificationScheduler.cancelNotification(requireContext(), event)
                NotificationScheduler.scheduleNotification(requireContext(), event)

                loadEventsForSelectedDate()
                Toast.makeText(
                    requireContext(),
                    "Event updated successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("CalendarFragment", "updateEvent", e)
                Toast.makeText(
                    requireContext(),
                    "Error updating event",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateEventsForDate(selectedDate: LocalDate) {
        val currentUser = loginViewModel.loggedInUser.value
        if (currentUser == null) {
            eventAdapter.submitList(emptyList())
            binding.noEventsText.visibility = View.VISIBLE
            binding.eventsRecyclerView.visibility = View.GONE
            binding.aiChatContainer.visibility = View.VISIBLE
            binding.eventsTitle.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, 0, 0, 0)
                }

                val endOfDay = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, 23, 59, 59)
                }

                val events = App.database.eventDao().getEventsByDateRange(
                    currentUser.email,
                    startOfDay.time,
                    endOfDay.time
                )
                if (events.isEmpty()) {
                    binding.eventsRecyclerView.visibility = View.GONE
                    binding.aiChatContainer.visibility = View.VISIBLE
                    binding.eventsTitle.visibility = View.GONE
                    binding.noEventsText.visibility = View.VISIBLE
                } else {
                    binding.eventsRecyclerView.visibility = View.VISIBLE
                    binding.aiChatContainer.visibility = View.GONE
                    binding.eventsTitle.visibility = View.VISIBLE
                    binding.noEventsText.visibility = View.GONE
                    eventAdapter.submitList(events)
                }
                Log.d("UpdateEventsForDate", "betöltött ${events.size} event itt: $selectedDate")
            } catch (e: Exception) {
                Log.e("CalendarFragment", "eeeee baj", e)
                Toast.makeText(
                    requireContext(),
                    "Error loading events",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}