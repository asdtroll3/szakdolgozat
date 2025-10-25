package com.example.Taskly.ui.mail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.Taskly.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class AiReply(val subject: String, val body: String)
class MailViewModel : ViewModel() {

    private val mailDao = App.database.mailDao()
    private val userDao = App.database.userDao()

    private val _inbox = MutableLiveData<List<Mail>>()
    val inbox: LiveData<List<Mail>> = _inbox

    private val _sent = MutableLiveData<List<Mail>>()
    val sent: LiveData<List<Mail>> = _sent

    private val _sendMailStatus = MutableLiveData<String?>()
    val sendMailStatus: LiveData<String?> = _sendMailStatus

    private val client = OkHttpClient()
    private val apiKey = "AIzaSyCtQ8vKKwdZsmKaesTfTO2l0FJ8CtTYzRQ"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val _isSummarizing = MutableLiveData<Boolean>()
    val isSummarizing: LiveData<Boolean> = _isSummarizing

    private val _summaryResult = MutableLiveData<String>()
    val summaryResult: LiveData<String> = _summaryResult

    private val _isGeneratingReply = MutableLiveData<Boolean>()
    val isGeneratingReply: LiveData<Boolean> = _isGeneratingReply

    private val _replyResult = MutableLiveData<AiReply?>()
    val replyResult: LiveData<AiReply?> = _replyResult

    private val _replyError = MutableLiveData<String?>()
    val replyError: LiveData<String?> = _replyError


    fun loadInbox(userEmail: String) {
        viewModelScope.launch {
            try {
                _inbox.postValue(mailDao.getInbox(userEmail))
            } catch (e: Exception) {
                _inbox.postValue(emptyList())
            }
        }
    }

    fun loadSentItems(userEmail: String) {
        viewModelScope.launch {
            try {
                _sent.postValue(mailDao.getSentItems(userEmail))
            } catch (e: Exception) {
                _sent.postValue(emptyList())
            }
        }
    }

    fun sendMail(senderEmail: String, recipientEmail: String, subject: String, body: String) {
        viewModelScope.launch {

            val recipientExists = userDao.findUserByEmail(recipientEmail)
            if (recipientExists == null) {
                _sendMailStatus.postValue("Error: Recipient user '$recipientEmail' not found.")
                return@launch
            }

            try {
                val newMail = Mail(
                    senderEmail = senderEmail,
                    recipientEmail = recipientEmail,
                    subject = subject,
                    body = body,
                    timestamp = System.currentTimeMillis()
                )
                mailDao.insertMail(newMail)
                _sendMailStatus.postValue(null) //success

                loadSentItems(senderEmail)
                if (senderEmail.equals(recipientEmail, ignoreCase = true)) {
                    loadInbox(senderEmail)
                }
            } catch (e: Exception) {
                _sendMailStatus.postValue("An error occurred while sending: ${e.message}")
            }
        }
    }
    fun markMailAsRead(mail: Mail) {
        val currentUserEmail = App.sharedPreferences.getString("logged_in_email", null)
        if (!mail.isRead && mail.recipientEmail.equals(currentUserEmail, ignoreCase = true)) {
            viewModelScope.launch {
                try {
                    mailDao.markAsRead(mail.id)
                    _inbox.value?.let { currentInbox ->
                        val updatedList = currentInbox.map {
                            if (it.id == mail.id) {
                                it.copy(isRead = true)
                            } else {
                                it
                            }
                        }
                        _inbox.postValue(updatedList)
                    }

                } catch (e: Exception) {
                    Log.e("MailViewModel", "neeem, ${e.message}")
                }
            }
        }
    }
    fun deleteMail(mail: Mail) {
        viewModelScope.launch {
            try {
                mailDao.markAsDeletedByRecipient(mail.id)

                loadInbox(mail.recipientEmail)

            } catch (e: Exception) {
                //
            }
        }
    }

    fun clearMail() {
        _inbox.value = emptyList()
        _sent.value = emptyList()
    }


    fun summarizeMail(body: String) {
        _isSummarizing.postValue(true)
        viewModelScope.launch(Dispatchers.IO) {
            val prompt = "Please summarize the following email body:\n\n$body"
            val result = makeGeminiApiCall(prompt)
            _summaryResult.postValue(result ?: "Error: No response from AI.")
            _isSummarizing.postValue(false)
        }
    }
    fun generateReply(mail: Mail) {
        _isGeneratingReply.postValue(true)
        _replyError.postValue(null)
        _replyResult.postValue(null)

        viewModelScope.launch(Dispatchers.IO) {
            val prompt = """
                Generate a professional reply to the following email:
                
                From: ${mail.senderEmail}
                Subject: ${mail.subject}
                Body:
                ${mail.body}
                
                Please provide the reply in the following JSON format:
                {
                  "subject": "RE: ${mail.subject.replace("\"", "\\\"")}",
                  "body": "[Your generated reply body]"
                }
            """.trimIndent()

            val result = makeGeminiApiCall(prompt)

            if (result == null || result.startsWith("Error:") || result.startsWith("Network error:") || result.startsWith("API Error:") || result.startsWith("Blocked due to")) {
                _replyError.postValue(result ?: "Error: No response from AI.")
            } else {
                try {

                    var jsonString = result.trim()
                    if (jsonString.startsWith("```json")) {
                        jsonString = jsonString.substring(7, jsonString.length - 3).trim()
                    } else if (jsonString.startsWith("```")) {
                        jsonString = jsonString.substring(3, jsonString.length - 3).trim()
                    }

                    val jsonResponse = JSONObject(jsonString)
                    val subject = jsonResponse.getString("subject")
                    val body = jsonResponse.getString("body")
                    _replyResult.postValue(AiReply(subject, body))
                } catch (e: Exception) {
                    _replyError.postValue("Error parsing AI reply. JSONBUG: $result")
                }
            }
            _isGeneratingReply.postValue(false)
        }
    }

    private suspend fun makeGeminiApiCall(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    }
                    put("contents", contentsArray)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$apiUrl?key=$apiKey")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let { parseGeminiResponse(it) }
                    } else {
                        "API Error: ${response.code} - ${response.message}"
                    }
                }
            } catch (e: IOException) {
                "Network error: ${e.message}"
            } catch (e: Exception) {
                "Unexpected error: ${e.message}"
            }
        }
    }

    private fun parseGeminiResponse(responseBody: String): String? {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    parts.getJSONObject(0).getString("text").trim()
                } else {
                    "No response text found."
                }
            } else {
                val promptFeedback = jsonResponse.optJSONObject("promptFeedback")
                if (promptFeedback != null) {
                    val safetyRatings = promptFeedback.optJSONArray("safetyRatings")
                    if (safetyRatings != null && safetyRatings.length() > 0) {
                        val firstRating = safetyRatings.getJSONObject(0)
                        "Blocked due to safety concerns: ${firstRating.getString("category")}"
                    } else {
                        "Response was empty. It might have been blocked."
                    }
                } else {
                    "No candidates found in response."
                }
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }
}