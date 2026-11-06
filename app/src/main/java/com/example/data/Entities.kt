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
    val transferGroupId: String? = null,
    /** Set on an instalment payment, so deleting or editing it corrects the loan too. */
    val loanId: Int? = null,
    /** Labels such as «سفر شمال», comma-separated; empty for none. */
    val tags: String = "",
    /** Photo file names in the app's attachments folder, comma-separated. */
    val attachments: String = ""
)

val Transaction.tagList: List<String>
    get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

val Transaction.attachmentList: List<String>
    get() = attachments.split(',').map { it.trim() }.filter { it.isNotEmpty() }

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
    val sortOrder: Int = 0,
    /** Monthly spending cap in toman; 0 means no budget for this category. */
    val monthlyBudget: Long = 0
)

/**
 * Where money sits. Not every account is a bank card: cash in a pocket is the
 * commonest account of all here, and it has no card number to give.
 */
object AccountType {
    const val BANK = "BANK"     // a card of your own
    const val CASH = "CASH"     // notes in a wallet
    const val OTHER = "OTHER"   // someone else's card, a savings box, anything
    const val CREDIT = "CREDIT" // a credit card: spending lowers the balance below zero, up to the limit
}

@Entity(tableName = "bank_cards")
data class BankCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** The account's display name. Doubles as its key — see [Transaction.bankName]. */
    val bankName: String,
    /** Empty for cash and for accounts that simply have no card. */
    val cardNumber: String = "",
    val balance: Long,
    val cardHolderName: String = "کاربر تراز",
    val accountType: String = AccountType.BANK,
    /** Credit cards only: the limit in toman. */
    val creditLimit: Long = 0,
    /** Credit cards only: day of the Jalali month repayment is due; 0 = none. */
    val dueDay: Int = 0
)

val BankCard.isCredit: Boolean
    get() = accountType == AccountType.CREDIT

val BankCard.isCash: Boolean
    get() = accountType == AccountType.CASH

/** Only a real card has digits worth showing. */
val BankCard.maskedNumber: String
    get() = if (cardNumber.length >= 4) "•••• " + cardNumber.takeLast(4) else ""

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
    val reminderEnabled: Boolean = true,
    /** "loan" for a bank loan, "purchase" for buying in instalments (store, BNPL). */
    val kind: String = LoanKind.LOAN
) {
    val isPurchase: Boolean get() = kind == LoanKind.PURCHASE
    val isSettled: Boolean get() = paidAmount >= totalAmount
}

object LoanKind {
    const val LOAN = "loan"
    const val PURCHASE = "purchase"
}

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** When the user wants to reach it; null = no deadline. */
    val deadline: Long? = null
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
    val message: String = "برای تحلیل دقیق‌تر، تراکنش‌های بیشتری ثبت کنید.",
    val action: String? = null
)

