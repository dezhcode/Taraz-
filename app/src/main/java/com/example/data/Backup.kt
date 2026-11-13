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

    class WrongPasswordException : Exception("رمز پشتیبان نادرست است یا فایل آسیب دیده است.")
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

    /** [photos]: the attachments folder; its pictures travel inside the backup. */
    suspend fun snapshot(db: AppDatabase, photos: java.io.File? = null): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", 1)
        root.put("exportedAt", System.currentTimeMillis())
        // مبلغ‌ها به همین واحد ذخیره شده‌اند؛ بازگردانی واحد را هم برمی‌گرداند
        root.put("currency", Money.current.code)
        root.put("transactions", JSONArray(db.transactionDao().getAllTransactions().first().map { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("amount", t.amount).put("category", t.category)
                .put("date", t.date).put("isExpense", t.isExpense).put("bankName", t.bankName).put("type", t.type)
                .put("transferGroupId", t.transferGroupId ?: JSONObject.NULL).put("loanId", t.loanId ?: JSONObject.NULL)
                .put("tags", t.tags).put("attachments", t.attachments)
        }))
        root.put("cards", JSONArray(db.bankCardDao().getAll().map { c ->
            JSONObject().put("id", c.id).put("bankName", c.bankName).put("cardNumber", c.cardNumber).put("balance", c.balance)
                .put("cardHolderName", c.cardHolderName).put("accountType", c.accountType)
                .put("creditLimit", c.creditLimit).put("dueDay", c.dueDay)
        }))
        root.put("loans", JSONArray(db.loanDao().getAllLoans().first().map { l ->
            JSONObject().put("id", l.id).put("bankName", l.bankName).put("loanName", l.loanName).put("totalAmount", l.totalAmount)
                .put("paidAmount", l.paidAmount).put("installmentAmount", l.installmentAmount).put("dueDate", l.dueDate)
                .put("dueDay", l.dueDay).put("reminderEnabled", l.reminderEnabled).put("kind", l.kind)
        }))
        root.put("goals", JSONArray(db.goalDao().getAllGoals().first().map { g ->
            JSONObject().put("id", g.id).put("title", g.title).put("targetAmount", g.targetAmount)
                .put("currentAmount", g.currentAmount).put("isCompleted", g.isCompleted).put("createdAt", g.createdAt)
                .put("deadline", g.deadline ?: JSONObject.NULL)
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
                .put("personId", d.personId ?: JSONObject.NULL).put("isRepayment", d.isRepayment).put("transactionId", d.transactionId ?: JSONObject.NULL)
                .put("dueDate", d.dueDate ?: JSONObject.NULL)
        }))
        root.put("cheques", JSONArray(db.chequeDao().getAllNow().map { c ->
            JSONObject().put("id", c.id).put("issued", c.issued).put("amount", c.amount).put("dueDate", c.dueDate).put("person", c.person)
                .put("bankName", c.bankName).put("sayadi", c.sayadi).put("category", c.category).put("note", c.note).put("status", c.status)
                .put("transactionId", c.transactionId ?: JSONObject.NULL).put("createdAt", c.createdAt)
        }))
        root.put("bills", JSONArray(db.billDao().getAllNow().map { b ->
            JSONObject().put("id", b.id).put("kind", b.kind).put("title", b.title).put("billId", b.billId).put("payId", b.payId)
                .put("amount", b.amount).put("dueDate", b.dueDate).put("periodMonths", b.periodMonths).put("bankName", b.bankName)
                .put("lastPaidAt", b.lastPaidAt ?: JSONObject.NULL).put("enabled", b.enabled).put("createdAt", b.createdAt)
        }))
        root.put("snapshots", JSONArray(db.snapshotDao().getAllNow().map { n ->
            JSONObject().put("monthKey", n.monthKey).put("netWorth", n.netWorth).put("usdToman", n.usdToman).put("gold18Toman", n.gold18Toman).put("at", n.at)
        }))
        root.put("assets", JSONArray(db.assetDao().getAllNow().map { a ->
            JSONObject().put("id", a.id).put("code", a.code).put("name", a.name).put("kind", a.kind).put("quantity", a.quantity)
                .put("buyPrice", a.buyPrice).put("manualPrice", a.manualPrice).put("note", a.note).put("createdAt", a.createdAt)
        }))
        // قیمت روزانه برای نمودارها (کنار پوشهٔ عکس‌ها، در پوشهٔ برنامه)
        photos?.parentFile?.let { dir -> PriceHistory.load(dir).takeIf { it.isNotEmpty() }?.let { root.put("priceHistory", PriceHistory.toJson(it)) } }
        root.put("assetTrades", JSONArray(db.assetTradeDao().getAllNow().map { t ->
            JSONObject().put("id", t.id).put("assetId", t.assetId).put("code", t.code).put("name", t.name).put("kind", t.kind)
                .put("isBuy", t.isBuy).put("quantity", t.quantity).put("total", t.total).put("costBasis", t.costBasis).put("date", t.date)
                .put("bankName", t.bankName ?: JSONObject.NULL).put("transactionId", t.transactionId ?: JSONObject.NULL).put("note", t.note)
        }))
        root.put("priceAlerts", JSONArray(db.priceAlertDao().getAllNow().map { a ->
            JSONObject().put("id", a.id).put("code", a.code).put("name", a.name).put("above", a.above).put("below", a.below)
                .put("inUsd", a.inUsd).put("enabled", a.enabled).put("lastFiredAt", a.lastFiredAt).put("createdAt", a.createdAt)
        }))
        root.put("persons", JSONArray(db.personDao().getAll().first().map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("relation", p.relation).put("phone", p.phone)
                .put("note", p.note).put("createdAt", p.createdAt).put("photo", p.photo)
                .apply {
                    // تصویر پروفایل کوچک است؛ داخل پشتیبان می‌رود تا روی گوشی دیگر هم بیاید
                    if (p.photo.isNotBlank() && photos != null) runCatching {
                        put("photoData", android.util.Base64.encodeToString(java.io.File(photos, p.photo).readBytes(), android.util.Base64.NO_WRAP))
                    }
                }
        }))
        root.put("senders", JSONArray(db.bankSenderDao().getAllSenders().map { s ->
            JSONObject().put("bankName", s.bankName).put("senderNumber", s.senderNumber).put("isEnabled", s.isEnabled)
                .put("learningStatus", s.learningStatus).put("parserVersion", s.parserVersion)
        }))
        if (photos != null) {
            val used = db.transactionDao().getAllTransactions().first().flatMap { it.attachmentList }.toSet()
            val obj = JSONObject()
            photos.listFiles()?.filter { it.name in used }?.forEach { f ->
                obj.put(f.name, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            }
            root.put("photos", obj)
        }
        root.toString()
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else getInt(name)
    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else getLong(name)
    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name) || !has(name)) null else getString(name)

    private inline fun <T> JSONArray?.items(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) out += block(getJSONObject(i))
        return out
    }

    /**
     * Replaces everything in the database with the snapshot. Returns how many transactions came back.
     *
     * The whole file is read into objects first and the database is emptied only after that
     * succeeded. It used to be emptied first: a file that was damaged or from an incompatible
     * version then failed half-way and left the user with nothing at all.
     */
    /** واحد پولی که مبلغ‌های این پشتیبان با آن ذخیره شده‌اند؛ پشتیبان‌های قدیمی تومانی‌اند. */
    fun currencyOf(json: String): AppCurrency =
        AppCurrency.of(runCatching { JSONObject(json).optString("currency") }.getOrNull()) ?: AppCurrency.TOMAN

    suspend fun restore(db: AppDatabase, json: String, photos: java.io.File? = null): Int {
        val root = JSONObject(json)
        if (!root.has("transactions") || !root.has("cards")) throw NotABackupException()

        // ── 1. read everything (any malformed row throws here, before anything is deleted)
        val cards = root.optJSONArray("cards").items { o ->
            BankCard(o.getInt("id"), o.getString("bankName"), o.optString("cardNumber"), o.getLong("balance"),
                o.optString("cardHolderName", "کاربر تراز"), o.optString("accountType", AccountType.BANK),
                o.optLong("creditLimit"), o.optInt("dueDay"))
        }
        val transactions = root.optJSONArray("transactions").items { o ->
            Transaction(
                id = o.getInt("id"), title = o.getString("title"), amount = o.getLong("amount"), category = o.getString("category"),
                date = o.getLong("date"), isExpense = o.getBoolean("isExpense"), bankName = o.getString("bankName"),
                type = o.optString("type", TransactionType.EXPENSE), transferGroupId = o.optStringOrNull("transferGroupId"),
                loanId = o.optIntOrNull("loanId"), tags = o.optString("tags"), attachments = o.optString("attachments")
            )
        }
        val loans = root.optJSONArray("loans").items { o ->
            Loan(o.getInt("id"), o.getString("bankName"), o.getString("loanName"), o.getLong("totalAmount"), o.getLong("paidAmount"),
                o.getLong("installmentAmount"), o.optString("dueDate"), o.optInt("dueDay", 1), o.optBoolean("reminderEnabled", true),
                o.optString("kind", LoanKind.LOAN))
        }
        val goals = root.optJSONArray("goals").items { o ->
            FinancialGoal(o.getInt("id"), o.getString("title"), o.getLong("targetAmount"), o.getLong("currentAmount"),
                o.optBoolean("isCompleted"), o.optLong("createdAt", System.currentTimeMillis()), o.optLongOrNull("deadline"))
        }
        val categories = root.optJSONArray("categories").items { o ->
            Category(o.getInt("id"), o.getString("name"), o.optString("iconKey", "other"), o.optString("colorHex", "#0B7A57"),
                o.optBoolean("isIncome"), o.optInt("sortOrder"), o.optLong("monthlyBudget"))
        }
        val loanPayments = root.optJSONArray("loanPayments").items { o ->
            LoanPayment(o.getInt("id"), o.getInt("loanId"), o.getLong("amount"), o.getLong("date"),
                o.getInt("jalaliYear"), o.getInt("jalaliMonth"), o.optIntOrNull("transactionId"))
        }
        val recurring = root.optJSONArray("recurring").items { o ->
            RecurringTransaction(o.getInt("id"), o.getString("title"), o.getLong("amount"), o.getString("category"),
                o.getBoolean("isExpense"), o.getString("bankName"), o.getInt("dayOfMonth"), o.optBoolean("enabled", true),
                o.optInt("lastRunKey"), o.optLong("createdAt", System.currentTimeMillis()))
        }
        val debts = root.optJSONArray("debts").items { o ->
            Debt(o.getInt("id"), o.getString("person"), o.getLong("amount"), o.getBoolean("owedToMe"), o.optString("note"),
                o.optLong("createdAt", System.currentTimeMillis()), o.optBoolean("settled"), o.optLongOrNull("settledAt"),
                o.optIntOrNull("personId"), o.optBoolean("isRepayment"), o.optIntOrNull("transactionId"), o.optLongOrNull("dueDate"))
        }
        val cheques = root.optJSONArray("cheques").items { o ->
            Cheque(o.getInt("id"), o.getBoolean("issued"), o.getLong("amount"), o.getLong("dueDate"), o.optString("person"),
                o.optString("bankName"), o.optString("sayadi"), o.optString("category", "سایر"), o.optString("note"),
                o.optString("status", ChequeStatus.PENDING), o.optIntOrNull("transactionId"), o.optLong("createdAt", System.currentTimeMillis()))
        }
        val bills = root.optJSONArray("bills").items { o ->
            Bill(o.getInt("id"), o.getString("kind"), o.getString("title"), o.optString("billId"), o.optString("payId"),
                o.optLong("amount"), o.getLong("dueDate"), o.optInt("periodMonths", 2), o.optString("bankName"),
                o.optLongOrNull("lastPaidAt"), o.optBoolean("enabled", true), o.optLong("createdAt", System.currentTimeMillis()))
        }
        val snapshots = root.optJSONArray("snapshots").items { o ->
            NetWorthSnapshot(o.getInt("monthKey"), o.getLong("netWorth"), o.getLong("usdToman"), o.getLong("gold18Toman"), o.getLong("at"))
        }
        val assets = root.optJSONArray("assets").items { o ->
            Asset(o.getInt("id"), o.getString("code"), o.getString("name"), o.getString("kind"), o.getDouble("quantity"),
                o.optLong("buyPrice"), o.optLong("manualPrice"), o.optString("note"), o.optLong("createdAt", System.currentTimeMillis()))
        }
        val assetTrades = root.optJSONArray("assetTrades").items { o ->
            AssetTrade(
                o.getInt("id"), o.getInt("assetId"), o.getString("code"), o.getString("name"), o.getString("kind"), o.getBoolean("isBuy"),
                o.getDouble("quantity"), o.getLong("total"), o.optLong("costBasis"), o.getLong("date"), o.optStringOrNull("bankName"),
                o.optIntOrNull("transactionId"), o.optString("note")
            )
        }
        val priceAlerts = root.optJSONArray("priceAlerts").items { o ->
            PriceAlert(
                o.getInt("id"), o.getString("code"), o.getString("name"), o.optDouble("above", 0.0), o.optDouble("below", 0.0),
                o.optBoolean("inUsd"), o.optBoolean("enabled", true), o.optLong("lastFiredAt"), o.optLong("createdAt", System.currentTimeMillis())
            )
        }
        // (person, base64 of the profile picture or null)
        val persons = root.optJSONArray("persons").items { o ->
            Person(o.getInt("id"), o.getString("name"), o.optString("relation", PersonRelation.OTHER), o.optString("phone"),
                o.optString("note"), o.optLong("createdAt", System.currentTimeMillis()), photo = o.optString("photo")) to
                o.optStringOrNull("photoData")
        }
        val senders = root.optJSONArray("senders").items { o ->
            BankSender(bankName = o.getString("bankName"), senderNumber = o.getString("senderNumber"),
                isEnabled = o.optBoolean("isEnabled", true), learningStatus = o.optString("learningStatus", "نیاز به آموزش"),
                parserVersion = o.optInt("parserVersion"))
        }

        // ── 2. pictures
        val safeName = Regex("[A-Za-z0-9_.-]+")
        val personRows = withContext(Dispatchers.IO) {
            if (photos != null) {
                photos.mkdirs()
                root.optJSONObject("photos")?.let { obj ->
                    obj.keys().forEach { name ->
                        if (name.matches(safeName)) {
                            runCatching { java.io.File(photos, name).writeBytes(android.util.Base64.decode(obj.getString(name), android.util.Base64.NO_WRAP)) }
                        }
                    }
                }
            }
            persons.map { (p, data) ->
                // A profile picture comes back only if its file does (already on the phone, or inside the backup).
                val ok = p.photo.isNotBlank() && p.photo.matches(safeName) && photos != null && runCatching {
                    val f = java.io.File(photos, p.photo)
                    if (!f.exists()) f.writeBytes(android.util.Base64.decode(data ?: error("no picture"), android.util.Base64.NO_WRAP))
                    true
                }.getOrDefault(false)
                if (ok) p else p.copy(photo = "")
            }
        }

        // ── 3. replace
        withContext(Dispatchers.IO) { db.clearAllTables() }
        db.withTransaction {
            cards.forEach { db.bankCardDao().insertCard(it) }
            transactions.forEach { db.transactionDao().insertTransaction(it) }
            loans.forEach { db.loanDao().insertLoan(it) }
            goals.forEach { db.goalDao().insertGoal(it) }
            db.categoryDao().insertAll(categories.ifEmpty { AppDatabase.DEFAULT_CATEGORIES })
            loanPayments.forEach { db.loanPaymentDao().insert(it) }
            recurring.forEach { db.recurringDao().insert(it) }
            debts.forEach { db.debtDao().insert(it) }
            cheques.forEach { db.chequeDao().insert(it) }
            bills.forEach { db.billDao().insert(it) }
            snapshots.forEach { db.snapshotDao().insert(it) }
            assets.forEach { db.assetDao().insert(it) }
            assetTrades.forEach { db.assetTradeDao().insert(it) }
            priceAlerts.forEach { db.priceAlertDao().insert(it) }
            personRows.forEach { db.personDao().insert(it) }
            senders.forEach { db.bankSenderDao().insertSender(it) }
        }
        // قیمت‌های روزانهٔ پشتیبان به تاریخچهٔ این گوشی افزوده می‌شود (روزهای این گوشی هم می‌ماند)
        photos?.parentFile?.let { dir ->
            root.optJSONObject("priceHistory")?.let { h ->
                withContext(Dispatchers.IO) { runCatching { PriceHistory.merge(dir, PriceHistory.fromJson(h)) } }
            }
        }
        return transactions.size
    }

    // ─── spreadsheet export ───

    /**
     * Transactions as CSV with a UTF-8 BOM so Excel shows the Persian text
     * correctly; dates in both Jalali and ISO for sorting.
     */
    suspend fun transactionsCsv(db: AppDatabase): ByteArray =
        csvOf(withContext(Dispatchers.IO) { db.transactionDao().getAllTransactions().first() })

    /** The given transactions as CSV (the filtered list on the Transactions screen). */
    /** مبلغ برای اکسل، بی جداکنندهٔ هزارگان: 1250000 یا برای دلار 12.50 */
    internal fun csvAmount(v: Long): String =
        if (Money.current.decimals == 0) v.toString()
        else (if (v < 0) "-" else "") + (kotlin.math.abs(v) / 100) + "." + String.format(java.util.Locale.US, "%02d", kotlin.math.abs(v) % 100)

    suspend fun csvOf(list: List<Transaction>): ByteArray = withContext(Dispatchers.Default) {
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.append("تاریخ,تاریخ میلادی,عنوان,دسته,حساب,نوع,مبلغ (${com.example.data.Money.label}),برچسب\n")
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        for (t in list) {
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
                .append(csvAmount(if (t.isExpense) -t.amount else t.amount)).append(',')
                .append(cell(t.tags)).append('\n')
        }
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8)
    }
}
