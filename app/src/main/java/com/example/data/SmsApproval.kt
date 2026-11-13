package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * تأیید یک تراکنش پیامکی: همان یک مسیر برای شیت «تراکنش‌های تازه»، صفحهٔ بررسی و دکمهٔ «ثبت سریع»
 * نوتیفیکیشن.
 *
 * تأییدها یکی‌یکی انجام می‌شوند و وضعیت هر تراکنش درون همان قفل دوباره از دیتابیس خوانده می‌شود؛
 * پس دو بار زدن «ثبت»، «ثبت همه»، یا تأیید هم‌زمان از نوتیفیکیشن و برنامه هرگز یک پیامک را دو بار
 * ثبت نمی‌کند و برای یک بانک دو حساب هم‌نام نمی‌سازد (که موجودی هر دو با هم تغییر می‌کرد).
 */
object SmsApproval {

    private val lock = Mutex()

    /** حسابی که تراکنش رویش ثبت شد، و خود تراکنش پیامکی. */
    class Approved(val account: String, val pending: PendingTransaction)

    /**
     * تراکنش پیامکی [id] را ثبت می‌کند؛ هر مقدار null یعنی همان که از پیامک خوانده شده.
     * null برمی‌گرداند اگر دیگر در انتظار نباشد (قبلاً ثبت یا رد شده).
     */
    suspend fun approve(
        db: AppDatabase,
        id: Int,
        title: String? = null,
        category: String? = null,
        amount: Long? = null,
        account: String? = null,
        isExpense: Boolean? = null
    ): Approved? = lock.withLock {
        val dao = db.pendingTransactionDao()
        val p = dao.getById(id) ?: return@withLock null
        if (p.status != PendingStatus.PENDING) return@withLock null

        val repo = FinanceRepository(db)
        val expense = isExpense ?: p.isExpense
        val finalAmount = amount?.takeIf { it > 0 } ?: p.amount
        val name = account?.trim()?.takeIf { it.isNotEmpty() }
            ?: SmsIngest.accountFor(p, repo.allCardsNow(), db.bankSenderDao().getAllSenders())
        val finalCategory = category?.trim()?.takeIf { it.isNotEmpty() }
            ?: guessCategory(p.copy(isExpense = expense), db.categoryDao().getAllNow())

        db.withTransaction {
            if (repo.getCardByName(name) == null) {
                // حساب تازه با مانده‌ای ساخته می‌شود که بعد از همین تراکنش برابر «مانده»ٔ پیامک شود.
                val delta = if (expense) -finalAmount else finalAmount
                repo.insertCard(
                    BankCard(
                        bankName = name, cardNumber = "",
                        balance = if (p.balance > 0) p.balance - delta else 0L,
                        accountType = AccountType.BANK
                    )
                )
            }
            repo.insertTransaction(
                Transaction(
                    title = title?.trim()?.takeIf { it.isNotEmpty() } ?: p.title.ifBlank { "تراکنش بانکی" },
                    amount = finalAmount, category = finalCategory, date = p.date,
                    isExpense = expense, bankName = name,
                    type = if (expense) TransactionType.EXPENSE else TransactionType.INCOME
                )
            )
            // نگه داشته می‌شود (نه حذف) تا همان پیامک دوباره پیشنهاد نشود.
            dao.setStatus(id, PendingStatus.APPROVED)
        }
        Approved(name, p)
    }

    /** «مال من نیست»: فقط اگر هنوز در انتظار باشد. */
    suspend fun reject(db: AppDatabase, id: Int) = lock.withLock {
        val dao = db.pendingTransactionDao()
        val p = dao.getById(id) ?: return@withLock
        if (p.status == PendingStatus.PENDING) dao.setStatus(id, PendingStatus.REJECTED)
    }

    /**
     * دستهٔ پیشنهادی: دسته‌ای از همان نوع که نامش در عنوان یا متن پیامک آمده؛ وگرنه «سایر» برای هزینه و
     * «سایر درآمدها» برای درآمد (یا اگر کاربر این‌ها را ندارد، دسته‌ای از همان نوع که دارد). هرگز نامی که
     * در فهرست دسته‌ها نیست.
     */
    fun guessCategory(p: PendingTransaction, categories: List<Category>): String {
        val kind = categories.filter { it.isIncome == !p.isExpense }
        kind.firstOrNull { p.title.contains(it.name) || p.smsBody.contains(it.name) }?.let { return it.name }
        val fallback = if (p.isExpense) "سایر" else "سایر درآمدها"
        return kind.firstOrNull { it.name == fallback }?.name
            ?: (if (p.isExpense) kind.lastOrNull() else kind.firstOrNull { it.name != "حقوق" } ?: kind.firstOrNull())?.name
            ?: fallback
    }
}
