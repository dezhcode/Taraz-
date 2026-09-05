package com.example.data

import kotlinx.coroutines.flow.Flow

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val bankCardDao: BankCardDao,
    private val loanDao: LoanDao,
    private val goalDao: GoalDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allCards: Flow<List<BankCard>> = bankCardDao.getAllCards()
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val activeGoal: Flow<FinancialGoal?> = goalDao.getActiveGoal()
    val allGoals: Flow<List<FinancialGoal>> = goalDao.getAllGoals()

    suspend fun insertTransaction(transaction: Transaction) {
        // Insert transaction first
        transactionDao.insertTransaction(transaction)

        // Find the card and update balance
        val card = bankCardDao.getCardByBankName(transaction.bankName)
        if (card != null) {
            val balanceDiff = if (transaction.isExpense) -transaction.amount else transaction.amount
            bankCardDao.insertCard(card.copy(balance = card.balance + balanceDiff))
        }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction.id)

        // Refund/revert the balance change on the card
        val card = bankCardDao.getCardByBankName(transaction.bankName)
        if (card != null) {
            val balanceDiff = if (transaction.isExpense) transaction.amount else -transaction.amount
            bankCardDao.insertCard(card.copy(balance = card.balance + balanceDiff))
        }
    }

    suspend fun insertCard(card: BankCard) {
        bankCardDao.insertCard(card)
    }

    suspend fun deleteCard(id: Int) {
        bankCardDao.deleteCard(id)
    }

    suspend fun insertLoan(loan: Loan) {
        loanDao.insertLoan(loan)
    }

    suspend fun deleteLoan(id: Int) {
        loanDao.deleteLoan(id)
    }

    suspend fun insertGoal(goal: FinancialGoal) {
        goalDao.insertGoal(goal)
    }

    suspend fun deleteGoal(id: Int) {
        goalDao.deleteGoal(id)
    }

    suspend fun clearAll() {
        transactionDao.deleteAll()
    }
}
