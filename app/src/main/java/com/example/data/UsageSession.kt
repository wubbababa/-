package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_sessions")
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val unlockTime: Long,                // timestamp in millis when screen unlocked
    val lockTime: Long = 0,              // timestamp in millis when screen locked, 0 if active
    val durationSeconds: Long = 0,       // usage duration in seconds
    val warned: Boolean = false          // whether they were warned during this session
)
