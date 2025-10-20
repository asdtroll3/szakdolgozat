package com.example.Taskly.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.Taskly.R
import com.example.Taskly.databinding.FragmentHomeBinding
import com.example.Taskly.ui.login.LoginViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Get a reference to the shared LoginViewModel
    private val loginViewModel: LoginViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_loginFragment)
        }

        binding.settingsDetailCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_settingsFragment)
        }

        // Observe the login state
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.usernameText.text = "Hello, ${user.username}!"
                binding.loginCard.visibility = View.GONE
            } else {
                binding.usernameText.text = "Not logged in"
                binding.loginCard.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}