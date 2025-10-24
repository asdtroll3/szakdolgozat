package com.example.Taskly.ui.login

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val userDao = App.database.userDao()

    // --- Add a companion object for the preference key ---
    companion object {
        const val PREF_LOGGED_IN_EMAIL = "logged_in_email"
    }
    // -----------------------------------------------------

    // LiveData holds the logged-in user
    private val _loggedInUser = MutableLiveData<User?>(null)
    val loggedInUser: LiveData<User?> = _loggedInUser

    init {
        // --- Add this logic to auto-login ---
        viewModelScope.launch(Dispatchers.IO) {
            // Check for a saved user in preferences
            val savedEmail = App.sharedPreferences.getString(PREF_LOGGED_IN_EMAIL, null)
            if (!savedEmail.isNullOrBlank()) {
                val user = userDao.findUserByEmail(savedEmail)
                if (user != null) {
                    _loggedInUser.postValue(user)
                } else {
                    // User was in prefs but not DB? Clear prefs.
                    App.sharedPreferences.edit().remove(PREF_LOGGED_IN_EMAIL).apply()
                }
            }
            // ------------------------------------

            // This part remains the same
            val admin = userDao.findUserByEmail("admin@admin.com")
            if (admin == null) {
                try {
                    // Fixed parameter order: email, username, password
                    userDao.insertUser(User("admin@admin.com", "admin", "admin"))
                } catch (e: SQLiteConstraintException) {
                    println("Admin insertion failed: ${e.message}")
                }
            }
        }
    }

    /**
     * 5. Make login a suspend function and use the DAO
     * @return true if login is successful, false otherwise.
     */
    suspend fun login(email: String, pass: String): Boolean {
        val user = userDao.findUserByEmailAndPassword(email, pass)

        return if (user != null) {
            _loggedInUser.postValue(user)
            // --- Save the logged-in user's email ---
            App.sharedPreferences.edit()
                .putString(PREF_LOGGED_IN_EMAIL, user.email)
                .apply()
            // ----------------------------------------
            true
        } else {
            false
        }
    }

    /**
     * 6. Make register a suspend function and use the DAO
     * @return null if successful, or a String error message if validation fails.
     */
    suspend fun register(username: String, email: String, pass: String, confirmPass: String): String? {
        // Criteria 4: Username length (3-10 chars)
        if (username.length < 3 || username.length > 10) {
            return "Username must be between 3 and 10 characters"
        }

        // Criteria 2: Email format
        if (!email.contains("@")) {
            return "Invalid email address"
        }

        // Criteria 1: Email uniqueness (Check database)
        if (userDao.findUserByEmail(email) != null) {
            return "An account with this email already exists"
        }

        // Criteria 3: Password length (6+ chars)
        if (pass.length < 6) {
            return "Password must be at least 6 characters long"
        }

        // Check password match
        if (pass != confirmPass) {
            return "Passwords do not match"
        }

        val newUser = User(email, username, pass)
        return try {
            userDao.insertUser(newUser)
            null
        } catch (e: SQLiteConstraintException) {
            "An error occurred: Email already exists"
        }
    }

    /**
     * Logs out the current user.
     */
    fun logout() {
        // --- Clear the saved user from preferences ---
        App.sharedPreferences.edit()
            .remove(PREF_LOGGED_IN_EMAIL)
            .apply()
        // -------------------------------------------
        _loggedInUser.value = null
    }
}