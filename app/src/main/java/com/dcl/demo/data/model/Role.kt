package com.dcl.demo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_role")
data class Role(
    @PrimaryKey(autoGenerate = false) val roleId: Int = 0,
    val role_name: String
)