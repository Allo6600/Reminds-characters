package com.reminds.characters.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.reminds.characters.RemindsApplication
import com.reminds.characters.ai.SmartScheduler
import com.reminds.characters.data.Task
import com.reminds.characters.data.toEpochMillis
import com.reminds.characters.notify.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as RemindsApplication).database.taskDao()

    val tasks: StateFlow<List<Task>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** メモを解析してタスク登録＋リマインド予約。予約時刻を返す */
    fun addMemo(memo: String, onScheduled: (LocalDateTime) -> Unit) {
        val text = memo.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val result = SmartScheduler.schedule(text)
            val task = Task(
                memo = text,
                remindAtMillis = result.remindAt.toEpochMillis(),
                deadlineMillis = result.deadline?.toEpochMillis(),
                bubbleMessage = result.bubbleMessage,
            )
            val id = dao.insert(task)
            ReminderScheduler.schedule(getApplication(), task.copy(id = id))
            onScheduled(result.remindAt)
        }
    }

    /** リスト上でリマインド時刻を編集する */
    fun updateTime(task: Task, newTime: LocalTime) {
        viewModelScope.launch {
            var newDateTime = LocalDateTime.now().with(newTime)
            if (newDateTime.isBefore(LocalDateTime.now())) {
                newDateTime = newDateTime.plusDays(1)
            }
            val updated = task.copy(remindAtMillis = newDateTime.toEpochMillis())
            dao.update(updated)
            ReminderScheduler.cancel(getApplication(), task.id)
            if (!updated.done) ReminderScheduler.schedule(getApplication(), updated)
        }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch {
            dao.update(task.copy(done = done))
            if (done) {
                ReminderScheduler.cancel(getApplication(), task.id)
            } else if (task.remindAtMillis > System.currentTimeMillis()) {
                ReminderScheduler.schedule(getApplication(), task.copy(done = false))
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
            ReminderScheduler.cancel(getApplication(), task.id)
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return TaskViewModel(application) as T
            }
        }
    }
}
