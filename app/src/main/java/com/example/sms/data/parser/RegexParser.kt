package com.example.sms.data.parser

import android.util.Log
import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.util.BankUtils
import java.util.regex.Pattern

class RegexParser : SmsParser {

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        val cleanBody = BankUtils.convertNumeralsAndClean(body)
        
        try {
            // 1. Identify transaction type
            val transactionType = when {
                cleanBody.contains("برداشت") || cleanBody.contains("خرید") || cleanBody.contains("پرداخت") -> "withdrawal"
                cleanBody.contains("واریز") || cleanBody.contains("سود") || cleanBody.contains("حقوق") -> "deposit"
                cleanBody.contains("انتقال") || cleanBody.contains("کارت به کارت") -> "transfer"
                else -> "unknown"
            }

            // If we cannot identify a valid transaction, reject early
            if (transactionType == "unknown") return null

            // 2. Extract Amount
            // Look for numerical sequences with commas or periods representing currency
            // e.g. "مبلغ: 25,339,753 ریال" or "مبلغ 3,500,000 ریال"
            val amountPatterns = listOf(
                Pattern.compile("(?:مبلغ|مبلغ:)\\s*([\\d,٫.]+)\\s*(?:ریال|تومان)?"),
                Pattern.compile("(?:برداشت|واریز|خرید|انتقال)\\s*([\\d,٫.]+)\\s*(?:ریال|تومان)"),
                Pattern.compile("([\\d,٫.]{4,15})\\s*(?:ریال|تومان)"),
                Pattern.compile("([\\d,٫.]+)\\s*Rls")
            )

            var amount = 0L
            for (pattern in amountPatterns) {
                val matcher = pattern.matcher(cleanBody)
                if (matcher.find()) {
                    val rawAmountStr = matcher.group(1) ?: ""
                    val cleanAmountStr = rawAmountStr.replace(",", "").replace(".", "").replace("٫", "").trim()
                    val parsed = cleanAmountStr.toLongOrNull()
                    if (parsed != null && parsed > 100) { // arbitrary threshold to avoid matching shortcodes or years
                        amount = parsed
                        break
                    }
                }
            }

            if (amount == 0L) {
                // Try a final general fallback for any sequence of digits with commas
                val fallbackPattern = Pattern.compile("([\\d,]{4,15})")
                val matcher = fallbackPattern.matcher(cleanBody)
                while (matcher.find()) {
                    val cleanVal = matcher.group(1)?.replace(",", "")?.toLongOrNull() ?: 0L
                    if (cleanVal > 1000) {
                        amount = cleanVal
                        break
                    }
                }
            }

            // If amount is still 0, we can't classify this as a transaction
            if (amount == 0L) return null

            // 3. Extract Balance/Remaining
            // Look for keywords like "مانده" or "موجودی" followed by numbers
            val balancePatterns = listOf(
                Pattern.compile("(?:مانده|موجودی|مانده حساب|موجودی جدید|جدید:)\\s*([\\d,٫.]+)"),
                Pattern.compile("([\\d,٫.]+)\\s*(?:ریال|تومان)?\\s*(?:مانده|موجودی)")
            )

            var balance = 0L
            for (pattern in balancePatterns) {
                val matcher = pattern.matcher(cleanBody)
                if (matcher.find()) {
                    val rawBalanceStr = matcher.group(1) ?: ""
                    val cleanBalanceStr = rawBalanceStr.replace(",", "").replace(".", "").replace("٫", "").trim()
                    val parsed = cleanBalanceStr.toLongOrNull()
                    if (parsed != null) {
                        balance = parsed
                        break
                    }
                }
            }

            // 4. Extract Card or Account Number
            // Normally 4 digits or card pattern
            val cardPatterns = listOf(
                Pattern.compile("(?:کارت|حساب|به کارت|از کارت|به حساب|از حساب)\\s*(?:به|از)?\\s*([\\d*\\-]+)"),
                Pattern.compile("([\\d]{4})\\b")
            )

            var card = "نامشخص"
            for (pattern in cardPatterns) {
                val matcher = pattern.matcher(cleanBody)
                if (matcher.find()) {
                    val matchedCard = matcher.group(1) ?: ""
                    if (matchedCard.length >= 4) {
                        card = matchedCard.takeLast(4)
                        break
                    }
                }
            }

            // 5. Extract Date
            // matches YYYY/MM/DD or YY/MM/DD
            val datePattern = Pattern.compile("(\\d{2,4}/\\d{2}/\\d{2})")
            val dateMatcher = datePattern.matcher(cleanBody)
            val dateMs = if (dateMatcher.find()) {
                // For simplicity, return system timestamp or map if date utility is present
                System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }

            // 6. Extract Reference number
            val refPattern = Pattern.compile("(?:پیگیری|مرجع|ارجاع|شماره پیگیری):?\\s*(\\d+)")
            val refMatcher = refPattern.matcher(cleanBody)
            val reference = if (refMatcher.find()) refMatcher.group(1) ?: "" else ""

            return ParsedTransactionModel(
                transactionType = transactionType,
                amount = amount,
                date = dateMs,
                cardNumber = card,
                balance = balance,
                bankName = bankName,
                reference = reference,
                merchant = ""
            )
        } catch (e: Exception) {
            Log.e("RegexParser", "Error parsing bank SMS with regex", e)
        }
        return null
    }
}
