package com.example.Taskly.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
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

        val navBar = activity?.findViewById<BottomNavigationView>(R.id.nav_view)
        navBar?.visibility = View.GONE

        val darkModeSwitch = binding.darkModeSwitch

        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {

                binding.logoutCard.visibility = View.VISIBLE

                darkModeSwitch.setOnCheckedChangeListener(null)
                darkModeSwitch.isChecked = user.isDarkMode


                darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    loginViewModel.updateUserDarkMode(isChecked)
                }

            } else {
                binding.logoutCard.visibility = View.GONE
                darkModeSwitch.isChecked = false
                darkModeSwitch.setOnCheckedChangeListener(null)
            }
        }
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.logoutCard.visibility = View.VISIBLE
            } else {
                binding.logoutCard.visibility = View.GONE
            }
        }


        binding.logoutCard.setOnClickListener {
            loginViewModel.logout()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE

        super.onDestroyView()
        _binding = null
    }
}