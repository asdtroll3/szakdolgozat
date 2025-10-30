package com.example.Taskly.ui.calendar

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.Taskly.R
import com.example.Taskly.databinding.FragmentCalendarBinding
import com.example.Taskly.ui.login.LoginViewModel
import com.example.Taskly.ui.projects.Project
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventAdapter: EventAdapter
    private var selectedDate: LocalDate = LocalDate.now()

    private val viewModel: CalendarViewModel by viewModels()
    private val loginViewModel: LoginViewModel by activityViewModels()

    private var userProjects: List<Project> = emptyList()

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeChatViews(view)
        setupCalendarView()
        setupRecyclerView()
        setupAddEventButton()
        setupChatListeners()

        updateEventsTitle(selectedDate)
        setupObservers()

        val currentMonth = YearMonth.now()
        val title = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}"
        binding.monthYearText.text = title

        binding.calendarView.monthScrollListener = { month ->
            val title = "${month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.yearMonth.year}"
            binding.monthYearText.text = title
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun setupObservers() {
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            viewModel.setUserEmail(user?.email)
        }

        viewModel.eventsForSelectedDate.observe(viewLifecycleOwner) { events ->
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
        }

        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            userProjects = projects
        }

        viewModel.chatHistory.observe(viewLifecycleOwner) { messages ->
            val userName = loginViewModel.loggedInUser.value?.username ?: "You"

            val chatText = messages.joinToString("\n\n") {
                val prefix = if (it.role == "model") "Gemini" else userName
                "$prefix: ${it.content}"
            }
            chatDisplay.text = chatText
            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
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
            viewModel.sendMessage(message)
            userInput.text.clear()
            hideKeyboard()
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
        if (n in 11..13) return "th"
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
                            if (selectedDate != data.date) {
                                selectedDate = data.date
                                updateEventsTitle(selectedDate)
                                binding.calendarView.notifyCalendarChanged()
                                viewModel.setSelectedDate(selectedDate)
                            }
                        }
                    }
                    DayPosition.InDate, DayPosition.OutDate -> {
                        container.textView.visibility = View.VISIBLE
                        container.textView.alpha = 0.3f
                        container.view.background = null

                        container.view.setOnClickListener {
                            selectedDate = data.date
                            updateEventsTitle(selectedDate)
                            viewModel.setSelectedDate(selectedDate)

                            if (data.position == DayPosition.InDate) {
                                binding.calendarView.findFirstVisibleMonth()?.let {
                                    binding.calendarView.scrollToMonth(it.yearMonth.minusMonths(1))
                                }
                            } else {
                                binding.calendarView.findFirstVisibleMonth()?.let {
                                    binding.calendarView.scrollToMonth(it.yearMonth.plusMonths(1))
                                }
                            }
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
                viewModel.updateEventCompletion(event, isChecked)
                Toast.makeText(
                    requireContext(),
                    if (isChecked) "Task marked as completed!" else "Task marked as incomplete!",
                    Toast.LENGTH_SHORT
                ).show()
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
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, startCalendar.get(Calendar.HOUR_OF_DAY), startCalendar.get(Calendar.MINUTE), 0)
                }
                val endTime = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, endCalendar.get(Calendar.HOUR_OF_DAY), endCalendar.get(Calendar.MINUTE), 0)
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
                        viewModel.updateEvent(updatedEvent)
                        Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addNewEvent(
                            currentUser.email,
                            title,
                            description,
                            startTime.time,
                            endTime.time,
                            selectedDateCalendar.time,
                            selectedProjectId
                        )
                        Toast.makeText(requireContext(), "Event added!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show()
                }
            }
            val cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            cancelButton.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(event: Event) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogTheme)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteEvent(event)
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
    private fun hideKeyboard() {
        val view = activity?.currentFocus
        if (view != null) {
            val imm =
                activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}