package com.example.Taskly.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.databinding.ItemEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter(
    private val onEventCheckedChange: (Event, Boolean) -> Unit, // Callback to handle checkbox state change
    private val onEventDeleteClick: (Event) -> Unit,
    private val onEventEditClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {
    private var events: List<Event> = emptyList()

    inner class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.eventTitle.text = event.title
            binding.eventDescription.text = event.description

            // Format start and end times
            val formattedStartTime = formatTo24HourTime(event.startTime)
            val formattedEndTime = formatTo24HourTime(event.endTime)

            // Display times
            binding.eventTime.text = "Starts: $formattedStartTime | Ends: $formattedEndTime"

            // IMPORTANT: Remove the listener before setting the checked state.
            // This prevents the listener from being fired when the view is recycled.
            binding.taskCheckBox.setOnCheckedChangeListener(null)
            binding.taskCheckBox.isChecked = event.isCompleted

            // Now, set the listener for user interaction.
            binding.taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onEventCheckedChange(event, isChecked)
            }

            // Set delete click listener
            binding.deleteEventIcon.setOnClickListener {
                onEventDeleteClick(event)
            }

            binding.editEventIcon.setOnClickListener {
                onEventEditClick(event)
            }
        }

        private fun formatTo24HourTime(date: Date): String {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            return format.format(date)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.bind(event)
    }

    override fun getItemCount() = events.size

    fun submitList(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}