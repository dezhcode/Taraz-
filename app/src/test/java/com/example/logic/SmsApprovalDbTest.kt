package com.example.logic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Backup
import com.example.data.BankCard
import com.example.data.BankSender
import com.example.data.Cheque
import com.example.data.ChequeStatus
import com.example.data.FinanceRepository
import com.example.data.PendingStatus
import com.example.data.PendingTransaction
import com.example.data.SmsApproval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تأیید تراکنش‌های پیامکی، چک‌ها و بازگردانی پشتیبان روی دیتابیس واقعی (در حافظه): هر پیامک فقط
 * یک بار ثبت شود، حساب تازه با ماندهٔ پیامک بخواند، و هیچ خطایی دادهٔ موجود را پاک نکند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsApprovalDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        runBlocking {
            repo.insertCard(BankCard(bankName = "ملت", cardNumber = "6104337412901234", balance = 10_000_000))
            repo.insertCard(BankCard(bankName = "نقد", balance = 1_000_000, accountType = com.example.data.AccountType.CASH))
            db.bankSenderDao().insertSender(BankSender(bankName = "بانک ملت", senderNumber = "700717"))
            db.bankSenderDao().insertSender(BankSender(bankName = "بانک سامان", senderNumber = "9821"))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun balance(name: String) = runBlocking { repo.getCardByName(name)!!.balance }
    private suspend fun transactions() = repo.allTransactions.first()

    private suspend fun pending(
        amount: Long, isExpense: Boolean = true, sender: String = "700717", card: String = "1234",
        balance: Long = 0, date: Long = 1_000, body: String = "sms $amount $date"
    ): Int = db.pendingTransactionDao().insertPendingTransaction(
        PendingTransaction(
            senderNumber = sender, amount = amount, date = date, isExpense = isExpense, card = card,
            title = "تراکنش پیامکی", smsBody = body, smsBodyHash = body, balance = balance
        )
    ).toInt()

    // ───────────── تأیید ─────────────

    @Test
    fun approvingTheSameSmsTwiceRecordsItOnce() = runBlocking {
        val id = pending(480_000)
        val first = SmsApproval.approve(db, id)
        assertEquals("ملت", first?.account)
        assertNull(SmsApproval.approve(db, id))
        assertEquals(1, transactions().size)
        assertEquals(9_520_000L, balance("ملت"))
        assertEquals(PendingStatus.APPROVED, db.pendingTransactionDao().getById(id)!!.status)
    }

    @Test
    fun approvalsRacingForOneSmsRecordItOnce() = runBlocking {
        val id = pending(480_000)
        val results = (1..8).map { async(Dispatchers.Default) { SmsApproval.approve(db, id) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, transactions().size)
        assertEquals(9_520_000L, balance("ملت"))
    }

    @Test
    fun twoSmsRacingForANewAccountMakeItOnlyOnce() = runBlocking {
        // دو پیامک از بانکی که حسابی در تراز ندارد، به ترتیب زمان
        val older = pending(500_000, sender = "9821", card = "", balance = 9_500_000, date = 1_000)
        val newer = pending(200_000, sender = "9821", card = "", balance = 9_300_000, date = 2_000)
        val both = listOf(older, newer).map { async(Dispatchers.Default) { SmsApproval.approve(db, it) } }.awaitAll()
        assertTrue(both.all { it?.account == "بانک سامان" })
        assertEquals(1, repo.allCardsNow().count { it.bankName == "بانک سامان" })
        assertEquals(2, transactions().size)
    }

    @Test
    fun inOrderANewAccountMatchesTheBankAfterEverySms() = runBlocking {
        val older = pending(500_000, sender = "9821", card = "", balance = 9_500_000, date = 1_000)
        val newer = pending(200_000, isExpense = false, sender = "9821", card = "", balance = 9_700_000, date = 2_000)
        SmsApproval.approve(db, older)
        assertEquals(9_500_000L, balance("بانک سامان"))
        SmsApproval.approve(db, newer)
        assertEquals(9_700_000L, balance("بانک سامان"))
    }

    @Test
    fun whatTheUserCorrectedIsWhatGetsRecorded() = runBlocking {
        val id = pending(480_000)
        val done = SmsApproval.approve(db, id, title = "برگشت خرید", amount = 300_000, account = "نقد", isExpense = false)
        assertEquals("نقد", done?.account)
        assertEquals(1_300_000L, balance("نقد"))
        assertEquals(10_000_000L, balance("ملت"))
        val tx = transactions().single()
        assertEquals("برگشت خرید", tx.title)
        assertEquals(300_000L, tx.amount)
        assertTrue(!tx.isExpense)
        // دسته‌ای که واقعاً برای درآمد هست، نه «درآمد»
        assertEquals("سایر درآمدها", tx.category)
    }

    @Test
    fun aRejectedSmsCannotBeApproved() = runBlocking {
        val id = pending(480_000)
        SmsApproval.reject(db, id)
        assertNull(SmsApproval.approve(db, id))
        assertTrue(transactions().isEmpty())
        assertEquals(10_000_000L, balance("ملت"))
        // و رد کردنِ تراکنشِ ثبت‌شده آن را برنمی‌گرداند
        val other = pending(100_000, date = 5_000)
        assertNotNull(SmsApproval.approve(db, other))
        SmsApproval.reject(db, other)
        assertEquals(PendingStatus.APPROVED, db.pendingTransactionDao().getById(other)!!.status)
    }

    // ───────────── چک و نام حساب ─────────────

    @Test
    fun renamingAnAccountCarriesItsChequesAndBills() = runBlocking {
        repo.saveCheque(Cheque(issued = true, amount = 3_000_000, dueDate = 0, person = "صاحبخانه", bankName = "ملت"))
        repo.saveBill(com.example.data.Bill(kind = "برق", title = "برق", dueDate = 0, bankName = "ملت"))
        assertTrue(repo.renameAccount(repo.getCardByName("ملت")!!, "ملت حقوق"))
        val cheque = repo.allCheques.first().first()
        assertEquals("ملت حقوق", cheque.bankName)
        assertEquals("ملت حقوق", repo.allBills.first().first().bankName)
        // وصول چک حالا روی همان حساب می‌نشیند
        repo.setChequeStatus(cheque, ChequeStatus.CLEARED)
        assertEquals(7_000_000L, balance("ملت حقوق"))
    }

    @Test
    fun editingAClearedChequeMovesItsMoneyToo() = runBlocking {
        repo.saveCheque(Cheque(issued = true, amount = 3_000_000, dueDate = 0, person = "صاحبخانه", bankName = "ملت"))
        repo.setChequeStatus(repo.allCheques.first().first(), ChequeStatus.CLEARED)
        assertEquals(7_000_000L, balance("ملت"))
        val cleared = repo.allCheques.first().first()
        val paidAt = transactions().single().date

        repo.saveCheque(cleared.copy(amount = 2_000_000))
        assertEquals(8_000_000L, balance("ملت"))
        val tx = transactions().single()
        assertEquals(2_000_000L, tx.amount)
        assertEquals(paidAt, tx.date)
        val after = repo.allCheques.first().first()
        assertEquals(ChequeStatus.CLEARED, after.status)
        assertEquals(tx.id, after.transactionId)

        // ویرایشی که به پول ربطی ندارد، تراکنش را دست نمی‌زند
        repo.saveCheque(after.copy(note = "اجارهٔ آذر"))
        assertEquals(tx.id, transactions().single().id)
        assertEquals(8_000_000L, balance("ملت"))
    }

    // ───────────── پشتیبان ─────────────

    @Test
    fun aDamagedBackupLeavesTheDataAlone() = runBlocking {
        repo.insertTransaction(com.example.data.Transaction(title = "خرید", amount = 250_000, category = "سایر", isExpense = true, bankName = "ملت"))
        try {
            // تراکنشی بدون عنوان و مبلغ: فایل ناقص
            Backup.restore(db, """{"transactions":[{"id":1}],"cards":[{"id":1,"bankName":"x","balance":5}]}""")
            fail("a damaged backup must be refused")
        } catch (e: Exception) { /* expected */ }
        assertEquals(2, repo.allCardsNow().size)
        assertEquals(9_750_000L, balance("ملت"))
        assertEquals(1, transactions().size)
    }

    @Test
    fun aBackupComesBackAsItWas() = runBlocking {
        repo.insertTransaction(com.example.data.Transaction(title = "خرید", amount = 250_000, category = "سایر", isExpense = true, bankName = "ملت"))
        repo.saveCheque(Cheque(issued = false, amount = 500_000, dueDate = 0, person = "علی", bankName = "نقد"))
        val json = Backup.snapshot(db)
        repo.insertTransaction(com.example.data.Transaction(title = "بعد از پشتیبان", amount = 1_000, category = "سایر", isExpense = true, bankName = "ملت"))

        assertEquals(1, Backup.restore(db, json))
        assertEquals(9_750_000L, balance("ملت"))
        assertEquals("خرید", transactions().single().title)
        assertEquals("علی", repo.allCheques.first().single().person)
        assertEquals(2, db.bankSenderDao().getAllSenders().size)
    }
}
