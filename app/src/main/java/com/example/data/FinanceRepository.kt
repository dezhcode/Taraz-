package com.example.data

import androidx.room.withTransaction
import com.example.utils.JalaliDate
import kotlinx.coroutines.flow.Flow

/**
 * Every write that touches money goes through here, inside one database
 * transaction, and moves balances with a single `balance = balance + delta`
 * statement. Reading a balance, adding to it in Kotlin and writing it back let
 * two quick writes (two SMS, a transfer's two halves) overwrite each other.
 *
 * Accounts are still linked to transactions by name ([Transaction.bankName]);
 * [renameAccount] and [deleteAccount] keep the two in step, so an account can
 * be renamed, and a deleted account's history never attaches itself to a new
 * account that happens to get the same name.
 */
class FinanceRepository(private val db: AppDatabase) {
    private val transactionDao = db.transactionDao()
    private val bankCardDao = db.bankCardDao()
    private val loanDao = db.loanDao()
    private val goalDao = db.goalDao()
    private val categoryDao = db.categoryDao()
    private val loanPaymentDao = db.loanPaymentDao()
    private val recurringDao = db.recurringDao()
    private val debtDao = db.debtDao()
    private val personDao = db.personDao()
    private val assetDao = db.assetDao()
    private val chequeDao = db.chequeDao()
    private val billDao = db.billDao()
    private val snapshotDao = db.snapshotDao()

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allCards: Flow<List<BankCard>> = bankCardDao.getAllCards()
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val activeGoal: Flow<FinancialGoal?> = goalDao.getActiveGoal()
    val allGoals: Flow<List<FinancialGoal>> = goalDao.getAllGoals()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allLoanPayments: Flow<List<LoanPayment>> = loanPaymentDao.getAll()
    val allRecurring: Flow<List<RecurringTransaction>> = recurringDao.getAll()
    val allDebts: Flow<List<Debt>> = debtDao.getAll()
    val allPersons: Flow<List<Person>> = personDao.getAll()
    val allAssets: Flow<List<Asset>> = assetDao.getAll()
    val allCheques: Flow<List<Cheque>> = chequeDao.getAll()
    val allBills: Flow<List<Bill>> = billDao.getAll()
    val allSnapshots: Flow<List<NetWorthSnapshot>> = snapshotDao.getAll()

    // ─── Cheques ───

    suspend fun saveCheque(c: Cheque) = chequeDao.insert(c)

    /** Deleting a cleared cheque also takes back the account transaction it made. */
    suspend fun deleteCheque(c: Cheque) = db.withTransaction {
        c.transactionId?.let { id -> transactionDao.getById(id)?.let { deleteTransaction(it) } }
        chequeDao.delete(c.id)
    }

    /**
     * Moves a cheque to [status]. Clearing records the money on [account]
     * (out for one the user wrote, in for one they received); going back
     * from cleared takes that transaction away again.
     */
    suspend fun setChequeStatus(c: Cheque, status: String, account: String = c.bankName, at: Long = System.currentTimeMillis()) =
        db.withTransaction {
            c.transactionId?.let { id -> transactionDao.getById(id)?.let { deleteTransaction(it) } }
            var txId: Int? = null
            if (status == ChequeStatus.CLEARED) {
                txId = insertTransaction(
                    Transaction(
                        title = if (c.issued) "چک به ${c.person}" else "چک از ${c.person}",
                        amount = c.amount, category = c.category, date = at,
                        isExpense = c.issued, bankName = account
                    )
                ).toInt()
            }
            chequeDao.insert(c.copy(status = status, transactionId = txId, bankName = account))
        }

    // ─── Bills ───

    suspend fun saveBill(b: Bill) = billDao.insert(b)

    suspend fun deleteBill(id: Int) = billDao.delete(id)

