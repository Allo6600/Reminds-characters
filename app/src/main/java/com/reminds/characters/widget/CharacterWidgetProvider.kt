package com.reminds.characters.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.reminds.characters.MainActivity
import com.reminds.characters.R
import com.reminds.characters.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * ホーム画面に常駐するキャラ「リマ」のウィジェット。
 * 吹き出しに次のタスクを表示し、時間が来るとセリフが催促に変わる。
 * タップでアプリ（メモ入力）が開く。
 */
class CharacterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val timeFormat = DateTimeFormatter.ofPattern("H:mm")

        /** タスクの追加・完了・時刻変更・リマインド発火のたびに呼んで吹き出しを更新する */
        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch { updateAllNow(context) }
        }

        suspend fun updateAllNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CharacterWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context))
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_character)
            views.setTextViewText(R.id.widget_bubble, bubbleText(context))

            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }

        private suspend fun bubbleText(context: Context): CharSequence {
            val next = TaskDatabase.get(context).taskDao().nextPending()
                ?: return context.getString(R.string.widget_empty)

            // 時間が来ていたら催促のセリフ、まだなら予告
            return if (next.remindAtMillis <= System.currentTimeMillis()) {
                next.bubbleMessage
            } else {
                val base = context.getString(
                    R.string.widget_next,
                    next.memo,
                    next.remindAt.format(timeFormat),
                )
                val deadline = next.deadline?.let {
                    context.getString(R.string.widget_deadline_suffix, it.format(timeFormat))
                } ?: ""
                base + deadline
            }
        }
    }
}
