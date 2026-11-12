package com.example.sms.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PendingTransaction
import com.example.data.UnknownSms
import com.example.sms.data.repository.SmsRepositoryImpl
import com.example.data.SmsPrefs
import com.example.sms.data.scanner.InboxMessage
import com.example.sms.data.scanner.SmsInbox
import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.usecase.GetSelectedSendersUseCase
import com.example.sms.domain.usecase.ScanSmsUseCase
import com.example.sms.domain.usecase.SaveSelectedSendersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmsParserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = SmsRepositoryImpl(
        application,
        db.bankSenderDao(),
        db.pendingTransactionDao(),
        db.unknownSmsDao()
    )

    // Use Cases
    private val scanSmsUseCase = ScanSmsUseCase(repository)
    private val getSelectedSendersUseCase = GetSelectedSendersUseCase(repository)
    private val saveSelectedSendersUseCase = SaveSelectedSendersUseCase(repository)

    // UI state for scanned candidates from device
    private val _scannedSenders = MutableStateFlow<List<BankSenderModel>>(emptyList())
    
    // کلید اصلی پیامک‌خوان (SmsPrefs): تا روشن نشود، هیچ پیامکی خوانده نمی‌شود
    private val _readerEnabled = MutableStateFlow(SmsPrefs.enabled(application))
    val readerEnabled: StateFlow<Boolean> = _readerEnabled.asStateFlow()

    fun setReaderEnabled(on: Boolean) {
        SmsPrefs.setEnabled(getApplication(), on)
        _readerEnabled.value = on
    }

    // پیامک‌های اخیر برای «انتخاب سرشماره از میان پیام‌ها»
    private val inbox = SmsInbox(application)
    private val _recentMessages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val recentMessages: StateFlow<List<InboxMessage>> = _recentMessages.asStateFlow()

    private val _messagesLoading = MutableStateFlow(false)
    val messagesLoading: StateFlow<Boolean> = _messagesLoading.asStateFlow()

    fun loadMessages(query: String = "") {
        viewModelScope.launch {
            _messagesLoading.value = true
            _recentMessages.value = inbox.recent(query = query)
            _messagesLoading.value = false
        }
    }

    fun setSenderEnabled(senderNumber: String, enabled: Boolean) {
        viewModelScope.launch { repository.toggleSender(senderNumber, enabled) }
    }

    fun removeSender(senderNumber: String) {
        viewModelScope.launch { repository.deleteSenderByNumber(senderNumber) }
    }

    /**
     * فرستندهٔ دلخواه (سرشماره) را با نام بانک ثبت می‌کند، چه در صندوق ورودی پیدا شده باشد چه نه.
     * برمی‌گرداند: null در صورت موفقیت، وگرنه پیام خطا.
     */
    suspend fun addSender(rawNumber: String, bankName: String): String? {
        val number = com.example.data.SmsIngest.latinDigits(rawNumber).trim().replace(" ", "")
        if (number.length < 3) return "سرشماره را وارد کنید (مثلاً ۹۸۱۰۰۰۱۲۳۴ یا Melli)."
        val name = bankName.trim().ifEmpty { "بانک ($number)" }
        return try {
            val existing = db.bankSenderDao().getAllSenders()
                .firstOrNull { com.example.data.SmsIngest.sameSender(it.senderNumber, number) }
            val model = BankSenderModel(
                id = existing?.id ?: 0, bankName = name, senderNumber = existing?.senderNumber ?: number, isEnabled = true
            )
            saveSelectedSendersUseCase(listOf(model))
            repository.setInitialBalanceFromLatestSms(model.senderNumber, name)
            null
        } catch (e: Exception) {
            Log.e("SmsParserViewModel", "Could not save the sender", e)
            "ثبت این سرشماره انجام نشد."
        }
    }

    // Search query StateFlow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Loading StateFlow
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error StateFlow
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Filtered Scanned Senders Flow using search query
    val scannedSenders: StateFlow<List<BankSenderModel>> = combine(
        _scannedSenders,
        _searchQuery
    ) { senders, query ->
        if (query.isBlank()) {
            senders
        } else {
            senders.filter {
                it.bankName.contains(query, ignoreCase = true) || 
                it.senderNumber.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently active saved senders from Room DB
    val savedSelectedSenders: StateFlow<List<BankSenderModel>> = getSelectedSendersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exposure of pending transactions & unknown SMS for dashboard reviews
    val pendingTransactions: StateFlow<List<PendingTransaction>> = db.pendingTransactionDao().getPendingTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unknownSmsList: StateFlow<List<UnknownSms>> = db.unknownSmsDao().getUnknownSmsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // کل صندوق پیامک دیگر با هر بار باز شدن این صفحه خوانده نمی‌شود: فهرست فرستنده‌ها از [loadMessages]
    // می‌آید و نتیجهٔ آن اسکن را هیچ صفحه‌ای نشان نمی‌داد.

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun triggerScan() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            scanSmsUseCase().fold(
                onSuccess = { list ->
                    _scannedSenders.value = list
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e("SmsParserViewModel", "Failed scanning device SMS", error)
                    _scannedSenders.value = emptyList()
                    _errorMessage.value = "هیچ پیامک بانکی معتبری یافت نشد"
                    _isLoading.value = false
                }
            )
        }
    }

    fun toggleSenderSelection(senderNumber: String) {
        val current = _scannedSenders.value
        _scannedSenders.value = current.map {
            if (it.senderNumber == senderNumber) {
                it.copy(isEnabled = !it.isEnabled)
            } else {
                it
            }
        }
    }

    fun selectAllSenders() {
        val current = _scannedSenders.value
        _scannedSenders.value = current.map { it.copy(isEnabled = true) }
    }

    fun deselectAllSenders() {
        val current = _scannedSenders.value
        _scannedSenders.value = current.map { it.copy(isEnabled = false) }
    }

    fun saveChoices() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Determine senders that are selected
                val allSenders = _scannedSenders.value
                val selectedList = allSenders.filter { it.isEnabled }
                val deselectedList = allSenders.filter { !it.isEnabled }

                // Save selected senders
                saveSelectedSendersUseCase(selectedList)
                selectedList.forEach { sender -> repository.setInitialBalanceFromLatestSms(sender.senderNumber, sender.bankName) }

                // Delete deselected ones to keep Room clean
                deselectedList.forEach {
                    repository.deleteSenderByNumber(it.senderNumber)
                }

                _isLoading.value = false
                triggerScan() // Re-sync state
            } catch (e: Exception) {
                _errorMessage.value = "خطا در ثبت بانک‌های انتخابی"
                _isLoading.value = false
            }
        }
    }

    fun deletePendingTransaction(id: Int) {
        viewModelScope.launch {
            try {
                db.pendingTransactionDao().setStatus(id, com.example.data.PendingStatus.REJECTED)
            } catch (e: Exception) {
                Log.e("SmsParserViewModel", "Error deleting pending transaction", e)
            }
        }
    }

    fun ignoreUnknownSms(id: Int) {
        viewModelScope.launch {
            try {
                db.unknownSmsDao().ignoreUnknownSms(id)
            } catch (e: Exception) {
                Log.e("SmsParserViewModel", "Error ignoring unknown SMS", e)
            }
        }
    }

    fun deleteUnknownSms(id: Int) {
        viewModelScope.launch {
            try {
                db.unknownSmsDao().deleteUnknownSms(id)
            } catch (e: Exception) {
                Log.e("SmsParserViewModel", "Error deleting unknown SMS", e)
            }
        }
    }
}
