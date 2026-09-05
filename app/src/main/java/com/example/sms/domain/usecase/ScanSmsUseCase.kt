package com.example.sms.domain.usecase

import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.repository.SmsRepository

class ScanSmsUseCase(private val repository: SmsRepository) {
    suspend operator fun invoke(): Result<List<BankSenderModel>> {
        return try {
            val senders = repository.scanDeviceSms()
            if (senders.isEmpty()) {
                Result.failure(Exception("هیچ فرستنده پیامک بانکی پیدا نشد"))
            } else {
                Result.success(senders)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
