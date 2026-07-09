package com.reminds.characters

import android.app.Application
import com.reminds.characters.data.TaskDatabase

class RemindsApplication : Application() {
    val database: TaskDatabase by lazy { TaskDatabase.get(this) }
}
