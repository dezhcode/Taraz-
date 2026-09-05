package com.example.sms.data.parser

import android.util.Log
import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.util.BankUtils

class CopilotParser : SmsParser {

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        val cleanBody = BankUtils.convertNumeralsAndClean(body)
        
        try {
            Log.d("CopilotParser", "Copilot Local Intelligence parsing incoming message...")

            // Heuristics: Split the text into alphanumeric and non-alphanumeric blocks
            val tokens = cleanBody.split(Regex("[\\s:\\n\\r]+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null

            // Step 1: Detect transaction action type
            var type = "unknown"
            val withdrawalIndicators = listOf("برداشت", "خرید", "پرداخت", "کسر", "withdrawal", "purchase", "pay")
            val depositIndicators = listOf("واریز", "سود", "حقوق", "دریافت", "deposit", "receive")
            val transferIndicators = listOf("انتقال", "کارت به کارت", "شبا", "پایا", "ساتنا", "transfer")

            for (token in tokens) {
                if (withdrawalIndicators.any { token.contains(it) }) {
                    type = "withdrawal"
                    break
                }
                if (depositIndicators.any { token.contains(it) }) {
                    type = "deposit"
                    break
                }
                if (transferIndicators.any { token.contains(it) }) {
                    type = "transfer"
                    break
                }
            }

            if (type == "unknown") {
                // If we didn't match, look at the overall body context
                type = when {
                    withdrawalIndicators.any { cleanBody.contains(it) } -> "withdrawal"
                    depositIndicators.any { cleanBody.contains(it) } -> "deposit"
                    transferIndicators.any { cleanBody.contains(it) } -> "transfer"
                    else -> "unknown"
                }
            }

            if (type == "unknown") return null

            // Step 2: Semantic window parsing for numbers (amounts and balances)
            val numbersWithPositions = mutableListOf<Pair<Long, Int>>() // Value, Index in tokens
            tokens.forEachIndexed { index, token ->
                val cleanNum = token.replace(",", "").replace(".", "").replace("٫", "")
                val lVal = cleanNum.toLongOrNull()
                if (lVal != null && lVal >= 0) {
                    numbersWithPositions.add(lVal to index)
                }
            }

            if (numbersWithPositions.isEmpty()) return null

            var amount = 0L
            var balance = 0L
            var card = "نامشخص"
            var reference = ""

            // Look for card: usually a 4-digit number or a number adjacent to "کارت" or "حساب"
            for ((num, idx) in numbersWithPositions) {
                val strNum = num.toString()
                if (strNum.length == 4) {
                    card = strNum
                    break
                }
            }

            // Identify Amount and Balance based on keyword proximity window
            // "مبلغ" is usually immediately followed by or close to the transaction amount.
            // "مانده" is close to the remaining balance.
            val amountKeywords = listOf("مبلغ", "واریز", "برداشت", "خرید", "انتقال")
            val balanceKeywords = listOf("مانده", "موجودی", "جدید")

            var bestAmountDist = Int.MAX_VALUE
            var bestBalanceDist = Int.MAX_VALUE

            for ((num, idx) in numbersWithPositions) {
                val strNum = num.toString()
                // Avoid using 4-digit card ending or 16-digit cards as amount/balance
                if (strNum == card || strNum.length == 16) continue
                // Avoid using small helper numbers (like 1 to 31 for dates, etc.)
                if (num <= 31 && idx > 0 && (tokens[idx - 1].contains("/") || tokens[idx - 1].contains("-"))) continue

                // Check distance to amount keywords
                for (keyword in amountKeywords) {
                    for (i in (idx - 3).coerceAtLeast(0)..(idx + 3).coerceAtMost(tokens.lastIndex)) {
                        if (tokens[i].contains(keyword)) {
                            val dist = Math.abs(idx - i)
                            if (dist < bestAmountDist) {
                                bestAmountDist = dist
                                amount = num
                            }
                        }
                    }
                }

                // Check distance to balance keywords
                for (keyword in balanceKeywords) {
                    for (i in (idx - 3).coerceAtLeast(0)..(idx + 3).coerceAtMost(tokens.lastIndex)) {
                        if (tokens[i].contains(keyword)) {
                            val dist = Math.abs(idx - i)
                            if (dist < bestBalanceDist) {
                                bestBalanceDist = dist
                                balance = num
                            }
                        }
                    }
                }
            }

            // Fallback: If amount is still 0, assign the largest number (except card/date values) as amount
            if (amount == 0L) {
                val filteredNumbers = numbersWithPositions
                    .map { it.first }
                    .filter { it.toString() != card && it.toString().length != 16 && it > 100 }
                if (filteredNumbers.isNotEmpty()) {
                    amount = filteredNumbers.maxOrNull() ?: 0L
                    if (filteredNumbers.size > 1) {
                        // Second largest is likely balance
                        balance = filteredNumbers.sortedDescending().getOrNull(1) ?: 0L
                    }
                }
            }

            // Extract reference (longer string of digits)
            for (token in tokens) {
                if (token.all { it.isDigit() } && token.length >= 6) {
                    reference = token
                    break
                }
            }

            if (amount > 0) {
                Log.d("CopilotParser", "Copilot successfully extracted: Amount=$amount, Card=$card, Balance=$balance")
                return ParsedTransactionModel(
                    transactionType = type,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    cardNumber = card,
                    balance = balance,
                    bankName = bankName,
                    reference = reference,
                    merchant = ""
                )
            }

        } catch (e: Exception) {
            Log.e("CopilotParser", "Copilot Local Intelligence parsing error", e)
        }
        return null
    }
}
