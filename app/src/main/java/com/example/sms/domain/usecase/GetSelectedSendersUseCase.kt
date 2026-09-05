package com.example.sms.domain.usecase

import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.repository.SmsRepository
import kotlinx.coroutines.flow.Flow

class GetSelectedSendersUseCase(private val repository: SmsRepository) {
    operator fun invoke(): Flow<List<BankSenderModel>> {
        return repository.getSelectedSendersFlow()
    }
}
