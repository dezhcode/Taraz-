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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.layer.edgeGlass

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
    /** When the app last went to the background; 0 while it is in front. */
    private var backgroundedAt = 0L

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after the app has been away, not only on a cold start; a short
        // grace period covers a quick look at another app or a system dialog.
        if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > LOCK_GRACE_MS) viewModel.lockApp()
        backgroundedAt = 0L
    }

    private companion object {
        const val LOCK_GRACE_MS = 30_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.example.utils.NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        com.example.reminders.ReminderScheduler.scheduleDailyLoanCheck(this)

        // انتخاب روشن/تیرهٔ کاربر قبل از اولین رسم اعمال می‌شود تا صفحه چشمک نزند
        com.example.ui.layer.ThemePrefs.load(this)?.let { com.example.ui.layer.LayerTheme.dark = it }

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
                // طرح «لایه»: روز انتخاب‌شده در تراکنش‌ها، شیت پرداخت قسط و ...
                var txSelectedDay by remember { mutableStateOf(System.currentTimeMillis()) }
                var loanToPay by remember { mutableStateOf<com.example.data.Loan?>(null) }
                val bankSenders by viewModel.bankSenders.collectAsStateWithLifecycle()
                val allGoals by viewModel.allGoals.collectAsStateWithLifecycle()

                // Overlays states
                var isAddTransactionOpen by remember { mutableStateOf(false) }
                var initialIsExpenseForDialog by remember { mutableStateOf(true) }
                var selectedTransactionForEdit by remember { mutableStateOf<com.example.data.Transaction?>(null) }
                var isAiSheetOpen by remember { mutableStateOf(false) }
                var isBudgetOpen by remember { mutableStateOf(false) }
                var isAddCardOpen by remember { mutableStateOf(false) }
                var accountToEdit by remember { mutableStateOf<com.example.data.BankCard?>(null) }
                var isTransferOpen by remember { mutableStateOf(false) }

                // صفحه‌های فرعی با پشتهٔ ساده: بازگشت همیشه به صفحهٔ قبلی می‌رود
                val subStack = remember { mutableStateListOf<String>() }
                val activeSubScreen: String? = subStack.lastOrNull()
                var selectedAccountId by remember { mutableStateOf<Int?>(null) }
                var selectedLoanId by remember { mutableStateOf<Int?>(null) }
                fun openSub(name: String) { subStack.add(name) }
                fun closeSub() { if (subStack.isNotEmpty()) subStack.removeAt(subStack.lastIndex) }

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
                    } else if (isBudgetOpen) {
                        isBudgetOpen = false
                    } else if (accountToEdit != null) {
                        accountToEdit = null
                    } else if (isAddCardOpen) {
                        isAddCardOpen = false
                    } else if (activeSubScreen != null) {
                        closeSub()
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

                // نوار وضعیت: آیکن‌های تیره روی زمینهٔ روشن و برعکس
                val isDark = com.example.ui.layer.LayerTheme.dark
                LaunchedEffect(isDark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (isDark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent)
                    this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                var themeChosen by remember { mutableStateOf(com.example.ui.layer.ThemePrefs.load(context) != null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.layer.L.Bg
                ) {
                    if (!themeChosen) {
                        // اولین اجرا: روشن یا تیره؟
                        com.example.ui.layer.LayerThemeChooser(onDone = {
                            com.example.ui.layer.ThemePrefs.save(context, com.example.ui.layer.LayerTheme.dark)
                            themeChosen = true
                        })
                    } else if (isAppLocked) {
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
                                "accounts" -> {
                                    com.example.ui.layer.LayerAccountsScreen(
                                        cards = cards,
                                        senders = bankSenders,
                                        totalBalance = totalBalance,
                                        onBack = { closeSub() },
                                        onOpenAccount = { selectedAccountId = it.id; openSub("account_detail") },
                                        onAdd = { isAddCardOpen = true },
                                        onTransfer = { isTransferOpen = true }
                                    )
                                }

                                "account_detail" -> {
                                    val idx = cards.indexOfFirst { it.id == selectedAccountId }
                                    val card = cards.getOrNull(idx)
                                    if (card == null) {
                                        LaunchedEffect(Unit) { closeSub() }
                                    } else {
                                        com.example.ui.layer.LayerAccountDetailScreen(
                                            card = card,
                                            cardIndex = idx,
                                            cards = cards,
                                            transactions = transactions,
                                            categories = categories,
                                            senders = bankSenders,
                                            holderName = userProfile?.name.orEmpty(),
                                            onBack = { closeSub() },
                                            onEdit = { accountToEdit = card },
                                            onTransfer = { isTransferOpen = true },
                                            onOpenTransactions = {
                                                subStack.clear()
                                                viewModel.setTab(Tab.TRANSACTIONS)
                                            },
                                            onOpenTransaction = { selectedTransactionForEdit = it },
                                            onToggleSms = { viewModel.toggleBankSender(it) },
                                            onOpenSmsSetup = { openSub("sms_setup") },
                                            onDelete = {
                                                viewModel.deleteCard(card.id)
                                                closeSub()
                                            }
                                        )
                                    }
                                }

                                "loan_detail" -> {
                                    val loan = loans.firstOrNull { it.id == selectedLoanId }
                                    if (loan == null) {
                                        LaunchedEffect(Unit) { closeSub() }
                                    } else {
                                        com.example.ui.layer.LayerLoanDetailScreen(
                                            loan = loan,
                                            transactions = transactions,
                                            onBack = { closeSub() },
                                            onPay = { loanToPay = loan },
                                            onToggleReminder = { viewModel.setLoanReminder(loan, it) },
                                            onDelete = {
                                                viewModel.deleteLoan(loan.id)
                                                closeSub()
                                            }
                                        )
                                    }
                                }

                                "add_loan" -> {
                                    com.example.ui.layer.LayerAddLoanScreen(
                                        cards = cards,
                                        onBack = { closeSub() },
                                        onConfirm = { bank, name, total, paid, inst, day, reminder ->
                                            viewModel.addLoan(bank, name, total, paid, inst, day, reminder)
                                            closeSub()
                                        }
                                    )
                                }

                                "settings" -> {
                                    com.example.ui.layer.LayerSettingsScreen(
                                        profile = userProfile,
                                        biometricEnabled = biometricEnabled,
                                        alertsEnabled = alertsEnabled,
                                        darkMode = com.example.ui.layer.LayerTheme.dark,
                                        onDarkModeChange = {
                                            com.example.ui.layer.LayerTheme.dark = it
                                            com.example.ui.layer.ThemePrefs.save(context, it)
                                        },
                                        smsBankCount = bankSenders.count { it.isEnabled },
                                        categoryCount = categories.size,
                                        onBack = { closeSub() },
                                        onBiometricChange = { viewModel.setBiometricEnabled(it) },
                                        onAlertsChange = { viewModel.setAlertsEnabled(it) },
                                        onTestLock = { viewModel.lockApp() },
                                        onOpenSms = { openSub("sms_setup") },
                                        onOpenCategories = { openSub("categories") },
                                        onOpenGoals = { openSub("goals") },
                                        onTestServer = { cb -> viewModel.testAiServerConnection(cb) },
                                        onLogout = {
                                            subStack.clear()
                                            viewModel.logout()
                                        },
                                        onResetAll = {
                                            subStack.clear()
                                            viewModel.resetAllData()
                                        }
                                    )
                                }

                                "sms_setup" -> {
                                    com.example.ui.layer.LayerSmsSetupScreen(
                                        viewModel = viewModel,
                                        onBack = { closeSub() },
                                        onOpenReview = { viewModel.setSmsAssistantSheetOpen(true) },
                                        onOpenLearn = { openSub("sms_learn") }
                                    )
                                }

                                "sms_learn" -> {
                                    com.example.ui.layer.LayerSmsLearnScreen(viewModel = viewModel, onBack = { closeSub() })
                                }

                                "categories" -> {
                                    com.example.ui.layer.LayerCategoriesScreen(
                                        categories = categories,
                                        onBack = { closeSub() },
                                        onAdd = { openSub("add_category") },
                                        onDelete = { viewModel.deleteCategory(it.id) }
                                    )
                                }

                                "add_category" -> {
                                    com.example.ui.layer.LayerAddCategoryScreen(
                                        onBack = { closeSub() },
                                        onSave = { name, icon, color, income ->
                                            viewModel.addCategory(name, icon, color, income)
                                            closeSub()
                                        }
                                    )
                                }

                                "goals" -> {
                                    com.example.ui.layer.LayerGoalsScreen(
                                        goals = allGoals,
                                        onBack = { closeSub() },
                                        onAddGoal = { t, target, cur -> viewModel.insertGoal(t, target, cur) },
                                        onAdjust = { g, d -> viewModel.adjustGoal(g, d) },
                                        onDelete = { viewModel.deleteGoal(it.id) }
                                    )
                                }

                                "notifications" -> {
                                    com.example.ui.layer.LayerNotificationsScreen(
                                        loans = loans,
                                        pendingSmsCount = pendingSms.size,
                                        todayExpense = todayExpense,
                                        dailyBudget = dailyBudget,
                                        alertsEnabled = alertsEnabled,
                                        onAlertsChange = { viewModel.setAlertsEnabled(it) },
                                        onBack = { closeSub() },
                                        onPay = { loanToPay = it },
                                        onReviewSms = { viewModel.setSmsAssistantSheetOpen(true) }
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
                                            // محتوا زیر نوار وضعیت و منوی پایین می‌رود و آنجا نرم مات و محو می‌شود
                                            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .edgeGlass(
                                                        top = statusTop + 26.dp,
                                                        bottom = if (isKeyboardVisible) 0.dp else navBottom + 88.dp
                                                    )
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
                                                            onOpenBudget = { isBudgetOpen = true },
                                                            onOpenNotifications = { openSub("notifications") },
                                                            onOpenAccounts = { openSub("accounts") },
                                                            onOpenSettings = { openSub("settings") },
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
                                                                viewModel.approvePendingTransaction(p, p.title.ifBlank { "تراکنش بانکی" }, cat, p.amount, null, p.isExpense)
                                                            },
                                                            onDismissSms = { p -> viewModel.rejectPendingTransaction(p.id) },
                                                            onReviewSms = { p ->
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
                                                            onAddLoan = { openSub("add_loan") },
                                                            onPay = { loanToPay = it },
                                                            onOpenLoan = { selectedLoanId = it.id; openSub("loan_detail") }
                                                        )
                                                    }

                                                    Tab.REPORTS -> {
                                                        com.example.ui.layer.LayerReportsScreen(
                                                            transactions = transactions,
                                                            goal = activeGoal,
                                                            onOpenGoals = { openSub("goals") },
                                                            onAskAssistant = { isAiSheetOpen = true }
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
                                     Box(modifier = Modifier.fillMaxHeight(0.88f)) {
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
                                                 onMicPermissionDenied = { viewModel.reportMicPermissionDenied() },
                                                 onNewChat = { viewModel.resetVisibleChat() }
                                             )
                                         }
                                     }
                                 }
                             }

                            // Adding uses the new sheet; editing an existing row uses the detail sheet.
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

                            if (isBudgetOpen) {
                                com.example.ui.layer.LayerBudgetSheet(
                                    current = dailyBudget,
                                    avgDaily = remember(transactions) { com.example.ui.layer.averageDailySpend(transactions) },
                                    onDismiss = { isBudgetOpen = false },
                                    onSave = {
                                        viewModel.setDailyBudget(it)
                                        isBudgetOpen = false
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
                                        subStack.clear()
                                        viewModel.setTab(Tab.HOME)
                                    }
                                )
                            }

                            selectedTransactionForEdit?.let { tx ->
                                com.example.ui.layer.LayerTxEditSheet(
                                    tx = tx,
                                    cards = cards,
                                    categories = categories,
                                    onDismiss = { selectedTransactionForEdit = null },
                                    onSave = { updated -> viewModel.updateTransaction(tx, updated) },
                                    onDelete = {
                                        viewModel.deleteTransaction(tx)
                                        selectedTransactionForEdit = null
                                    }
                                )
                            }

                            // One sheet for adding an account, wherever it is reached from.
                            if (isAddCardOpen) {
                                com.example.ui.layer.LayerAddAccountSheet(
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

                            accountToEdit?.let { card ->
                                com.example.ui.layer.LayerEditAccountSheet(
                                    card = card,
                                    existingNames = cards.filter { it.id != card.id }.map { it.bankName },
                                    onDismiss = { accountToEdit = null },
                                    onSave = { updated, newName ->
                                        viewModel.saveAccount(updated, newName)
                                        accountToEdit = null
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

                            // بررسی پیامک‌ها تمام‌صفحه روی همه چیز
                            if (showSmsAssistantSheet) {
                                com.example.ui.layer.LayerSmsReviewScreen(
                                    viewModel = viewModel,
                                    pending = pendingSms,
                                    categories = categories,
                                    cards = cards,
                                    onBack = { viewModel.setSmsAssistantSheetOpen(false) }
                                )
                            }

                            // موجودی با پیامک بانک نمی‌خواند
                            val mismatch by viewModel.balanceMismatch.collectAsStateWithLifecycle()
                            mismatch?.let { m ->
                                com.example.ui.layer.LayerSheet(
                                    title = "موجودی ${m.account} با پیامک بانک فرق دارد",
                                    onDismiss = { viewModel.dismissBalanceMismatch() },
                                    closeLabel = "فعلاً نه",
                                    bottomBar = {
                                        com.example.ui.layer.BigButton("موجودی را ${com.example.ui.layer.money(m.inSms)} کن") { viewModel.acceptSmsBalance() }
                                    }
                                ) {
                                    androidx.compose.material3.Text(
                                        "در تراز: ${com.example.ui.layer.money(m.inApp)} تومان\n" +
                                            "طبق پیامک بانک: ${com.example.ui.layer.money(m.inSms)} تومان\n\n" +
                                            "معمولاً یعنی تراکنشی بیرون از تراز ثبت نشده یا موجودی اول درست وارد نشده بود.",
                                        style = com.example.ui.layer.lt(14.sp, lineHeight = 24.sp)
                                    )
                                }
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
