package com.example.Taskly.ui.projects

import android.app.TimePickerDialog // Import TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter // Import ArrayAdapter
import android.widget.EditText // Import EditText
import android.widget.Spinner // Import Spinner
import android.widget.TextView // Import TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.Taskly.App
import com.example.Taskly.NotificationScheduler
import com.example.Taskly.R
import com.example.Taskly.databinding.FragmentProjectDetailsBinding
import com.example.Taskly.ui.calendar.EventAdapter
import com.example.Taskly.ui.login.LoginViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.example.Taskly.ui.calendar.Event // Make sure Event is imported
import com.google.android.material.textfield.TextInputEditText // Import TextInputEditText
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat // Import SimpleDateFormat
import java.util.Calendar // Import Calendar
import java.util.Locale // Import Locale

class ProjectDetailsFragment : Fragment() {

    private var _binding: FragmentProjectDetailsBinding? = null
    private val binding get() = _binding!!

    private val args: ProjectDetailsFragmentArgs by navArgs()
    private val viewModel: ProjectDetailsViewModel by viewModels()
    private val loginViewModel: LoginViewModel by activityViewModels()

    private lateinit var eventAdapter: EventAdapter
    private var userProjects: List<Project> = emptyList() // To hold projects for the spinner

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectDetailsBinding.inflate(inflater, container, false)
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.GONE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchUserProjects() // Fetch projects for the spinner
        setupHeader()
        setupRecyclerView()
        setupObservers()

