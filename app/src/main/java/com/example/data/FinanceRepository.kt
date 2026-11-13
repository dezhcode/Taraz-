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
    val allTrades: Flow<List<AssetTrade>> = db.assetTradeDao().getAll()
    val allAlerts: Flow<List<PriceAlert>> = db.priceAlertDao().getAll()

    // ─── Cheques ───

    /**
     * Saves a cheque. One that has already cleared carries an account transaction:
     * when its amount, account, category or direction is edited, that transaction is
     * recorded again with the new figures (on its original date), so the cheque and
     * the balance never disagree.
     */
    suspend fun saveCheque(c: Cheque) = db.withTransaction {
        val old = c.transactionId?.takeIf { c.status == ChequeStatus.CLEARED }?.let { transactionDao.getById(it) }
        if (old != null && (old.amount != c.amount || old.bankName != c.bankName || old.category != c.category || old.isExpense != c.issued)) {
            setChequeStatus(c, ChequeStatus.CLEARED, c.bankName, at = old.date)
        } else {
            chequeDao.insert(c)
        }
    }

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
                        title = if (c.issued) "پرداخت چک به ${c.person}" else "دریافت چک از ${c.person}",
                        amount = c.amount, category = c.category, date = at,
                        isExpense = c.issued, bankName = account,
                        type = if (c.issued) TransactionType.EXPENSE else TransactionType.INCOME
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
        while (m < 1) { m += 12; y-- }
        val day = d.day.coerceAtMost(com.example.utils.JalaliDate.daysInMonth(y, m))
        val timeOfDay = ts - com.example.utils.JalaliDate.toTimestamp(d.year, d.month, d.day)
        return com.example.utils.JalaliDate.toTimestamp(y, m, day) + timeOfDay
    }

    // ─── Net worth vs inflation ───

    suspend fun saveSnapshot(s: NetWorthSnapshot) = snapshotDao.insert(s)

    suspend fun saveAsset(asset: Asset) = assetDao.insert(asset)

    suspend fun deleteAsset(id: Int) = assetDao.delete(id)

    /**
     * Records a purchase or sale of [quantity] units for [total] (book currency) and updates the
     * holding: a purchase adds units and cost; a sale takes units off at their average cost, and
     * when nothing is left the holding goes from the list (its trades stay as history). With
     * [bankName], the money leaves or reaches that account as a transfer, so buying gold is not
     * counted as spending. [asset] is null for a first purchase.
     */
    suspend fun recordTrade(
        asset: Asset?, code: String, name: String, kind: String, isBuy: Boolean, quantity: Double, total: Long,
        date: Long, bankName: String?, note: String = ""
    ) = db.withTransaction {
        require(quantity > 0 && total >= 0)
        val current = asset?.id?.let { assetDao.getById(it) }
        var assetId = current?.id ?: 0
        var costBasis = 0L
        if (isBuy) {
            if (current == null) {
                assetId = assetDao.insert(Asset(code = code, name = name, kind = kind, quantity = quantity, buyPrice = total, createdAt = date)).toInt()
            } else {
                // بهای خرید قبلی نامعلوم بوده: نیمه‌کاره جمع نشود، سود اشتباه نشان می‌داد
                val cost = if (current.buyPrice > 0 || current.quantity <= 0) current.buyPrice + total else 0L
                assetDao.insert(current.copy(quantity = current.quantity + quantity, buyPrice = cost))
            }
        } else {
            requireNotNull(current) { "nothing to sell" }
            val sold = quantity.coerceAtMost(current.quantity)
            costBasis = if (current.buyPrice > 0 && current.quantity > 0) Math.round(current.buyPrice * sold / current.quantity) else 0L
            val left = current.quantity - sold
            if (left <= 1e-9) assetDao.delete(current.id)
            else assetDao.insert(current.copy(quantity = left, buyPrice = (current.buyPrice - costBasis).coerceAtLeast(0)))
        }
        var txId: Int? = null
        if (!bankName.isNullOrBlank() && total > 0) {
            txId = insertTransaction(
                Transaction(
                    title = (if (isBuy) "خرید " else "فروش ") + name, amount = total, category = "سرمایه‌گذاری", date = date,
                    isExpense = isBuy, bankName = bankName, type = TransactionType.TRANSFER
                )
            ).toInt()
        }
        db.assetTradeDao().insert(
            AssetTrade(
                assetId = assetId, code = code, name = name, kind = kind, isBuy = isBuy, quantity = quantity, total = total,
                costBasis = costBasis, date = date, bankName = bankName?.takeIf { it.isNotBlank() }, transactionId = txId, note = note
            )
        )
    }

    /** Takes a trade back: the units and their cost return to (or leave) the holding, and the account's money moves back. */
    suspend fun deleteTrade(trade: AssetTrade) = db.withTransaction {
        val current = assetDao.getById(trade.assetId) ?: db.assetDao().getAllNow().firstOrNull { it.code == trade.code }
        if (trade.isBuy) {
            if (current != null) {
                val left = current.quantity - trade.quantity
                if (left <= 1e-9) assetDao.delete(current.id)
                else assetDao.insert(current.copy(quantity = left, buyPrice = (current.buyPrice - trade.total).coerceAtLeast(0)))
            }
        } else {
            if (current != null) assetDao.insert(current.copy(quantity = current.quantity + trade.quantity, buyPrice = current.buyPrice + trade.costBasis))
            else assetDao.insert(
                Asset(id = trade.assetId, code = trade.code, name = trade.name, kind = trade.kind, quantity = trade.quantity, buyPrice = trade.costBasis)
            )
        }
        trade.transactionId?.let { id -> transactionDao.getById(id)?.let { deleteTransaction(it) } }
        db.assetTradeDao().delete(trade.id)
    }

    /**
     * Changes a recorded trade: it is taken back exactly as [deleteTrade] does (units, cost and the
     * account's money), then recorded again with the new values, in one step. The holding keeps its
     * row, name and manual price even when taking the old trade back emptied it.
     */
    suspend fun updateTrade(
        old: AssetTrade, isBuy: Boolean, quantity: Double, total: Long, date: Long, bankName: String?
    ) = db.withTransaction {
        val before = assetDao.getById(old.assetId) ?: assetDao.getAllNow().firstOrNull { it.code == old.code }
        deleteTrade(old)
        // برگرداندن فروشی که همه را فروخته بود، دارایی را دوباره می‌سازد؛ وگرنه ردیف قبلی (یا پوستهٔ خالی‌اش)
        val holding = assetDao.getById(before?.id ?: old.assetId)
            ?: assetDao.getAllNow().firstOrNull { it.code == old.code }
            ?: before?.copy(quantity = 0.0, buyPrice = 0)?.also { assetDao.insert(it) }
        recordTrade(holding, old.code, old.name, old.kind, isBuy, quantity, total, date, bankName, old.note)
        // خریدی که روی داراییِ خالی‌شده ثبت شد مقدارش را دارد؛ اگر چیزی نماند، ردیف خالی نماند
        holding?.let { h -> assetDao.getById(h.id)?.takeIf { it.quantity <= 1e-9 }?.let { assetDao.delete(it.id) } }
    }

    /**
     * Removes a holding and its purchase/sale history. With [undoMoney], the account transactions
     * those trades made are deleted too, so the balances go back; without it they stay, since the
     * money really moved.
     */
    suspend fun deleteAssetWithTrades(asset: Asset, undoMoney: Boolean) = db.withTransaction {
        val others = assetDao.getAllNow().any { it.id != asset.id && it.code == asset.code }
        val trades = db.assetTradeDao().getAllNow().filter { it.assetId == asset.id || (!others && it.code == asset.code) }
        for (t in trades) {
            if (undoMoney) t.transactionId?.let { id -> transactionDao.getById(id)?.let { deleteTransaction(it) } }
            db.assetTradeDao().delete(t.id)
        }
        assetDao.delete(asset.id)
    }

    suspend fun loanById(id: Int): Loan? = loanDao.getById(id)

    suspend fun saveAlert(alert: PriceAlert) = db.priceAlertDao().insert(alert)

    suspend fun deleteAlert(id: Int) = db.priceAlertDao().delete(id)

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
        // A cleared cheque whose money is taken back is pending again, and a bill whose
        // payment is taken back is due again, so neither says «done» with nothing behind it.
        val ids = rows.map { it.id }.toSet()
        val cheques = chequeDao.getAllNow().filter { it.transactionId != null && it.transactionId in ids }
        cheques.forEach { chequeDao.insert(it.copy(status = ChequeStatus.PENDING, transactionId = null)) }
        val bills = billDao.getAllNow().filter { b ->
            b.lastPaidAt != null && rows.any { r -> r.isExpense && r.category == "قبوض" && r.date == b.lastPaidAt }
        }
        bills.forEach { b ->
            billDao.insert(b.copy(lastPaidAt = null, dueDate = shiftMonths(b.dueDate, -b.periodMonths.coerceAtLeast(1))))
        }
        Deleted(rows, payments, debts, cheques, bills)
    }

    /** What a delete removed: enough to restore it exactly. */
    data class Deleted(
        val transactions: List<Transaction>,
        val loanPayments: List<LoanPayment>,
        val debts: List<Debt>,
        /** Cheques and bills as they were before the delete reopened them. */
        val cheques: List<Cheque> = emptyList(),
        val bills: List<Bill> = emptyList()
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
        d.cheques.forEach { chequeDao.insert(it) }
        d.bills.forEach { billDao.insert(it) }
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
        // چک وصول‌شده‌ای که این تراکنش را ساخته با آن هم‌خوان می‌ماند (مبلغ، حساب، دسته).
        chequeDao.getAllNow().filter { it.transactionId == old.id }.forEach { c ->
            chequeDao.insert(c.copy(amount = merged.amount, bankName = merged.bankName, category = merged.category))
        }
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
        // Cheques and bills name the account they are paid from; left behind, a cheque
        // cleared later was recorded on the old name and moved no balance.
        chequeDao.renameAccount(card.bankName, clean)
        billDao.renameAccount(card.bankName, clean)
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
        for (listed in recurringDao.getEnabled()) {
            if (listed.lastRunKey >= key) continue
            val day = listed.dayOfMonth.coerceIn(1, 31).coerceAtMost(JalaliDate.daysInMonth(today.year, today.month))
            if (today.day < day) continue
            val recorded = db.withTransaction {
                // دوباره و داخل همان تراکنش خوانده می‌شود: باز شدن برنامه و کار روزانهٔ پس‌زمینه ممکن است
                // هم‌زمان اجرا شوند و هر دو همان مورد را برای این ماه ثبت کنند.
                val item = recurringDao.getById(listed.id)?.takeIf { it.enabled && it.lastRunKey < key }
                    ?: return@withTransaction false
                insertTransaction(
                    Transaction(
                        title = item.title, amount = item.amount, category = item.category,
                        date = JalaliDate.toTimestamp(today.year, today.month, day) + 9 * 60 * 60 * 1000L,
                        isExpense = item.isExpense, bankName = item.bankName,
                        type = if (item.isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                    )
                )
                recurringDao.insert(item.copy(lastRunKey = key))
                true
            }
            if (recorded) count++
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
                owedToMe -> "پرداخت به ${person.name}"
                else -> "دریافت از ${person.name}"
            }
            txId = insertTransaction(
                Transaction(
                    title = if (note.isNotBlank()) "$title · $note" else title,
                    amount = amount, category = "حساب اشخاص", date = at,
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

    // ─── Currency ───

    /**
     * Re-expresses every stored amount from [from] to [to] in one transaction: toman↔rial exactly
     * (×10 / ÷10, rounded to the nearest toman), and to or from dollars at [usdToman] tomans per
     * dollar (dollars are kept in cents). A month's net-worth snapshot is converted at its own
     * dollar rate. Nothing changes when [from] equals [to].
     */
    suspend fun convertCurrency(from: AppCurrency, to: AppCurrency, usdToman: Long) = db.withTransaction {
        if (from == to) return@withTransaction
        require(usdToman > 0 || (from != AppCurrency.USD && to != AppCurrency.USD)) { "a dollar rate is needed" }
        val f = Money.factor(from, to, usdToman)
        val columns = listOf(
            "transactions" to listOf("amount"),
            "bank_cards" to listOf("balance", "creditLimit"),
            "loans" to listOf("totalAmount", "paidAmount", "installmentAmount"),
            "financial_goals" to listOf("targetAmount", "currentAmount"),
            "categories" to listOf("monthlyBudget"),
            "loan_payments" to listOf("amount"),
            "recurring_transactions" to listOf("amount"),
            "debts" to listOf("amount"),
            "assets" to listOf("buyPrice", "manualPrice"),
            "asset_trades" to listOf("total", "costBasis"),
            "cheques" to listOf("amount"),
            "bills" to listOf("amount"),
            "pending_transactions" to listOf("amount", "balance")
        )
        val sql = db.openHelper.writableDatabase
        for ((table, cols) in columns) {
            val set = cols.joinToString(", ") { "$it = CAST(ROUND($it * ?) AS INTEGER)" }
            sql.execSQL("UPDATE $table SET $set", Array<Any>(cols.size) { f })
        }
        for (s in snapshotDao.getAllNow()) {
            snapshotDao.insert(s.copy(netWorth = Money.convert(s.netWorth, from, to, s.usdToman.takeIf { it > 0 } ?: usdToman)))
        }
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
