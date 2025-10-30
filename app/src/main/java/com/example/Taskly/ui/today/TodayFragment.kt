package com.example.Taskly.ui.today

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.R
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.calendar.EventAdapter
import com.example.Taskly.ui.login.LoginViewModel
import com.example.Taskly.ui.projects.Project
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodayFragment : Fragment() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView

    private lateinit var todayEventsRecyclerView: RecyclerView
    private lateinit var noEventsTextView: TextView
    private lateinit var eventAdapter: EventAdapter

    private val viewModel: TodayViewModel by viewModels()
    private val loginViewModel: LoginViewModel by activityViewModels()

    private var userProjects: List<Project> = emptyList()

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
        setupObservers()

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTodayEvents()
    }

    private fun initializeViews(view: View) {
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatDisplay = view.findViewById(R.id.text_today)
        chatScrollView = view.findViewById(R.id.chatScrollView)

        todayEventsRecyclerView = view.findViewById(R.id.todayEventsRecyclerView)
        noEventsTextView = view.findViewById(R.id.noEventsTextView)
    }

    private fun setupObservers() {
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            viewModel.setUserEmail(user?.email)
        }

        viewModel.todayEvents.observe(viewLifecycleOwner) { events ->
            if (events.isEmpty()) {
                noEventsTextView.visibility = View.VISIBLE
                todayEventsRecyclerView.visibility = View.GONE
            } else {
                noEventsTextView.visibility = View.GONE
                todayEventsRecyclerView.visibility = View.VISIBLE
                eventAdapter.submitList(events)
            }
        }

        viewModel.chatHistory.observe(viewLifecycleOwner) { messages ->
            val userName = loginViewModel.loggedInUser.value?.username ?: "You"

            val chatText = messages.joinToString("\n\n") {
                val prefix = when (it.role) {
                    "model" -> "Gemini"
                    "user" -> userName
                    else -> "System"
                }
                "$prefix: ${it.content}"
            }
            chatDisplay.text = chatText
            scrollToBottom()
        }

        viewModel.isSendingMessage.observe(viewLifecycleOwner) { isSending ->
            sendButton.isEnabled = !isSending
            sendButton.text =
                if (isSending) getString(R.string.sending) else getString(R.string.send)
        }

        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            this.userProjects = projects
        }
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
                showEditEventDialog(event)
            },
            onEventHelpClick = { event ->
                val helpPrompt =
                    "Help me with this task: \nTitle: ${event.title}\nDescription: ${event.description}"
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
                viewModel.deleteEvent(event)
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
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
            val imm =
                activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
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
        viewModel.sendMessage(message)
        userInput.text.clear()
        hideKeyboard()
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
                    viewModel.updateEvent(updatedEvent)
                    Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show()
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
}