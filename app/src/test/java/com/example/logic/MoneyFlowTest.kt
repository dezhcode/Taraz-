package com.example.logic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BankCard
import com.example.data.FinanceRepository
import com.example.data.Loan
import com.example.data.Person
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Money moving between accounts, loans and people, on a real (in-memory)
 * database: every balance has to come out exactly right, and every delete
 * has to put things back as they were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        runBlocking {
            repo.insertCard(BankCard(bankName = "ملت", balance = 10_000_000))
            repo.insertCard(BankCard(bankName = "نقد", balance = 1_000_000))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun balance(name: String) = runBlocking { repo.getCardByName(name)!!.balance }

    private fun expense(amount: Long, account: String = "ملت") = Transaction(
        title = "خرید", amount = amount, category = "خوراک", isExpense = true, bankName = account, type = TransactionType.EXPENSE
    )

    @Test
    fun anExpenseLowersTheBalanceAndDeletingItGivesItBack() = runBlocking {
        val id = repo.insertTransaction(expense(250_000)).toInt()
        assertEquals(9_750_000L, balance("ملت"))
        repo.deleteTransaction(repo.allTransactions.first().first { it.id == id })
        assertEquals(10_000_000L, balance("ملت"))
    }

    @Test
    fun aTransferMovesMoneyAndDeletingOneHalfRemovesBoth() = runBlocking {
        repo.insertTransfer("ملت", "نقد", 2_000_000)
        assertEquals(8_000_000L, balance("ملت"))
        assertEquals(3_000_000L, balance("نقد"))
        val half = repo.allTransactions.first().first { !it.isExpense }
        repo.deleteTransaction(half)
        assertTrue(repo.allTransactions.first().isEmpty())
        assertEquals(10_000_000L, balance("ملت"))
        assertEquals(1_000_000L, balance("نقد"))
    }

    @Test
    fun editingAnExpenseOntoAnotherAccountMovesItsEffect() = runBlocking {
        val id = repo.insertTransaction(expense(500_000)).toInt()
        val tx = repo.allTransactions.first().first { it.id == id }
        repo.updateTransaction(tx.copy(amount = 300_000, bankName = "نقد"))
        assertEquals(10_000_000L, balance("ملت"))
        assertEquals(700_000L, balance("نقد"))
    }

    @Test
    fun theLastInstalmentPaysOnlyWhatIsLeft() = runBlocking {
        repo.insertLoan(Loan(id = 1, bankName = "ملت", loanName = "وام", totalAmount = 5_000_000, paidAmount = 4_000_000, installmentAmount = 3_000_000, dueDate = "", dueDay = 10))
        val paid = repo.payInstallment(1, "ملت")
        assertEquals(1_000_000L, paid)
        assertEquals(9_000_000L, balance("ملت"))
        val loan = repo.allLoans.first().first()
        assertEquals(5_000_000L, loan.paidAmount)
        assertNull(repo.payInstallment(1, "ملت"))
    }

    @Test
    fun deletingAnInstalmentPaymentGivesItBackToTheLoan() = runBlocking {
        repo.insertLoan(Loan(id = 1, bankName = "ملت", loanName = "وام", totalAmount = 10_000_000, paidAmount = 0, installmentAmount = 2_000_000, dueDate = "", dueDay = 10))
        repo.payInstallment(1, "ملت")
        val tx = repo.allTransactions.first().first()
        repo.deleteTransaction(tx)
        assertEquals(0L, repo.allLoans.first().first().paidAmount)
        assertEquals(10_000_000L, balance("ملت"))
        assertTrue(repo.paymentsFor(1).isEmpty())
    }

    @Test
    fun lendingToAPersonMovesTheAccountButIsNotSpending() = runBlocking {
        val id = repo.savePerson(Person(name = "علی"))
        val ali = Person(id = id, name = "علی")
        repo.addPersonEntry(ali, 2_000_000, owedToMe = true, isRepayment = false, account = "ملت", note = "")
        assertEquals(8_000_000L, balance("ملت"))
        val tx = repo.allTransactions.first().first()
        assertEquals(TransactionType.TRANSFER, tx.type)
        val owed = repo.allDebts.first().filter { it.personId == id }.sumOf { it.signedAmount }
        assertEquals(2_000_000L, owed)
        repo.deletePersonEntry(repo.allDebts.first().first())
        assertEquals(10_000_000L, balance("ملت"))
        assertTrue(repo.allTransactions.first().isEmpty())
    }

    @Test
    fun aSharedBillTakesTheWholeAmountButOnlyMyShareIsSpending() = runBlocking {
        val a = repo.savePerson(Person(name = "علی"))
        val b = repo.savePerson(Person(name = "سارا"))
        repo.splitExpense("شام", 3_000_000, "ملت", "خوراک", includeMe = true,
            people = listOf(Person(id = a, name = "علی"), Person(id = b, name = "سارا")))
        assertEquals(7_000_000L, balance("ملت"))
        val spending = repo.allTransactions.first().filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(1_000_000L, spending)
        val owed = repo.allDebts.first().sumOf { it.signedAmount }
        assertEquals(2_000_000L, owed)
    }

    @Test
    fun renamingAnAccountCarriesItsTransactions() = runBlocking {
        repo.insertTransaction(expense(100_000))
        val card = repo.getCardByName("ملت")!!
        assertTrue(repo.renameAccount(card, "ملت حقوق"))
        assertEquals("ملت حقوق", repo.allTransactions.first().first().bankName)
        assertEquals(9_900_000L, balance("ملت حقوق"))
    }

    @Test
    fun aDeletedAccountsHistoryDoesNotJoinANewAccountWithItsName() = runBlocking {
        repo.insertTransaction(expense(100_000))
        repo.deleteAccount(repo.getCardByName("ملت")!!.id)
        repo.insertCard(BankCard(bankName = "ملت", balance = 0))
        assertTrue(repo.allTransactions.first().none { it.bankName == "ملت" })
        assertEquals(0L, balance("ملت"))
    }

    @Test
    fun aClearedChequeMovesMoneyAndUndoingItGivesItBack() = runBlocking {
        repo.saveCheque(com.example.data.Cheque(issued = true, amount = 3_000_000, dueDate = 0, person = "صاحبخانه", bankName = "ملت"))
        val c = repo.allCheques.first().first()
        repo.setChequeStatus(c, com.example.data.ChequeStatus.CLEARED)
        assertEquals(7_000_000L, balance("ملت"))
        val cleared = repo.allCheques.first().first()
        assertTrue(cleared.transactionId != null)
        repo.setChequeStatus(cleared, com.example.data.ChequeStatus.PENDING)
        assertEquals(10_000_000L, balance("ملت"))
        assertNull(repo.allCheques.first().first().transactionId)
        assertTrue(repo.allTransactions.first().isEmpty())
    }

    @Test
    fun aReceivedChequeThatClearsAddsMoney() = runBlocking {
        repo.saveCheque(com.example.data.Cheque(issued = false, amount = 500_000, dueDate = 0, person = "علی", bankName = "نقد"))
        repo.setChequeStatus(repo.allCheques.first().first(), com.example.data.ChequeStatus.CLEARED)
        assertEquals(1_500_000L, balance("نقد"))
    }

    @Test
    fun payingABillSpendsAndMovesItsDueDateOnePeriod() = runBlocking {
        val due = com.example.utils.JalaliDate.toTimestamp(1405, 11, 30)
        repo.saveBill(com.example.data.Bill(kind = "برق", title = "برق", dueDate = due, periodMonths = 2))
        repo.payBill(repo.allBills.first().first(), 400_000, "ملت")
        assertEquals(9_600_000L, balance("ملت"))
        val next = com.example.utils.JalaliDate.fromTimestamp(repo.allBills.first().first().dueDate)
        assertEquals(1406, next.year)
        assertEquals(1, next.month)
        assertEquals("قبوض", repo.allTransactions.first().first().category)
    }
}
