package com.reminds.characters.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ユーザーが書いたメモ本文 */
    val memo: String,
    /** リマインド予定時刻（エポックミリ秒） */
    val remindAtMillis: Long,
    /** 締切（営業時間の終わりなど）。無ければnull */
    val deadlineMillis: Long? = null,
    /** キャラが通知で話すセリフ */
    val bubbleMessage: String,
    val done: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val remindAt: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(remindAtMillis), ZoneId.systemDefault())

    val deadline: LocalDateTime?
        get() = deadlineMillis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
}

fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
