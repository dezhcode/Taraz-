package com.example.data

interface AiRepository {
    suspend fun healthCheck(): Result<String>
    suspend fun chat(message: String): Result<String>
    /** مثل chat_turn در duck_cli.py: تاریخچه به‌صورت پیام‌های جدا (role, text) */
    suspend fun chatWithHistory(prompt: String, history: List<Pair<String, String>>): Result<String>
    suspend fun calculateFinancialHealth(
        transactions: List<Transaction>,
        cards: List<BankCard>,
        loans: List<Loan>,
        activeGoal: FinancialGoal?
    ): Result<FinancialHealthResult>
    suspend fun getFinancialCoachInsight(
        transactions: List<Transaction>,
        todayExpense: Long,
        dailyBudget: Long,
        cards: List<BankCard>,
        loans: List<Loan>,
        activeGoal: FinancialGoal?
    ): Result<FinancialCoachInsight>
    fun getBaseUrl(): String
}
