package com.example.alphaverzio.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alphaverzio.databinding.ItemEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter(
    private val onEventCheckedChange: (Event, Boolean) -> Unit // Callback to handle checkbox state change
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {
    private var events: List<Event> = emptyList()

    class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event, onCheckedChange: (Boolean) -> Unit) {
            binding.eventTitle.text = event.title
            binding.eventDescription.text = event.description

            // Format start and end times
            val formattedStartTime = formatTo24HourTime(event.startTime)
            val formattedEndTime = formatTo24HourTime(event.endTime)

            // Display times
            binding.eventTime.text = "Starts: $formattedStartTime | Ends: $formattedEndTime"

            // Bind checkbox state to event's completion status
            binding.taskCheckBox.isChecked = event.isCompleted
            binding.taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked) // Notify when the checkbox is toggled
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
        holder.bind(event) { isChecked ->
            onEventCheckedChange(event, isChecked) // Trigger the callback to update the event state
        }
    }

    override fun getItemCount() = events.size

    fun submitList(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
