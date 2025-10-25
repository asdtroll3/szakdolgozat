package com.example.Taskly.ui.mail

import android.content.DialogInterface
import androidx.lifecycle.Observer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var currentTabPosition = 0

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
                if (currentTabPosition == 0) {
                    showMailOptionsDialog(mail)
                } else {
                    showMailDetailsDialog(mail)
                }
            },
            isSentbox = { currentTabPosition == 1 }
        )
        binding.mailRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mailAdapter
        }

        val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (currentTabPosition == 1) {
                    return 0
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                val position = viewHolder.adapterPosition
                val mail = mailAdapter.currentList[position]

                MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogTheme)
                    .setTitle("Delete Mail")
                    .setMessage("Are you sure you want to delete this mail?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        mailAdapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .setPositiveButton("Delete") { dialog, _ ->
                        mailViewModel.deleteMail(mail)
                        Toast.makeText(requireContext(), "Mail deleted", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.mailRecyclerView)
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
        val mails = if (currentTabPosition == 0) {
            mailViewModel.inbox.value
        } else {
            mailViewModel.sent.value
        }
        mailAdapter.submitList(mails)

        binding.mailRecyclerView.scrollToPosition(0)
    }


    private fun setupFab() {
        binding.fabComposeMail.setOnClickListener {
            showComposeDialog()
        }
    }

    private fun observeViewModels() {
        loginViewModel.loggedInUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.textMailInfo.visibility = View.GONE
                binding.mailRecyclerView.visibility = View.VISIBLE
                binding.fabComposeMail.visibility = View.VISIBLE

                mailViewModel.loadInbox(user.email)
                mailViewModel.loadSentItems(user.email)
            } else {

                //binding.textMailInfo.setText(R.string.title_mail)
                binding.textMailInfo.visibility = View.VISIBLE
                binding.mailRecyclerView.visibility = View.GONE
                binding.fabComposeMail.visibility = View.GONE

                mailViewModel.clearMail()
                mailAdapter.submitList(emptyList())
            }
        }

        mailViewModel.inbox.observe(viewLifecycleOwner) { inboxMails ->
            if (currentTabPosition == 0) {
                mailAdapter.submitList(inboxMails)
            }
        }

        mailViewModel.sent.observe(viewLifecycleOwner) { sentMails ->
            if (currentTabPosition == 1) {
                mailAdapter.submitList(sentMails)
            }
        }

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
            val mail = mailBeingProcessed ?: return@observe

            if (summary.startsWith("Error:") || summary.startsWith("Network error:") || summary.startsWith("API Error:") || summary.startsWith("Blocked due to")) {
                Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show()
            } else {
                showSummaryDialog(mail, summary)
            }

            mailBeingProcessed = null
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
                    showComposeDialog(originalSender, reply.subject, reply.body)
                }
                mailBeingProcessed = null
            }
        }

        mailViewModel.replyError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                mailBeingProcessed = null
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
            .setPositiveButton("Send", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val sendButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                val recipient = dialogBinding.toEmailEdit.text.toString().trim()
                val subject = dialogBinding.subjectEdit.text.toString().trim()
                val body = dialogBinding.bodyEdit.text.toString()

                if (recipient.isEmpty()) {
                    Toast.makeText(requireContext(), "Recipient email cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (subject.isEmpty()) {
                    Toast.makeText(requireContext(), "Subject cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val statusObserver = object : Observer<String?> {
                    override fun onChanged(errorMessage: String?) {
                        if (errorMessage != null) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                        } else {
                            dialog.dismiss()
                        }
                        mailViewModel.sendMailStatus.removeObserver(this)
                    }
                }
                mailViewModel.sendMailStatus.observe(viewLifecycleOwner, statusObserver)

                mailViewModel.sendMail(user.email, recipient, subject, body)
            }
            val cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            cancelButton.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }

        dialog.show()
    }


    private fun showMailOptionsDialog(mail: Mail) {
        val dialogBinding = DialogMailOptionsBinding.inflate(layoutInflater)

        dialogBinding.mailOptionsTitle.text = "Subject: ${mail.subject.ifEmpty { "(No Subject)" }}"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .create()

        dialogBinding.buttonRead.setOnClickListener {
            mailViewModel.markMailAsRead(mail)
            showMailDetailsDialog(mail)
            dialog.dismiss()
        }

        dialogBinding.buttonReplyAi.setOnClickListener {
            mailViewModel.markMailAsRead(mail)
            mailBeingProcessed = mail
            mailViewModel.generateReply(mail)
            dialog.dismiss()
        }

        dialogBinding.buttonSummarize.setOnClickListener {
            mailViewModel.markMailAsRead(mail)
            mailBeingProcessed = mail
            mailViewModel.summarizeMail(mail.body)
            dialog.dismiss()
        }

        dialog.show()
    }

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

        val message = "From: $from\nTo: $to\n\n--- AI Summary ---\n\n$summary"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showLoadingDialog(message: String) {
        if (loadingDialog != null && loadingDialog!!.isShowing) {
            return
        }
        loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setCancelable(false)
            .show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}