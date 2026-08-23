package com.example.focus.focus

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.local.FocusSessionEntity
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * singleton owner of focus session state and expiration alarms
 * we separate this from the view model since multiple can be created (why? I have no idea), but we really don't
 * want to duplicate this logic.
 */
class FocusSessionManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.create(appContext).appDao()
    private val repository = FocusSessionRepository(dao)
    private val scheduler = FocusSessionAlarmScheduler(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifications = FocusSessionNotificationManager(appContext)
    private var expirationJob: Job? = null

    val active: Flow<FocusSessionEntity?> = dao.observeActiveFocusSession()
    val automaticallyEnd = appContext.focusDataStore.data
        .map { it[SettingsKeys.automaticallyEndFocusSessions] ?: true }

    init {
        scope.launch {
            combine(active, automaticallyEnd) { session, enabled -> session to enabled }
                .distinctUntilChanged()
                .collectLatest { (session, enabled) ->
                    if (session == null) notifications.cancelActiveSession()
                    else notifications.showActiveSession(session)
                    expirationJob?.cancel()
                    if (session == null) return@collectLatest
                    val expiration = session.startedAtMillis + session.plannedDurationMillis
                    if (enabled && expiration <= System.currentTimeMillis()) {
                        Log.d("FocusAccessibilityService", "Focus session ${session.id} has expired, stopping it")
                        repository.stopIfActive(session.id)
                    } else if (enabled) {
                        Log.d("FocusAccessibilityService", "Scheduling focus session expiration for ${session.id} in ${expiration - System.currentTimeMillis()} ms")
                        scheduler.schedule(session.id, expiration)
                        // AlarmManager is inaccurate for short durations, so keep our own timer in case it doesn't fire
                        // in time. it's a bit of a hack, but it works.
                        expirationJob = scope.launch {
                            delay((expiration - System.currentTimeMillis()).coerceAtLeast(0L).milliseconds)
                            repository.stopIfActive(session.id)
                            scheduler.cancel(session.id)
                        }
                    } else {
                        Log.d("FocusAccessibilityService", "Automatically ending focus sessions is disabled, cancelling any scheduled expiration for ${session.id}")
                        scheduler.cancel(session.id)
                    }
                }
        }
    }

    suspend fun start(durationMillis: Long) {
        if (repository.activeSession() == null) repository.start(durationMillis)
    }

    suspend fun stop() {
        expirationJob?.cancel()
        repository.activeSession()?.let { session ->
            scheduler.cancel(session.id)
            repository.stopIfActive(session.id)
        }
    }

    suspend fun setAutomaticallyEnd(enabled: Boolean) {
        appContext.focusDataStore.edit { it[SettingsKeys.automaticallyEndFocusSessions] = enabled }
    }

    companion object {
        @Volatile private var instance: FocusSessionManager? = null
        fun get(context: Context): FocusSessionManager = instance ?: synchronized(this) {
            instance ?: FocusSessionManager(context).also { instance = it }
        }
    }
}
