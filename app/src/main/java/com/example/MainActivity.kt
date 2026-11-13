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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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
    /** تراکنش پیامکی‌ای که نوتیفیکیشنش زده شد (همراه [quickAction] = SMS_REVIEW)؛ ۰ یعنی هیچ. */
    private val quickTargetId = mutableStateOf(0)
    /** با هر بار جلو آمدن برنامه یکی بیشتر می‌شود؛ شیت «تراکنش‌های تازه» با آن دوباره بررسی می‌شود. */
    private val resumeTick = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickAction.value = intent.action?.takeIf { it.startsWith("taraz.dezhcode.app.") }
        quickTargetId.value = intent.getIntExtra(com.example.utils.NotificationHelper.EXTRA_TARGET_ID, 0)
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
        resumeTick.intValue++
        // هر بار که تراز جلو می‌آید، پیامک‌های بانکی تازهٔ صندوق ورودی خوانده می‌شوند؛ شیت تراکنش‌ها نشانشان می‌دهد.
        if (com.example.Features.SMS) {
            lifecycleScope.launch { com.example.data.SmsCatchUp.run(this@MainActivity, notify = false) }
        }
    }

    private companion object {
        const val LOCK_GRACE_MS = 30_000L
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            quickAction.value = intent?.action?.takeIf { it.startsWith("taraz.dezhcode.app.") }
            quickTargetId.value = intent?.getIntExtra(com.example.utils.NotificationHelper.EXTRA_TARGET_ID, 0) ?: 0
        }
        enableEdgeToEdge()

        com.example.utils.NotificationHelper.createNotificationChannel(this)
        // دیتابیس الگوی پیامک بانک‌ها را از پیش بخوان تا صفحهٔ پیامک و نخستین پیامک منتظر نمانند.
        Thread({ com.example.sms.data.parser.BankPatternDb.warmUp() }, "sms-patterns").start()
        requestNotificationPermissionIfNeeded()
        com.example.reminders.ReminderScheduler.scheduleDailyLoanCheck(this)
        com.example.reminders.ReminderScheduler.scheduleWeeklySummary(this)
        // پیامک‌های بانکی صندوق ورودی هر ۱۵ دقیقه، برای گوشی‌هایی که پیامک تازه را به برنامه اعلام نمی‌کنند
        com.example.reminders.ReminderScheduler.scheduleSmsCatchUp(this)

        // واحد پولی دفتر (تومان/ریال/دلار) پیش از هر نمایش مبلغ
        com.example.data.Money.load(this)
        com.example.ui.layer.Haptics.load(this)
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
                val assetsTotal = assets.sumOf { com.example.data.valueOf(it, prices) }
                var assetSheetFor by remember { mutableStateOf<com.example.data.Asset?>(null) }
                var isAssetSheetOpen by remember { mutableStateOf(false) }
                var assetPreset by remember { mutableStateOf<Pair<String, String>?>(null) }
                val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
                val assetTrades by viewModel.assetTrades.collectAsStateWithLifecycle()
                val defaultAccount by viewModel.defaultAccount.collectAsStateWithLifecycle()
                val priceAlerts by viewModel.priceAlerts.collectAsStateWithLifecycle()
                var detailCode by remember { mutableStateOf<String?>(null) }
                var tradeOpen by remember { mutableStateOf(false) }
                var tradeBuy by remember { mutableStateOf(true) }
                var tradeEditing by remember { mutableStateOf<com.example.data.AssetTrade?>(null) }
                var alertOpen by remember { mutableStateOf(false) }
                fun detailName(code: String): String =
                    assets.firstOrNull { it.code == code }?.name ?: com.example.data.PriceCatalog.find(code)?.name ?: code

                // Overlays states
                var isAddTransactionOpen by remember { mutableStateOf(false) }
                var addWithVoice by remember { mutableStateOf(false) }
                var initialIsExpenseForDialog by remember { mutableStateOf(true) }
                val pendingQuick = quickAction.value
                var weeklyOpen by remember { mutableStateOf(false) }
                // تغییر واحد پول (تنظیمات)
                var currencyOpen by remember { mutableStateOf(false) }
                var currencyBusy by remember { mutableStateOf(false) }
                var currencyError by remember { mutableStateOf<String?>(null) }
                // شیت «تراکنش‌های تازه»: با باز شدن برنامه، نوتیفیکیشن یا بنر خانه
                var pendingSheetOpen by remember { mutableStateOf(false) }
                var pendingFocusId by remember { mutableStateOf<Int?>(null) }
                var reviewFocusId by remember { mutableStateOf<Int?>(null) }
                fun openPendingSheet(focus: Int? = null) {
                    if (!com.example.Features.SMS) return
                    pendingFocusId = focus
                    pendingSheetOpen = true
                }
                LaunchedEffect(pendingQuick) {
                    when (pendingQuick) {
                        com.example.data.SmsQuickAction.ACTION_REVIEW -> openPendingSheet(quickTargetId.value.takeIf { it > 0 })
                        com.example.reminders.WeeklySummaryWorker.ACTION_OPEN -> { viewModel.setTab(Tab.REPORTS); weeklyOpen = true }
                        com.example.widget.QuickAction.ADD_EXPENSE -> { initialIsExpenseForDialog = true; addWithVoice = false; isAddTransactionOpen = true }
                        com.example.widget.QuickAction.ADD_INCOME -> { initialIsExpenseForDialog = false; addWithVoice = false; isAddTransactionOpen = true }
                        com.example.widget.QuickAction.VOICE -> { initialIsExpenseForDialog = true; addWithVoice = com.example.Features.VOICE; isAddTransactionOpen = true }
                    }
                    if (pendingQuick != null) quickAction.value = null
                }
                val sheetContext = LocalContext.current
                val newestPendingId = pendingSms.maxOfOrNull { it.id } ?: 0
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
                var txFilter by remember { mutableStateOf(com.example.ui.layer.TxFilter()) }
                var txFilterOpen by remember { mutableStateOf(false) }
                var allToolsOpen by remember { mutableStateOf(false) }
                var billToPay by remember { mutableStateOf<com.example.data.Bill?>(null) }
                var creditToRepay by remember { mutableStateOf<com.example.data.BankCard?>(null) }
                var searchOpen by remember { mutableStateOf(false) }
                // پیام کوتاه درون‌برنامه (با لوگوی تازه) به‌جای Toast سیستم
                var appToast by remember { mutableStateOf<Pair<String, Long>?>(null) }
                fun showToast(msg: String) { appToast = msg to System.currentTimeMillis() }
                var loansSegment by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

                // صفحهٔ پشتیبان رمز پشتیبان خودکار را دارد؛ با قفل روشن، اول هویت تأیید شود
                fun openBackupGuarded() {
                    val act = this@MainActivity
                    if (biometricEnabled && com.example.utils.BiometricHelper.isBiometricAvailable(act)) {
                        com.example.utils.BiometricHelper.showBiometricPrompt(
                            activity = act,
                            title = "پشتیبان تراز",
                            subtitle = "برای مشاهدهٔ پشتیبان و رمز آن، هویت خود را تأیید کنید",
                            onSuccess = { openSub("backup") },
                            onError = { msg -> showToast(msg.toString()) }
                        )
                    } else openSub("backup")
                }

                fun runTool(id: String) {
                    when (id) {
                        "expense" -> { initialIsExpenseForDialog = true; addWithVoice = false; isAddTransactionOpen = true }
                        "income" -> { initialIsExpenseForDialog = false; addWithVoice = false; isAddTransactionOpen = true }
                        "voice" -> if (com.example.Features.VOICE) { initialIsExpenseForDialog = true; addWithVoice = true; isAddTransactionOpen = true }
                            else showToast("ثبت صوتی به‌زودی فعال می‌شود")
                        "transfer" -> isTransferOpen = true
                        "sms" -> if (com.example.Features.SMS) { if (pendingSms.isNotEmpty()) openPendingSheet() else viewModel.setSmsAssistantSheetOpen(true) }
                            else showToast("ثبت خودکار از پیامک بانک به‌زودی فعال می‌شود")
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
                        "search" -> searchOpen = true
                        "backup" -> openBackupGuarded()
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
                            val unitBefore = com.example.data.Money.current
                            viewModel.restoreBackup(bytes, pass) { ok, msg ->
                                backupBusy = false
                                backupMsg = ok to msg
                                // پشتیبان با واحد پول دیگری بود: صفحه‌ها با واحد تازه از نو ساخته شوند
                                if (ok && com.example.data.Money.current != unitBefore) {
                                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                    this@MainActivity.recreate()
                                }
                            }
                        }
                    }
                }
                // ─── گوگل درایو: یک فایل پشتیبان در درایو که تراز هر بار رویش می‌نویسد ───
                var pendingDrivePassword by remember { mutableStateOf<String?>(null) }
                fun restoreBytes(bytes: ByteArray, pass: String, onOk: () -> Unit = {}) {
                    val unitBefore = com.example.data.Money.current
                    viewModel.restoreBackup(bytes, pass) { ok, msg ->
                        backupBusy = false
                        backupMsg = ok to msg
                        if (ok) onOk()
                        if (ok && com.example.data.Money.current != unitBefore) {
                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                            this@MainActivity.recreate()
                        }
                    }
                }
                val driveCreateLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    val pass = pendingDrivePassword
                    pendingDrivePassword = null
                    if (uri == null) {
                        backupBusy = false
                    } else {
                        viewModel.connectDrive(uri, pass) { err ->
                            backupBusy = false
                            backupMsg = if (err == null) true to "به گوگل درایو وصل شد و پشتیبان ذخیره شد." else false to err
                        }
                    }
                }
                val driveOpenLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
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
                            // بازگردانی موفق: همان فایل برای پشتیبان‌های بعدی وصل می‌ماند
                            restoreBytes(bytes, pass) { viewModel.linkDriveFile(uri) }
                        }
                    }
                }

                val activity = context as? androidx.fragment.app.FragmentActivity
                var lastBackPressTime by remember { mutableStateOf(0L) }
                val exitToastMessage = "برای خروج، دکمهٔ بازگشت را دوباره بزنید"

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
                            showToast(exitToastMessage)
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
                var splashDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    splashDone = true
                }

                // «+ … جدید» در هر انتخابگر: برگهٔ ساختن روی همان صفحه باز می‌شود
                var quickCategory by remember { mutableStateOf<Boolean?>(null) }
                val creators = remember {
                    com.example.ui.layer.Creators(
                        addAccount = { isAddCardOpen = true },
                        addCategory = { quickCategory = it },
                        addPerson = { personSheetFor = null; isPersonSheetOpen = true }
                    )
                }

                // راهنمای گام‌به‌گام صفحه‌ها (بار اول هر صفحه)
                val tourState = remember { com.example.ui.layer.TourState() }

                // شیت «تراکنش‌های تازه» خودش باز می‌شود: وقتی تراز جلو می‌آید یا تراکنش تازه‌ای می‌رسد، ولی
                // نه وسط کار کاربر (روی برگه یا فرم دیگری که باز است). آن‌وقت بنر خانه و نوتیفیکیشن می‌مانند
                // و بار بعد که برنامه جلو بیاید باز می‌شود.
                val resumed = resumeTick.intValue
                LaunchedEffect(newestPendingId, resumed, screenState, isAppLocked, activeSubScreen) {
                    if (screenState != Screen.MAIN || isAppLocked) return@LaunchedEffect
                    if (newestPendingId <= com.example.data.SmsPrefs.sheetSeenMaxId(sheetContext)) return@LaunchedEffect
                    val busy = isAddTransactionOpen || selectedTransactionForEdit != null || isAiSheetOpen || isBudgetOpen ||
                        isAddCardOpen || accountToEdit != null || isTransferOpen || isAssetSheetOpen || loanToPay != null ||
                        isPersonSheetOpen || isEntrySheetOpen || isSplitOpen || chequeSheetOpen || shareMonthTs != null ||
                        homeLayoutOpen || txFilterOpen || allToolsOpen || billToPay != null || creditToRepay != null ||
                        searchOpen || weeklyOpen || quickCategory != null || showSmsAssistantSheet ||
                        activeSubScreen in setOf("add_loan", "add_category", "backup", "sms_setup")
                    if (!busy) openPendingSheet(pendingFocusId)
                }

                CompositionLocalProvider(
                    com.example.ui.layer.LocalCreators provides creators,
                    com.example.ui.layer.LocalTour provides tourState
                ) {
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
                                        onOpenAssets = { openSub("assets") },
                                        assets = remember(assets, prices) { com.example.ui.layer.assetsSummaryOf(assets, prices) },
                                        onEditAccount = { accountToEdit = it },
                                        onToggleSms = { viewModel.toggleBankSender(it) },
                                        onOpenSmsSetup = { openSub("sms_setup") },
                                        onDeleteAccount = { viewModel.deleteCard(it.id) },
                                        defaultAccount = defaultAccount,
                                        onSetDefault = { viewModel.setDefaultAccount(it) }
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
                                            onRepay = { creditToRepay = card },
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
                                        onOpenBackup = { openBackupGuarded() },
                                        onOpenPrivacy = { openSub("privacy") },
                                        onOpenCurrency = { currencyOpen = true },
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

                                "privacy" -> {
                                    com.example.ui.layer.LayerPrivacyScreen(onBack = { closeSub() })
                                }

                                "sms_setup" -> {
                                    com.example.ui.layer.LayerSmsSetupScreen(
                                        viewModel = viewModel,
                                        onBack = { closeSub() },
                                        onOpenReview = { openPendingSheet() },
                                        onOpenUnread = { openSub("sms_unread") }
                                    )
                                }

                                "sms_unread" -> {
                                    com.example.ui.layer.LayerSmsUnreadScreen(
                                        viewModel = viewModel,
                                        onBack = { closeSub() },
                                        onManualEntry = { closeSub(); initialIsExpenseForDialog = true; addWithVoice = false; isAddTransactionOpen = true }
                                    )
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
                                        onAdd = { assetSheetFor = null; assetPreset = null; isAssetSheetOpen = true },
                                        onOpen = { assetSheetFor = it; assetPreset = null; isAssetSheetOpen = true },
                                        snapshots = netWorthSnapshots,
                                        history = priceHistory,
                                        onAddCode = { code ->
                                            val name = com.example.data.PriceCatalog.find(code)?.name ?: assets.firstOrNull { it.code == code }?.name.orEmpty()
                                            assetSheetFor = null; assetPreset = code to name; isAssetSheetOpen = true
                                        },
                                        onOpenDetail = { code -> detailCode = code; openSub("asset_detail") }
                                    )
                                }

                                "asset_detail" -> {
                                    val code = detailCode
                                    if (code == null) {
                                        LaunchedEffect(Unit) { closeSub() }
                                    } else {
                                        com.example.ui.layer.LayerAssetDetailScreen(
                                            code = code,
                                            name = detailName(code),
                                            holding = assets.firstOrNull { it.code == code },
                                            quote = prices[code],
                                            tomanNow = viewModel.tomanPrice(code),
                                            history = priceHistory[code].orEmpty(),
                                            trades = assetTrades.filter { it.code == code },
                                            alerts = priceAlerts.filter { it.code == code },
                                            usdToman = viewModel.usdTomanRate(),
                                            hideAmounts = false,
                                            onBack = { closeSub() },
                                            onEdit = { assetSheetFor = it; assetPreset = null; isAssetSheetOpen = true },
                                            onTrade = { buy -> tradeBuy = buy; tradeEditing = null; tradeOpen = true },
                                            onDeleteTrade = { viewModel.deleteTrade(it) },
                                            onEditTrade = { t -> tradeEditing = t; tradeBuy = t.isBuy; tradeOpen = true },
                                            onAddAlert = { alertOpen = true },
                                            onToggleAlert = { viewModel.toggleAlert(it) },
                                            onDeleteAlert = { viewModel.deleteAlert(it) }
                                        )
                                    }
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
                                                context.startActivity(android.content.Intent.createChooser(send, "ارسال یادآوری"))
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
                                        driveInstalled = remember { viewModel.driveAppInstalled() },
                                        onDriveConnect = { pass ->
                                            backupBusy = true
                                            backupMsg = null
                                            pendingDrivePassword = pass
                                            driveCreateLauncher.launch("taraz-backup.taraz")
                                        },
                                        onDriveSaveNow = {
                                            backupBusy = true
                                            backupMsg = null
                                            viewModel.backupToDrive { err ->
                                                backupBusy = false
                                                backupMsg = if (err == null) true to "پشتیبان در گوگل درایو ذخیره شد." else false to err
                                            }
                                        },
                                        onDriveRestore = { pass ->
                                            backupBusy = true
                                            backupMsg = null
                                            viewModel.readDriveBackup { bytes, err ->
                                                if (bytes == null) {
                                                    backupBusy = false
                                                    backupMsg = false to (err ?: "فایل درایو خوانده نشد.")
                                                } else restoreBytes(bytes, pass)
                                            }
                                        },
                                        onDriveOpenExisting = { pass ->
                                            backupBusy = true
                                            backupMsg = null
                                            pendingRestorePassword = pass
                                            driveOpenLauncher.launch(arrayOf("*/*"))
                                        },
                                        onDriveDisconnect = { viewModel.disconnectDrive(); backupMsg = true to "اتصال گوگل درایو قطع شد؛ فایل در درایو می‌ماند." },
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
                                        onReviewSms = { openPendingSheet() },
                                        payments = loanPayments
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
                                                                    add(com.example.ui.layer.UpcomingItem("قبض", b.title.ifBlank { b.kind }, b.amount, b.dueDate, "ثبت پرداخت", 1) { billToPay = b })
                                                                }
                                                                cheques.filter { it.status == com.example.data.ChequeStatus.PENDING && J.daysUntil(it.dueDate, now) in -30..7 }.forEach { c ->
                                                                    add(com.example.ui.layer.UpcomingItem(
                                                                        if (c.issued) "پرداخت چک" else "دریافت چک", (if (c.issued) "به " else "از ") + c.person,
                                                                        c.amount, c.dueDate, "وصول شد؟", if (c.issued) 2 else 3
                                                                    ) { chequeEditing = c; chequeSheetOpen = true })
                                                                }
                                                                debts.filter { d -> d.dueDate != null && !d.settled && !d.isRepayment && d.personId != null }.forEach { d ->
                                                                    val bal = personBalances[d.personId] ?: 0L
                                                                    val due = d.dueDate ?: return@forEach
                                                                    if (bal != 0L && (bal > 0) == d.owedToMe && J.daysUntil(due, now) in -7..7) add(
                                                                        com.example.ui.layer.UpcomingItem(
                                                                            if (d.owedToMe) "دریافت از شخص" else "پرداخت به شخص", (if (d.owedToMe) "از " else "به ") + d.person,
                                                                            kotlin.math.abs(bal), due, if (d.owedToMe) "یادآوری" else "تسویه", if (d.owedToMe) 3 else 2
                                                                        ) { selectedPersonId = d.personId; openSub("person_detail") }
                                                                    )
                                                                }
                                                                cards.filter { it.accountType == com.example.data.AccountType.CREDIT && it.dueDay > 0 && it.balance < 0 }.forEach { c ->
                                                                    val due = J.nextDueTimestamp(c.dueDay, now)
                                                                    if (J.daysUntil(due, now) in 0..7) add(
                                                                        com.example.ui.layer.UpcomingItem("اعتباری", c.bankName, -c.balance, due, "بازپرداخت", 0) { creditToRepay = c }
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
                                                            onOpenSearch = { searchOpen = true },
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
                                                            onOpenSmsInbox = { openPendingSheet() },
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
                                                                val cat = com.example.data.SmsApproval.guessCategory(p, categories)
                                                                viewModel.approvePendingTransaction(p, p.title.ifBlank { "تراکنش بانکی" }, cat, p.amount, null, p.isExpense)
                                                            },
                                                            onDismissSms = { p -> viewModel.rejectPendingTransaction(p.id) },
                                                            onReviewSms = { p -> openPendingSheet(p.id) },
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
                                                            filter = txFilter,
                                                            onFilterChange = { txFilter = it },
                                                            onOpenFilter = { txFilterOpen = true },
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
                                                            onSegment = { loansSegment = it },
                                                            loanPayments = loanPayments
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
                                     containerColor = com.example.ui.layer.L.Bg
                                 ) {
                                     Box(modifier = Modifier.fillMaxHeight(0.88f)) {
                                         if (!com.example.Features.AI) {
                                             com.example.ui.layer.ComingSoonPanel(
                                                 title = "دستیار هوشمند تراز",
                                                 body = "به‌زودی می‌توانید دربارهٔ هزینه‌ها و حساب‌های خود پرسش کنید و تراکنش‌ها را با یک جمله ثبت کنید.",
                                                 points = listOf(
                                                     "تحلیل هزینه‌های ماهانه",
                                                     "ثبت تراکنش با یک جمله",
                                                     "تبدیل گفتار به متن"
                                                 ),
                                                 onClose = { isAiSheetOpen = false }
                                             )
                                         } else run {
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
                                    // حساب پیش‌فرض اول، تا از پیش انتخاب شود
                                    cards = remember(cards, defaultAccount) { cards.sortedByDescending { it.bankName == defaultAccount } },
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
                                    payments = loanPayments,
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
                                    onDelete = { viewModel.deleteAsset(it.id) },
                                    preset = assetPreset,
                                    trades = assetSheetFor?.let { a -> assetTrades.filter { it.assetId == a.id || it.code == a.code } }.orEmpty(),
                                    onDeleteWithTrades = { a, undo -> viewModel.deleteAssetWithTrades(a, undo) }
                                )
                            }

                            if (tradeOpen) detailCode?.let { code ->
                                val holding = assets.firstOrNull { it.code == code }
                                val name = detailName(code)
                                val kind = holding?.kind ?: com.example.data.PriceCatalog.find(code)?.kind
                                    ?: if (com.example.data.PriceCatalog.isFund(code)) com.example.data.AssetKind.FUND else com.example.data.AssetKind.CURRENCY
                                com.example.ui.layer.TradeSheet(
                                    name = name,
                                    unitName = com.example.data.PriceCatalog.find(code)?.unit ?: "واحد",
                                    holding = holding,
                                    startBuy = tradeBuy,
                                    livePrice = holding?.manualPrice?.takeIf { it > 0 }?.toDouble() ?: prices[code]?.exact,
                                    cards = cards,
                                    onDismiss = { tradeOpen = false; tradeEditing = null },
                                    onSave = { isBuy, q, total, date, bank ->
                                        val old = tradeEditing
                                        if (old != null) viewModel.updateTrade(old, isBuy, q, total, date, bank)
                                        else viewModel.recordTrade(holding, code, name, kind, isBuy, q, total, date, bank)
                                    },
                                    editing = tradeEditing,
                                    onDelete = tradeEditing?.let { t -> { viewModel.deleteTrade(t) } }
                                )
                            }

                            if (alertOpen) detailCode?.let { code ->
                                val inUsd = com.example.ui.layer.inDollars(code) && prices[code]?.usd != null
                                com.example.ui.layer.AlertSheet(
                                    name = detailName(code),
                                    inUsd = inUsd,
                                    current = if (inUsd) prices[code]?.usd else prices[code]?.exact,
                                    onDismiss = { alertOpen = false },
                                    onSave = { above, below -> viewModel.addAlert(code, detailName(code), inUsd, above, below) }
                                )
                            }

                            // جشن پرداخت قسط آخر: کاغذرنگی، لرزش جشن و پیام کوتاه
                            val celebration by viewModel.celebration.collectAsStateWithLifecycle()
                            celebration?.let { msg ->
                                val buzz = com.example.ui.layer.rememberBuzz()
                                LaunchedEffect(msg) { buzz(com.example.ui.layer.Buzz.PARTY) }
                                Box(Modifier.fillMaxSize()) {
                                    com.example.ui.layer.Confetti(onDone = { viewModel.celebrated() })
                                    Box(
                                        Modifier.align(Alignment.Center).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                            .background(Color(0xE6161A22)).padding(horizontal = 22.dp, vertical = 14.dp)
                                    ) { Text(msg, style = com.example.ui.layer.lt(17.sp, FontWeight.ExtraBold, Color.White)) }
                                }
                            }

                            if (currencyOpen) {
                                com.example.ui.layer.CurrencySheet(
                                    current = com.example.data.Money.current,
                                    suggestedUsdToman = viewModel.usdTomanRate(),
                                    busy = currencyBusy,
                                    error = currencyError,
                                    onDismiss = { if (!currencyBusy) { currencyOpen = false; currencyError = null } },
                                    onOpenBackup = { currencyOpen = false; currencyError = null; openBackupGuarded() },
                                    onConvert = { to, rate ->
                                        currencyBusy = true
                                        currencyError = null
                                        viewModel.changeCurrency(to, rate) { err ->
                                            currencyBusy = false
                                            if (err != null) currencyError = err
                                            else {
                                                currencyOpen = false
                                                // همهٔ صفحه‌ها با واحد تازه از نو ساخته شوند
                                                this@MainActivity.recreate()
                                            }
                                        }
                                    }
                                )
                            }

                            if (weeklyOpen) {
                                val digest = remember(transactions, loans, cheques, bills, loanPayments) {
                                    com.example.data.WeeklyDigest.build(transactions, loans, cheques, bills, payments = loanPayments)
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

                            if (txFilterOpen) {
                                com.example.ui.layer.TxFilterSheet(
                                    current = txFilter,
                                    transactions = transactions,
                                    cards = cards,
                                    categories = categories,
                                    onDismiss = { txFilterOpen = false },
                                    onApply = { txFilter = it }
                                )
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
                            if (searchOpen) {
                                com.example.ui.layer.HomeSearchSheet(
                                    transactions = transactions,
                                    cards = cards,
                                    categories = categories,
                                    persons = persons,
                                    balances = personBalances,
                                    bills = bills,
                                    cheques = cheques,
                                    onTool = { runTool(it) },
                                    onTransaction = { selectedTransactionForEdit = it },
                                    onPerson = { selectedPersonId = it.id; openSub("person_detail") },
                                    onBills = { openSub("bills") },
                                    onCheque = { chequeEditing = it; chequeSheetOpen = true },
                                    onDismiss = { searchOpen = false }
                                )
                            }
                            creditToRepay?.let { c ->
                                com.example.ui.layer.RepayCreditSheet(
                                    card = cards.firstOrNull { it.id == c.id } ?: c,
                                    cards = cards,
                                    onDismiss = { creditToRepay = null },
                                    onRepay = { from, amount -> viewModel.transferBetweenCards(from, c.bankName, amount) }
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

                            // ساختن دستهٔ جدید از دل یک انتخابگر (روی همهٔ برگه‌ها)
                            quickCategory?.let { startIncome ->
                                BackHandler { quickCategory = null }
                                Box(
                                    Modifier.fillMaxSize().background(com.example.ui.layer.L.Bg)
                                        // لمس‌ها به برگه‌های زیرین نرسد
                                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                                ) {
                                    com.example.ui.layer.LayerAddCategoryScreen(
                                        existingNames = categories.map { it.name },
                                        startIncome = startIncome,
                                        onBack = { quickCategory = null },
                                        onSave = { name, icon, color, income, budget ->
                                            viewModel.addCategory(name, icon, color, income)
                                            if (budget > 0) viewModel.setBudgetForNewCategory(name, budget)
                                            quickCategory = null
                                        }
                                    )
                                }
                            }

                            // شیت «تراکنش‌های تازه»؛ ویرایش یک ردیف صفحهٔ کامل بررسی را رویش باز می‌کند
                            if (pendingSheetOpen && pendingSms.isNotEmpty() && !showSmsAssistantSheet) {
                                com.example.ui.layer.LayerPendingSheet(
                                    pending = pendingSms,
                                    categories = categories,
                                    cards = cards,
                                    senders = bankSenders,
                                    focusId = pendingFocusId,
                                    onApprove = { p, category ->
                                        viewModel.approvePendingTransaction(p, p.title.ifBlank { "تراکنش بانکی" }, category, p.amount, null, p.isExpense)
                                    },
                                    onApproveAll = { viewModel.approvePendingBatch(it) },
                                    onReject = { p -> viewModel.rejectPendingTransaction(p.id) },
                                    onEdit = { p -> reviewFocusId = p.id; viewModel.setSmsAssistantSheetOpen(true) },
                                    onLater = {
                                        com.example.data.SmsPrefs.setSheetSeenMaxId(sheetContext, newestPendingId)
                                        pendingSheetOpen = false
                                        pendingFocusId = null
                                    }
                                )
                            }

                            // بررسی پیامک‌ها تمام‌صفحه روی همه چیز
                            if (showSmsAssistantSheet) {
                                com.example.ui.layer.LayerSmsReviewScreen(
                                    viewModel = viewModel,
                                    pending = pendingSms.sortedByDescending { it.id == reviewFocusId },
                                    categories = categories,
                                    cards = cards,
                                    onBack = { reviewFocusId = null; viewModel.setSmsAssistantSheetOpen(false) }
                                )
                            }

                            // لایهٔ راهنما بالای همهٔ صفحه‌ها و برگه‌ها
                            com.example.ui.layer.TourHost(tourState)

                            // برگرداندن حذف (۶ ثانیه)
                            val undoOffer by viewModel.undo.collectAsStateWithLifecycle()
                            undoOffer?.let { u ->
                                com.example.ui.layer.UndoBar(u.title, u.detail, u.shownAt, viewModel.undoWindowMs) { viewModel.undoDelete() }
                            }
                            appToast?.let { (msg, at) ->
                                com.example.ui.layer.AppToast(msg, at) { if (appToast?.second == at) appToast = null }
                            }

                            // موجودی با پیامک بانک نمی‌خواند
                            val mismatch by viewModel.balanceMismatch.collectAsStateWithLifecycle()
                            mismatch?.let { m ->
                                com.example.ui.layer.LayerSheet(
                                    title = "موجودی ${m.account} با پیامک بانک فرق دارد",
                                    onDismiss = { viewModel.dismissBalanceMismatch() },
                                    closeLabel = "بعداً",
                                    bottomBar = {
                                        com.example.ui.layer.BigButton("موجودی را ${com.example.ui.layer.money(m.inSms)} کن") { viewModel.acceptSmsBalance() }
                                    }
                                ) {
                                    androidx.compose.material3.Text(
                                        "در تراز: ${com.example.ui.layer.money(m.inApp)} ${com.example.data.Money.label}\n" +
                                            "طبق پیامک بانک: ${com.example.ui.layer.money(m.inSms)} ${com.example.data.Money.label}\n\n" +
                                            "معمولاً یعنی تراکنشی بیرون از تراز ثبت نشده یا موجودی اول درست وارد نشده بود.",
                                        style = com.example.ui.layer.lt(14.sp, lineHeight = 24.sp)
                                    )
                                }
                            }
                        }
                    }
                
                    // اسپلش طرح «صفحه خودِ لوگو» بلافاصله بعد از اسپلش سیستمی؛ فقط هنگام باز شدن برنامه
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !splashDone,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(380))
                    ) { com.example.ui.layer.TarazSplash() }
                }
                } // LocalCreators
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
