package com.example.data

import android.content.Context

/**
 * کلید اصلی پیامک‌خوان. تا کاربر آن را روشن نکند (و اجازهٔ پیامک نداده باشد)،
 * گیرندهٔ پیامک هیچ پیامکی را نمی‌خواند، حتی اگر فرستنده‌ای ثبت شده باشد.
 */
object SmsPrefs {
    private const val FILE = "taraz_sms"
    private const val KEY_ENABLED = "reader_enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
