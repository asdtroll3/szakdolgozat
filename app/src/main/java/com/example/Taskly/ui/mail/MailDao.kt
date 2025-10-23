package com.example.Taskly.ui.mail

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MailDao {
    @Insert
    suspend fun insertMail(mail: Mail)

    @Delete
    suspend fun deleteMail(mail: Mail)

    @Query("UPDATE mail_table SET isDeletedByRecipient = 1 WHERE id = :mailId")
    suspend fun markAsDeletedByRecipient(mailId: Int)

    @Query("SELECT * FROM mail_table WHERE recipientEmail = :email COLLATE NOCASE AND isDeletedByRecipient = 0 ORDER BY timestamp DESC")
    suspend fun getInbox(email: String): List<Mail>

    @Query("SELECT * FROM mail_table WHERE senderEmail = :email COLLATE NOCASE ORDER BY timestamp DESC")
    suspend fun getSentItems(email: String): List<Mail>
}