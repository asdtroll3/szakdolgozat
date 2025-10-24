package com.example.Taskly.ui.mail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.databinding.ItemMailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MailAdapter(
    private val onMailClick: (Mail) -> Unit,
    private val isSentbox: () -> Boolean
) : ListAdapter<Mail, MailAdapter.MailViewHolder>(MailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MailViewHolder {
        val binding = ItemMailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MailViewHolder, position: Int) {
        val mail = getItem(position)
        holder.bind(mail)
    }

    inner class MailViewHolder(private val binding: ItemMailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

        fun bind(mail: Mail) {
            binding.mailSubject.text = mail.subject.ifEmpty { "(No Subject)" }
            binding.mailSnippet.text = mail.body.replace("\n", " ").take(100)

            if (isSentbox()) {
                binding.mailFromOrTo.text = "To: ${mail.recipientEmail}"
                binding.unreadDot.visibility = View.GONE
            } else {
                binding.mailFromOrTo.text = "From: ${mail.senderEmail}"
                if (mail.isRead) {
                    binding.unreadDot.visibility = View.GONE
                } else {
                    binding.unreadDot.visibility = View.VISIBLE
                }
            }

            binding.mailTimestamp.text = dateFormat.format(Date(mail.timestamp))

            binding.root.setOnClickListener {
                onMailClick(mail)
            }
        }
    }
}

class MailDiffCallback : DiffUtil.ItemCallback<Mail>() {
    override fun areItemsTheSame(oldItem: Mail, newItem: Mail): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: Mail, newItem: Mail): Boolean {
        return oldItem == newItem
    }
}