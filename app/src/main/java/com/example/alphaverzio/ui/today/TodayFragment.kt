package com.example.alphaverzio.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.alphaverzio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TodayFragment : Fragment() {

    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatDisplay: TextView
    private lateinit var chatScrollView: ScrollView

    private val chatHistory = mutableListOf<ChatMessage>()

    private val client = OkHttpClient()

    private val apiKey = "AIzaSyAS5DdxZflY8IWwklBvVP3cTCPh2X0OKac"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent"

    data class ChatMessage(
        val role: String, // "user" or "assistant"
        val content: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatDisplay = view.findViewById(R.id.text_today)
        chatScrollView = view.findViewById(R.id.chatScrollView)
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            handleSendMessage()
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                handleSendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun scrollToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun handleSendMessage() {
        val message = userInput.text.toString().trim()

        if (message.isEmpty()) {
            Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Add user message to history and display
        addMessageToChat("You", message)
        userInput.text.clear()

        // Disable send button while processing
        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        // Send message to ChatGPT
        sendMessageToChatGPT(message)
    }

    private fun addMessageToChat(sender: String, message: String) {
        val currentText = chatDisplay.text.toString()
        val newText = if (currentText.isEmpty()) {
            "$sender: $message"
        } else {
            "$currentText\n\n$sender: $message"
        }
        chatDisplay.text = newText

        // Scroll to bottom
        scrollToBottom()
    }

    private fun sendMessageToChatGPT(message: String) {
        lifecycleScope.launch {
            try {
                // Add user message to chat history
                chatHistory.add(ChatMessage("user", message))

                val response = withContext(Dispatchers.IO) {
                    makeApiCall(message)
                }

                response?.let { aiResponse ->
                    if (aiResponse.startsWith("API Error: 429")) {
                        addMessageToChat("System", "Rate limit exceeded. Please wait a moment before trying again.")
                    } else if (aiResponse.startsWith("API Error:") || aiResponse.startsWith("Network error:")) {
                        addMessageToChat("System", aiResponse)
                    } else {
                        // Add AI response to history and display
                        chatHistory.add(ChatMessage("assistant", aiResponse))
                        addMessageToChat("Assistant", aiResponse)
                    }
                } ?: run {
                    addMessageToChat("System", "Sorry, I couldn't get a response. Please try again.")
                }

            } catch (e: Exception) {
                addMessageToChat("System", "Error: ${e.message}")
            } finally {
                // Re-enable send button
                sendButton.isEnabled = true
                sendButton.text = "Send"
            }
        }
    }

    private suspend fun makeApiCall(message: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Gemini API format
                val json = JSONObject().apply {
                    val contents = JSONArray()
                    val content = JSONObject().apply {
                        val parts = JSONArray()
                        parts.put(JSONObject().apply {
                            put("text", message)
                        })
                        put("parts", parts)
                    }
                    contents.put(content)
                    put("contents", contents)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$apiUrl?key=$apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let { parseGeminiResponse(it) }
                } else {
                    when (response.code) {
                        429 -> "Rate limit exceeded. Please wait a few minutes before trying again."
                        401 -> "Invalid API key. Please check your Gemini API key."
                        403 -> "Access forbidden. Check your API key permissions."
                        500 -> "Gemini server error. Please try again later."
                        else -> "API Error: ${response.code} - ${response.message}"
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
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    val firstPart = parts.getJSONObject(0)
                    firstPart.getString("text").trim()
                } else {
                    "No response generated"
                }
            } else {
                "No response generated"
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }

    private fun parseApiResponse(responseBody: String): String? {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content").trim()
            } else {
                "No response generated"
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up resources if needed
    }
}