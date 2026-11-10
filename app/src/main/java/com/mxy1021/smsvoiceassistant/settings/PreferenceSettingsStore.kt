package com.mxy1021.smsvoiceassistant.settings

import android.content.SharedPreferences

class PreferenceSettingsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun boolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun string(key: String) = prefs.getString(key, null)
    override fun int(key: String, default: Int) = prefs.getInt(key, default)
    override fun write(values: Map<String, Any?>) {
        val edit = prefs.edit()
        values.forEach { (key, value) -> when (value) {
            null -> edit.remove(key)
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
        } }
        // Memory is updated synchronously; disk IO is scheduled by SharedPreferences.
        edit.apply()
    }
}
