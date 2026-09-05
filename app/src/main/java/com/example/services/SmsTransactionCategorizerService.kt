package com.example.services

import com.example.data.Transaction
import java.util.Calendar

object SmsTransactionCategorizerService {

    // Define standard categories
    val CATEGORIES = listOf("غذا", "حقوق", "پوشاک", "تفریح", "قسط", "سایر")

    /**
     * Categorizes a transaction based on SMS body text, title, and whether it's an expense.
     */
    fun categorize(smsBody: String, title: String, isExpense: Boolean): String {
        if (!isExpense) {
            val lowerBody = smsBody.lowercase()
            val lowerTitle = title.lowercase()
            if (lowerBody.contains("حقوق") || lowerBody.contains("salary") || lowerBody.contains("واریز حقوق") ||
                lowerTitle.contains("حقوق") || lowerTitle.contains("salary") || lowerTitle.contains("واریز حقوق") ||
                lowerBody.contains("مساعده") || lowerBody.contains("پاداش")
            ) {
                return "حقوق"
            }
            return "سایر"
        }

        val body = smsBody.lowercase()
        val t = title.lowercase()

        // 1. Installments & Loans (قسط)
        if (body.contains("قسط") || body.contains("تسهیلات") || body.contains("وام") || body.contains("بیمه") ||
            body.contains("installment") || body.contains("loan") || body.contains("insurance") ||
            t.contains("قسط") || t.contains("وام") || t.contains("بیمه") || t.contains("تسهیلات") ||
            body.contains("لیزینگ") || body.contains("کسر قسط")
        ) {
            return "قسط"
        }

        // 2. Food / Groceries / Dining (غذا)
        if (body.contains("رستوران") || body.contains("کافه") || body.contains("فست فود") || body.contains("اسنپ فود") ||
            body.contains("سوپرمارکت") || body.contains("هایپر") || body.contains("هایپرمارکت") || body.contains("نانوایی") ||
            body.contains("قنادی") || body.contains("شیرینی") || body.contains("میوه") || body.contains("سبزی") ||
            body.contains("پروتئین") || body.contains("لبنیات") || body.contains("قصابی") || body.contains("مواد غذایی") ||
            body.contains("snappfood") || body.contains("restaurant") || body.contains("cafe") || body.contains("hyper") ||
            body.contains("میوه فروشی") || body.contains("نانوا") || body.contains("شکلات") ||
            t.contains("سوپرمارکت") || t.contains("رستوران") || t.contains("غذا")
        ) {
            return "غذا"
        }

        // 3. Clothing / Apparel (پوشاک)
        if (body.contains("پوشاک") || body.contains("کیف") || body.contains("کفش") || body.contains("لباس") ||
            body.contains("مانتو") || body.contains("بوتیک") || body.contains("شلوار") || body.contains("مزون") ||
            body.contains("boutique") || body.contains("clothing") || body.contains("shoes") ||
            t.contains("پوشاک") || t.contains("لباس") || t.contains("کفش") || body.contains("کت و شلوار")
        ) {
            return "پوشاک"
        }

        // 4. Entertainment / Travel / Transit (تفریح)
        if (body.contains("سینما") || body.contains("تئاتر") || body.contains("کنسرت") || body.contains("شهربازی") ||
            body.contains("تفریح") || body.contains("اسنپ") || body.contains("تپسی") || body.contains("آژانس") ||
            body.contains("تاکسی") || body.contains("مسافرت") || body.contains("هتل") || body.contains("بلیط") ||
            body.contains("پرواز") || body.contains("سفر") || body.contains("گردش") ||
            body.contains("snapp") || body.contains("tapsi") || body.contains("travel") || body.contains("hotel") ||
            t.contains("تفریح") || t.contains("سفر") || t.contains("اسنپ") || body.contains("بلیط هواپیما") ||
            body.contains("رزرو")
        ) {
            return "تفریح"
        }

        return "سایر"
    }

    /**
     * Helper logic to generate Monthly Report of Income and Expenses.
     * Returns a list of MonthlyReport sorted by date.
     */
    fun generateMonthlyReports(transactions: List<Transaction>): List<MonthlyReport> {
        val calendar = Calendar.getInstance()
        
        // Group transactions by Year and Month
        val grouped = transactions.groupBy { tx ->
            calendar.timeInMillis = tx.date
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // 1-indexed
            getPersianMonthYear(year, month)
        }

        return grouped.map { (monthYear, txList) ->
            val income = txList.filter { !it.isExpense }.sumOf { it.amount }
            val expense = txList.filter { it.isExpense }.sumOf { it.amount }
            
            val categoryBreakdown = txList.filter { it.isExpense }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            MonthlyReport(
                monthYear = monthYear,
                totalIncome = income,
                totalExpense = expense,
                categoryBreakdown = categoryBreakdown,
                transactionCount = txList.size
            )
        }.sortedBy { it.monthYear }
    }

    private fun getPersianMonthYear(gregorianYear: Int, gregorianMonth: Int): String {
        val monthName = when (gregorianMonth) {
            1 -> "دی"
            2 -> "بهمن"
            3 -> "اسفند"
            4 -> "فروردین"
            5 -> "اردیبهشت"
            6 -> "خرداد"
            7 -> "تیر"
            8 -> "مرداد"
            9 -> "شهریور"
            10 -> "مهر"
            11 -> "آبان"
            12 -> "آذر"
            else -> "نامشخص"
        }
        val yearOffset = if (gregorianMonth < 3) 622 else 621
        val solarYear = gregorianYear - yearOffset
        return "$monthName $solarYear"
    }
}

data class MonthlyReport(
    val monthYear: String,
    val totalIncome: Long,
    val totalExpense: Long,
    val categoryBreakdown: Map<String, Long>,
    val transactionCount: Int
)
