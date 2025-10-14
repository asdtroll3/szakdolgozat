package com.example.alphaverzio.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// A simple User data class
data class User(val username: String, val email: String)

class LoginViewModel : ViewModel() {

    // Hardcoded user credentials
    private val adminUser = User("admin", "admin@admin.com")
    private val adminPassword = "admin"

    // LiveData to hold the currently logged-in user
    private val _loggedInUser = MutableLiveData<User?>(null)
    val loggedInUser: LiveData<User?> = _loggedInUser

    /**
     * Attempts to log in the user with the given credentials.
     * @return true if login is successful, false otherwise.
     */
    fun login(email: String, pass: String): Boolean {
        if (email.equals(adminUser.email, ignoreCase = true) && pass == adminPassword) {
            _loggedInUser.value = adminUser
            return true
        }
        return false
    }

    /**
     * Logs out the current user.
     */
    fun logout() {
        _loggedInUser.value = null
    }
}