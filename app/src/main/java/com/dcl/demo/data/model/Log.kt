package com.dcl.demo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "event_log",
        foreignKeys = [ForeignKey(
        entity = UserData::class,
        parentColumns = ["userId"],
        childColumns = ["user_id"]
    )])
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Long,
    val typeEvent: String,
    val event: String,
    val generatedAt: Long = System.currentTimeMillis()
)