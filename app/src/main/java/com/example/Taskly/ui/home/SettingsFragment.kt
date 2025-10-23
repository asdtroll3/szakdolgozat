package com.example.Taskly.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.Taskly.App
import com.example.Taskly.R
import com.example.Taskly.databinding.FragmentSettingsBinding
import com.example.Taskly.ui.login.LoginViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val loginViewModel: LoginViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.post {
            activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.GONE
        }

        val darkModeSwitch = binding.darkModeSwitch

        // --- Use the global App.sharedPreferences ---
        val sharedPreferences = App.sharedPreferences
        // ------------------------------------------

        // Set the switch to the current saved state
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        darkModeSwitch.isChecked = isDarkMode

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPreferences.edit().putBoolean("dark_mode", true).apply()
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                sharedPreferences.edit().putBoolean("dark_mode", false).apply()
            }
        }
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                // User is logged in, show the logout button
                binding.logoutCard.visibility = View.VISIBLE
            } else {
                // User is logged out, hide the button
                binding.logoutCard.visibility = View.GONE
            }
        }

        // Handle logout click
        binding.logoutCard.setOnClickListener {
            loginViewModel.logout()
            // Navigate back to the home screen
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        // Restore the bottom navigation view *before* the view is destroyed
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE

        super.onDestroyView()
        _binding = null
    }
}