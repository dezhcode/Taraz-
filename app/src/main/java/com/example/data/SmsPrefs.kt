package com.example.data

import android.content.Context

/**
 * کلید اصلی پیامک‌خوان. تا کاربر آن را روشن نکند (و اجازهٔ پیامک نداده باشد)،
 * گیرندهٔ پیامک هیچ پیامکی را نمی‌خواند، حتی اگر فرستنده‌ای ثبت شده باشد.
 */
object SmsPrefs {
    private const val FILE = "taraz_sms"
    private const val KEY_ENABLED = "reader_enabled"
    private const val KEY_ENABLED_AT = "reader_enabled_at"
    private const val KEY_LAST_SCAN = "last_inbox_scan"
    private const val KEY_LAST_BROADCAST = "last_broadcast"
    private const val KEY_SHEET_SEEN = "sheet_seen_max_id"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        val was = enabled(context)
        val edit = prefs(context).edit().putBoolean(KEY_ENABLED, on)
        // لحظهٔ روشن شدن: تراز فقط پیامک‌هایی را می‌خواند که از این به بعد می‌رسند.
        if (on && !was) edit.putLong(KEY_ENABLED_AT, System.currentTimeMillis())
        edit.apply()
    }

    /**
     * آخرین باری که پیامک‌خوان روشن شد؛ ۰ برای کسی که پیش از این نسخه روشنش کرده بود.
     * پیامک‌های پیش از این لحظه خوانده نمی‌شوند: کاربر موجودی حساب‌ها را با همان وضعیت وارد کرده و ثبت
     * دوبارهٔ آن تراکنش‌ها موجودی را دو بار تغییر می‌داد.
     */
    fun enabledAt(context: Context): Long = prefs(context).getLong(KEY_ENABLED_AT, 0L)

    /** زمان (date صندوق ورودی) آخرین پیامکی که بررسی صندوق ورودی دیده؛ ۰ یعنی هنوز هیچ. */
    fun lastInboxScan(context: Context): Long = prefs(context).getLong(KEY_LAST_SCAN, 0L)

    fun setLastInboxScan(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_SCAN, at).apply()
    }

    /** آخرین باری که اندروید پیامک تازه را مستقیم به تراز داد؛ ۰ یعنی هرگز (روی بعضی گوشی‌ها مسدود است). */
    fun lastBroadcast(context: Context): Long = prefs(context).getLong(KEY_LAST_BROADCAST, 0L)

    fun markBroadcast(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_BROADCAST, System.currentTimeMillis()).apply()
    }

    /** بزرگ‌ترین شناسهٔ تراکنش در انتظاری که کاربر در شیت دیده و «بعداً» زده؛ شیت تا تراکنش تازه‌تری نیاید باز نمی‌شود. */
    fun sheetSeenMaxId(context: Context): Int = prefs(context).getInt(KEY_SHEET_SEEN, 0)

    fun setSheetSeenMaxId(context: Context, id: Int) {
        if (id > sheetSeenMaxId(context)) prefs(context).edit().putInt(KEY_SHEET_SEEN, id).apply()
    }
}
