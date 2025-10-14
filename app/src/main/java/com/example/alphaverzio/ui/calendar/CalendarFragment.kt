package com.example.alphaverzio.ui.calendar

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alphaverzio.databinding.FragmentCalendarBinding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.alphaverzio.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.*
import com.example.alphaverzio.App
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.concurrent.TimeUnit

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private val binding get() = _binding!!
    private lateinit var eventAdapter: EventAdapter
    private val eventsByDate: MutableMap<String, MutableList<Event>> = mutableMapOf()

    // Store selected start and end times
    private var startCalendar: Calendar = Calendar.getInstance()
    private var endCalendar: Calendar = Calendar.getInstance()

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


        val currentMonth = YearMonth.now()
        val title = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}"
        binding.monthYearText.text = title

        binding.calendarView.monthScrollListener = { month ->
            val title = "${month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.yearMonth.year}"
            binding.monthYearText.text = title
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

                // Handle different day positions (current month, previous/next month)
                when (data.position) {
                    DayPosition.MonthDate -> {
                        container.textView.visibility = View.VISIBLE
                        container.textView.alpha = 1.0f

                        // Highlight selected date
                        if (data.date == selectedDate) {
                            container.view.setBackgroundResource(R.drawable.selected_date_background)
                        } else {
                            container.view.background = null
                        }

                        container.view.setOnClickListener {
                            val previousSelection = selectedDate
                            selectedDate = data.date
                            updateEventsTitle(selectedDate)


                            // Refresh the calendar to update selection
                            binding.calendarView.notifyCalendarChanged()

                            updateEventsForDate(selectedDate)
                        }
                    }
                    DayPosition.InDate, DayPosition.OutDate -> {
                        // Handle dates from previous/next months
                        container.textView.visibility = View.VISIBLE
                        container.textView.alpha = 0.3f
                        container.view.background = null

                        container.view.setOnClickListener {
                            val previousSelection = selectedDate
                            selectedDate = data.date
                            updateEventsTitle(selectedDate)

                            // Navigate to the month of the selected date
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
        eventAdapter = EventAdapter { event, isChecked ->
            event.isCompleted = isChecked

            // Update the event in database
            lifecycleScope.launch {
                try {
                    App.database.eventDao().updateEvent(event)
                    Toast.makeText(
                        requireContext(),
                        if (isChecked) "Task marked as completed!" else "Task marked as incomplete!",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e("CalendarFragment", "Error updating event", e)
                    Toast.makeText(
                        requireContext(),
                        "Error updating task",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }

    private fun setupAddEventButton() {
        binding.calendarAddEvent.setOnClickListener {
            showAddEventDialog()
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
            true // 24-hour format
        )
        timePickerDialog.show()
    }

    private fun showAddEventDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null, false)
        val titleEdit = dialogView.findViewById<EditText>(R.id.eventTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.eventDescriptionEdit)
        val startTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.startTimeEdit)
        val endTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.endTimeEdit)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Set initial times
        startCalendar.time = Date()
        endCalendar.time = Date()
        endCalendar.add(Calendar.HOUR_OF_DAY, 1) // Default to one hour later

        startTimeEdit.setText(timeFormat.format(startCalendar.time))
        endTimeEdit.setText(timeFormat.format(endCalendar.time))

        startTimeEdit.setOnClickListener {
            showTimePickerDialog(requireContext(), startCalendar) { newCalendar ->
                startCalendar = newCalendar
                startTimeEdit.setText(timeFormat.format(startCalendar.time))
            }
        }

        endTimeEdit.setOnClickListener {
            showTimePickerDialog(requireContext(), endCalendar) { newCalendar ->
                endCalendar = newCalendar
                endTimeEdit.setText(timeFormat.format(endCalendar.time))
            }
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Add", null) // Will be overridden
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            addButton.setOnClickListener {
                val title = titleEdit.text.toString().trim()
                val description = descEdit.text.toString().trim()

                if (title.isEmpty()) {
                    titleEdit.error = "Title cannot be empty"
                    return@setOnClickListener
                }
                // Convert LocalDate to Calendar for database operations
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                }

                // Set start time using the currently selected date
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

                // Set end time using the currently selected date
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

                // Ensure end time is after start time
                if (endTime.after(startTime)) {
                    addNewEvent(
                        title,
                        description,
                        startTime.time,
                        endTime.time,
                        selectedCalendar.time
                    )
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "End time must be after start time",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        dialog.show()
    }

    private fun addNewEvent(title: String, description: String, startTime: Date, endTime: Date, date: Date) {
        val event = Event(
            title = title,
            description = description,
            date = date,
            startTime = startTime,
            endTime = endTime,
            isCompleted = false
        )

        lifecycleScope.launch {
            try {
                App.database.eventDao().insertEvent(event)
                loadEventsForSelectedDate()
                Toast.makeText(
                    requireContext(),
                    "Event added successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("CalendarFragment", "Error adding event", e)
                Toast.makeText(
                    requireContext(),
                    "Error adding event",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadEventsForSelectedDate() {
        updateEventsForDate(selectedDate)
    }

    private fun formatDate(date: LocalDate): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance().apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth)
        }
        return formatter.format(calendar.time)
    }

    private fun updateEventsForDate(selectedDate: LocalDate) {
        lifecycleScope.launch {
            try {
                // Convert LocalDate to Calendar and set to start of the day
                val startOfDay = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, 0, 0, 0)
                }

                // Set to end of the day
                val endOfDay = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, 23, 59, 59)
                }

                // Use the new query to get events within the date range
                val events = App.database.eventDao().getEventsByDateRange(startOfDay.time, endOfDay.time)

                eventAdapter.submitList(events)
                Log.d("UpdateEventsForDate", "Loaded ${events.size} events from DB for date: $selectedDate")
            } catch (e: Exception) {
                Log.e("CalendarFragment", "Error loading events", e)
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