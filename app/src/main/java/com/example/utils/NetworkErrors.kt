package com.example.utils

/**
 * One Persian sentence for whatever a network call threw.
 *
 * The app's UI is entirely Persian and right-to-left. Letting a raw
 * exception message through puts a line like
 * "Failed to connect to dezhcode.pyho.ir/1.2.3.4:443" in front of the user,
 * which tells them nothing and reads as a crash. Every network failure the
 * user can see goes through here.
 */
object NetworkErrors {

    fun message(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> httpMessage(e.code())
        is java.net.SocketTimeoutException ->
            "زمان پاسخ‌دهی سرور به پایان رسید. لطفاً مجدداً تلاش کنید."
        is java.net.UnknownHostException ->
            "اتصال به اینترنت برقرار نیست یا آدرس سرور در دسترس نمی‌باشد."
        is java.net.ConnectException ->
            "ارتباط با سرور برقرار نشد. لطفاً بعداً تلاش کنید."
        is javax.net.ssl.SSLException ->
            "ارتباط امن با سرور برقرار نشد. لطفاً از اتصال اینترنت خود مطمئن شوید."
        is java.io.IOException ->
            "خطا در برقراری ارتباط با سرور. اتصال اینترنت خود را بررسی کنید."
        else -> "خطای غیرمنتظره در ارتباط با سرور."
    }

    private fun httpMessage(code: Int): String = when (code) {
        401, 403 -> "دسترسی شما به سرور تأیید نشد. لطفاً دوباره وارد شوید."
        404 -> "مسیر سرویس روی سرور یافت نشد (خطای ۴۰۴)."
        429 -> "تعداد درخواست‌ها بیش از حد مجاز است. کمی بعد تلاش کنید."
        in 500..599 -> "سرور در حال حاضر پاسخ‌گو نیست (کد $code). لطفاً بعداً تلاش کنید."
        else -> "خطای ارتباط با سرور (کد $code)."
    }
}
