package com.example.Taskly.ui.login

import android.database.sqlite.SQLiteConstraintException
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import com.example.Taskly.ui.projects.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel : ViewModel() {

    private val userDao = App.database.userDao()
    private val projectDao = App.database.projectDao()

    companion object {
        const val PREF_LOGGED_IN_EMAIL = "logged_in_email"
    }

    private val _loggedInUser = MutableLiveData<User?>(null)
    val loggedInUser: LiveData<User?> = _loggedInUser

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val savedEmail = App.sharedPreferences.getString(PREF_LOGGED_IN_EMAIL, null)
            if (!savedEmail.isNullOrBlank()) {
                val user = userDao.findUserByEmail(savedEmail)
                if (user != null) {
                    _loggedInUser.postValue(user)
                    withContext(Dispatchers.Main) {
                        applyTheme(user.isDarkMode)
                    }
                } else {
                    App.sharedPreferences.edit().remove(PREF_LOGGED_IN_EMAIL).apply()
                }
            }

            val admin = userDao.findUserByEmail("admin@admin.com")
            if (admin == null) {
                try {
                    userDao.insertUser(User("admin@admin.com", "admin", "admin"))
                    val adminHome = Project(ownerEmail = "admin@admin.com", name = "Home", iconName = "ic_home_project")
                    projectDao.insert(adminHome)
                } catch (e: SQLiteConstraintException) {
                    println("Admin insertion failed: ${e.message}")
                }
            }
        }
    }

    suspend fun login(email: String, pass: String): Boolean {
        val user = userDao.findUserByEmailAndPassword(email, pass)

        return if (user != null) {
            _loggedInUser.postValue(user)

            withContext(Dispatchers.Main) {
                applyTheme(user.isDarkMode)
            }

            App.sharedPreferences.edit()
                .putString(PREF_LOGGED_IN_EMAIL, user.email)
                .apply()
            true
        } else {
            false
        }
    }

    suspend fun register(username: String, email: String, pass: String, confirmPass: String): String? {

        if (username.length < 3 || username.length > 10) {
            return "Username must be between 3 and 10 characters"
        }

        if (!email.contains("@")) {
            return "Invalid email address"
        }

        if (userDao.findUserByEmail(email) != null) {
            return "An account with this email already exists"
        }

        if (pass.length < 6) {
            return "Password must be at least 6 characters long"
        }

        if (pass != confirmPass) {
            return "Passwords do not match"
        }

        val newUser = User(email, username, pass)
        return try {
            userDao.insertUser(newUser)
            val defaultProject = Project(ownerEmail = email, name = "Home", iconName = "ic_home_project")
            projectDao.insert(defaultProject)
            null
        } catch (e: SQLiteConstraintException) {
            "An error occurred: Email already exists"
        }
    }

    fun updateUserDarkMode(isDarkMode: Boolean) {
        _loggedInUser.value?.let { currentUser ->
            val updatedUser = currentUser.copy(isDarkMode = isDarkMode)
            _loggedInUser.value = updatedUser

            viewModelScope.launch(Dispatchers.IO) {
                userDao.updateUser(updatedUser)
            }
        }
    }

    fun logout() {
        App.sharedPreferences.edit()
            .remove(PREF_LOGGED_IN_EMAIL)
            .apply()
        _loggedInUser.value = null
        applyTheme(false)
    }

    private fun applyTheme(isDarkMode: Boolean) {
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}