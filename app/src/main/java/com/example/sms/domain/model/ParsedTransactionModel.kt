package com.example.sms.domain.model

data class ParsedTransactionModel(
    val transactionType: String, // "withdrawal" (برداشت), "deposit" (واریز), "transfer" (انتقال), "purchase" (خرید), "unknown"
    val amount: Long,
    val date: Long,
    val cardNumber: String,
    val balance: Long,
    val bankName: String,
    val reference: String,
    val merchant: String
)
