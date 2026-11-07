package com.example.sms.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BankBin
import com.example.data.BankCard
import com.example.sms.data.repository.ActivationResult
import com.example.sms.data.repository.InboxSender
import com.example.sms.data.repository.SenderPreview
import com.example.sms.data.repository.SmsActivationRepository
import com.example.sms.data.repository.SmsRepositoryImpl
import com.example.sms.engine.BankCatalog
import com.example.sms.engine.SmsBank
import com.example.sms.engine.SmsSenders
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three things the flow asks for, in order, then the outcome. */
enum class ActivationStep { CARD, PERMISSION, SENDER, DONE }

/** Where the bank on screen came from, so a changed card number can retire a stale guess. */
enum class BankSource { NONE, CARD_NUMBER, ACCOUNT_NAME, PICKED }

data class SmsActivationState(
    val step: ActivationStep = ActivationStep.CARD,

    // step 1 — the card
    val cardDigits: String = "",
    /** The bank read from the card number's prefix, from the account's name, or picked by hand. */
    val bank: SmsBank? = null,
    val bankSource: BankSource = BankSource.NONE,

    // step 2 — permission
    val permissionDenied: Boolean = false,

    // step 3 — the sender number
    val loadingSenders: Boolean = false,
    val senders: List<InboxSender> = emptyList(),
    val showAllSenders: Boolean = false,
    val query: String = "",
    val manualNumber: String = "",
    val selected: String? = null,
    val preview: SenderPreview? = null,
    val previewLoading: Boolean = false,
    /** Name of another card this sender currently feeds, if any. */
    val takenBy: String? = null,
    /** 0 = only new messages; otherwise the days of inbox history to queue for review. */
    val importDays: Int = 0,
    val saving: Boolean = false,

    val error: String? = null,
    val result: ActivationResult? = null
) {
    val cardComplete: Boolean
        get() = bank != null && (cardDigits.isEmpty() || cardDigits.length == 16)

    /** The senders worth showing by default; the rest sit behind «نمایش همه». */
    val visibleSenders: List<InboxSender>
        get() {
            val pool = if (showAllSenders) senders
            else senders.filter { it.knownForBank || it.recognized > 0 || it.looksLikeBank }
            val q = query.trim()
            if (q.isEmpty()) return pool
            val key = SmsSenders.canonical(q)
            return pool.filter {
                it.address.contains(q, ignoreCase = true) ||
                    (key.isNotEmpty() && SmsSenders.canonical(it.address).contains(key))
            }
        }
}

class SmsActivationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = SmsActivationRepository(
        application,
        db,
        SmsRepositoryImpl(application, db.bankSenderDao(), db.pendingTransactionDao(), db.unknownSmsDao())
    )

    private val _state = MutableStateFlow(SmsActivationState())
    val state: StateFlow<SmsActivationState> = _state.asStateFlow()

    private var card: BankCard? = null
    private var previewJob: Job? = null

    /**
     * Begins the flow for [card]. [startAtSender] reopens it at the last step to
     * change the sender of a card that is already connected.
     */
    fun start(card: BankCard, startAtSender: Boolean = false) {
        this.card = card
        previewJob?.cancel()
        val digits = card.cardNumber.filter { it.isDigit() }.take(16)
        val fromNumber = bankFor(digits)
        val fromName = if (fromNumber == null) BankCatalog.find(card.bankName) else null
        val bank = fromNumber ?: fromName
        _state.value = SmsActivationState(
            cardDigits = digits,
            bank = bank,
            bankSource = when {
                fromNumber != null -> BankSource.CARD_NUMBER
                fromName != null -> BankSource.ACCOUNT_NAME
                else -> BankSource.NONE
            }
        )
        if (startAtSender && bank != null) goToSenderOrPermission()
    }

    // ---- step 1: the card -------------------------------------------------------

    fun onCardDigits(input: String) {
        val digits = input.filter { it.isDigit() }.take(16)
        _state.update { s ->
            val detected = bankFor(digits)
            when {
                detected != null -> s.copy(cardDigits = digits, bank = detected, bankSource = BankSource.CARD_NUMBER, error = null)
                // The prefix that identified the bank is gone; do not keep a stale guess.
                s.bankSource == BankSource.CARD_NUMBER -> s.copy(cardDigits = digits, bank = null, bankSource = BankSource.NONE, error = null)
                // A bank taken from the account's name or picked by hand stays.
                else -> s.copy(cardDigits = digits, error = null)
            }
        }
    }

    fun onBankPicked(bank: SmsBank) {
        _state.update { it.copy(bank = bank, bankSource = BankSource.PICKED, error = null) }
    }

    fun continueFromCard() {
        val s = _state.value
        val current = card ?: return
        if (!s.cardComplete) {
            _state.update { it.copy(error = if (it.bank == null) "بانک کارت را مشخص کنید." else "شمارهٔ کارت باید ۱۶ رقم باشد.") }
            return
        }
        viewModelScope.launch {
            // Only a full number replaces what the card already has.
            if (s.cardDigits.length == 16) repository.saveCardNumber(current, s.cardDigits)
            goToSenderOrPermission()
        }
    }

    // ---- step 2: permission -----------------------------------------------------

    private fun goToSenderOrPermission() {
        if (repository.hasPermission()) {
            _state.update { it.copy(step = ActivationStep.SENDER, permissionDenied = false) }
            loadSenders()
        } else {
            _state.update { it.copy(step = ActivationStep.PERMISSION) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && repository.hasPermission()) {
            _state.update { it.copy(step = ActivationStep.SENDER, permissionDenied = false) }
            loadSenders()
        } else {
            _state.update { it.copy(permissionDenied = true) }
        }
    }

    /** The user came back from the system settings page; check whether they allowed it there. */
    fun recheckPermission() {
        if (_state.value.step == ActivationStep.PERMISSION && repository.hasPermission()) {
            onPermissionResult(true)
        }
    }

    // ---- step 3: the sender number ------------------------------------------------

    fun loadSenders() {
        val bank = _state.value.bank ?: return
        _state.update { it.copy(loadingSenders = true, error = null) }
        viewModelScope.launch {
            try {
                val senders = repository.loadSenders(bank)
                _state.update { s ->
                    // With nothing suggested, show the whole inbox rather than an empty list.
                    val hasSuggestions = senders.any { it.knownForBank || it.recognized > 0 || it.looksLikeBank }
                    s.copy(loadingSenders = false, senders = senders, showAllSenders = s.showAllSenders || !hasSuggestions)
                }
                // A bank's own number is almost always right: pre-select it when it is the only one.
                val sure = senders.filter { it.knownForBank && it.recognized > 0 }
                if (_state.value.selected == null && sure.size == 1) selectSender(sure[0].address)
            } catch (e: Exception) {
                Log.e("SmsActivationVM", "Could not list senders", e)
                _state.update { it.copy(loadingSenders = false, error = "خواندن پیامک‌ها ممکن نشد.") }
            }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    fun onShowAll(show: Boolean) = _state.update { it.copy(showAllSenders = show) }

    fun onManualNumber(input: String) = _state.update { it.copy(manualNumber = input.take(24), error = null) }

    /** Uses a number typed by hand — for a sender that has not written to this phone yet. */
    fun useManualNumber() {
        val typed = _state.value.manualNumber.trim()
        if (SmsSenders.canonical(typed).length < 3) {
            _state.update { it.copy(error = "سرشمارهٔ واردشده معتبر نیست.") }
            return
        }
        selectSender(typed)
    }

    fun selectSender(address: String) {
        val current = card ?: return
        val bank = _state.value.bank ?: return
        previewJob?.cancel()
        _state.update { it.copy(selected = address, preview = null, previewLoading = true, takenBy = null, error = null) }
        previewJob = viewModelScope.launch {
            val preview = repository.preview(address, bank)
            val taken = repository.cardLinkedTo(address, current)
            _state.update { s ->
                if (s.selected == address) s.copy(preview = preview, previewLoading = false, takenBy = taken) else s
            }
        }
    }

    fun onImportDays(days: Int) = _state.update { it.copy(importDays = days) }

    fun activate() {
        val s = _state.value
        val current = card ?: return
        val bank = s.bank ?: return
        val address = s.selected ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.activate(current, bank, address, s.importDays)
                _state.update { it.copy(saving = false, step = ActivationStep.DONE, result = result) }
            } catch (e: Exception) {
                Log.e("SmsActivationVM", "Activation failed", e)
                _state.update { it.copy(saving = false, error = "فعال‌سازی انجام نشد. دوباره تلاش کنید.") }
            }
        }
    }

    fun back() {
        _state.update {
            when (it.step) {
                ActivationStep.SENDER, ActivationStep.PERMISSION -> it.copy(step = ActivationStep.CARD, error = null)
                else -> it
            }
        }
    }

    private fun bankFor(digits: String): SmsBank? =
        BankBin.bankFor(digits)?.let { BankCatalog.forCardBankName(it) }
            ?: com.example.sms.util.BankUtils.detectBankFromCardNumber(digits)?.let { BankCatalog.forCardBankName(it) }
}
