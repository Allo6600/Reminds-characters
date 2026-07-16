package com.reminds.characters.overlay

import android.content.Context

/** キャラ常駐（オーバーレイ）のON/OFF設定 */
object OverlayPrefs {
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "overlay_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
