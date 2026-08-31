package app.enlily.focus.focus

import app.enlily.focus.data.local.AppDao
import app.enlily.focus.data.local.FocusSessionEntity
import app.enlily.focus.data.local.FocusSessionStatus
import kotlinx.coroutines.flow.Flow

class FocusSessionRepository(private val dao: AppDao) {
    fun observeActive(): Flow<FocusSessionEntity?> = dao.observeActiveFocusSession()

    /** Returns the active focus session, if any */
    suspend fun activeSession(): FocusSessionEntity? = dao.activeFocusSession()

    /** Start a new focus session with the given planned duration and returns its ID */
    suspend fun start(durationMillis: Long): Long = dao.insertFocusSession(
        FocusSessionEntity(
            startedAtMillis = System.currentTimeMillis(),
            endedAtMillis = null,
            plannedDurationMillis = durationMillis,
            status = FocusSessionStatus.ACTIVE
        )
    )

    /** Stop the focus session with the given ID if it is currently active */
    suspend fun stopIfActive(id: Long) {
        // set it to cancelled if we're stopping before its planned duration
        val session = activeSession() ?: return
        if (session.id != id) return

        // small margin in case the alarm fires a bit early
        val status = if (System.currentTimeMillis() < session.startedAtMillis + session.plannedDurationMillis - 5000L) {
            FocusSessionStatus.CANCELLED
        } else {
            FocusSessionStatus.COMPLETED
        }
        dao.completeFocusSessionIfActive(id, System.currentTimeMillis(), status)
    }
}