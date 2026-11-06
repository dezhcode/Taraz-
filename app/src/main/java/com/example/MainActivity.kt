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
import com.example.data.tagList
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

    /** «ثبت هزینه / درآمد / صوتی» از میان‌بُر آیکن یا ویجت؛ صفحهٔ ثبت باز می‌شود */
    private val quickAction = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickAction.value = intent.action?.takeIf { it.startsWith("ir.taraz.finance.") }
    }

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) quickAction.value = intent?.action?.takeIf { it.startsWith("ir.taraz.finance.") }
        enableEdgeToEdge()

        com.example.utils.NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        com.example.reminders.ReminderScheduler.scheduleDailyLoanCheck(this)
        com.example.reminders.ReminderScheduler.scheduleWeeklySummary(this)

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
                val loanPayments by viewModel.loanPayments.collectAsStateWithLifecycle()
                val recurringItems by viewModel.recurring.collectAsStateWithLifecycle()
                val debts by viewModel.debts.collectAsStateWithLifecycle()
                val persons by viewModel.persons.collectAsStateWithLifecycle()
                val personBalances by viewModel.personBalances.collectAsStateWithLifecycle()
                val assets by viewModel.assets.collectAsStateWithLifecycle()
                val prices by viewModel.prices.collectAsStateWithLifecycle()
                val pricesRefreshing by viewModel.pricesRefreshing.collectAsStateWithLifecycle()
                val priceErrors by viewModel.priceErrors.collectAsStateWithLifecycle()
                val cheques by viewModel.cheques.collectAsStateWithLifecycle()
                val bills by viewModel.bills.collectAsStateWithLifecycle()
                val netWorthSnapshots by viewModel.netWorthSnapshots.collectAsStateWithLifecycle()
                val homeLayout by viewModel.homeLayout.collectAsStateWithLifecycle()
                val homeTools by viewModel.homeTools.collectAsStateWithLifecycle()
                val assetsTotal = assets.sumOf { a ->
                    val u = if (a.manualPrice > 0) a.manualPrice else prices[a.code]?.price ?: 0L
                    kotlin.math.round(u * a.quantity).toLong()
                }
                var assetSheetFor by remember { mutableStateOf<com.example.data.Asset?>(null) }
                var isAssetSheetOpen by remember { mutableStateOf(false) }

                // Overlays states
                var isAddTransactionOpen by remember { mutableStateOf(false) }
                var addWithVoice by remember { mutableStateOf(false) }
                var initialIsExpenseForDialog by remember { mutableStateOf(true) }
                val pendingQuick = quickAction.value
                var weeklyOpen by remember { mutableStateOf(false) }
                LaunchedEffect(pendingQuick) {
                    when (pendingQuick) {
                        com.example.reminders.WeeklySummaryWorker.ACTION_OPEN -> { viewModel.setTab(Tab.REPORTS); weeklyOpen = true }
                        com.example.widget.QuickAction.ADD_EXPENSE -> { initialIsExpenseForDialog = true; addWithVoice = false; isAddTransactionOpen = true }
                        com.example.widget.QuickAction.ADD_INCOME -> { initialIsExpenseForDialog = false; addWithVoice = false; isAddTransactionOpen = true }
                        com.example.widget.QuickAction.VOICE -> { initialIsExpenseForDialog = true; addWithVoice = true; isAddTransactionOpen = true }
                    }
                    if (pendingQuick != null) quickAction.value = null
                }
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
                var editingCategoryId by remember { mutableStateOf<Int?>(null) }
                // اشخاص: صفحهٔ شخص، شیت افزودن/ویرایش، شیت ثبت قرض
                var selectedPersonId by remember { mutableStateOf<Int?>(null) }
                var personSheetFor by remember { mutableStateOf<com.example.data.Person?>(null) }
                var isPersonSheetOpen by remember { mutableStateOf(false) }
                var entrySheetPersonId by remember { mutableStateOf<Int?>(null) }
                var isEntrySheetOpen by remember { mutableStateOf(false) }
                var isSplitOpen by remember { mutableStateOf(false) }
                var chequeSheetOpen by remember { mutableStateOf(false) }
                var shareMonthTs by remember { mutableStateOf<Long?>(null) }
                var chequeEditing by remember { mutableStateOf<com.example.data.Cheque?>(null) }
                fun openSub(name: String) { subStack.add(name) }
                fun closeSub() { if (subStack.isNotEmpty()) subStack.removeAt(subStack.lastIndex) }

                // ─── خانهٔ قابل چیدن: ابزارها، «یک نگاه» و «پیش رو» ───
                var homeLayoutOpen by remember { mutableStateOf(false) }
                var allToolsOpen by remember { mutableStateOf(false) }
                var billToPay by remember { mutableStateOf<com.example.data.Bill?>(null) }
                var loansSegment by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

                fun runTool(id: String) {
                    when (id) {
                        "expense" -> { initialIsExpenseForDialog = true; addWithVoice = false; isAddTransactionOpen = true }
                        "income" -> { initialIsExpenseForDialog = false; addWithVoice = false; isAddTransactionOpen = true }
                        "voice" -> { initialIsExpenseForDialog = true; addWithVoice = true; isAddTransactionOpen = true }
                        "transfer" -> isTransferOpen = true
                        "sms" -> viewModel.setSmsAssistantSheetOpen(true)
                        "recurring" -> openSub("recurring")
                        "pay_loan" -> {
                            val active = loans.filter { !it.isSettled }
                            if (active.size == 1) loanToPay = active.first()
                            else { loansSegment = 0; viewModel.setTab(Tab.LOANS) }
                        }
                        "bills" -> openSub("bills")
                        "cheque" -> { chequeEditing = null; chequeSheetOpen = true }
                        "cheques" -> { loansSegment = 2; viewModel.setTab(Tab.LOANS) }
                        "add_loan" -> openSub("add_loan")
                        "accounts", "wallet" -> openSub("accounts")
                        "add_account" -> isAddCardOpen = true
                        "add_person" -> { personSheetFor = null; isPersonSheetOpen = true }
                        "person_entry" -> { entrySheetPersonId = null; isEntrySheetOpen = true }
                        "split" -> isSplitOpen = true
                        "people" -> { loansSegment = 1; viewModel.setTab(Tab.LOANS) }
                        "budget" -> isBudgetOpen = true
                        "goals" -> openSub("goals")
                        "categories" -> openSub("categories")
                        "assets" -> openSub("assets")
                        "report", "tags" -> viewModel.setTab(Tab.REPORTS)
                        "weekly" -> weeklyOpen = true
                        "year" -> openSub("year")
                        "share" -> shareMonthTs = System.currentTimeMillis()
                        "assistant" -> isAiSheetOpen = true
                        "search" -> viewModel.setTab(Tab.TRANSACTIONS)
                        "backup" -> openSub("backup")
                    }
                }

                // System Back Button Handling
                val context = LocalContext.current

                // ─── پشتیبان: انتخاب جای فایل با پنجرهٔ خود اندروید ───
                var backupMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
                var backupBusy by remember { mutableStateOf(false) }
                var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
                var pendingRestorePassword by remember { mutableStateOf<String?>(null) }
                val writeFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    val bytes = pendingBackupBytes
                    pendingBackupBytes = null
                    backupBusy = false
                    if (uri != null && bytes != null) {
                        backupMsg = try {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            true to "فایل ذخیره شد."
                        } catch (e: Exception) {
                            false to "ذخیرهٔ فایل انجام نشد."
                        }
                    }
                }
                val autoBackupState by viewModel.autoBackup.collectAsStateWithLifecycle()
                val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                ) { uri -> if (uri != null) viewModel.setAutoBackupFolder(uri) }
                val readFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    val pass = pendingRestorePassword
                    pendingRestorePassword = null
                    if (uri == null || pass == null) {
                        backupBusy = false
                    } else {
                        val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
                        if (bytes == null) {
                            backupBusy = false
                            backupMsg = false to "فایل خوانده نشد."
                        } else {
                            viewModel.restoreBackup(bytes, pass) { ok, msg -> backupBusy = false; backupMsg = ok to msg }
                        }
                    }
                }
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
                                        onTransfer = { isTransferOpen = true },
                                        holderName = userProfile?.name.orEmpty(),
                                        assetsTotal = assetsTotal,
                                        onOpenAssets = { openSub("assets") }
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
                                            payments = loanPayments,
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
                                        onConfirm = { bank, name, total, paid, inst, day, reminder, kind ->
                                            viewModel.addLoan(bank, name, total, paid, inst, day, reminder, kind)
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
                                        onBack = { closeSub() },
                                        onBiometricChange = { viewModel.setBiometricEnabled(it) },
                                        onAlertsChange = { viewModel.setAlertsEnabled(it) },
                                        onTestLock = { viewModel.lockApp() },
                                        onOpenSms = { openSub("sms_setup") },
                                        onOpenBackup = { openSub("backup") },
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
                                        transactions = transactions,
                                        onBack = { closeSub() },
                                        onAdd = { editingCategoryId = null; openSub("add_category") },
                                        onEdit = { editingCategoryId = it.id; openSub("add_category") },
                                        onDelete = { viewModel.deleteCategory(it.id) },
                                        onSetBudget = { c, amount -> viewModel.setCategoryBudget(c, amount) }
                                    )
                                }

                                "add_category" -> {
                                    val editing = categories.firstOrNull { it.id == editingCategoryId }
                                    com.example.ui.layer.LayerAddCategoryScreen(
                                        existing = editing,
                                        existingNames = categories.map { it.name },
                                        onBack = { editingCategoryId = null; closeSub() },
                                        onSave = { name, icon, color, income, budget ->
                                            if (editing != null) viewModel.updateCategory(editing, name, icon, color, income, budget)
                                            else {
                                                viewModel.addCategory(name, icon, color, income)
                                                // بودجهٔ دستهٔ تازه بعد از ساخته شدن ذخیره می‌شود
                                                if (budget > 0) viewModel.setBudgetForNewCategory(name, budget)
                                            }
                                            editingCategoryId = null
                                            closeSub()
                                        },
                                        onDelete = editing?.let { e -> { viewModel.deleteCategory(e.id); editingCategoryId = null; closeSub() } }
                                    )
                                }

                                "recurring" -> {
                                    com.example.ui.layer.LayerRecurringScreen(
                                        items = recurringItems,
                                        cards = cards,
                                        categories = categories,
                                        transactions = transactions,
                                        onBack = { closeSub() },
                                        onAdd = { t, a, c, e, b, d -> viewModel.addRecurring(t, a, c, e, b, d) },
                                        onToggle = { r, on -> viewModel.setRecurringEnabled(r, on) },
                                        onDelete = { viewModel.deleteRecurring(it.id) }
                                    )
                                }

                                "bills" -> {
                                    com.example.ui.layer.LayerBillsScreen(
                                        bills = bills,
                                        cards = cards,
                                        onBack = { closeSub() },
                                        onSave = { viewModel.saveBill(it) },
                                        onDelete = { viewModel.deleteBill(it.id) },
                                        onPay = { b, amount, account -> viewModel.payBill(b, amount, account) }
                                    )
                                }

                                "year" -> {
                                    com.example.ui.layer.LayerYearScreen(
                                        transactions = transactions,
                                        payments = loanPayments,
                                        debts = debts,
                                        onBack = { closeSub() }
                                    )
                                }

                                "assets" -> {
                                    com.example.ui.layer.LayerAssetsScreen(
                                        assets = assets,
                                        prices = prices,
                                        refreshing = pricesRefreshing,
                                        errors = priceErrors,
                                        accountsTotal = totalBalance,
                                        debtsTotal = loans.filter { !it.isSettled }.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0) } +
                                            personBalances.values.filter { it < 0 }.sumOf { -it },
                                        hideAmounts = false,
                                        onBack = { closeSub() },
                                        onRefresh = { force -> viewModel.refreshPrices(force) },
                                        onAdd = { assetSheetFor = null; isAssetSheetOpen = true },
                                        onOpen = { assetSheetFor = it; isAssetSheetOpen = true },
                                        snapshots = netWorthSnapshots
                                    )
                                }

                                "person_detail" -> {
                                    val person = persons.firstOrNull { it.id == selectedPersonId }
                                    if (person == null) {
                                        LaunchedEffect(Unit) { closeSub() }
                                    } else {
                                        com.example.ui.layer.LayerPersonDetailScreen(
                                            person = person,
                                            balance = personBalances[person.id] ?: 0L,
                                            entries = debts,
                                            cards = cards,
                                            onBack = { closeSub() },
                                            onAddEntry = { entrySheetPersonId = person.id; isEntrySheetOpen = true },
                                            onSettle = { acc -> viewModel.settlePerson(person.id, acc) },
                                            onDeleteEntry = { viewModel.deletePersonEntry(it) },
                                            onEdit = { personSheetFor = person; isPersonSheetOpen = true },
                                            onDelete = {
                                                viewModel.deletePerson(person.id)
                                                closeSub()
                                            },
                                            onShareReminder = {
                                                val text = viewModel.reminderText(person, personBalances[person.id] ?: 0L)
                                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(send, "فرستادن یادآوری"))
                                            }
                                        )
                                    }
                                }

                                "backup" -> {
                                    com.example.ui.layer.LayerBackupScreen(
                                        message = backupMsg,
                                        busy = backupBusy,
                                        onBack = { backupMsg = null; closeSub() },
                                        onCreate = { pass ->
                                            backupBusy = true
                                            backupMsg = null
                                            viewModel.createBackup(pass) { bytes, err ->
                                                if (bytes == null) {
                                                    backupBusy = false
                                                    backupMsg = false to (err ?: "ساخت پشتیبان انجام نشد.")
                                                } else {
                                                    pendingBackupBytes = bytes
                                                    val d = com.example.utils.JalaliDate.today()
                                                    writeFileLauncher.launch("taraz-backup-%04d-%02d-%02d.taraz".format(java.util.Locale.US, d.year, d.month, d.day))
                                                }
                                            }
                                        },
                                        onRestore = { pass ->
                                            backupBusy = true
                                            backupMsg = null
                                            pendingRestorePassword = pass
                                            readFileLauncher.launch(arrayOf("*/*"))
                                        },
                                        auto = autoBackupState,
                                        onAutoEnabled = { viewModel.setAutoBackupEnabled(it) },
                                        onAutoPeriod = { viewModel.setAutoBackupPeriod(it) },
                                        onPickFolder = { folderLauncher.launch(null) },
                                        onAutoPassword = { viewModel.setAutoBackupPassword(it) },
                                        onAutoNow = {
                                            backupBusy = true
                                            backupMsg = null
                                            viewModel.runAutoBackupNow { err ->
                                                backupBusy = false
                                                backupMsg = if (err == null) true to "پشتیبان در پوشهٔ انتخابی ذخیره شد." else false to err
                                            }
                                        },
                                        onExportCsv = {
                                            backupBusy = true
                                            backupMsg = null
                                            viewModel.exportCsv { bytes, err ->
                                                if (bytes == null) {
                                                    backupBusy = false
                                                    backupMsg = false to (err ?: "ساخت فایل انجام نشد.")
                                                } else {
                                                    pendingBackupBytes = bytes
                                                    val d = com.example.utils.JalaliDate.today()
                                                    writeFileLauncher.launch("taraz-transactions-%04d-%02d-%02d.csv".format(java.util.Locale.US, d.year, d.month, d.day))
                                                }
                                            }
                                        }
                                    )
                                }

                                "goals" -> {
                                    com.example.ui.layer.LayerGoalsScreen(
                                        goals = allGoals,
                                        transactions = transactions,
                                        onBack = { closeSub() },
                                        onAddGoal = { t, target, cur, dl -> viewModel.insertGoal(t, target, cur, dl) },
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
                                                        val creditUsed = cards.filter { it.accountType == com.example.data.AccountType.CREDIT && it.balance < 0 }.sumOf { -it.balance }
                                                        val glance = remember(monthlyExpense, monthlyIncome, loans, personBalances, cheques, cards, totalBalance) {
                                                            val loansLeft = loans.filter { !it.isSettled }.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0) }
                                                            val iOwePeople = personBalances.values.filter { it < 0 }.sumOf { -it }
                                                            val owedPeople = personBalances.values.filter { it > 0 }.sum()
                                                            val pending = cheques.filter { it.status == com.example.data.ChequeStatus.PENDING }
                                                            val chequesOut = pending.filter { it.issued }.sumOf { it.amount }
                                                            val chequesIn = pending.filter { !it.issued }.sumOf { it.amount }
                                                            com.example.ui.layer.HomeGlance(
                                                                spent = monthlyExpense,
                                                                income = monthlyIncome,
                                                                owe = loansLeft + iOwePeople + chequesOut + creditUsed,
                                                                oweNote = listOfNotNull(
                                                                    if (loansLeft > 0) "وام" else null,
                                                                    if (iOwePeople > 0) "اشخاص" else null,
                                                                    if (chequesOut > 0) "چک" else null,
                                                                    if (creditUsed > 0) "اعتباری" else null
                                                                ).joinToString("، "),
                                                                owed = owedPeople + chequesIn,
                                                                owedNote = listOfNotNull(
                                                                    personBalances.values.count { it > 0 }.takeIf { it > 0 }?.let { "از ${com.example.utils.JalaliDate.toPersianDigits(it)} نفر" },
                                                                    if (chequesIn > 0) "چک" else null
                                                                ).joinToString(" · "),
                                                                // کارت اعتباری در موجودی منفی است و در بدهی هم آمده؛ یک بار حساب شود
                                                                balance = totalBalance + creditUsed
                                                            )
                                                        }
                                                        val upcoming = remember(loans, loanPayments, bills, cheques, debts, personBalances, cards) {
                                                            val now = System.currentTimeMillis()
                                                            val J = com.example.utils.JalaliDate
                                                            buildList {
                                                                loans.filter { !it.isSettled }.forEach { l ->
                                                                    val due = J.nextDueTimestamp(l.dueDay, now)
                                                                    val paid = loanPayments.any { it.loanId == l.id && it.monthKey == J.fromTimestamp(due).monthKey() }
                                                                    if (!paid && J.daysUntil(due, now) in 0..7) add(
                                                                        com.example.ui.layer.UpcomingItem(if (l.isPurchase) "قسط خرید" else "قسط", "${l.loanName} · ${l.bankName}",
                                                                            l.installmentAmount, due, "پرداخت", 0, primary = true) { loanToPay = l }
                                                                    )
                                                                }
                                                                bills.filter { it.enabled && J.daysUntil(it.dueDate, now) <= 7 }.forEach { b ->
                                                                    add(com.example.ui.layer.UpcomingItem("قبض", b.title.ifBlank { b.kind }, b.amount, b.dueDate, "پرداخت کردم", 1) { billToPay = b })
                                                                }
                                                                cheques.filter { it.status == com.example.data.ChequeStatus.PENDING && J.daysUntil(it.dueDate, now) in -30..7 }.forEach { c ->
                                                                    add(com.example.ui.layer.UpcomingItem(
                                                                        if (c.issued) "چک" else "چک دریافتی", (if (c.issued) "به " else "از ") + c.person,
                                                                        c.amount, c.dueDate, "پاس شد؟", if (c.issued) 2 else 3
                                                                    ) { chequeEditing = c; chequeSheetOpen = true })
                                                                }
                                                                debts.filter { d -> d.dueDate != null && !d.settled && !d.isRepayment && d.personId != null }.forEach { d ->
                                                                    val bal = personBalances[d.personId] ?: 0L
                                                                    val due = d.dueDate ?: return@forEach
                                                                    if (bal != 0L && (bal > 0) == d.owedToMe && J.daysUntil(due, now) in -7..7) add(
                                                                        com.example.ui.layer.UpcomingItem(
                                                                            if (d.owedToMe) "طلب" else "بدهی", (if (d.owedToMe) "از " else "به ") + d.person,
                                                                            kotlin.math.abs(bal), due, if (d.owedToMe) "یادآوری" else "تسویه", if (d.owedToMe) 3 else 2
                                                                        ) { selectedPersonId = d.personId; openSub("person_detail") }
                                                                    )
                                                                }
                                                                cards.filter { it.accountType == com.example.data.AccountType.CREDIT && it.dueDay > 0 && it.balance < 0 }.forEach { c ->
                                                                    val due = J.nextDueTimestamp(c.dueDay, now)
                                                                    if (J.daysUntil(due, now) in 0..7) add(
                                                                        com.example.ui.layer.UpcomingItem("اعتباری", c.bankName, -c.balance, due, "بازپرداخت", 0) { isTransferOpen = true }
                                                                    )
                                                                }
                                                            }.distinctBy { it.kind + it.title + it.at }.sortedBy { it.at }
                                                        }
                                                        com.example.ui.layer.LayerHomeScreen(
                                                            layout = homeLayout,
                                                            tools = homeTools,
                                                            glance = glance,
                                                            upcoming = upcoming,
                                                            onTool = { runTool(it) },
                                                            onOpenAllTools = { allToolsOpen = true },
                                                            onOpenLayout = { homeLayoutOpen = true },
                                                            onOpenObligations = { loansSegment = 0; viewModel.setTab(Tab.LOANS) },
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
                                                            onTransactionClick = { tx -> selectedTransactionForEdit = tx },
                                                            activeGoal = activeGoal,
                                                            onOpenGoals = { openSub("goals") },
                                                            onOpenCategories = { openSub("categories") }
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
                                                            onTransactionClick = { tx -> selectedTransactionForEdit = tx },
                                                            recurringCount = recurringItems.count { it.enabled },
                                                            onOpenRecurring = { openSub("recurring") },
                                                            billsDue = com.example.ui.layer.billsDueSoon(bills),
                                                            onOpenBills = { openSub("bills") },
                                                            onExport = { list ->
                                                                viewModel.exportCsvOf(list) { bytes, _ ->
                                                                    if (bytes != null) {
                                                                        pendingBackupBytes = bytes
                                                                        val d = com.example.utils.JalaliDate.today()
                                                                        writeFileLauncher.launch("taraz-filtered-%04d-%02d-%02d.csv".format(java.util.Locale.US, d.year, d.month, d.day))
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }

                                                    Tab.LOANS -> {
                                                        com.example.ui.layer.LayerObligationsScreen(
                                                            loans = loans,
                                                            persons = persons,
                                                            balances = personBalances,
                                                            onAddLoan = { openSub("add_loan") },
                                                            onPay = { loanToPay = it },
                                                            onOpenLoan = { selectedLoanId = it.id; openSub("loan_detail") },
                                                            onOpenPerson = { selectedPersonId = it.id; openSub("person_detail") },
                                                            onAddPerson = { personSheetFor = null; isPersonSheetOpen = true },
                                                            onAddEntry = { entrySheetPersonId = null; isEntrySheetOpen = true },
                                                            onSplit = { isSplitOpen = true },
                                                            cheques = cheques,
                                                            onAddCheque = { chequeEditing = null; chequeSheetOpen = true },
                                                            onOpenCheque = { chequeEditing = it; chequeSheetOpen = true },
                                                            creditUsed = cards.filter { it.accountType == com.example.data.AccountType.CREDIT && it.balance < 0 }.sumOf { -it.balance },
                                                            segment = loansSegment,
                                                            onSegment = { loansSegment = it }
                                                        )
                                                    }

                                                    Tab.REPORTS -> {
                                                        com.example.ui.layer.LayerReportsScreen(
                                                            transactions = transactions,
                                                            goal = activeGoal,
                                                            onOpenGoals = { openSub("goals") },
                                                            onOpenYear = { openSub("year") },
                                                            onShare = { shareMonthTs = it },
                                                            onOpenWeekly = { weeklyOpen = true },
                                                            onAskAssistant = { prompt ->
                                                                viewModel.prepareChatForOpening(prompt)
                                                                isAiSheetOpen = true
                                                            }
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
                                    onDismiss = { isAddTransactionOpen = false; addWithVoice = false },
                                    onSave = { title, amount, category, isExpense, bankName, date, tags ->
                                        viewModel.addTransaction(title, amount, category, isExpense, bankName, date, tags)
                                    },
                                    tagSuggestions = remember(transactions) {
                                        transactions.flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12)
                                    },
                                    onTransfer = { from, to, amount ->
                                        viewModel.transferBetweenCards(from, to, amount)
                                    },
                                    persons = persons,
                                    personBalances = personBalances,
                                    onPersonMoney = { pid, amount, outgoing, account, note ->
                                        viewModel.recordPersonMoney(pid, amount, outgoing, account, note)
                                    },
                                    isListening = isDictating,
                                    startWithVoice = addWithVoice,
                                    onVoice = { onDraft ->
                                        viewModel.toggleVoiceDictation { spoken -> viewModel.parseSpokenEntry(spoken, onDraft) }
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
                                ,
                                    tagSuggestions = remember(transactions) {
                                        transactions.flatMap { it.tagList }.groupingBy { it }.eachCount()
                                            .entries.sortedByDescending { it.value }.map { it.key }.take(12)
                                    }
                                )
                            }

                            // One sheet for adding an account, wherever it is reached from.
                            if (isAddCardOpen) {
                                com.example.ui.layer.LayerAddAccountSheet(
                                    existingNames = cards.map { it.bankName },
                                    onDismiss = { isAddCardOpen = false },
                                    onConfirm = { name, cardNumber, balance, accountType, limit, dueDay ->
                                        viewModel.addCard(
                                            bankName = name,
                                            cardNumber = cardNumber,
                                            balance = balance,
                                            accountType = accountType,
                                            creditLimit = limit,
                                            dueDay = dueDay
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

                            if (isAssetSheetOpen) {
                                com.example.ui.layer.AssetSheet(
                                    existing = assetSheetFor,
                                    prices = prices,
                                    onDismiss = { isAssetSheetOpen = false },
                                    onSearchFunds = { q, cb -> viewModel.searchFunds(q, cb) },
                                    onSave = { viewModel.saveAsset(it) },
                                    onDelete = { viewModel.deleteAsset(it.id) }
                                )
                            }

                            if (weeklyOpen) {
                                val digest = remember(transactions, loans, cheques, bills) {
                                    com.example.data.WeeklyDigest.build(transactions, loans, cheques, bills)
                                }
                                com.example.ui.layer.WeeklySummarySheet(
                                    digest = digest,
                                    onDismiss = { weeklyOpen = false },
                                    onAskAssistant = { prompt ->
                                        weeklyOpen = false
                                        viewModel.prepareChatForOpening(prompt)
                                        isAiSheetOpen = true
                                    }
                                )
                            }
                            shareMonthTs?.let { ts ->
                                com.example.ui.layer.ShareReportSheet(transactions, ts, onDismiss = { shareMonthTs = null })
                            }

                            if (homeLayoutOpen) {
                                com.example.ui.layer.HomeLayoutSheet(
                                    layout = homeLayout,
                                    tools = homeTools,
                                    onLayout = { viewModel.setHomeLayout(it) },
                                    onTools = { viewModel.setHomeTools(it) },
                                    onAddTool = { allToolsOpen = true },
                                    onReset = { viewModel.resetHomeLayout() },
                                    onDismiss = { homeLayoutOpen = false }
                                )
                            }
                            if (allToolsOpen) {
                                com.example.ui.layer.AllToolsSheet(
                                    pinned = homeTools,
                                    onToggle = { viewModel.toggleHomeTool(it) },
                                    onRun = { id -> allToolsOpen = false; homeLayoutOpen = false; runTool(id) },
                                    onDismiss = { allToolsOpen = false }
                                )
                            }
                            billToPay?.let { b ->
                                com.example.ui.layer.PayBillSheet(b, cards, onDismiss = { billToPay = null }, onPay = { amount, account -> viewModel.payBill(b, amount, account) })
                            }

                            if (chequeSheetOpen) {
                                com.example.ui.layer.ChequeSheet(
                                    cheque = chequeEditing,
                                    cards = cards,
                                    categories = categories,
                                    personNames = persons.map { it.name },
                                    onDismiss = { chequeSheetOpen = false; chequeEditing = null },
                                    onSave = { viewModel.saveCheque(it) },
                                    onStatus = { c, st, acc -> viewModel.setChequeStatus(c, st, acc) },
                                    onDelete = { viewModel.deleteCheque(it) }
                                )
                            }

                            if (isSplitOpen) {
                                com.example.ui.layer.SplitExpenseSheet(
                                    persons = persons,
                                    cards = cards,
                                    categories = categories,
                                    onDismiss = { isSplitOpen = false },
                                    onSave = { t, total, acc, cat, me, ids -> viewModel.splitExpense(t, total, acc, cat, me, ids) }
                                )
                            }

                            // ثبت قرض یا پس‌دادن با یک شخص
                            if (isEntrySheetOpen) {
                                com.example.ui.layer.PersonEntrySheet(
                                    persons = persons,
                                    preselectedId = entrySheetPersonId,
                                    balances = personBalances,
                                    cards = cards,
                                    onDismiss = { isEntrySheetOpen = false },
                                    onAddPerson = { personSheetFor = null; isPersonSheetOpen = true },
                                    onSave = { pid, amount, owed, rep, account, note, due ->
                                        viewModel.addPersonEntry(pid, amount, owed, rep, account, note, due)
                                    }
                                )
                            }

                            // افزودن یا ویرایش شخص (روی شیت ثبت قرض هم باز می‌شود)
                            if (isPersonSheetOpen) {
                                com.example.ui.layer.PersonSheet(
                                    person = personSheetFor,
                                    existingNames = persons.map { it.name },
                                    onDismiss = { isPersonSheetOpen = false },
                                    onSave = { p ->
                                        val isNew = p.id == 0
                                        viewModel.savePerson(p) { id -> if (isNew) entrySheetPersonId = id }
                                        isPersonSheetOpen = false
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

                            // برگرداندن حذف (۶ ثانیه)
                            val undoOffer by viewModel.undo.collectAsStateWithLifecycle()
                            undoOffer?.let { u ->
                                com.example.ui.layer.UndoBar(u.title, u.detail, u.shownAt, viewModel.undoWindowMs) { viewModel.undoDelete() }
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
