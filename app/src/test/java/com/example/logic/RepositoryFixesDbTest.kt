package com.example.logic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BankCard
import com.example.data.Cheque
import com.example.data.ChequeStatus
import com.example.data.FinanceRepository
import com.example.data.RecurringTransaction
import com.example.utils.JalaliDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** تراکنش تکراری فقط یک بار در ماه، و چک وصول‌شده هم‌خوان با تراکنشش. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryFixesDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        runBlocking { repo.insertCard(BankCard(bankName = "ملت", cardNumber = "6104337412901234", balance = 10_000_000)) }
    }

    @After
    fun tearDown() = db.close()

    private fun balance(name: String) = runBlocking { repo.getCardByName(name)!!.balance }

    @Test
    fun recurringRunsRacingRecordTheMonthOnce() = runBlocking {
        repo.insertRecurring(
            RecurringTransaction(title = "اجاره", amount = 2_000_000, category = "سایر", isExpense = true, bankName = "ملت", dayOfMonth = 1)
        )
        val now = JalaliDate.toTimestamp(1405, 3, 10) + 12 * 3_600_000L
        val counts = (1..6).map { async(Dispatchers.Default) { repo.runDueRecurring(now) } }.awaitAll()
        assertEquals(1, counts.sum())
        assertEquals(1, repo.allTransactions.first().size)
        assertEquals(8_000_000L, balance("ملت"))
        // دوباره در همان ماه: چیزی ثبت نمی‌شود
        assertEquals(0, repo.runDueRecurring(now + 3_600_000L))
    }

    @Test
    fun editingAChequesTransactionKeepsTheChequeInStep() = runBlocking {
        repo.saveCheque(Cheque(issued = true, amount = 3_000_000, dueDate = 0, person = "صاحبخانه", bankName = "ملت"))
        repo.setChequeStatus(repo.allCheques.first().first(), ChequeStatus.CLEARED)
        val tx = repo.allTransactions.first().single()
        repo.updateTransaction(tx.copy(amount = 2_500_000))
        assertEquals(7_500_000L, balance("ملت"))
        val cheque = repo.allCheques.first().single()
        assertEquals(2_500_000L, cheque.amount)
        assertEquals(tx.id, cheque.transactionId)
    }
}
