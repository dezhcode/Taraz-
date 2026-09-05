package com.example.sms.domain.model

data class BankSenderModel(
    val id: Int = 0,
    val bankName: String,
    val senderNumber: String,
    val isEnabled: Boolean = true,
    val messageCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
