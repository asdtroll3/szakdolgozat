package com.example.Taskly.ui.projects

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.example.Taskly.App
import com.example.Taskly.ui.calendar.Event
import com.example.Taskly.ui.login.User

class ProjectDetailsViewModel : ViewModel() {

    private val eventDao = App.database.eventDao()

    // Holds the current project ID and user for fetching
    private val _requestData = MutableLiveData<Pair<Int, User>>()

    // LiveData for the events, automatically updated when _requestData changes
    val projectEvents: LiveData<List<Event>> = _requestData.switchMap { (projectId, user) ->
        eventDao.getEventsForProject(projectId, user.email)
    }

    // Function to trigger loading events for a specific project and user
    fun loadEvents(projectId: Int, user: User) {
        _requestData.value = Pair(projectId, user)
    }
}