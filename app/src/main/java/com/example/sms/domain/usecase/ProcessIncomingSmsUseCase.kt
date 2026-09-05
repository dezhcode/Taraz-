package com.example.sms.domain.usecase

import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.domain.repository.SmsRepository

class ProcessIncomingSmsUseCase(private val repository: SmsRepository) {
    suspend operator fun invoke(sender: String, body: String): Result<ParsedTransactionModel?> {
        return try {
            val parsed = repository.processIncomingSms(sender, body)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
