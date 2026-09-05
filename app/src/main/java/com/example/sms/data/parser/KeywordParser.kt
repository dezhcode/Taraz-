package com.example.sms.data.parser

import android.util.Log
import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.util.BankUtils

class KeywordParser : SmsParser {

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        val cleanBody = BankUtils.convertNumeralsAndClean(body)
        
        try {
            val lines = cleanBody.split("\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null

            // Detect Transaction Type
            var type = "unknown"
            for (line in lines) {
                when {
                    line.contains("برداشت") || line.contains("خرید") || line.contains("پرداخت") -> {
                        type = "withdrawal"
                        break
                    }
                    line.contains("واریز") || line.contains("سود") || line.contains("حقوق") -> {
                        type = "deposit"
                        break
                    }
                    line.contains("انتقال") || line.contains("کارت به کارت") -> {
                        type = "transfer"
                        break
                    }
                }
            }

            if (type == "unknown") return null

            var amount = 0L
            var balance = 0L
            var card = "نامشخص"
            var reference = ""

            // Iterate line by line to extract variables semantically
            for (line in lines) {
                val words = line.split(" ", ":", "：").map { it.trim() }.filter { it.isNotEmpty() }
                
                // Extract amount from lines containing transaction keywords or "مبلغ"
                if (line.contains("مبلغ") || line.contains("برداشت") || line.contains("واریز") || line.contains("خرید") || line.contains("انتقال")) {
                    for (word in words) {
                        val cleanNum = word.replace(",", "").replace(".", "").replace("٫", "")
                        val numVal = cleanNum.toLongOrNull()
                        if (numVal != null && numVal > 1000) {
                            if (amount == 0L) {
                                amount = numVal
                            }
                        }
                    }
                }

                // Extract balance from lines containing "مانده" or "موجودی"
                if (line.contains("مانده") || line.contains("موجودی")) {
                    for (word in words) {
                        val cleanNum = word.replace(",", "").replace(".", "").replace("٫", "")
                        val numVal = cleanNum.toLongOrNull()
                        if (numVal != null && numVal >= 0) {
                            balance = numVal
                        }
                    }
                }

                // Extract card or account number (4 digits ending)
                if (line.contains("کارت") || line.contains("حساب")) {
                    for (word in words) {
                        val cleanNum = word.replace("*", "").replace("-", "")
                        if (cleanNum.length == 4 && cleanNum.all { it.isDigit() }) {
                            card = cleanNum
                        }
                    }
                }

                // Extract reference / tracking number
                if (line.contains("پیگیری") || line.contains("مرجع") || line.contains("شماره")) {
                    for (word in words) {
                        val numVal = word.toLongOrNull()
                        if (numVal != null && word.length >= 6) {
                            reference = word
                        }
                    }
                }
            }

            if (amount > 0) {
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
            Log.e("KeywordParser", "Error parsing bank SMS with keywords", e)
        }
        return null
    }
}
