package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OTP is requested from — and verified by — the backend.
 *
 * The app never sees the code and never holds an SMS-provider credential.
 * Generation, storage, expiry, rate limiting and comparison all happen server
 * side. The client only forwards the phone number and, later, the digits the
 * user typed.
 */
object OtpService {

    private const val TAG = "OtpService"

    /** Ask the backend to generate a code and send it by SMS. */
    suspend fun requestOtp(
        recipientPhone: String,
        userName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getApiService().requestOtp(
                OtpRequest(phone = normalizePhone(recipientPhone), name = userName)
            )
            if (response.success) {
                Result.success(response.message ?: "کد تأیید به شماره شما ارسال شد.")
            } else {
                Result.failure(Exception(response.error ?: "ارسال کد تأیید ناموفق بود."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestOtp failed", e)
            Result.failure(Exception(networkMessage(e)))
        }
    }

    /**
     * Send the typed code to the backend for verification.
     * Returns the session token issued by the server on success.
     */
    suspend fun verifyOtp(
        recipientPhone: String,
        code: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getApiService().verifyOtp(
                OtpVerifyRequest(phone = normalizePhone(recipientPhone), code = code.trim())
            )
            if (response.success && response.verified) {
                Result.success(response.token ?: "")
            } else {
                Result.failure(Exception(response.error ?: "کد وارد شده نادرست یا منقضی شده است."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp failed", e)
            Result.failure(Exception(networkMessage(e)))
        }
    }

    /** 09xxxxxxxxx form, accepting +98 / 98 prefixes. */
    private fun normalizePhone(raw: String): String {
        var phone = raw.trim().replace(" ", "").replace("-", "")
        if (phone.startsWith("+98")) phone = "0" + phone.substring(3)
        else if (phone.startsWith("98") && phone.length == 12) phone = "0" + phone.substring(2)
        return phone
    }

    private fun networkMessage(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException ->
            "زمان پاسخ‌دهی سرور به پایان رسید. لطفاً مجدداً تلاش کنید."
        is java.net.UnknownHostException ->
            "اتصال به اینترنت برقرار نیست یا آدرس سرور در دسترس نمی‌باشد."
        is java.net.ConnectException ->
            "ارتباط با سرور برقرار نشد. لطفاً بعداً تلاش کنید."
        else -> e.localizedMessage ?: "خطای غیرمنتظره در ارتباط با سرور."
    }
}
