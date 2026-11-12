package com.example.logic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppCurrency
import com.example.data.AppDatabase
import com.example.data.Backup
import com.example.data.BankCard
import com.example.data.Cheque
import com.example.data.FinanceRepository
import com.example.data.Loan
import com.example.data.Money
import com.example.data.NetWorthSnapshot
import com.example.data.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** تغییر واحد پول همهٔ مبلغ‌های دیتابیس را با هم و درست تبدیل می‌کند؛ پشتیبان واحدش را همراه دارد. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrencyDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        Money.setForTest(AppCurrency.TOMAN)
        runBlocking {
            repo.insertCard(BankCard(bankName = "ملت", cardNumber = "6104337412901234", balance = 10_000_000))
            repo.insertTransaction(Transaction(title = "خرید", amount = 250_005, category = "سایر", isExpense = true, bankName = "ملت"))
            repo.insertLoan(Loan(bankName = "ملت", loanName = "وام", totalAmount = 90_000_000, paidAmount = 9_000_000, installmentAmount = 4_500_000, dueDate = "۵ هر ماه", dueDay = 5))
            repo.saveCheque(Cheque(issued = true, amount = 3_000_000, dueDate = 0, person = "علی", bankName = "ملت"))
            repo.saveSnapshot(NetWorthSnapshot(140501, 18_000_000, 90_000, 4_000_000, 0))
        }
    }

    @After
    fun tearDown() {
        db.close()
        Money.setForTest(AppCurrency.TOMAN)
    }

    private fun balance() = runBlocking { repo.getCardByName("ملت")!!.balance }

    @Test
    fun tomanToRialMultipliesEveryAmountByTen() = runBlocking {
        repo.convertCurrency(AppCurrency.TOMAN, AppCurrency.RIAL, 0)
        assertEquals(97_499_950L, balance())
        assertEquals(2_500_050L, repo.allTransactions.first().single().amount)
        val loan = repo.allLoans.first().single()
        assertEquals(900_000_000L, loan.totalAmount)
        assertEquals(90_000_000L, loan.paidAmount)
        assertEquals(45_000_000L, loan.installmentAmount)
        assertEquals(30_000_000L, repo.allCheques.first().single().amount)
        assertEquals(180_000_000L, repo.allSnapshots.first().single().netWorth)
        // و برگشت دقیق
        repo.convertCurrency(AppCurrency.RIAL, AppCurrency.TOMAN, 0)
        assertEquals(9_749_995L, balance())
        assertEquals(250_005L, repo.allTransactions.first().single().amount)
    }

    @Test
    fun tomanToDollarsUsesTheRateAndKeepsCents() = runBlocking {
        repo.convertCurrency(AppCurrency.TOMAN, AppCurrency.USD, 90_000)
        // ۹٬۷۴۹٬۹۹۵ تومان ÷ ۹۰٬۰۰۰ = ۱۰۸٫۳۳ دلار
        assertEquals(10_833L, balance())
        assertEquals(278L, repo.allTransactions.first().single().amount)
        assertEquals(100_000L, repo.allLoans.first().single().totalAmount)
        // دارایی خالص هر ماه با نرخ دلار همان ماه
        assertEquals(20_000L, repo.allSnapshots.first().single().netWorth)
    }

    @Test
    fun aBackupCarriesItsCurrency() = runBlocking {
        Money.setForTest(AppCurrency.RIAL)
        val json = Backup.snapshot(db)
        assertEquals(AppCurrency.RIAL, Backup.currencyOf(json))
        // پشتیبان‌های پیش از این نسخه تومانی‌اند
        assertEquals(AppCurrency.TOMAN, Backup.currencyOf("""{"transactions":[],"cards":[]}"""))
    }
}
