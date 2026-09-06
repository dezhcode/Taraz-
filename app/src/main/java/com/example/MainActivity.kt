package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.example.ui.components.AiFloatingButton
import com.example.ui.components.CardDetailBottomSheet
import com.example.ui.components.AddCardBottomSheet

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
                    if (showSmsAssistantSheet) {
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
                    color = BackgroundLight
                ) {
                    if (isAppLocked) {
                        BiometricLockScreen(
                            onUnlockSuccess = { viewModel.unlockApp() },
                            onUnlockFallback = { viewModel.unlockApp() }
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
                                        aiServerUrl = aiServerUrl,
                                        onBiometricChange = { viewModel.setBiometricEnabled(it) },
                                        onAlertsChange = { viewModel.setAlertsEnabled(it) },
                                        onAiServerUrlChange = { viewModel.setAiServerUrl(it) },
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
                                                .background(BackgroundLight)
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
                                                        HorizonHomeScreen(
                                                            userProfile = userProfile,
                                                            totalBalance = totalBalance,
                                                            monthlyIncome = monthlyIncome,
                                                            monthlyExpense = monthlyExpense,
                                                            todayExpense = todayExpense,
                                                            dailyBudget = dailyBudget,
                                                            onSetDailyBudget = { viewModel.setDailyBudget(it) },
                                                            transactions = transactions,
                                                            cards = cards,
                                                            loans = loans,
                                                            activeGoal = activeGoal,
                                                            onSaveGoal = { title, target, saved ->
                                                                viewModel.insertGoal(title, target, saved)
                                                            },
                                                            onDeleteGoal = { goalId -> viewModel.deleteGoal(goalId) },
                                                            onCardClick = { card -> selectedCardForDetail = card },
                                                            onNavigateToCards = { activeSubScreen = "banks" },
                                                            onNavigateToLoans = { activeSubScreen = "loans" },
                                                            onNavigateToSettings = { activeSubScreen = "settings" },
                                                            onSeeAllTransactions = { viewModel.setTab(Tab.TRANSACTIONS) },
                                                            onTransactionClick = { tx -> selectedTransactionForEdit = tx }
                                                        )
                                                    }

                                                    Tab.TRANSACTIONS -> {
                                                        TransactionsScreen(
                                                            transactions = transactions,
                                                            onDeleteTransaction = { tx ->
                                                                viewModel.deleteTransaction(tx)
                                                            },
                                                            onAddTransactionClick = {
                                                                initialIsExpenseForDialog = true
                                                                isAddTransactionOpen = true
                                                            },
                                                            onTransactionClick = { tx ->
                                                                selectedTransactionForEdit = tx
                                                            }
                                                        )
                                                    }

                                                    Tab.LOANS -> {
                                                        LoansSubScreen(
                                                            loans = loans,
                                                            cards = cards,
                                                            onBack = { viewModel.setTab(Tab.HOME) },
                                                            onPayInstallment = { loan ->
                                                                viewModel.payInstallment(loan)
                                                            },
                                                            onAddLoan = { bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay ->
                                                                viewModel.addLoan(bankName, loanName, totalAmount, paidAmount, installmentAmount, dueDay)
                                                            }
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
                                            
                                            // HorizonBottomBar floating on top
                                            if (!isKeyboardVisible) {
                                                HorizonBottomBar(
                                                    currentTab = tabState,
                                                    onTabSelected = { viewModel.setTab(it) },
                                                    onAddClick = {
                                                        initialIsExpenseForDialog = true
                                                        isAddTransactionOpen = true
                                                    },
                                                    modifier = Modifier.align(Alignment.BottomCenter)
                                                )
                                            }

                                             // AI Assistant Floating Action Button (with contextual advice bubble)
                                             if (!isKeyboardVisible) {
                                                 AiFloatingButton(
                                                     currentTab = tabState,
                                                     transactions = transactions,
                                                     loans = loans,
                                                     totalBalance = totalBalance,
                                                     monthlyIncome = monthlyIncome,
                                                     monthlyExpense = monthlyExpense,
                                                     onClick = { prefilledPrompt ->
                                                         if (prefilledPrompt != null) {
                                                             viewModel.sendChatMessage(prefilledPrompt)
                                                         }
                                                         isAiSheetOpen = true
                                                     },
                                                     modifier = Modifier
                                                         .align(Alignment.BottomEnd)
                                                         .padding(bottom = 100.dp, end = 16.dp)
                                                 )
                                             }
                                        }
                                    }
                                }
                            }

                            // AI Assistant bottom sheet overlay
                             if (isAiSheetOpen) {
                                 ModalBottomSheet(
                                     onDismissRequest = { isAiSheetOpen = false },
                                     sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                     dragHandle = { BottomSheetDefaults.DragHandle() },
                                     containerColor = BackgroundLight
                                 ) {
                                     Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                                         AiAssistantScreen(
                                             messages = chatMessages, toastMessage = chatErrorToast, onDismissToast = { viewModel.clearChatErrorToast() },
                                             isLoading = isAiLoading,
                                             onOpenChat = { viewModel.prepareChatForOpening() },
                                             onApproveAction = { viewModel.executeProposedAction(it) },
                                             onCancelAction = { viewModel.cancelProposedAction(it) },
                                             onSendMessage = { prompt ->
                                                 viewModel.sendChatMessage(prompt)
                                             }
                                         )
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
                                AddEntrySheet(
                                    cards = cards,
                                    categories = categories,
                                    startAsExpense = initialIsExpenseForDialog,
                                    onDismiss = { isAddTransactionOpen = false },
                                    onSaveEntry = { title, amount, category, isExpense, bankName ->
                                        viewModel.addTransaction(title, amount, category, isExpense, bankName)
                                    },
                                    onTransfer = { from, to, amount ->
                                        viewModel.transferBetweenCards(from, to, amount)
                                    },
                                    onCreateCategory = { name, iconKey, colorHex, isIncome ->
                                        viewModel.addCategory(name, iconKey, colorHex, isIncome)
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

                            if (isAddCardOpen) {
                                AddCardBottomSheet(
                                    onDismiss = { isAddCardOpen = false },
                                    onConfirm = { bankName, cardNumber, balance, cardHolderName ->
                                        viewModel.addCard(bankName, cardNumber, balance, cardHolderName)
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
