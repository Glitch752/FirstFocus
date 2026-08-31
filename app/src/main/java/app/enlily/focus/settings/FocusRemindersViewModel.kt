package app.enlily.focus.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.enlily.focus.data.local.AppDatabase
import app.enlily.focus.data.local.FocusReminderEntity
import app.enlily.focus.focus.FocusReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusRemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.create(application).appDao()
    private val scheduler = FocusReminderScheduler(application)
    val reminders = dao.observeFocusReminders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set the enabled state of a reminder and saves it to the database */
    fun setEnabled(reminder: FocusReminderEntity, enabled: Boolean) = upsert(reminder.copy(enabled = enabled))

    /** Remove the given reminder from the database and cancels any scheduled notifications for it */
    fun delete(reminder: FocusReminderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteFocusReminder(reminder.id)
            scheduler.reschedule(dao.focusReminders())
        }
    }

    /** Save the given reminder to the database and reschedules notifications */
    fun upsert(reminder: FocusReminderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (reminder.id == 0L) dao.insertFocusReminder(reminder) else dao.upsertFocusReminder(reminder)
            scheduler.reschedule(dao.focusReminders())
        }
    }
}
