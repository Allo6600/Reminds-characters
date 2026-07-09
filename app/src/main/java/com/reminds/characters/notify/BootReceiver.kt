package com.reminds.characters.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminds.characters.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 再起動後にまだ鳴っていないリマインドを予約し直す */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskDatabase.get(context).taskDao()
                    .pendingReminders(System.currentTimeMillis())
                    .forEach { ReminderScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
