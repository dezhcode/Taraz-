package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SmsCatchUp

/**
 * هر ۱۵ دقیقه پیامک‌های بانکی تازهٔ صندوق ورودی را می‌خواند و برای تراکنش‌های تازه نوتیفیکیشن می‌دهد؛
 * پشتیبان گیرندهٔ پیامک روی گوشی‌هایی که پیامک تازه را به برنامه اعلام نمی‌کنند.
 */
class SmsCatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        com.example.data.Money.load(applicationContext)
        SmsCatchUp.run(applicationContext, notify = true)
        return Result.success()
    }
}
