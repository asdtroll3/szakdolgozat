package com.example.alphaverzio.ui.mail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MailViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Mail"
    }
    val text: LiveData<String> = _text
}