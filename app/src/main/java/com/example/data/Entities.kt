package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What a transaction IS. [Transaction.isExpense] stays the DIRECTION of the money. */
object TransactionType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
}

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Long,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    /** Direction: true = money left the account, false = money arrived. */
    val isExpense: Boolean,
    val bankName: String,
    /** Nature: one of [TransactionType]. A transfer has a direction too. */
    val type: String = TransactionType.EXPENSE,
    /** Ties the two halves of a transfer together. Null for everything else. */
    val transferGroupId: String? = null
)

/**
 * Moving money between your own accounts is not income and not spending.
 * Counting it as both inflates the month by the same figure twice and puts a
 * phantom category in the reports — use these instead of raw [Transaction.isExpense]
 * anywhere a total is computed. Display code keeps using isExpense for the sign.
 */
val Transaction.isTransfer: Boolean
    get() = type == TransactionType.TRANSFER

val Transaction.countsAsExpense: Boolean
    get() = isExpense && !isTransfer

val Transaction.countsAsIncome: Boolean
    get() = !isExpense && !isTransfer

/** Signed contribution to net worth: transfers contribute nothing. */
val Transaction.netFlow: Long
    get() = when {
        isTransfer -> 0L
        isExpense -> -amount
        else -> amount
    }

/**
 * A spending category the user can create: name, an icon key the UI maps to a
 * vector, and a colour. Seeded with the six that used to be hard-coded in
 * AddTransactionSheet so nothing a user already recorded loses its category.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val iconKey: String = "other",
    val colorHex: String = "#0B7A57",
    val isIncome: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "bank_cards")
data class BankCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bankName: String,
    val cardNumber: String,
    val balance: Long,
    val cardHolderName: String = "کاربر تراز"
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bankName: String,
    val loanName: String,
    val totalAmount: Long,
    val paidAmount: Long,
    val installmentAmount: Long,
    /** Human-readable label, kept for display and for AI context. */
    val dueDate: String,
    /** Day of the Jalali month the instalment falls due, 1..31. Drives reminders. */
    val dueDay: Int = 1,
    val reminderEnabled: Boolean = true
) {
    val isSettled: Boolean get() = paidAmount >= totalAmount
}

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class FinancialHealthResult(
    val score: Int = 0,
    val status: String = "no_data",
    val recommendation: String = "اطلاعات مالی بیشتری ثبت کنید"
)

data class FinancialCoachInsight(
    val id: String = "empty_001",
    val type: String = "empty", // success, warning, danger, suggestion, empty
    val priority: Int = 0,
    val message: String = "برای تحلیل بهتر، تراکنش‌های بیشتری ثبت کن.",
    val action: String? = null
)

