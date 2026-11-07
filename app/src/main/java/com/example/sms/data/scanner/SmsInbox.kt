package com.example.sms.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sms.data.parser.SmsFilter
import com.example.sms.util.BankUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک پیامک از صندوق ورودی، برای انتخاب «سرشماره» از میان پیام‌ها. */
data class InboxMessage(
    val address: String,
    val body: String,
    val date: Long,
    /** نام بانکی که تراز از شماره یا متن حدس می‌زند؛ «بانک نامشخص» اگر حدسی نباشد. */
    val bankGuess: String,
    /** متن شبیه پیامک بانکی است (واژه‌هایی مثل برداشت، واریز، مانده). */
    val looksBank: Boolean
)

/**
 * فهرست پیامک‌های اخیر گوشی. فقط روی همین گوشی و فقط وقتی کاربر صفحهٔ انتخاب فرستنده را
 * باز کرده خوانده می‌شود. رمزهای یک‌بارمصرف از فهرست حذف می‌شوند تا هرگز روی صفحه نیایند.
 */
class SmsInbox(private val context: Context) {

    suspend fun recent(limit: Int = 400, query: String = ""): List<InboxMessage> = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext emptyList()

        val q = SmsFilter.normalize(query).trim()
        val out = ArrayList<InboxMessage>()
        try {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null, null, "date DESC"
            )?.use { c ->
                val a = c.getColumnIndex("address")
                val b = c.getColumnIndex("body")
                val d = c.getColumnIndex("date")
                if (a < 0 || b < 0) return@use
                while (c.moveToNext() && out.size < limit) {
                    val address = c.getString(a)?.trim().orEmpty()
                    val body = c.getString(b).orEmpty()
                    if (address.isEmpty() || body.isBlank()) continue
                    if (SmsFilter.isOneTimeCode(body)) continue
                    if (q.isNotEmpty() && !SmsFilter.normalize(body).contains(q) && !SmsFilter.normalize(address).contains(q)) continue
                    out += InboxMessage(
                        address = address,
                        body = body,
                        date = if (d >= 0) c.getLong(d) else 0L,
                        bankGuess = BankUtils.detectBankName(address, body),
                        looksBank = BankUtils.isBankSms(body)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsInbox", "Could not read the SMS inbox", e)
        }
        out
    }
}
