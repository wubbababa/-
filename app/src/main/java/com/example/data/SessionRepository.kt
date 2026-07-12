package com.example.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: UsageSessionDao) {
    val allSessions: Flow<List<UsageSession>> = dao.getAllSessions()

    suspend fun insertSession(session: UsageSession): Long {
        return dao.insertSession(session)
    }

    suspend fun updateSession(session: UsageSession) {
        dao.updateSession(session)
    }

    suspend fun getLatestSession(): UsageSession? {
        return dao.getLatestSession()
    }

    suspend fun deleteAllSessions() {
        dao.deleteAllSessions()
    }
}
