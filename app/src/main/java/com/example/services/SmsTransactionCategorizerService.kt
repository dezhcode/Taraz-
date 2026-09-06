package com.example.services

import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome

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
        // Group by real Jalali month, not by the Gregorian month it happens to fall in.
        return transactions
            .groupBy { tx -> com.example.utils.JalaliDate.fromTimestamp(tx.date).monthKey() }
            .map { (monthKey, txList) ->
                val month = monthKey % 100
                val year = monthKey / 100
                val label = "${com.example.utils.JalaliDate.MONTH_NAMES[month - 1]} " +
                        com.example.utils.JalaliDate.toPersianDigits(year)

                MonthlyReport(
                    monthYear = label,
                    monthKey = monthKey,
                    totalIncome = txList.filter { it.countsAsIncome }.sumOf { it.amount },
                    totalExpense = txList.filter { it.countsAsExpense }.sumOf { it.amount },
                    categoryBreakdown = txList.filter { it.countsAsExpense }
                        .groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.amount } },
                    transactionCount = txList.size
                )
            }
            .sortedBy { it.monthKey }   // chronological; a month NAME cannot be sorted as text
    }
}

data class MonthlyReport(
    val monthYear: String,
    val monthKey: Int = 0,
    val totalIncome: Long,
    val totalExpense: Long,
    val categoryBreakdown: Map<String, Long>,
    val transactionCount: Int
)
