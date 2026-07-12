package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions ORDER BY unlockTime DESC")
    fun getAllSessions(): Flow<List<UsageSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UsageSession): Long

    @Update
    suspend fun updateSession(session: UsageSession)

    @Query("SELECT * FROM usage_sessions ORDER BY unlockTime DESC LIMIT 1")
    suspend fun getLatestSession(): UsageSession?

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()
}
