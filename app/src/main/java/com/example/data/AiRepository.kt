package com.example.data

interface AiRepository {
    suspend fun healthCheck(): Result<String>
    suspend fun chat(message: String): Result<String>
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
    /** Returns false when the address is rejected; the active one is unchanged. */
    fun setBaseUrl(url: String): Boolean
}
