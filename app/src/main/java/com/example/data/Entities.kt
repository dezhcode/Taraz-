package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Long,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val isExpense: Boolean,
    val bankName: String
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
    val dueDate: String
)

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

