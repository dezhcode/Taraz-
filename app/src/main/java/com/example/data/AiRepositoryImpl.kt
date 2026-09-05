package com.example.data

import android.content.Context
import android.util.Log
import com.example.services.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AiRepositoryImpl(private val context: Context) : AiRepository {

    init {
        // Initialize central ApiConfig when the repository is created
        ApiConfig.initialize(context)
    }

    override suspend fun healthCheck(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiService = com.example.data.remote.ApiClient.getApiService()
            val response = apiService.healthCheck()
            if (response.status == "online" || response.service != null) {
                Result.success(response.service ?: "سرور آنلاین است")
            } else {
                Result.success("ارتباط با سرور برقرار شد")
            }
        } catch (e: Exception) {
            Log.e("AiRepositoryImpl", "Health check failed", e)
            Result.failure(e)
        }
    }

    override suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiService = com.example.data.remote.ApiClient.getApiService()
            val request = com.example.data.remote.ChatRequest(message = message)
            val response = apiService.chat(request)
            if (response.success && response.answer != null) {
                Result.success(response.answer)
            } else {
                val errorMessage = response.error ?: "پاسخ ناموفق از سرور دریافت شد"
                Result.failure(Exception("خطا در پاسخ سرور: $errorMessage"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("AiRepositoryImpl", "Network timeout calling FastAPI", e)
            Result.failure(Exception("خطای زمان پاسخ‌دهی (Timeout): اتصال به سرور هوش مصنوعی برقرار نشد یا سرور بیش از حد شلوغ است. لطفا مجدداً تلاش کنید."))
        } catch (e: java.net.UnknownHostException) {
            Log.e("AiRepositoryImpl", "Host resolution failed calling FastAPI", e)
            Result.failure(Exception("خطای عدم اتصال به شبکه: آدرس سرور یافت نشد. لطفا از فعال بودن اتصال اینترنت خود مطمئن شوید."))
        } catch (e: java.net.ConnectException) {
            Log.e("AiRepositoryImpl", "Connection refused calling FastAPI", e)
            Result.failure(Exception("خطای ارتباط با سرور: اتصال با سرور هوش مصنوعی برقرار نشد. احتمالاً سرور خاموش است یا دسترسی به آن محدود شده است."))
        } catch (e: Exception) {
            Log.e("AiRepositoryImpl", "FastAPI chat call failed with unexpected error", e)
            Result.failure(Exception("خطای غیرمنتظره در ارتباط با دستیار هوش مصنوعی: ${e.localizedMessage ?: "مجدداً تلاش فرمایید"}"))
        }
    }

    override suspend fun calculateFinancialHealth(
        transactions: List<Transaction>,
        cards: List<BankCard>,
        loans: List<Loan>,
        activeGoal: FinancialGoal?
    ): Result<FinancialHealthResult> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty() && cards.isEmpty() && loans.isEmpty()) {
            return@withContext Result.success(
                FinancialHealthResult(
                    score = 0,
                    status = "no_data",
                    recommendation = "اطلاعات مالی بیشتری ثبت کنید"
                )
            )
        }

        val income = transactions.filter { !it.isExpense }.sumOf { it.amount }
        val expense = transactions.filter { it.isExpense }.sumOf { it.amount }
        val balance = cards.sumOf { it.balance }
        val totalLoanDebt = loans.sumOf { it.totalAmount - it.paidAmount }
        val goalInfo = activeGoal?.let { "عنوان: ${it.title} (هدف: ${it.targetAmount} تومان، ذخیره‌شده: ${it.currentAmount} تومان)" } ?: "بدون هدف فعال"

        val prompt = """
            شما یک دستیار هوشمند ارزیابی سلامت مالی در اپلیکیشن تراز هستید.
            بر اساس داده‌های واقعی زیر که از دیتابیس SQLite کاربر واکشی شده‌اند، شاخص سلامت مالی را محاسبه و تحلیل کن:
            - درآمد ماه جاری: $income تومان
            - هزینه ماه جاری: $expense تومان
            - تعداد کل تراکنش‌ها: ${transactions.size}
            - موجودی کل کارت‌ها: $balance تومان
            - بدهی کل وام‌ها: $totalLoanDebt تومان
            - وضعیت هدف مالی: $goalInfo

            لطفاً سلامت مالی کاربر را با یک نمره عددی (score بین 20 تا 100)، یک وضعیت (status: یکی از "excellent" یا "good" یا "needs_improvement") و یک توصیه خلاصه فارسی (recommendation) برگردان.
            خروجی را دقیقا و صرفا به صورت فرمت JSON زیر بده و هیچ متن اضافه یا کد بلاک Markdown ننویس:
            {
              "score": 85,
              "status": "excellent",
              "recommendation": "عملکرد مالی شما بسیار عالی است"
            }
        """.trimIndent()

        try {
            val chatResult = chat(prompt).getOrThrow()
            var cleaned = chatResult.trim()
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substringAfter("```json").substringAfter("```")
                cleaned = cleaned.substringBeforeLast("```").trim()
            }
            val jsonObj = org.json.JSONObject(cleaned)
            val score = jsonObj.getInt("score").coerceIn(0, 100)
            val status = jsonObj.optString("status", "good")
            val rec = jsonObj.optString("recommendation", "مدیریت مالی خود را بر اساس بودجه ادامه دهید")

            Result.success(
                FinancialHealthResult(
                    score = score,
                    status = status,
                    recommendation = rec
                )
            )
        } catch (e: Exception) {
            Log.e("AiRepositoryImpl", "AI Financial Health evaluation failed, using fallback score", e)
            var baseScore = 100
            if (expense > income && income > 0) baseScore -= 25
            if (totalLoanDebt > balance && balance > 0) baseScore -= 20
            if (income > expense && income > 0) {
                val savingsRate = ((income - expense).toDouble() / income).coerceIn(0.0, 1.0)
                baseScore += (savingsRate * 15).toInt()
            }
            val finalScore = baseScore.coerceIn(20, 100)
            val (status, rec) = when {
                finalScore >= 80 -> Pair("excellent", "عالی")
                finalScore >= 60 -> Pair("good", "خوب")
                else -> Pair("needs_improvement", "نیاز به بهبود")
            }
            Result.success(
                FinancialHealthResult(
                    score = finalScore,
                    status = status,
                    recommendation = rec
                )
            )
        }
    }

    override suspend fun getFinancialCoachInsight(
        transactions: List<Transaction>,
        todayExpense: Long,
        dailyBudget: Long,
        cards: List<BankCard>,
        loans: List<Loan>,
        activeGoal: FinancialGoal?
    ): Result<FinancialCoachInsight> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            return@withContext Result.success(
                FinancialCoachInsight(
                    id = "empty_001",
                    type = "empty",
                    priority = 0,
                    message = "برای تحلیل بهتر، تراکنش‌های بیشتری ثبت کن.",
                    action = null
                )
            )
        }

        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
        val recentExpenses = transactions.filter { it.isExpense && it.date >= sevenDaysAgo }
        val weeklyExpenseSum = recentExpenses.sumOf { it.amount }
        val weeklyAvgDailyExpense = if (recentExpenses.isNotEmpty()) weeklyExpenseSum / 7 else 0L

        val categoryBreakdown = recentExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { tx -> tx.amount } }
            .entries.sortedByDescending { it.value }
            .take(3)
            .joinToString { "${it.key}: ${it.value} تومان" }

        val prompt = """
            شما یک مربی و دستیار هوشمند مالی (AI Financial Coach) در اپلیکیشن تراز هستید.
            بر اساس داده‌های واقعی زیر که از دیتابیس SQLite کاربر دریافت شده‌اند، مهم‌ترین تحلیل یا توصیه شخصی‌سازی‌شده (فقط ۱ یا ۲ جمله کوتاه و کاربردی به فارسی) با تعیین نوع پیام و اولویت برگردان:
            - هزینه امروز: $todayExpense تومان
            - بودجه روزانه تعیین‌شده: $dailyBudget تومان
            - میانگین هزینه روزانه هفته اخیر: $weeklyAvgDailyExpense تومان
            - دسته‌های پرهزینه هفته اخیر: $categoryBreakdown
            - هدف مالی فعال: ${activeGoal?.title ?: "ندارد"}

            انواع پیام (type):
            - "danger" (خطر/عبور از بودجه، اولویت 80 تا 100)
            - "warning" (هشدار/هزینه غیرمعمول، اولویت 60 تا 80)
            - "success" (تشویق/صرفه‌جویی عالی، اولویت 40 تا 60)
            - "suggestion" (پیشنهاد بهبود، اولویت 20 تا 40)

            خروجی را دقیقا و صرفاً به فرمت JSON زیر برگردان و هیچ متن اضافی ننویس:
            {
              "financialInsight": {
                "id": "insight_101",
                "type": "warning",
                "priority": 75,
                "message": "این هفته هزینه خوراک بیشتر از میانگین بوده است.",
                "action": "بررسی هزینه‌ها"
              }
            }
        """.trimIndent()

        try {
            val chatResult = chat(prompt).getOrThrow()
            var cleaned = chatResult.trim()
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substringAfter("```json").substringAfter("```")
                cleaned = cleaned.substringBeforeLast("```").trim()
            }
            val jsonObj = org.json.JSONObject(cleaned)
            val insightObj = jsonObj.optJSONObject("financialInsight") ?: jsonObj
            val id = insightObj.optString("id", "insight_${System.currentTimeMillis()}")
            val type = insightObj.optString("type", "suggestion")
            val priority = insightObj.optInt("priority", 50)
            val message = insightObj.optString("message", "مدیریت مالی خود را بر اساس بودجه ادامه دهید.")
            val rawAction = insightObj.optString("action", "")
            val action = if (rawAction.isNotBlank() && rawAction != "null") rawAction else null

            Result.success(
                FinancialCoachInsight(
                    id = id,
                    type = type,
                    priority = priority,
                    message = message,
                    action = action
                )
            )
        } catch (e: Exception) {
            Log.e("AiRepositoryImpl", "AI Financial Coach evaluation failed, using fallback", e)
            val type: String
            val priority: Int
            val message: String
            val action: String?

            if (dailyBudget > 0 && todayExpense > dailyBudget) {
                type = "danger"
                priority = 90
                message = "امروز از بودجه مجاز عبور کرده‌اید! خریدهای غیرضروری را محدود کنید."
                action = "مدیریت بودجه"
            } else if (dailyBudget > 0 && todayExpense >= (dailyBudget * 0.8)) {
                type = "warning"
                priority = 75
                message = "نزدیک به سقف بودجه روزانه هستید. خرج‌های امروز را کنترل کنید."
                action = "مشاهده بودجه"
            } else if (todayExpense > 0 && dailyBudget > 0 && todayExpense < (dailyBudget * 0.5)) {
                type = "success"
                priority = 50
                message = "امروز خرجت کمتر از میانگین بوده، عالی ادامه بده."
                action = "ادامه روند"
            } else {
                type = "suggestion"
                priority = 30
                message = "با ثبت منظم تراکنش‌ها، پیشنهادهای هوشمندتر دریافت کنید."
                action = null
            }

            Result.success(
                FinancialCoachInsight(
                    id = "fallback_${System.currentTimeMillis()}",
                    type = type,
                    priority = priority,
                    message = message,
                    action = action
                )
            )
        }
    }

    override fun getBaseUrl(): String {
        return ApiConfig.getBaseUrl()
    }

    override fun setBaseUrl(url: String) {
        ApiConfig.setBaseUrl(context, url)
    }
}
