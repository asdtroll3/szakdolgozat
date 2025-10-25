package com.example.Taskly.ui.home

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.Taskly.R
import com.example.Taskly.databinding.DialogAddProjectBinding
import com.example.Taskly.databinding.FragmentHomeBinding
import com.example.Taskly.ui.login.LoginViewModel
import com.example.Taskly.ui.projects.ProjectAdapter
import com.example.Taskly.ui.projects.IconSpinnerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Get a reference to the shared LoginViewModel
    private val loginViewModel: LoginViewModel by activityViewModels()
    // Get a reference to the HomeViewModel
    private val homeViewModel: HomeViewModel by viewModels()

    private lateinit var projectAdapter: ProjectAdapter

    private val availableIcons = listOf(
        "Home" to "ic_home_project",
        "Icon1" to "ic_icon1",
        "Icon2" to "ic_icon2",
        "Icon3" to "ic_icon3",
        "Icon4" to "ic_icon4"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onResume() {
        super.onResume()
        // Re-load projects to refresh counts when user returns to this screen
        loginViewModel.loggedInUser.value?.let { user ->
            homeViewModel.loadProjectsForUser(user.email)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.loginCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_loginFragment)
        }

        binding.settingsDetailCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_settingsFragment)
        }

        binding.myProjectsHeader.findViewById<View>(R.id.addProjectIcon).setOnClickListener {
            showAddProjectDialog()
        }


        // Observe the login state
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.usernameText.text = "Hello, ${user.username}!"
                binding.loginCard.visibility = View.GONE
                // Load projects for the logged-in user
                homeViewModel.loadProjectsForUser(user.email)
            } else {
                binding.usernameText.text = "Not logged in"
                binding.loginCard.visibility = View.VISIBLE
                // Clear projects
                homeViewModel.clearProjects()
            }
        }

        // Observe the projects list
        homeViewModel.projects.observe(viewLifecycleOwner) { projectsWithCount ->
            projectAdapter.submitList(projectsWithCount)
        }

        homeViewModel.quote.observe(viewLifecycleOwner) { quoteText ->
            binding.quoteTextView.text = quoteText
        }
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { projectWithCount ->
            val project = projectWithCount.project
            // Check event count for AI suggestion
            val showSuggestion = projectWithCount.eventCount >= 3
            val action = HomeFragmentDirections.actionNavigationHomeToProjectDetailsFragment(
                projectId = project.id,
                projectName = project.name,
                projectIconName = project.iconName,
                showAiSuggestion = showSuggestion
            )
            findNavController().navigate(action)
            // --- ***************************** ---
        }
        binding.projectsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = projectAdapter
        }

    }

    private fun showAddProjectDialog() {
        val user = loginViewModel.loggedInUser.value
        if (user == null) {
            Toast.makeText(requireContext(), "You must be logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddProjectBinding.inflate(layoutInflater)
        val projectNameEdit = dialogBinding.projectNameEdit
        val iconSpinner = dialogBinding.iconSpinner // Get the icon spinner

        val spinnerAdapter = IconSpinnerAdapter(requireContext(), availableIcons)
        iconSpinner.adapter = spinnerAdapter
        iconSpinner.setSelection(0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            addButton.setOnClickListener {
                val projectName = projectNameEdit.text.toString().trim()
                val selectedPosition = iconSpinner.selectedItemPosition
                val selectedIconName = availableIcons[selectedPosition].second
                if (projectName.isNotEmpty()) {
                    homeViewModel.addProject(projectName, selectedIconName, user.email)
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Project name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            val cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            cancelButton.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}