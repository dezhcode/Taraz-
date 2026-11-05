package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.components.CardDetailBottomSheet

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val viewModel: FinanceViewModel by viewModels()

    /**
     * From Android 13 notifications are silently dropped without this runtime
     * grant — the app never asked, so no reminder could ever have been shown.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Declining is fine: reminders stay off until enabled in settings. */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.example.utils.NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        com.example.reminders.ReminderScheduler.scheduleDailyLoanCheck(this)

        setContent {
            // Force Fidar Design System Light Theme and custom styling
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val screenState by viewModel.currentScreen.collectAsStateWithLifecycle()
                val tabState by viewModel.currentTab.collectAsStateWithLifecycle()
                val onboardingStep by viewModel.onboardingStep.collectAsStateWithLifecycle()
                val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

                // Persistent settings and session states
                val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
                val alertsEnabled by viewModel.alertsEnabled.collectAsStateWithLifecycle()
                val aiServerUrl by viewModel.aiServerUrl.collectAsStateWithLifecycle()
                val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
                val isDictating by viewModel.isDictating.collectAsStateWithLifecycle()
                val dictationAmplitude by viewModel.dictationAmplitude.collectAsStateWithLifecycle()
                val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()

                // Core financial states
                val transactions by viewModel.transactions.collectAsStateWithLifecycle()
                val cards by viewModel.cards.collectAsStateWithLifecycle()
                val loans by viewModel.loans.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val totalBalance by viewModel.totalBalance.collectAsStateWithLifecycle()
                val monthlyIncome by viewModel.monthlyIncome.collectAsStateWithLifecycle()
                val monthlyExpense by viewModel.monthlyExpense.collectAsStateWithLifecycle()
                val todayExpense by viewModel.todayExpense.collectAsStateWithLifecycle()
                val dailyBudget by viewModel.dailyBudget.collectAsStateWithLifecycle()
                val activeGoal by viewModel.activeGoal.collectAsStateWithLifecycle()
                val financialHealth by viewModel.financialHealth.collectAsStateWithLifecycle()
                val financialCoachInsight by viewModel.financialCoachInsight.collectAsStateWithLifecycle()
                val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()

                // AI Assist states
                val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
                val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
                val chatErrorToast by viewModel.chatErrorToast.collectAsStateWithLifecycle()
                val showSmsAssistantSheet by viewModel.showSmsAssistantSheet.collectAsStateWithLifecycle()
                val pendingSms by viewModel.pendingTransactions.collectAsStateWithLifecycle()
                // طرح «لایه»: روز انتخاب‌شده در تراکنش‌ها، شیت پرداخت قسط، دیالوگ وام جدید
                var txSelectedDay by remember { mutableStateOf(System.currentTimeMillis()) }
                var loanToPay by remember { mutableStateOf<com.example.data.Loan?>(null) }
                var isAddLoanOpen by remember { mutableStateOf(false) }

                // Overlays states
                var isAddTransactionOpen by remember { mutableStateOf(false) }
                var initialIsExpenseForDialog by remember { mutableStateOf(true) }
                var selectedTransactionForEdit by remember { mutableStateOf<com.example.data.Transaction?>(null) }
                var isAiSheetOpen by remember { mutableStateOf(false) }
                var activeSubScreen by remember { mutableStateOf<String?>(null) } // "banks" or "loans"
                var selectedCardForDetail by remember { mutableStateOf<com.example.data.BankCard?>(null) }
                var isAddCardOpen by remember { mutableStateOf(false) }
                var isTransferOpen by remember { mutableStateOf(false) }

                // System Back Button Handling
                val context = LocalContext.current
                val activity = context as? androidx.fragment.app.FragmentActivity
                var lastBackPressTime by remember { mutableStateOf(0L) }
                val exitToastMessage = "برای خروج از برنامه دوبار برگشت بزنید پشت سر هم"

                BackHandler(enabled = screenState == Screen.MAIN) {
                    if (loanToPay != null) {
                        loanToPay = null
                    } else if (showSmsAssistantSheet) {
                        viewModel.setSmsAssistantSheetOpen(false)
                    } else if (isAiSheetOpen) {
                        isAiSheetOpen = false
                    } else if (isAddTransactionOpen || selectedTransactionForEdit != null) {
                        isAddTransactionOpen = false
                        selectedTransactionForEdit = null
                    } else if (isTransferOpen) {
                        isTransferOpen = false
                    } else if (selectedCardForDetail != null) {
                        selectedCardForDetail = null
                    } else if (isAddCardOpen) {
                        isAddCardOpen = false
                    } else if (activeSubScreen != null) {
                        activeSubScreen = null
                    } else if (tabState != Tab.HOME) {
                        viewModel.setTab(Tab.HOME)
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) {
                            activity?.finish()
                        } else {
                            lastBackPressTime = currentTime
                            android.widget.Toast.makeText(context, exitToastMessage, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.layer.L.Bg
                ) {
                    if (isAppLocked) {
                        BiometricLockScreen(
                            onUnlockSuccess = { viewModel.unlockApp() },
                            onLockUnavailable = { viewModel.disableLockUnavailableOnDevice() }
                        )
                    } else if (screenState == Screen.AUTH || screenState == Screen.ONBOARDING || screenState == Screen.SPLASH) {
                        AuthScreen(viewModel = viewModel)
                    } else {
                        if (screenState == Screen.MAIN) {
                            // Sub-screens override main tab navigation
                            when (activeSubScreen) {
                                "banks" -> {
                                    BanksSubScreen(
                                        cards = cards,
                                        onBack = { activeSubScreen = null },
                                        onAddCard = { bankName, cardNumber, balance, cardHolderName ->
                                            viewModel.addCard(bankName, cardNumber, balance, cardHolderName)
                                        }
                                    )
                                }

                                "loans" -> {
                                    LoansSubScreen(
                                        loans = loans,
                                        cards = cards,
                                        onBack = { activeSubScreen = null },
                                        onPayInstallment = { loan ->
                                            viewModel.payInstallment(loan)
                                        },
                                        onAddLoan = { bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay ->
                                            viewModel.addLoan(bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay)
                                        }
                                    )
                                }

                                "settings" -> {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        userProfile = userProfile,
                                        biometricEnabled = biometricEnabled,
                                        alertsEnabled = alertsEnabled,
                                        onBiometricChange = { viewModel.setBiometricEnabled(it) },
                                        onAlertsChange = { viewModel.setAlertsEnabled(it) },
                                        onResetAll = { viewModel.resetAllData() },
                                        onLogout = { viewModel.logout() },
                                        onScheduleReminder = { title, delay -> viewModel.scheduleReminder(title, delay) },
                                        onBack = { activeSubScreen = null }
                                    )
                                }

                                else -> {
                                    val isKeyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                                    // Main tab structure with M3 Bottom Bar
                                    Scaffold(
                                        modifier = Modifier.fillMaxSize()
                                    ) { scaffoldPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(com.example.ui.layer.L.Bg)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(top = scaffoldPadding.calculateTopPadding())
                                            ) {
                                            AnimatedContent(
                                                targetState = tabState,
                                                transitionSpec = {
                                                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)))
                                                        .togetherWith(fadeOut(animationSpec = tween(200)))
                                                },
                                                label = "tabTransition",
                                                modifier = Modifier.fillMaxSize()
                                            ) { targetTab ->
                                                when (targetTab) {
                                                    Tab.HOME -> {
                                                        com.example.ui.layer.LayerHomeScreen(
                                                            userName = userProfile?.name.orEmpty(),
                                                            cards = cards,
                                                            totalBalance = totalBalance,
                                                            monthlyIncome = monthlyIncome,
                                                            monthlyExpense = monthlyExpense,
                                                            todayExpense = todayExpense,
                                                            dailyBudget = dailyBudget,
                                                            transactions = transactions,
                                                            loans = loans,
                                                            pendingSms = pendingSms,
                                                            categories = categories,
                                                            onSetDailyBudget = { viewModel.setDailyBudget(it) },
                                                            onOpenAccounts = { activeSubScreen = "banks" },
                                                            onOpenSettings = { activeSubScreen = "settings" },
                                                            onOpenSmsInbox = { viewModel.setSmsAssistantSheetOpen(true) },
                                                            onOpenReports = { viewModel.setTab(Tab.REPORTS) },
                                                            onOpenDay = { day ->
                                                                txSelectedDay = day
                                                                viewModel.setTab(Tab.TRANSACTIONS)
                                                            },
                                                            onSeeAllTransactions = {
                                                                txSelectedDay = System.currentTimeMillis()
                                                                viewModel.setTab(Tab.TRANSACTIONS)
                                                            },
                                                            onPayLoan = { loanToPay = it },
                                                            onApproveSms = { p ->
                                                                val cat = categories.firstOrNull { c ->
                                                                    c.isIncome == !p.isExpense && p.title.contains(c.name)
                                                                }?.name ?: if (p.isExpense) "سایر" else "درآمد"
                                                                viewModel.approvePendingTransaction(p, p.title.ifBlank { "تراکنش بانکی" }, cat, p.amount, p.card, p.isExpense)
                                                            },
                                                            onDismissSms = { p -> viewModel.rejectPendingTransaction(p.id) },
                                                            onReviewSms = { p ->
                                                                viewModel.selectPendingTx(p)
                                                                viewModel.setSmsAssistantSheetOpen(true)
                                                            },
                                                            onTransactionClick = { tx -> selectedTransactionForEdit = tx }
                                                        )
                                                    }

                                                    Tab.TRANSACTIONS -> {
                                                        com.example.ui.layer.LayerTransactionsScreen(
                                                            transactions = transactions,
                                                            cards = cards,
                                                            categories = categories,
                                                            dailyBudget = dailyBudget,
                                                            selectedDay = txSelectedDay,
                                                            onSelectDay = { txSelectedDay = it },
                                                            onTransactionClick = { tx -> selectedTransactionForEdit = tx }
                                                        )
                                                    }

                                                    Tab.LOANS -> {
                                                        com.example.ui.layer.LayerLoansScreen(
                                                            loans = loans,
                                                            onAddLoan = { isAddLoanOpen = true },
                                                            onPay = { loanToPay = it }
                                                        )
                                                    }

                                                    Tab.REPORTS -> {
                                                        ReportsScreen(
                                                            transactions = transactions,
                                                            monthlyIncome = monthlyIncome,
                                                            monthlyExpense = monthlyExpense
                                                        )
                                                    }
                                                }
                                            }

                                            } // Close the inner content Box
                                            
                                            // منوی شناور طرح «لایه»
                                            if (!isKeyboardVisible) {
                                                com.example.ui.layer.LayerNavBar(
                                                    currentTab = tabState,
                                                    onTabSelected = { viewModel.setTab(it) },
                                                    onAssistant = { isAiSheetOpen = true },
                                                    onAdd = {
                                                        initialIsExpenseForDialog = true
                                                        isAddTransactionOpen = true
                                                    },
                                                    modifier = Modifier.align(Alignment.BottomCenter)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // AI Assistant bottom sheet overlay
                             if (isAiSheetOpen) {
                                 ModalBottomSheet(
                                     onDismissRequest = {
                                         viewModel.endLiveVoiceCall()
                                         isAiSheetOpen = false
                                     },
                                     sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                     dragHandle = { BottomSheetDefaults.DragHandle() },
                                     containerColor = BackgroundLight
                                 ) {
                                     Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                                         if (liveVoiceState.isActive) {
                                             LiveVoiceOverlayScreen(
                                                 liveState = liveVoiceState,
                                                 realtimeWebView = viewModel.liveVoiceWebView,
                                                 onSelectVoice = { viewModel.selectLiveVoicePreset(it) },
                                                 onToggleMute = { viewModel.toggleLiveVoiceMute() },
                                                 onSendSpokenPrompt = { viewModel.sendLiveVoiceText(it) },
                                                 onEndCall = { viewModel.endLiveVoiceCall() }
                                             )
                                         } else {
                                             AiAssistantScreen(
                                                 messages = chatMessages, toastMessage = chatErrorToast, onDismissToast = { viewModel.clearChatErrorToast() },
                                                 isLoading = isAiLoading,
                                                 onOpenChat = { viewModel.prepareChatForOpening() },
                                                 onApproveAction = { viewModel.executeProposedAction(it) },
                                                 onCancelAction = { viewModel.cancelProposedAction(it) },
                                                 onSendMessage = { prompt ->
                                                     viewModel.sendChatMessage(prompt)
                                                 },
                                                 isDictating = isDictating,
                                                 dictationAmplitude = dictationAmplitude,
                                                 onToggleDictation = { onText -> viewModel.toggleVoiceDictation(onText) },
                                                 speakingMessageId = speakingMessageId,
                                                 onSpeakMessage = { viewModel.speakChatMessage(it) },
                                                 onStartLiveVoice = { viewModel.startLiveVoiceCall() },
                                                 onMicPermissionDenied = { viewModel.reportMicPermissionDenied() }
                                             )
                                         }
                                     }
                                 }
                             }

                             // Quick transaction register modal overlays
                             // Bank SMS Assistant bottom sheet overlay
                             if (showSmsAssistantSheet) {
                                 ModalBottomSheet(
                                     onDismissRequest = { viewModel.setSmsAssistantSheetOpen(false) },
                                     sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                     dragHandle = { BottomSheetDefaults.DragHandle() },
                                     containerColor = BackgroundLight
                                 ) {
                                     Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                                         BankSmsAssistantBottomSheet(
                                             viewModel = viewModel,
                                             onDismiss = { viewModel.setSmsAssistantSheetOpen(false) }
                                         )
                                     }
                                 }
                             }

                            // Adding uses the new sheet; editing an existing row keeps the
                            // old one, which already knows how to delete and pre-fill.
                            if (isAddTransactionOpen) {
                                com.example.ui.layer.LayerAddSheet(
                                    categories = categories,
                                    cards = cards,
                                    transactions = transactions,
                                    startAsExpense = initialIsExpenseForDialog,
                                    onDismiss = { isAddTransactionOpen = false },
                                    onSave = { title, amount, category, isExpense, bankName ->
                                        viewModel.addTransaction(title, amount, category, isExpense, bankName)
                                    },
                                    onTransfer = { from, to, amount ->
                                        viewModel.transferBetweenCards(from, to, amount)
                                    }
                                )
                            }

                            loanToPay?.let { loan ->
                                com.example.ui.layer.LayerPaySheet(
                                    loan = loan,
                                    cards = cards,
                                    onConfirm = { l, from -> viewModel.payInstallmentFrom(l, from) },
                                    onDismiss = { loanToPay = null },
                                    onGoHome = {
                                        loanToPay = null
                                        viewModel.setTab(Tab.HOME)
                                    }
                                )
                            }

                            if (isAddLoanOpen) {
                                AddLoanDialog(
                                    onDismiss = { isAddLoanOpen = false },
                                    onConfirm = { bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay ->
                                        viewModel.addLoan(bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay)
                                        isAddLoanOpen = false
                                    }
                                )
                            }

                            if (selectedTransactionForEdit != null) {
                                PremiumTransactionSheet(
                                    cards = cards,
                                    existingTransaction = selectedTransactionForEdit,
                                    onDismiss = {
                                        isAddTransactionOpen = false
                                        selectedTransactionForEdit = null
                                    },
                                    onConfirm = { title, amount, category, isExpense, bankName ->
                                        val oldTx = selectedTransactionForEdit!!
                                        val updatedTx = oldTx.copy(
                                            title = title,
                                            amount = amount,
                                            category = category,
                                            isExpense = isExpense,
                                            bankName = bankName
                                        )
                                        viewModel.updateTransaction(oldTx, updatedTx)
                                        isAddTransactionOpen = false
                                        selectedTransactionForEdit = null
                                    },
                                    onDelete = { tx ->
                                        viewModel.deleteTransaction(tx)
                                        isAddTransactionOpen = false
                                        selectedTransactionForEdit = null
                                    }
                                )
                            }

                            if (selectedCardForDetail != null) {
                                CardDetailBottomSheet(
                                    card = selectedCardForDetail!!,
                                    onDismiss = { selectedCardForDetail = null },
                                    onEnableSmsBanking = { bankName ->
                                        activeSubScreen = "settings"
                                    },
                                    onUpdateCard = { updatedCard ->
                                        viewModel.updateCard(updatedCard)
                                        selectedCardForDetail = null
                                    },
                                    onDeleteCard = { cardId ->
                                        viewModel.deleteCard(cardId)
                                        selectedCardForDetail = null
                                    }
                                )
                            }

                            // One sheet for adding an account, wherever it is reached from.
                            if (isAddCardOpen) {
                                AddAccountSheet(
                                    existingNames = cards.map { it.bankName },
                                    onDismiss = { isAddCardOpen = false },
                                    onConfirm = { name, cardNumber, balance, accountType ->
                                        viewModel.addCard(
                                            bankName = name,
                                            cardNumber = cardNumber,
                                            balance = balance,
                                            accountType = accountType
                                        )
                                        isAddCardOpen = false
                                    }
                                )
                            }

                            if (isTransferOpen) {
                                TransferDialog(
                                    cards = cards,
                                    onDismiss = { isTransferOpen = false },
                                    onConfirm = { fromCardName, toCardName, amount ->
                                        viewModel.transferBetweenCards(fromCardName, toCardName, amount)
                                        isTransferOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                }
            }
            }
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
