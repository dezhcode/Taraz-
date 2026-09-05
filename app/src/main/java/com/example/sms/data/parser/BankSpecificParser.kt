package com.example.sms.data.parser

import android.util.Log
import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.util.BankUtils

class BankSpecificParser : SmsParser {

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        val cleanBody = BankUtils.convertNumeralsAndClean(body).trim()
        val isMelli = bankName.contains("ملی") || cleanBody.contains("ملی") || cleanBody.contains("Melli") || cleanBody.contains("ملي")
        val isResalat = bankName.contains("رسالت") || cleanBody.contains("رسالت") || cleanBody.contains("Resalat")

        if (!isMelli && !isResalat) return null

        try {
            val lines = cleanBody.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null

            if (isResalat) {
                return parseResalat(lines, cleanBody)
            } else if (isMelli) {
                return parseMelli(lines, cleanBody)
            }
        } catch (e: Exception) {
            Log.e("BankSpecificParser", "Error parsing specific bank sms", e)
        }
        return null
    }

    private fun parseResalat(lines: List<String>, originalBody: String): ParsedTransactionModel? {
        var accountOrCard = "نامشخص"
        var amount = 0L
        var isExpense = true
        var balance = 0L
        val dateMs = System.currentTimeMillis()

        for (line in lines) {
            val cleanedLine = line.replace(" ", "")
            if (cleanedLine.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
                accountOrCard = cleanedLine
            } else if (cleanedLine.startsWith("-") || cleanedLine.startsWith("+")) {
                val cleanAmount = cleanedLine.substring(1).replace(",", "").replace(".", "").replace("٫", "")
                val parsedAmt = cleanAmount.toLongOrNull()
                if (parsedAmt != null) {
                    amount = parsedAmt
                    isExpense = cleanedLine.startsWith("-")
                }
            } else if (line.contains("مانده:")) {
                val balStr = line.substringAfter("مانده:").replace(",", "").replace(".", "").replace("٫", "").trim()
                balance = balStr.toLongOrNull() ?: 0L
            }
        }

        if (amount == 0L) {
            val amountRegex = Regex("([+-])[\\d,٫.]+")
            val match = amountRegex.find(originalBody)
            if (match != null) {
                val sign = match.groupValues[1]
                val amtStr = match.value.substring(1).replace(",", "").replace(".", "").replace("٫", "")
                amount = amtStr.toLongOrNull() ?: 0L
                isExpense = sign == "-"
            }
        }

        if (amount > 0) {
            return ParsedTransactionModel(
                transactionType = if (isExpense) "withdrawal" else "deposit",
                amount = amount,
                date = dateMs,
                cardNumber = accountOrCard.takeLast(4),
                balance = balance,
                bankName = "بانک رسالت",
                reference = "",
                merchant = ""
            )
        }
        return null
    }

    private fun parseMelli(lines: List<String>, originalBody: String): ParsedTransactionModel? {
        var accountOrCard = "نامشخص"
        var amount = 0L
        var isExpense = true
        var balance = 0L
        val dateMs = System.currentTimeMillis()

        for (line in lines) {
            when {
                line.contains("حساب:") -> {
                    accountOrCard = line.substringAfter("حساب:").trim()
                }
                line.contains("کارت:") || line.contains("كارت:") -> {
                    accountOrCard = line.substringAfter("کارت:").substringAfter("كارت:").trim()
                }
                line.contains("مانده:") -> {
                    val valStr = line.substringAfter("مانده:").replace(",", "").replace(".", "").replace("٫", "").trim()
                    val cleanVal = valStr.replace("-", "").replace("+", "").trim()
                    balance = cleanVal.toLongOrNull() ?: 0L
                }
                line.contains("انتقال:") || line.contains("برداشت:") || line.contains("خرید:") || line.contains("خريد:") || line.contains("قسط:") -> {
                    val label = when {
                        line.contains("انتقال:") -> "انتقال:"
                        line.contains("برداشت:") -> "برداشت:"
                        line.contains("خرید:") -> "خرید:"
                        line.contains("خريد:") -> "خريد:"
                        line.contains("قسط:") -> "قسط:"
                        else -> ""
                    }
                    val amtStr = line.substringAfter(label).trim()
                    isExpense = amtStr.endsWith("-") || line.contains("برداشت") || line.contains("خرید") || line.contains("خريد") || line.contains("قسط")
                    val cleanAmt = amtStr.replace("-", "").replace("+", "").replace(",", "").replace(".", "").replace("٫", "").trim()
                    amount = cleanAmt.toLongOrNull() ?: 0L
                }
            }
        }

        if (amount == 0L) {
            for (line in lines) {
                if (line.endsWith("-")) {
                    val cleanAmt = line.replace("-", "").replace(",", "").replace(".", "").replace("٫", "").trim()
                    amount = cleanAmt.toLongOrNull() ?: 0L
                    isExpense = true
                    break
                }
            }
        }

        if (amount > 0) {
            return ParsedTransactionModel(
                transactionType = if (isExpense) "withdrawal" else "deposit",
                amount = amount,
                date = dateMs,
                cardNumber = accountOrCard.takeLast(4),
                balance = balance,
                bankName = "بانک ملی",
                reference = "",
                merchant = ""
            )
        }
        return null
    }
}