    /** Records the payment as a «قبوض» expense and moves the bill to its next period. */
    suspend fun payBill(b: Bill, amount: Long, account: String, at: Long = System.currentTimeMillis()) = db.withTransaction {
        insertTransaction(
            Transaction(
                title = "قبض ${b.title.ifBlank { b.kind }}", amount = amount, category = "قبوض",
                date = at, isExpense = true, bankName = account
            )
        )
        billDao.insert(b.copy(amount = amount, bankName = account, lastPaidAt = at, dueDate = shiftMonths(b.dueDate, b.periodMonths.coerceAtLeast(1))))
    }

    /** Same Jalali day [n] months later (clamped to the month's length). */
    private fun shiftMonths(ts: Long, n: Int): Long {
        val d = com.example.utils.JalaliDate.fromTimestamp(ts)
        var y = d.year
        var m = d.month + n
        while (m > 12) { m -= 12; y++ }
        val day = d.day.coerceAtMost(com.example.utils.JalaliDate.daysInMonth(y, m))
        val timeOfDay = ts - com.example.utils.JalaliDate.toTimestamp(d.year, d.month, d.day)
        return com.example.utils.JalaliDate.toTimestamp(y, m, day) + timeOfDay
    }

    // ─── Net worth vs inflation ───

    suspend fun saveSnapshot(s: NetWorthSnapshot) = snapshotDao.insert(s)

    suspend fun saveAsset(asset: Asset) = assetDao.insert(asset)

    suspend fun deleteAsset(id: Int) = assetDao.delete(id)

    private fun signed(tx: Transaction): Long = if (tx.isExpense) -tx.amount else tx.amount

