package com.example.alphaverzio.ui.calendar

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
import android.widget.TimePicker
import android.widget.Toast
import com.example.alphaverzio.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.*
import com.example.alphaverzio.App
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private val binding get() = _binding!!
    private lateinit var eventAdapter: EventAdapter
    private val eventsByDate: MutableMap<String, MutableList<Event>> = mutableMapOf()

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

    private fun showAddEventDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null, false)
        val titleEdit = dialogView.findViewById<EditText>(R.id.eventTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.eventDescriptionEdit)
        val startTimePicker = dialogView.findViewById<TimePicker>(R.id.startTimePicker)
        val endTimePicker = dialogView.findViewById<TimePicker>(R.id.endTimePicker)

        // Ensure TimePickers are in 24-hour format
        startTimePicker.setIs24HourView(true)
        endTimePicker.setIs24HourView(true)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Event")
            .setView(dialogView)
            .setPositiveButton("Add", null) // Temporarily set the listener to null
            .setNegativeButton("Cancel") { dialogInterface: DialogInterface, _ ->
                dialogInterface.dismiss()
            }
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

                // Get selected start time
                val startHour = startTimePicker.hour
                val startMinute = startTimePicker.minute

                // Get selected end time
                val endHour = endTimePicker.hour
                val endMinute = endTimePicker.minute

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
                        startHour,
                        startMinute,
                        0 // seconds
                    )
                }

                // Set end time using the currently selected date
                val endTime = Calendar.getInstance().apply {
                    set(
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                        endHour,
                        endMinute,
                        0 // seconds
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
                // Convert LocalDate to Date for database query
                val calendar = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                }
                val events = App.database.eventDao().getEventsByDate(calendar.time)
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