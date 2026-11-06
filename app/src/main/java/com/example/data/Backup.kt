package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A password-protected copy of everything the user recorded, in one file they
 * keep wherever they like (phone storage, Drive, Telegram saved messages…).
 *
 * File layout: "TRZB1" | salt (16) | iv (12) | AES-256-GCM ciphertext of the
 * JSON snapshot. The key comes from the password with PBKDF2 (120k rounds), so
 * the file is useless without it — and a wrong password is detected by GCM
 * instead of silently restoring garbage.
 *
 * Pending/unknown SMS are left out: they are re-read from the inbox, and
 * leaving them out keeps message text off the backup.
 */
object Backup {
    private val MAGIC = "TRZB1".toByteArray()
    private const val ITERATIONS = 120_000

    class WrongPasswordException : Exception("رمز پشتیبان درست نیست یا فایل خراب است.")
    class NotABackupException : Exception("این فایل پشتیبان تراز نیست.")

    // ─── encryption ───

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(data: ByteArray, password: String): String {
        if (data.size < MAGIC.size + 28 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = data.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val body = data.copyOfRange(MAGIC.size + 28, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
    }

    // ─── snapshot ───

    suspend fun snapshot(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("transactions", JSONArray(db.transactionDao().getAllTransactions().first().map { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("amount", t.amount).put("category", t.category)
                .put("date", t.date).put("isExpense", t.isExpense).put("bankName", t.bankName).put("type", t.type)
                .put("transferGroupId", t.transferGroupId ?: JSONObject.NULL).put("loanId", t.loanId ?: JSONObject.NULL)
        }))
        root.put("cards", JSONArray(db.bankCardDao().getAll().map { c ->
            JSONObject().put("id", c.id).put("bankName", c.bankName).put("cardNumber", c.cardNumber).put("balance", c.balance)
                .put("cardHolderName", c.cardHolderName).put("accountType", c.accountType)
        }))
        root.put("loans", JSONArray(db.loanDao().getAllLoans().first().map { l ->
            JSONObject().put("id", l.id).put("bankName", l.bankName).put("loanName", l.loanName).put("totalAmount", l.totalAmount)
                .put("paidAmount", l.paidAmount).put("installmentAmount", l.installmentAmount).put("dueDate", l.dueDate)
                .put("dueDay", l.dueDay).put("reminderEnabled", l.reminderEnabled)
        }))
        root.put("goals", JSONArray(db.goalDao().getAllGoals().first().map { g ->
            JSONObject().put("id", g.id).put("title", g.title).put("targetAmount", g.targetAmount)
                .put("currentAmount", g.currentAmount).put("isCompleted", g.isCompleted).put("createdAt", g.createdAt)
        }))
        root.put("categories", JSONArray(db.categoryDao().getAllCategories().first().map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("iconKey", c.iconKey).put("colorHex", c.colorHex)
                .put("isIncome", c.isIncome).put("sortOrder", c.sortOrder).put("monthlyBudget", c.monthlyBudget)
        }))
        root.put("loanPayments", JSONArray(db.loanPaymentDao().getAll().first().map { p ->
            JSONObject().put("id", p.id).put("loanId", p.loanId).put("amount", p.amount).put("date", p.date)
                .put("jalaliYear", p.jalaliYear).put("jalaliMonth", p.jalaliMonth).put("transactionId", p.transactionId ?: JSONObject.NULL)
        }))
        root.put("recurring", JSONArray(db.recurringDao().getAll().first().map { r ->
            JSONObject().put("id", r.id).put("title", r.title).put("amount", r.amount).put("category", r.category)
                .put("isExpense", r.isExpense).put("bankName", r.bankName).put("dayOfMonth", r.dayOfMonth)
                .put("enabled", r.enabled).put("lastRunKey", r.lastRunKey).put("createdAt", r.createdAt)
        }))
        root.put("debts", JSONArray(db.debtDao().getAll().first().map { d ->
            JSONObject().put("id", d.id).put("person", d.person).put("amount", d.amount).put("owedToMe", d.owedToMe)
                .put("note", d.note).put("createdAt", d.createdAt).put("settled", d.settled).put("settledAt", d.settledAt ?: JSONObject.NULL)
        }))
        root.put("senders", JSONArray(db.bankSenderDao().getAllSenders().map { s ->
            JSONObject().put("bankName", s.bankName).put("senderNumber", s.senderNumber).put("isEnabled", s.isEnabled)
                .put("learningStatus", s.learningStatus).put("parserVersion", s.parserVersion)
        }))
        root.toString()
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else getInt(name)
    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else getLong(name)
    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name) || !has(name)) null else getString(name)

    private inline fun JSONArray?.each(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (i in 0 until length()) block(getJSONObject(i))
    }

