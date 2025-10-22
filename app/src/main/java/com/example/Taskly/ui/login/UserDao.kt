package com.example.Taskly.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.Taskly.ui.login.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM user_table WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findUserByEmail(email: String): User?

    // Add "COLLATE NOCASE" to the query
    @Query("SELECT * FROM user_table WHERE email = :email COLLATE NOCASE AND password = :password LIMIT 1")
    suspend fun findUserByEmailAndPassword(email: String, password: String): User?
}