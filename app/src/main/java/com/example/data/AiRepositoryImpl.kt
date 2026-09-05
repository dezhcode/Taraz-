package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.remote.ApiClient
import com.example.data.remote.ChatRequest
import com.example.data.remote.CopilotDirectEngine
import com.example.services.ApiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepositoryImpl(private val context: Context) : AiRepository {

    init {
        // Initialize central ApiConfig when the repository is created
        ApiConfig.initialize(context)
    }

    override suspend fun healthCheck(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService()
            val response = apiService.healthCheck()
            if (response.status == "online" || response.service != null) {
                Result.success(response.service ?: "سرور آنلاین است")
            } else {
                Result.success("ارتباط با سرور برقرار شد")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AiRepositoryImpl", "Backend health check failed (${e.localizedMessage}), testing Copilot Native engine...")
            val directTest = CopilotDirectEngine.askCopilot("تست")
            if (directTest.isSuccess) {
                Result.success("سرور کوپایلوت فعال است")
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        // Step 1: Try the configured backend server (e.g. FastAPI)
        try {
            val apiService = ApiClient.getApiService()
            val request = ChatRequest(message = message)
            val response = apiService.chat(request)
            if (response.success && response.answer != null) {
                return@withContext Result.success(response.answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: retrofit2.HttpException) {
            Log.w("AiRepositoryImpl", "Backend returned HTTP ${e.code()}, falling back to Copilot Native engine")
        } catch (e: java.net.ConnectException) {
            Log.w("AiRepositoryImpl", "Backend connection refused, falling back to Copilot Native engine")
        } catch (e: java.net.UnknownHostException) {
            Log.w("AiRepositoryImpl", "Backend host resolution failed, falling back to Copilot Native engine")
        } catch (e: java.net.SocketTimeoutException) {
            Log.w("AiRepositoryImpl", "Backend timeout, falling back to Copilot Native engine")
        } catch (e: Exception) {
            Log.w("AiRepositoryImpl", "Backend chat failed (${e.localizedMessage}), falling back to Copilot Native engine")
        }

        // Step 2: Fallback seamlessly to Copilot Native engine
        try {
            val copilotResult = CopilotDirectEngine.askCopilot(message)
            if (copilotResult.isSuccess) {
                return@withContext copilotResult
            }
            val copilotError = copilotResult.exceptionOrNull()?.localizedMessage ?: "پاسخی از هوش مصنوعی دریافت نشد"
            Result.failure(Exception("خطا در ارتباط با دستیار هوش مصنوعی: $copilotError"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AiRepositoryImpl", "Copilot chat failed: ${e.localizedMessage}")
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("AiRepositoryImpl", "AI Financial Health evaluation using offline calculation: ${e.localizedMessage}")
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("AiRepositoryImpl", "AI Financial Coach evaluation using offline calculation: ${e.localizedMessage}")
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
