package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One instalment paid on a loan. [jalaliYear]/[jalaliMonth] is the instalment it
 * settles (not always the month it was paid in: a late payment settles last
 * month's), which is what reminders and the payment history read.
 */
@Entity(tableName = "loan_payments")
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanId: Int,
    val amount: Long,
    val date: Long,
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val transactionId: Int? = null
) {
    val monthKey: Int get() = jalaliYear * 100 + jalaliMonth
}

/** A transaction that repeats every month on [dayOfMonth] (rent, salary, subscriptions). */
@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Long,
    val category: String,
    val isExpense: Boolean,
    val bankName: String,
    val dayOfMonth: Int,
    val enabled: Boolean = true,
    /** year*100+month of the last month it was recorded for; 0 = never. */
    val lastRunKey: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Someone the user keeps an account with: a friend, family, a relative, a colleague. */
@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** One of [PersonRelation]. */
    val relation: String = PersonRelation.OTHER,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object PersonRelation {
    const val FRIEND = "دوست"
    const val FAMILY = "خانواده"
    const val RELATIVE = "فامیل"
    const val COLLEAGUE = "همکار"
    const val OTHER = "سایر"
    val all = listOf(FRIEND, FAMILY, RELATIVE, COLLEAGUE, OTHER)
}

/**
 * One line in a person's ledger. Each line moves the balance with that person:
 * [owedToMe] true adds to what they owe me, false takes from it (or adds to
 * what I owe them). [isRepayment] only changes the wording:
 *  - lent       (قرض دادم):  owedToMe = true,  isRepayment = false
 *  - borrowed   (قرض گرفتم): owedToMe = false, isRepayment = false
 *  - got back   (پس گرفتم):  owedToMe = false, isRepayment = true
 *  - paid back  (پس دادم):   owedToMe = true,  isRepayment = true
 * Lines marked [settled] are from the older settle-in-one-go model and no
 * longer count towards the balance.
 */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** The person's name at the time; [personId] is what links the line. */
    val person: String,
    val amount: Long,
    val owedToMe: Boolean,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val settled: Boolean = false,
    val settledAt: Long? = null,
    val personId: Int? = null,
    val isRepayment: Boolean = false,
    /** The account transaction recorded with this line, if money moved through an account. */
    val transactionId: Int? = null,
    /** When the money should come back (or be paid back); drives a reminder. */
    val dueDate: Long? = null
) {
    /** Effect on the balance with the person: positive means they owe me. */
    val signedAmount: Long get() = if (settled) 0L else if (owedToMe) amount else -amount

    /** برچسب رسمی: پول به شخص داده شد ← بستانکار؛ از شخص گرفته شد ← بدهکار */
    val kindLabel: String get() = if (owedToMe) "بستانکار" else "بدهکار"
}

@Dao
interface LoanPaymentDao {
    @Query("SELECT * FROM loan_payments ORDER BY date DESC")
    fun getAll(): Flow<List<LoanPayment>>

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY date DESC")
    suspend fun forLoan(loanId: Int): List<LoanPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: LoanPayment): Long

    @Query("DELETE FROM loan_payments WHERE transactionId = :transactionId")
    suspend fun deleteByTransaction(transactionId: Int)

    @Query("SELECT * FROM loan_payments WHERE transactionId = :transactionId")
    suspend fun byTransaction(transactionId: Int): List<LoanPayment>

    @Query("UPDATE loan_payments SET amount = :amount WHERE transactionId = :transactionId")
    suspend fun updateAmountByTransaction(transactionId: Int, amount: Long)

    @Query("DELETE FROM loan_payments WHERE loanId = :loanId")
    suspend fun deleteForLoan(loanId: Int)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY dayOfMonth ASC")
    fun getAll(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE enabled = 1")
    suspend fun getEnabled(): List<RecurringTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecurringTransaction): Long

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE recurring_transactions SET bankName = :newName WHERE bankName = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)

    @Query("UPDATE recurring_transactions SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("UPDATE recurring_transactions SET enabled = 0 WHERE bankName = :name")
    suspend fun disableForAccount(name: String)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAll(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person): Long

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY settled ASC, createdAt DESC")
    fun getAll(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt): Long

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Debt?

    @Query("SELECT * FROM debts WHERE personId = :personId")
    suspend fun forPerson(personId: Int): List<Debt>

    @Query("DELETE FROM debts WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Int)

    @Query("DELETE FROM debts WHERE transactionId = :transactionId")
    suspend fun deleteByTransaction(transactionId: Int)

    @Query("SELECT * FROM debts WHERE transactionId = :transactionId")
    suspend fun byTransaction(transactionId: Int): List<Debt>

    @Query("UPDATE debts SET person = :name WHERE personId = :personId")
    suspend fun renamePerson(personId: Int, name: String)
}
