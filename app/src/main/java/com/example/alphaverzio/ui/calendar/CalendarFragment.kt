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
import android.widget.TimePicker
import android.widget.Toast
import com.example.alphaverzio.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private var selectedDate: Calendar = Calendar.getInstance()
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
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            updateEventsForDate(selectedDate) // Refresh events for the newly selected date
        }
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter { event, isChecked ->
            event.isCompleted = isChecked // Update the completion status
            val selectedDate = Calendar.getInstance().apply { timeInMillis = binding.calendarView.date }
            val dateKey = formatDate(selectedDate)
            eventsByDate[dateKey]?.find { it == event }?.isCompleted = isChecked
            Toast.makeText(
                requireContext(),
                if (isChecked) "Task marked as completed!" else "Task marked as incomplete!",
                Toast.LENGTH_SHORT
            ).show()
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
                    // Show an error and prevent the dialog from closing
                    titleEdit.error = "Title cannot be empty"
                    return@setOnClickListener
                }

                val selectedDate = Calendar.getInstance().apply {
                    timeInMillis = binding.calendarView.date // Get selected date from CalendarView
                }

                // Get selected start time
                val startHour = startTimePicker.hour
                val startMinute = startTimePicker.minute

                // Get selected end time
                val endHour = endTimePicker.hour
                val endMinute = endTimePicker.minute

                // Set start time
                val startTime = Calendar.getInstance()
                startTime.set(
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH),
                    startHour,
                    startMinute
                )

                // Set end time
                val endTime = Calendar.getInstance()
                endTime.set(
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH),
                    endHour,
                    endMinute
                )

                // Ensure end time is after start time
                if (endTime.after(startTime)) {
                    addNewEvent(
                        title,
                        description,
                        startTime.time,
                        endTime.time
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

    private fun addNewEvent(title: String, description: String, startTime: Date, endTime: Date) {
        val dateKey = formatDate(selectedDate)
        val newEvent = Event(
            title = title,
            description = description,
            date = selectedDate.time,
            startTime = startTime,
            endTime = endTime
        )
        // Add the event to the map
        val eventsForDate = eventsByDate[dateKey] ?: mutableListOf()
        eventsForDate.add(newEvent)
        eventsByDate[dateKey] = eventsForDate
        updateEventsForDate(selectedDate)
        Log.d("AddNewEvent", "Added event to date: $dateKey -> $newEvent")
    }
    private fun formatDate(calendar: Calendar): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    private fun updateEventsForDate(selectedDate: Calendar) {
        val dateKey = formatDate(selectedDate)

        // Get events for the selected date or an empty list if none exist
        val selectedEvents = eventsByDate[dateKey] ?: emptyList()

        // Submit the list to the adapter
        eventAdapter.submitList(selectedEvents)
        Log.d("UpdateEventsForDate", "Events for date $dateKey: ${eventsByDate[dateKey]}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}