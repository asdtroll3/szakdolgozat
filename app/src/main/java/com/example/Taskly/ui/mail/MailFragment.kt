package com.example.Taskly.ui.mail

import android.content.DialogInterface
import androidx.lifecycle.Observer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog // <-- CHANGE: Import the correct AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.Taskly.R
import com.example.Taskly.databinding.DialogComposeMailBinding
import com.example.Taskly.databinding.DialogMailOptionsBinding
import com.example.Taskly.databinding.FragmentMailBinding
import com.example.Taskly.ui.login.LoginViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class MailFragment : Fragment() {

    private var _binding: FragmentMailBinding? = null
    private val binding get() = _binding!!

    private lateinit var mailViewModel: MailViewModel
    private val loginViewModel: LoginViewModel by activityViewModels()
    private lateinit var mailAdapter: MailAdapter

    private var currentTabPosition = 0 // 0 for Inbox, 1 for Sent

    private var loadingDialog: AlertDialog? = null
    private var mailBeingProcessed: Mail? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mailViewModel = ViewModelProvider(this).get(MailViewModel::class.java)
        _binding = FragmentMailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupFab()
        observeViewModels()
    }

    private fun setupRecyclerView() {
        mailAdapter = MailAdapter(
            onMailClick = { mail ->
                // Check if we are in the "Inbox" tab (position 0)
                if (currentTabPosition == 0) {
                    showMailOptionsDialog(mail)
                } else {
                    // For "Sent" tab, just show the details
                    showMailDetailsDialog(mail)
                }
            },
            isSentbox = { currentTabPosition == 1 }
        )
        binding.mailRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mailAdapter
        }
    }

    private fun setupTabs() {
        binding.mailTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                updateMailList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateMailList() {
        val mails = if (currentTabPosition == 0) { // Inbox
            mailViewModel.inbox.value
        } else { // Sent
            mailViewModel.sent.value
        }
        mailAdapter.submitList(mails)
        if (mails.isNullOrEmpty()) {
            // Can show a "no mail" text here if needed
        }
        binding.mailRecyclerView.scrollToPosition(0) // Scroll to top
    }


    private fun setupFab() {
        binding.fabComposeMail.setOnClickListener {
            showComposeDialog()
        }
    }

    private fun observeViewModels() {
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                // User is logged in
                binding.textMailInfo.visibility = View.GONE
                binding.mailRecyclerView.visibility = View.VISIBLE
                binding.fabComposeMail.visibility = View.VISIBLE
                // Load mail
                mailViewModel.loadInbox(user.email)
                mailViewModel.loadSentItems(user.email)
            } else {
                // User is logged out
                binding.textMailInfo.setText(R.string.title_mail) // You can change this message
                binding.textMailInfo.visibility = View.VISIBLE
                binding.mailRecyclerView.visibility = View.GONE
                binding.fabComposeMail.visibility = View.GONE
                // Clear lists
                mailViewModel.clearMail()
                mailAdapter.submitList(emptyList())
            }
        }

        // Observe inbox
        mailViewModel.inbox.observe(viewLifecycleOwner) { inboxMails ->
            if (currentTabPosition == 0) {
                mailAdapter.submitList(inboxMails)
            }
        }

        // Observe sent items
        mailViewModel.sent.observe(viewLifecycleOwner) { sentMails ->
            if (currentTabPosition == 1) {
                mailAdapter.submitList(sentMails)
            }
        }

        // Observe mail send status
        mailViewModel.sendMailStatus.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage == null) {
                Toast.makeText(requireContext(), "Mail sent successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        mailViewModel.isSummarizing.observe(viewLifecycleOwner) { isSummarizing ->
            if (isSummarizing) {
                showLoadingDialog("Summarizing...")
            } else {
                dismissLoadingDialog()
            }
        }

        mailViewModel.summaryResult.observe(viewLifecycleOwner) { summary ->
            // This observer is triggered *after* summarization is complete.
            // The loading dialog is already hidden by the isSummarizing observer.
            val mail = mailBeingProcessed ?: return@observe // Get the mail we're working on

            if (summary.startsWith("Error:") || summary.startsWith("Network error:") || summary.startsWith("API Error:") || summary.startsWith("Blocked due to")) {
                Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show()
            } else {
                // Success! Show the summary.
                showSummaryDialog(mail, summary)
            }

            mailBeingProcessed = null // Clear the processed mail
        }
        mailViewModel.isGeneratingReply.observe(viewLifecycleOwner) { isGenerating ->
            if (isGenerating) {
                showLoadingDialog("Generating reply...")
            } else {
                dismissLoadingDialog()
            }
        }

        mailViewModel.replyResult.observe(viewLifecycleOwner) { reply ->
            if (reply != null) {
                val originalSender = mailBeingProcessed?.senderEmail
                if (originalSender != null) {
                    // Success! Show the compose dialog with pre-filled data
                    showComposeDialog(originalSender, reply.subject, reply.body)
                }
                mailBeingProcessed = null // Clear
            }
        }

        mailViewModel.replyError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                // Show error toast
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                mailBeingProcessed = null // Clear
            }
        }
    }

    private fun showComposeDialog(recipient: String? = null,
                                  subject: String? = null,
                                  body: String? = null) {
        val user = loginViewModel.loggedInUser.value
        if (user == null) {
            Toast.makeText(requireContext(), "You must be logged in to send mail", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogComposeMailBinding.inflate(layoutInflater)

        recipient?.let { dialogBinding.toEmailEdit.setText(it) }
        subject?.let { dialogBinding.subjectEdit.setText(it) }
        body?.let { dialogBinding.bodyEdit.setText(it) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Send", null) // Will be overridden
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val sendButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                val recipient = dialogBinding.toEmailEdit.text.toString().trim()
                val subject = dialogBinding.subjectEdit.text.toString().trim()
                val body = dialogBinding.bodyEdit.text.toString()

                // --- ADD THIS VALIDATION ---
                if (recipient.isEmpty()) {
                    Toast.makeText(requireContext(), "Recipient email cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (subject.isEmpty()) {
                    Toast.makeText(requireContext(), "Subject cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // --- END OF ADDITION ---

                // Observe sendMailStatus for this specific send action
                val statusObserver = object : Observer<String?> {
                    override fun onChanged(errorMessage: String?) {
                        if (errorMessage != null) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                        } else {
                            dialog.dismiss() // Only dismiss on success
                        }
                        // Remove observer after handling to prevent multiple triggers
                        mailViewModel.sendMailStatus.removeObserver(this)
                    }
                }
                mailViewModel.sendMailStatus.observe(viewLifecycleOwner, statusObserver)

                // Trigger the send mail action
                mailViewModel.sendMail(user.email, recipient, subject, body)
            }
            val cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            cancelButton.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }

        dialog.show()
    }

    /**
     * This is the updated function that shows the custom dialog.
     */
    private fun showMailOptionsDialog(mail: Mail) {
        // Inflate the custom layout
        val dialogBinding = DialogMailOptionsBinding.inflate(layoutInflater)

        // Set the mail subject as the title
        dialogBinding.mailOptionsTitle.text = mail.subject.ifEmpty { "(No Subject)" }

        // Create the dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .create()

        // Set click listeners for the buttons
        dialogBinding.buttonRead.setOnClickListener {
            showMailDetailsDialog(mail)
            dialog.dismiss()
        }

        dialogBinding.buttonReplyAi.setOnClickListener {
            mailBeingProcessed = mail // Store the mail
            mailViewModel.generateReply(mail) // Call the new function
            dialog.dismiss()
        }

        dialogBinding.buttonSummarize.setOnClickListener {
            mailBeingProcessed = mail // Store the mail
            mailViewModel.summarizeMail(mail.body) // Start the API call
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * This function is now called by the "Read" option.
     */
    private fun showMailDetailsDialog(mail: Mail) {
        val title = mail.subject.ifEmpty { "(No Subject)" }
        val from = mail.senderEmail
        val to = mail.recipientEmail
        val body = mail.body

        val message = "From: $from\nTo: $to\n\n$body"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
    private fun showSummaryDialog(mail: Mail, summary: String) {
        val title = mail.subject.ifEmpty { "(No Subject)" }
        val from = mail.senderEmail
        val to = mail.recipientEmail

        // Format the message with the summary
        val message = "From: $from\nTo: $to\n\n--- AI Summary ---\n\n$summary"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    /**
     * Shows a simple, non-cancelable loading dialog.
     */
    private fun showLoadingDialog(message: String) {
        if (loadingDialog != null && loadingDialog!!.isShowing) {
            return // Don't show multiple
        }
        loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setCancelable(false)
            .show()
    }

    /**
     * Dismisses the loading dialog.
     */
    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}