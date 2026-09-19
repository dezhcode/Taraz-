package com.example.utils

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetworkErrors {
    fun message(e: Throwable): String {
        return when (e) {
            is HttpException -> {
                when (e.code()) {
                    401, 403 -> "دسترسی غیرمجاز است. لطفاً مجدداً وارد شوید."
                    404 -> "سرویس یا مسیر مورد نظر در سرور یافت نشد."
                    429 -> "تعداد درخواست‌ها بیش از حد مجاز است. لطفاً کمی صبر کرده و مجدداً تلاش کنید."
                    in 500..599 -> "سرور با مشکل موقت مواجه شده است. لطفاً دقایقی دیگر امتحان کنید."
                    else -> "خطای ارتباط با سرور (کد خطا: ${e.code()})"
                }
            }
            is SocketTimeoutException -> "مهلت ارتباط با سرور به پایان رسید. لطفاً اتصال اینترنت خود را بررسی کنید."
            is UnknownHostException -> "اتصال به اینترنت برقرار نیست یا آدرس سرور در دسترس نمی‌باشد."
            is ConnectException -> "امکان اتصال به سرور وجود ندارد. لطفاً از اتصال اینترنت خود مطمئن شوید."
            is SSLException -> "خطای امنیتی در برقراری ارتباط با سرور رخ داده است."
            is IOException -> "خطا در تبادل داده با شبکه. لطفاً وضعیت اتصال خود را بررسی کنید."
            else -> e.localizedMessage ?: "خطای غیرمنتظره در ارتباط با شبکه رخ داده است."
        }
    }
}