    // ─── Categories ───

    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)

    suspend fun deleteCategory(id: Int) = categoryDao.deleteCategory(id)

    /** Saves an edited category; a new name is carried onto its transactions and recurring items. */
    suspend fun updateCategory(old: Category, updated: Category) = db.withTransaction {
        categoryDao.insertCategory(updated)
        if (old.name != updated.name) {
            transactionDao.renameCategory(old.name, updated.name)
            recurringDao.renameCategory(old.name, updated.name)
        }
    }

    // ─── Transactions ───

    /** Records [transaction] and moves its account's balance. Returns the new row id. */
    suspend fun insertTransaction(transaction: Transaction): Long = db.withTransaction {
        val id = transactionDao.insertTransaction(transaction)
        bankCardDao.adjustBalance(transaction.bankName, signed(transaction))
        id
    }

    /** Both halves of a transfer, written together or not at all. */
    suspend fun insertTransfer(fromName: String, toName: String, amount: Long, date: Long = System.currentTimeMillis()) {
        val groupId = java.util.UUID.randomUUID().toString()
        db.withTransaction {
            insertTransaction(
                Transaction(
                    title = "انتقال به $toName", amount = amount, category = "انتقال", date = date,
                    isExpense = true, bankName = fromName, type = TransactionType.TRANSFER, transferGroupId = groupId
                )
            )
            insertTransaction(
                Transaction(
                    title = "انتقال از $fromName", amount = amount, category = "انتقال", date = date,
                    isExpense = false, bankName = toName, type = TransactionType.TRANSFER, transferGroupId = groupId
                )
            )
        }
    }

    /**
     * Deletes [transaction] and undoes its effect: the balance goes back, both
     * halves of a transfer go together, and an instalment payment is taken off
     * its loan again.
     */
    suspend fun deleteTransaction(transaction: Transaction): Deleted = db.withTransaction {
        val rows = transaction.transferGroupId?.let { transactionDao.getByTransferGroup(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(transactionDao.getById(transaction.id))
        // What goes with the rows, kept so «برگردان» can put everything back.
        val payments = rows.flatMap { loanPaymentDao.byTransaction(it.id) }
        val debts = rows.flatMap { debtDao.byTransaction(it.id) }
        for (row in rows) {
            transactionDao.deleteTransaction(row.id)
            bankCardDao.adjustBalance(row.bankName, -signed(row))
            val loanId = row.loanId
            if (loanId != null) {
                loanDao.getById(loanId)?.let { loan ->
                    loanDao.insertLoan(loan.copy(paidAmount = (loan.paidAmount - row.amount).coerceAtLeast(0)))
                }
                loanPaymentDao.deleteByTransaction(row.id)
            }
            // A loan to or from a person goes with the money that moved for it.
            debtDao.deleteByTransaction(row.id)
        }
        Deleted(rows, payments, debts)
    }

    /** What a delete removed: enough to restore it exactly. */
    data class Deleted(
        val transactions: List<Transaction>,
        val loanPayments: List<LoanPayment>,
        val debts: List<Debt>
    )

    /** Undoes [deleteTransaction] / [deletePersonEntry]: rows, balances, loan payments and ledger lines come back. */
    suspend fun restore(d: Deleted) = db.withTransaction {
        for (row in d.transactions) {
            insertTransaction(row)
            val loanId = row.loanId
            if (loanId != null) {
                loanDao.getById(loanId)?.let { loan ->
                    loanDao.insertLoan(loan.copy(paidAmount = (loan.paidAmount + row.amount).coerceAtMost(loan.totalAmount)))
                }
            }
        }
        d.loanPayments.forEach { loanPaymentDao.insert(it) }
        d.debts.forEach { debtDao.insert(it) }
    }

    /**
     * Replaces a transaction with its edited version: the old effect on the
     * balance is taken back and the new one applied, in one step. An edited
     * instalment amount corrects the loan as well.
     */
    suspend fun updateTransaction(updated: Transaction) = db.withTransaction {
        val old = transactionDao.getById(updated.id) ?: return@withTransaction
        bankCardDao.adjustBalance(old.bankName, -signed(old))
        val merged = updated.copy(
            type = when {
                old.type == TransactionType.TRANSFER -> TransactionType.TRANSFER
                updated.isExpense -> TransactionType.EXPENSE
                else -> TransactionType.INCOME
            },
            transferGroupId = old.transferGroupId,
            loanId = old.loanId
        )
        transactionDao.insertTransaction(merged)
        bankCardDao.adjustBalance(merged.bankName, signed(merged))
        val loanId = old.loanId
        if (loanId != null && merged.amount != old.amount) {
            loanDao.getById(loanId)?.let { loan ->
                val paid = (loan.paidAmount - old.amount + merged.amount).coerceIn(0, loan.totalAmount)
                loanDao.insertLoan(loan.copy(paidAmount = paid))
            }
            loanPaymentDao.updateAmountByTransaction(old.id, merged.amount)
        }
    }

    // ─── Accounts ───

    suspend fun insertCard(card: BankCard) = bankCardDao.insertCard(card)

    suspend fun getCardByName(name: String): BankCard? = bankCardDao.getCardByBankName(name)

    suspend fun allCardsNow(): List<BankCard> = bankCardDao.getAll()

    /**
     * Renames an account and every transaction and recurring item recorded on it.
     * Returns false when another account already has [newName].
     */
    suspend fun renameAccount(card: BankCard, newName: String): Boolean = db.withTransaction {
        val clean = newName.trim()
        if (clean.isEmpty()) return@withTransaction false
        if (clean == card.bankName) return@withTransaction true
        val taken = bankCardDao.getAll().any { it.id != card.id && it.bankName.equals(clean, ignoreCase = true) }
        if (taken) return@withTransaction false
        bankCardDao.rename(card.id, clean)
        transactionDao.renameAccount(card.bankName, clean)
        recurringDao.renameAccount(card.bankName, clean)
        true
    }

    /**
     * Removes an account but keeps its transactions, relabelled «نام (حذف‌شده)»
     * so that they no longer count towards an account added later with the same
     * name. Its recurring items stop.
     */
    suspend fun deleteAccount(id: Int) = db.withTransaction {
        val card = bankCardDao.getById(id) ?: return@withTransaction
        bankCardDao.deleteCard(id)
        var label = "${card.bankName} (حذف‌شده)"
        var n = 2
        while (bankCardDao.getCardByBankName(label) != null) label = "${card.bankName} (حذف‌شده ${n++})"
        transactionDao.renameAccount(card.bankName, label)
        recurringDao.disableForAccount(card.bankName)
    }

    // ─── Loans ───

    suspend fun insertLoan(loan: Loan) = loanDao.insertLoan(loan)

    suspend fun deleteLoan(id: Int) = db.withTransaction {
        loanDao.deleteLoan(id)
        loanPaymentDao.deleteForLoan(id)
    }

    suspend fun paymentsFor(loanId: Int): List<LoanPayment> = loanPaymentDao.forLoan(loanId)

    /**
     * Pays one instalment of [loanId] from the account [fromName]. When less
     * than an instalment is left, only what is left is paid. Returns the amount
     * paid, or null when the loan is already settled or gone.
     */
    suspend fun payInstallment(loanId: Int, fromName: String, amountOverride: Long? = null, at: Long = System.currentTimeMillis()): Long? =
        db.withTransaction {
            val loan = loanDao.getById(loanId) ?: return@withTransaction null
            val remaining = loan.totalAmount - loan.paidAmount
            if (remaining <= 0) return@withTransaction null
            val wanted = (amountOverride ?: loan.installmentAmount).takeIf { it > 0 } ?: remaining
            val amount = minOf(wanted, remaining)
            val paidKeys = loanPaymentDao.forLoan(loanId).map { it.monthKey }.toSet()
            val key = JalaliDate.settledMonthKey(loan.dueDay, at, paidKeys)
            val txId = transactionDao.insertTransaction(
                Transaction(
                    title = "پرداخت قسط ${loan.loanName}",
                    amount = amount,
                    category = "قسط",
                    date = at,
                    isExpense = true,
                    bankName = fromName,
                    type = TransactionType.EXPENSE,
                    loanId = loan.id
                )
            ).toInt()
            bankCardDao.adjustBalance(fromName, -amount)
            loanDao.insertLoan(loan.copy(paidAmount = loan.paidAmount + amount))
            loanPaymentDao.insert(
                LoanPayment(loanId = loan.id, amount = amount, date = at, jalaliYear = key / 100, jalaliMonth = key % 100, transactionId = txId)
            )
            amount
        }

    // ─── Goals ───

    suspend fun insertGoal(goal: FinancialGoal) = goalDao.insertGoal(goal)

    suspend fun deleteGoal(id: Int) = goalDao.deleteGoal(id)

    // ─── Recurring ───

    suspend fun insertRecurring(item: RecurringTransaction) = recurringDao.insert(item)

    suspend fun deleteRecurring(id: Int) = recurringDao.delete(id)

    /**
     * Records every enabled recurring item whose day has come this month and
     * that has not been recorded for this month yet. Safe to call often.
     * Returns how many were recorded.
     */
    suspend fun runDueRecurring(now: Long = System.currentTimeMillis()): Int {
        val today = JalaliDate.fromTimestamp(now)
        val key = today.monthKey()
        var count = 0
        for (item in recurringDao.getEnabled()) {
            if (item.lastRunKey >= key) continue
            val day = item.dayOfMonth.coerceIn(1, 31).coerceAtMost(JalaliDate.daysInMonth(today.year, today.month))
            if (today.day < day) continue
            db.withTransaction {
                insertTransaction(
                    Transaction(
                        title = item.title, amount = item.amount, category = item.category,
                        date = JalaliDate.toTimestamp(today.year, today.month, day) + 9 * 60 * 60 * 1000L,
                        isExpense = item.isExpense, bankName = item.bankName,
                        type = if (item.isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                    )
                )
                recurringDao.insert(item.copy(lastRunKey = key))
            }
            count++
        }
        return count
    }

    // ─── People ───

    /** Adds or edits a person; a new name is copied onto their ledger lines. Returns the id. */
    suspend fun savePerson(person: Person): Int = db.withTransaction {
        val id = personDao.insert(person).toInt()
        if (person.id != 0) {
            debtDao.renamePerson(person.id, person.name)
            person.id
        } else id
    }

    /** Removes a person and their ledger. Account transactions stay: that money really moved. */
    suspend fun deletePerson(id: Int) = db.withTransaction {
        debtDao.deleteForPerson(id)
        personDao.delete(id)
    }

    /**
     * Adds a line to a person's ledger. With [account], the money is also
     * recorded on that account as a transfer — it changes the balance but is
     * not spending or income (lending is not buying anything).
     */
    suspend fun addPersonEntry(
        person: Person, amount: Long, owedToMe: Boolean, isRepayment: Boolean, account: String?, note: String,
        at: Long = System.currentTimeMillis(), dueDate: Long? = null
    ) = db.withTransaction {
        var txId: Int? = null
        if (!account.isNullOrBlank()) {
            val title = when {
                !isRepayment && owedToMe -> "قرض به ${person.name}"
                !isRepayment -> "قرض از ${person.name}"
                owedToMe -> "پس دادن به ${person.name}"
                else -> "پس گرفتن از ${person.name}"
            }
            txId = insertTransaction(
                Transaction(
                    title = if (note.isNotBlank()) "$title · $note" else title,
                    amount = amount, category = "قرض و طلب", date = at,
                    // Lending or paying back takes money out; borrowing or getting it back brings it in.
                    isExpense = owedToMe, bankName = account, type = TransactionType.TRANSFER
                )
            ).toInt()
        }
        debtDao.insert(
            Debt(
                person = person.name, amount = amount, owedToMe = owedToMe, note = note.trim(), createdAt = at,
                personId = person.id, isRepayment = isRepayment, transactionId = txId, dueDate = dueDate
            )
        )
    }

    /**
     * A shared bill paid from one account: the user's own share is spending in
     * [category]; everyone else's share becomes a loan to them, so the account
     * drops by the whole bill while monthly spending shows only the user's part.
     */
    suspend fun splitExpense(
        title: String, total: Long, account: String, category: String, includeMe: Boolean, people: List<Person>
    ) = db.withTransaction {
        val n = people.size + if (includeMe) 1 else 0
        if (n == 0 || total <= 0) return@withTransaction
        val share = total / n
        val rest = total - share * n
        if (includeMe) {
            insertTransaction(
                Transaction(
                    title = title, amount = share + rest, category = category, isExpense = true,
                    bankName = account, type = TransactionType.EXPENSE
                )
            )
        }
        people.forEachIndexed { i, p ->
            val amount = share + if (!includeMe && i == 0) rest else 0
            if (amount > 0) addPersonEntry(p, amount, owedToMe = true, isRepayment = false, account = account, note = "سهم $title")
        }
    }

    /** Removes a ledger line and the account transaction recorded with it. */
    suspend fun deletePersonEntry(debt: Debt): Deleted = db.withTransaction {
        val withTx = debt.transactionId?.let { id -> transactionDao.getById(id)?.let { deleteTransaction(it) } }
        debtDao.delete(debt.id)
        Deleted(withTx?.transactions.orEmpty(), withTx?.loanPayments.orEmpty(), (withTx?.debts.orEmpty() + debt).distinctBy { it.id })
    }

    // ─── Reset ───

    /**
     * Empties every table — accounts, transactions, loans, goals, banks, SMS,
     * recurring items and debts — then puts the starting categories back.
     */
    suspend fun clearAll() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        db.withTransaction { categoryDao.insertAll(AppDatabase.DEFAULT_CATEGORIES) }
    }
}