        binding.backButtonProjectDetails.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun fetchUserProjects() {
        loginViewModel.loggedInUser.value?.let { user ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    userProjects = App.database.projectDao().getProjectsForUserList(user.email)
                } catch (e: Exception) {
                    Log.e("ProjectDetails", "Error fetching projects", e)
                    userProjects = emptyList()
                }
            }
        }
    }

    private fun setupHeader() {
        binding.projectNameHeader.text = args.projectName

        val iconResId = requireContext().resources.getIdentifier(
            args.projectIconName,
            "drawable",
            requireContext().packageName
        )
        if (iconResId != 0) {
            binding.projectIconHeader.setImageResource(iconResId)
        } else {
            binding.projectIconHeader.setImageResource(R.drawable.ic_home_project)
        }
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            showDate = true,
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
                        Log.e("ProjectDetails", "Error updating event", e) // Log tag updated
                        Toast.makeText(requireContext(), "Error updating task", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEventDeleteClick = { event ->
                showDeleteConfirmationDialog(event)
            },
            // Call the new edit dialog function
            onEventEditClick = { event ->
                showEditEventDialog(event)
            }
        )
        binding.projectEventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }

    private fun setupObservers() {
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                viewModel.loadEvents(args.projectId, user)
            } else {
                eventAdapter.submitList(emptyList())
                binding.projectEventsRecyclerView.visibility = View.GONE
                binding.noEventsTextProject.visibility = View.VISIBLE
                binding.noEventsTextProject.text = "Please log in to view project events."
            }
        }

        viewModel.projectEvents.observe(viewLifecycleOwner) { events ->
            if (events.isNullOrEmpty()) {
                binding.projectEventsRecyclerView.visibility = View.GONE
                binding.noEventsTextProject.visibility = View.VISIBLE
                binding.noEventsTextProject.text = "No events in this project."
            } else {
                binding.projectEventsRecyclerView.visibility = View.VISIBLE
                binding.noEventsTextProject.visibility = View.GONE
                eventAdapter.submitList(events)
            }
        }
    }

    private fun showDeleteConfirmationDialog(event: Event) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogTheme)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}' from this project?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteEvent(event)
            }
            .show()
    }

    private fun deleteEvent(event: Event) {
        lifecycleScope.launch {
            try {
                NotificationScheduler.cancelNotification(requireContext(), event)
                App.database.eventDao().deleteEvent(event)
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProjectDetails", "Error deleting event", e)
                Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- START: ADDED EDIT EVENT DIALOG LOGIC ---
    private fun showEditEventDialog(eventToEdit: Event) {
        // Inflate the same dialog used for adding events
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null, false)
        val dialogHeader = dialogView.findViewById<TextView>(R.id.dialogHeaderTitle)
        val titleEdit = dialogView.findViewById<EditText>(R.id.eventTitleEdit)
        val descEdit = dialogView.findViewById<EditText>(R.id.eventDescriptionEdit)
        val startTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.startTimeEdit)
        val endTimeEdit = dialogView.findViewById<TextInputEditText>(R.id.endTimeEdit)
        val projectSpinner = dialogView.findViewById<Spinner>(R.id.projectSpinner)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        dialogHeader.text = "Edit Event" // Set title

        // Setup Spinner
        val projectNames = mutableListOf("No Project")
        projectNames.addAll(userProjects.map { it.name })
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            projectNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        projectSpinner.adapter = spinnerAdapter

        // Initialize time calendars
        val startCalendar = Calendar.getInstance().apply { time = eventToEdit.startTime }
        val endCalendar = Calendar.getInstance().apply { time = eventToEdit.endTime }

        // Pre-populate fields
        titleEdit.setText(eventToEdit.title)
        descEdit.setText(eventToEdit.description)
        startTimeEdit.setText(timeFormat.format(startCalendar.time))
        endTimeEdit.setText(timeFormat.format(endCalendar.time))

        // Set spinner selection
        val projectIndex = userProjects.indexOfFirst { it.id == eventToEdit.projectId }
        projectSpinner.setSelection(if (projectIndex != -1) projectIndex + 1 else 0)

        // Set up time picker listeners
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
            .setPositiveButton("Save", null) // Use null listener initially
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
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

                // Get Project ID from Spinner
                val selectedSpinnerPosition = projectSpinner.selectedItemPosition
                val selectedProjectId: Int? = if (selectedSpinnerPosition == 0) {
                    null // "No Project" selected
                } else {
                    userProjects[selectedSpinnerPosition - 1].id
                }

                // Ensure end time is after start time
                if (endCalendar.after(startCalendar)) {
                    // Create the updated event object
                    // IMPORTANT: Preserve the original event's date, only update time
                    val originalDateCalendar = Calendar.getInstance().apply { time = eventToEdit.date }
                    val startTimeWithOriginalDate = Calendar.getInstance().apply {
                        time = originalDateCalendar.time // Start with original date
                        set(Calendar.HOUR_OF_DAY, startCalendar.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, startCalendar.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val endTimeWithOriginalDate = Calendar.getInstance().apply {
                        time = originalDateCalendar.time // Start with original date
                        set(Calendar.HOUR_OF_DAY, endCalendar.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, endCalendar.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }


                    val updatedEvent = eventToEdit.copy(
                        title = title,
                        description = description,
                        startTime = startTimeWithOriginalDate.time, // Use time combined with original date
                        endTime = endTimeWithOriginalDate.time,   // Use time combined with original date
                        projectId = selectedProjectId
                        // date field remains unchanged from eventToEdit
                    )

                    // Launch coroutine to update
                    updateEvent(updatedEvent) // Call the existing update function
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

    private fun updateEvent(event: Event) {
        lifecycleScope.launch {
            try {
                App.database.eventDao().updateEvent(event)
                NotificationScheduler.cancelNotification(requireContext(), event)
                NotificationScheduler.scheduleNotification(requireContext(), event)
                Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show()
                // LiveData should update the list automatically, no need to manually refresh
            } catch (e: Exception) {
                Log.e("ProjectDetails", "Error updating event", e)
                Toast.makeText(requireContext(), "Error updating event", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Helper function for time pickers
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
            true // 24-hour format
        )
        timePickerDialog.show()
    }
    // --- END: ADDED EDIT EVENT DIALOG LOGIC ---

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE
        _binding = null
    }
}