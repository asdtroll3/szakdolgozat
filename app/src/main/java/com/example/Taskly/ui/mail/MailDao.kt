package com.example.Taskly.ui.mail

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MailDao {
    @Insert
    suspend fun insertMail(mail: Mail)

    @Query("SELECT * FROM mail_table WHERE recipientEmail = :email COLLATE NOCASE ORDER BY timestamp DESC")
    suspend fun getInbox(email: String): List<Mail>

    @Query("SELECT * FROM mail_table WHERE senderEmail = :email COLLATE NOCASE ORDER BY timestamp DESC")
    suspend fun getSentItems(email: String): List<Mail>
}