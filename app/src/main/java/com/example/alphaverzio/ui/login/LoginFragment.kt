package com.example.alphaverzio.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.alphaverzio.R
import com.example.alphaverzio.databinding.FragmentLoginBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Get a reference to the shared LoginViewModel
    private val loginViewModel: LoginViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        // Hide the bottom navigation view
        val navBar = activity?.findViewById<BottomNavigationView>(R.id.nav_view)
        navBar?.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            val email = binding.emailEdit.text.toString().trim()
            val password = binding.passwordEdit.text.toString().trim()

            if (loginViewModel.login(email, password)) {
                // Navigate back to the home screen on successful login
                findNavController().popBackStack()
                Toast.makeText(requireContext(), "Login Successful!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        // Restore the bottom navigation view
        val navBar = activity?.findViewById<BottomNavigationView>(R.id.nav_view)
        navBar?.visibility = View.VISIBLE
    }
}