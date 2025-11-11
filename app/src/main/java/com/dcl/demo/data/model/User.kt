package com.dcl.demo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "user_data",
    //foreignKeys = [ForeignKey(
    //    entity = Role::class,
    //    parentColumns = ["roleId"],
    //    childColumns = ["role_id"]
    //)]
)
data class UserData(
    @PrimaryKey val userId: Long = 0,
    val name: String,
    val role_Id: Int,
    val embedding: String,   // ✅ Base64 no ByteArray
    val fingerprintCsv: String,     // ❌ CSV ( coma separada ) revisar no recomendado
    val createdAt: Long = System.currentTimeMillis()
)