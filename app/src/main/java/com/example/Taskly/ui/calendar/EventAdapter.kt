package com.example.Taskly.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.databinding.ItemEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter(
    private val showDate: Boolean,
    private val onEventCheckedChange: (Event, Boolean) -> Unit,
    private val onEventDeleteClick: (Event) -> Unit,
    private val onEventEditClick: (Event) -> Unit,
    private val onEventHelpClick: ((Event) -> Unit)? = null
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {
    private var events: List<Event> = emptyList()

    inner class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val eventDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        fun bind(event: Event) {
            binding.eventTitle.text = event.title
            binding.eventDescription.text = event.description

            val formattedStartTime = formatTo24HourTime(event.startTime)
            val formattedEndTime = formatTo24HourTime(event.endTime)

            binding.eventTime.text = "Starts: $formattedStartTime | Ends: $formattedEndTime"

            if (showDate) {
                binding.eventDate.text = eventDateFormat.format(event.date)
                binding.eventDate.visibility = View.VISIBLE
            } else {
                binding.eventDate.visibility = View.GONE
            }

            binding.taskCheckBox.setOnCheckedChangeListener(null)
            binding.taskCheckBox.isChecked = event.isCompleted

            binding.taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onEventCheckedChange(event, isChecked)
            }

            binding.deleteEventIcon.setOnClickListener {
                onEventDeleteClick(event)
            }

            binding.editEventIcon.setOnClickListener {
                onEventEditClick(event)
            }
            if (onEventHelpClick != null) {
                binding.helpEventIcon.visibility = View.VISIBLE
                binding.helpEventIcon.setOnClickListener {
                    onEventHelpClick?.invoke(event)
                }
            } else {
                binding.helpEventIcon.visibility = View.GONE
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