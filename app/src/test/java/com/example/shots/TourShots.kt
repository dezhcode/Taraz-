package com.example.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Loan
import com.example.data.Person
import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.ui.Tab
import com.example.ui.layer.HomeGlance
import com.example.ui.layer.L
import com.example.ui.layer.LayerAccountsScreen
import com.example.ui.layer.LayerAddSheet
import com.example.ui.layer.LayerHomeScreen
import com.example.ui.layer.LayerNavBar
import com.example.ui.layer.LayerObligationsScreen
import com.example.ui.layer.LayerReportsScreen
import com.example.ui.layer.LayerTheme
import com.example.ui.layer.LayerTransactionsScreen
import com.example.ui.layer.LocalTour
import com.example.ui.layer.TourHost
import com.example.ui.layer.TourState
import com.example.ui.layer.TourStep
import com.example.ui.layer.Tours
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * تصویر هر گام راهنما روی صفحهٔ واقعی برنامه (حالت تیره، اندازهٔ گوشی).
 * در CI با -Proborazzi.test.record=true اجرا می‌شود و تصویرها کنار APK منتشر می‌شوند.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class TourShots {
    @get:Rule val compose = createComposeRule()

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private val cards = listOf(
        BankCard(id = 1, bankName = "ملت", cardNumber = "6104337412345678", balance = 3_200_000),
        BankCard(id = 2, bankName = "سامان", cardNumber = "6219861012345678", balance = 1_450_000),
        BankCard(id = 3, bankName = "وجه نقد", balance = 527_412, accountType = "CASH")
    )
    private val categories = listOf(
        Category(id = 1, name = "غذا", iconKey = "food"), Category(id = 2, name = "حمل‌ونقل", iconKey = "transport"),
        Category(id = 3, name = "خرید", iconKey = "shopping"), Category(id = 4, name = "قبوض", iconKey = "bill"),
        Category(id = 5, name = "حقوق", iconKey = "wage", isIncome = true)
    )
    private val transactions = listOf(
        Transaction(id = 1, title = "خرید از فروشگاه", amount = 850_000, category = "خرید", date = now - day, isExpense = true, bankName = "ملت"),
        Transaction(id = 2, title = "ناهار", amount = 420_000, category = "غذا", date = now - 2 * day, isExpense = true, bankName = "سامان"),
        Transaction(id = 3, title = "حقوق آبان", amount = 13_900_000, category = "حقوق", date = now - 5 * day, isExpense = false, bankName = "ملت", type = TransactionType.INCOME),
        Transaction(id = 4, title = "تاکسی", amount = 160_000, category = "حمل‌ونقل", date = now - 3 * day, isExpense = true, bankName = "وجه نقد")
    )
    private val persons = listOf(Person(id = 1, name = "علی رضایی", relation = "دوست"), Person(id = 2, name = "مریم احمدی", relation = "همکار"))

    private fun shoot(name: String, key: String, steps: List<TourStep>, content: @Composable () -> Unit) {
        LayerTheme.dark = true
        lateinit var tour: TourState
        compose.setContent {
            tour = remember { TourState().also { it.preview = key to 0 } }
            CompositionLocalProvider(LocalTour provides tour, LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(L.Bg)) {
                    content()
                    TourHost(tour)
                }
            }
        }
        steps.indices.forEach { i ->
            compose.runOnIdle { tour.preview = key to i }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.onRoot().captureRoboImage("build/tourshots/${name}_${i + 1}.png")
        }
    }

    @Test fun home() = shoot("1_home", "home", Tours.home) {
        Box(Modifier.fillMaxSize()) {
            LayerHomeScreen(
                userName = "علی", cards = cards, totalBalance = cards.sumOf { it.balance },
                monthlyIncome = 13_900_000, monthlyExpense = 11_240_000, todayExpense = 420_000, dailyBudget = 2_500_000,
                transactions = transactions, loans = emptyList<Loan>(), pendingSms = emptyList(), categories = categories,
                onOpenBudget = {}, onOpenAccounts = {}, onOpenSettings = {}, onOpenSmsInbox = {}, onOpenReports = {},
                onOpenDay = {}, onSeeAllTransactions = {}, onPayLoan = {}, onApproveSms = {}, onDismissSms = {},
                onReviewSms = {}, onTransactionClick = {},
                glance = HomeGlance(spent = 11_240_000, income = 13_900_000, owe = 800_000, oweNote = "اشخاص", owed = 2_000_000, owedNote = "از ۱ نفر", balance = cards.sumOf { it.balance })
            )
            LayerNavBar(Tab.HOME, {}, {}, {}, Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test fun transactions() = shoot("2_transactions", "transactions", Tours.transactions) {
        Box(Modifier.fillMaxSize()) {
            LayerTransactionsScreen(
                transactions = transactions, cards = cards, categories = categories, dailyBudget = 2_500_000,
                selectedDay = now, onSelectDay = {}, onTransactionClick = {}
            )
            LayerNavBar(Tab.TRANSACTIONS, {}, {}, {}, Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test fun obligations() = shoot("3_obligations", "obligations", Tours.obligations) {
        Box(Modifier.fillMaxSize()) {
            LayerObligationsScreen(
                loans = emptyList(), persons = persons, balances = mapOf(1 to 2_000_000L, 2 to -800_000L),
                onAddLoan = {}, onPay = {}, onOpenLoan = {}, onOpenPerson = {}, onAddPerson = {}, onAddEntry = {},
                segment = 1
            )
            LayerNavBar(Tab.LOANS, {}, {}, {}, Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test fun reports() = shoot("4_reports", "reports", Tours.reports) {
        LayerReportsScreen(transactions = transactions, goal = null, onOpenGoals = {}, onAskAssistant = {})
    }

    @Test fun accounts() = shoot("5_accounts", "accounts", Tours.accounts) {
        LayerAccountsScreen(
            cards = cards, senders = emptyList(), totalBalance = cards.sumOf { it.balance },
            onBack = {}, onOpenAccount = {}, onAdd = {}, onTransfer = {}
        )
    }

    @Test fun add() = shoot("6_add", "add", Tours.add) {
        LayerAddSheet(
            categories = categories, cards = cards, transactions = transactions,
            onDismiss = {}, onSave = { _, _, _, _, _, _, _ -> }, onTransfer = { _, _, _ -> }
        )
    }
}
