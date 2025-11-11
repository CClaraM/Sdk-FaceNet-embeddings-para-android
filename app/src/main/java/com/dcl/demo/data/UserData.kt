package com.dcl.demo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dcl.demo.data.model.UserData

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserData)

    @Query("SELECT * FROM user_data WHERE userId = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserData?

    @Query("SELECT * FROM user_data")
    suspend fun getAllUsers(): List<UserData>
}
