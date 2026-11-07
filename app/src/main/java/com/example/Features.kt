package com.example

/**
 * کلید قابلیت‌ها برای نسخهٔ انتشار.
 * دستیار هوش مصنوعی و ثبت با صدا فعلاً خاموش‌اند و در برنامه «به زودی» نوشته می‌شوند.
 * خواندن پیامک بانک روشن است؛ مجوزهای READ_SMS / RECEIVE_SMS در مانیفست هستند، ولی تا کاربر
 * «پیامک‌خوان» را روشن نکند هیچ پیامکی خوانده نمی‌شود (SmsPrefs).
 */
object Features {
    const val AI = false
    const val VOICE = false
    const val SMS = true
    /** ورود با شمارهٔ موبایل و کد پیامکی؛ خاموش یعنی فقط با نام وارد می‌شوید و داده‌ها روی گوشی می‌ماند */
    const val LOGIN = false

    const val SOON = "به‌زودی"
}
