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

/** Money owed between the user and another person, outside any bank account. */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val person: String,
    val amount: Long,
    /** true: they owe me (طلب). false: I owe them (بدهی). */
    val owedToMe: Boolean,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val settled: Boolean = false,
    val settledAt: Long? = null
)

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

    @Query("UPDATE recurring_transactions SET enabled = 0 WHERE bankName = :name")
    suspend fun disableForAccount(name: String)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY settled ASC, createdAt DESC")
    fun getAll(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt): Long

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: Int)
}
