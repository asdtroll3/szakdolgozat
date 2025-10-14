package com.example.alphaverzio.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.alphaverzio.R
import com.example.alphaverzio.databinding.FragmentRegisterBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)

        // Hide the bottom navigation view
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerButtonSubmit.setOnClickListener {
            val username = binding.usernameEdit.text.toString().trim()
            val email = binding.emailEditRegister.text.toString().trim()
            val password = binding.passwordEditRegister.text.toString()
            val confirmPassword = binding.confirmPasswordEdit.text.toString()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: Add user registration logic here
            Toast.makeText(requireContext(), "Registration successful (not implemented)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Restore the bottom navigation view
        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE
    }
}