    /** Replaces everything in the database with the snapshot. Returns how many transactions came back. */
    suspend fun restore(db: AppDatabase, json: String): Int {
        val root = JSONObject(json)
        if (!root.has("transactions") || !root.has("cards")) throw NotABackupException()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        var count = 0
        db.withTransaction {
            root.optJSONArray("cards").each { o ->
                db.bankCardDao().insertCard(
                    BankCard(o.getInt("id"), o.getString("bankName"), o.optString("cardNumber"), o.getLong("balance"),
                        o.optString("cardHolderName", "کاربر تراز"), o.optString("accountType", AccountType.BANK))
                )
            }
            root.optJSONArray("transactions").each { o ->
                db.transactionDao().insertTransaction(
                    Transaction(
                        id = o.getInt("id"), title = o.getString("title"), amount = o.getLong("amount"), category = o.getString("category"),
                        date = o.getLong("date"), isExpense = o.getBoolean("isExpense"), bankName = o.getString("bankName"),
                        type = o.optString("type", TransactionType.EXPENSE), transferGroupId = o.optStringOrNull("transferGroupId"),
                        loanId = o.optIntOrNull("loanId")
                    )
                )
                count++
            }
            root.optJSONArray("loans").each { o ->
                db.loanDao().insertLoan(
                    Loan(o.getInt("id"), o.getString("bankName"), o.getString("loanName"), o.getLong("totalAmount"), o.getLong("paidAmount"),
                        o.getLong("installmentAmount"), o.optString("dueDate"), o.optInt("dueDay", 1), o.optBoolean("reminderEnabled", true))
                )
            }
            root.optJSONArray("goals").each { o ->
                db.goalDao().insertGoal(
                    FinancialGoal(o.getInt("id"), o.getString("title"), o.getLong("targetAmount"), o.getLong("currentAmount"),
                        o.optBoolean("isCompleted"), o.optLong("createdAt", System.currentTimeMillis()))
                )
            }
            val cats = mutableListOf<Category>()
            root.optJSONArray("categories").each { o ->
                cats += Category(o.getInt("id"), o.getString("name"), o.optString("iconKey", "other"), o.optString("colorHex", "#0B7A57"),
                    o.optBoolean("isIncome"), o.optInt("sortOrder"), o.optLong("monthlyBudget"))
            }
            db.categoryDao().insertAll(cats.ifEmpty { AppDatabase.DEFAULT_CATEGORIES })
            root.optJSONArray("loanPayments").each { o ->
                db.loanPaymentDao().insert(
                    LoanPayment(o.getInt("id"), o.getInt("loanId"), o.getLong("amount"), o.getLong("date"),
                        o.getInt("jalaliYear"), o.getInt("jalaliMonth"), o.optIntOrNull("transactionId"))
                )
            }
            root.optJSONArray("recurring").each { o ->
                db.recurringDao().insert(
                    RecurringTransaction(o.getInt("id"), o.getString("title"), o.getLong("amount"), o.getString("category"),
                        o.getBoolean("isExpense"), o.getString("bankName"), o.getInt("dayOfMonth"), o.optBoolean("enabled", true),
                        o.optInt("lastRunKey"), o.optLong("createdAt", System.currentTimeMillis()))
                )
            }
            root.optJSONArray("debts").each { o ->
                db.debtDao().insert(
                    Debt(o.getInt("id"), o.getString("person"), o.getLong("amount"), o.getBoolean("owedToMe"), o.optString("note"),
                        o.optLong("createdAt", System.currentTimeMillis()), o.optBoolean("settled"), o.optLongOrNull("settledAt"))
                )
            }
            root.optJSONArray("senders").each { o ->
                db.bankSenderDao().insertSender(
                    BankSender(bankName = o.getString("bankName"), senderNumber = o.getString("senderNumber"),
                        isEnabled = o.optBoolean("isEnabled", true), learningStatus = o.optString("learningStatus", "نیاز به آموزش"),
                        parserVersion = o.optInt("parserVersion"))
                )
            }
        }
        return count
    }

    // ─── spreadsheet export ───

    /**
     * Transactions as CSV with a UTF-8 BOM so Excel shows the Persian text
     * correctly; dates in both Jalali and ISO for sorting.
     */
    suspend fun transactionsCsv(db: AppDatabase): ByteArray = withContext(Dispatchers.IO) {
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.append("تاریخ,تاریخ میلادی,عنوان,دسته,حساب,نوع,مبلغ (تومان)\n")
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        for (t in db.transactionDao().getAllTransactions().first()) {
            val j = com.example.utils.JalaliDate.fromTimestamp(t.date)
            val kind = when {
                t.type == TransactionType.TRANSFER -> "انتقال"
                t.isExpense -> "هزینه"
                else -> "درآمد"
            }
            sb.append(cell("%04d/%02d/%02d".format(java.util.Locale.US, j.year, j.month, j.day))).append(',')
                .append(cell(iso.format(java.util.Date(t.date)))).append(',')
                .append(cell(t.title)).append(',')
                .append(cell(t.category)).append(',')
                .append(cell(t.bankName)).append(',')
                .append(cell(kind)).append(',')
                .append(if (t.isExpense) -t.amount else t.amount).append('\n')
        }
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8)
    }
}
