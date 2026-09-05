package com.example.sms.domain.usecase

import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.repository.SmsRepository

class SaveSelectedSendersUseCase(private val repository: SmsRepository) {
    suspend operator fun invoke(senders: List<BankSenderModel>): Result<Unit> {
        return try {
            repository.saveSelectedSenders(senders)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
