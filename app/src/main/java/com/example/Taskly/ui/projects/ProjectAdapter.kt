package com.example.Taskly.ui.projects

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.Taskly.databinding.ItemProjectBinding
import com.example.Taskly.ui.home.ProjectWithCount

class ProjectAdapter(
    private val onProjectClick: (Project) -> Unit
) : ListAdapter<ProjectWithCount, ProjectAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val projectwithCount = getItem(position)
        holder.bind(projectwithCount)
    }

    inner class ProjectViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context

        fun bind(projectWithCount: ProjectWithCount) { // Expect ProjectWithCount
            val project = projectWithCount.project
            val count = projectWithCount.eventCount

            binding.projectName.text = project.name
            binding.projectTaskCount.text = count.toString() // Set the dynamic count
            binding.root.setOnClickListener {
                onProjectClick(project) // Pass the original project
            }

            // --- Dynamically set the icon ---
            val iconResId = context.resources.getIdentifier(
                project.iconName,
                "drawable",
                context.packageName
            )

            if (iconResId != 0) { // 0 means not found
                binding.projectIcon.setImageResource(iconResId)
            } else {
                // Fallback to a default icon if not found
                binding.projectIcon.setImageResource(
                    context.resources.getIdentifier(
                        "ic_home_project",
                        "drawable",
                        context.packageName
                    )
                )
            }
        }
    }
}

class ProjectDiffCallback : DiffUtil.ItemCallback<ProjectWithCount>() {
    override fun areItemsTheSame(oldItem: ProjectWithCount, newItem: ProjectWithCount): Boolean {
        return oldItem.project.id == newItem.project.id
    }
    override fun areContentsTheSame(oldItem: ProjectWithCount, newItem: ProjectWithCount): Boolean {
        return oldItem == newItem
    }
}