package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun getByTransferGroup(groupId: String): List<Transaction>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

    @Query("UPDATE transactions SET bankName = :newName WHERE bankName = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("SELECT * FROM transactions WHERE bankName = :bankName AND date >= :from AND date < :to")
    suspend fun inRangeForAccount(bankName: String, from: Long, to: Long): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BankCardDao {
    @Query("SELECT * FROM bank_cards")
    fun getAllCards(): Flow<List<BankCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: BankCard)

    @Query("DELETE FROM bank_cards WHERE id = :id")
    suspend fun deleteCard(id: Int)

    @Query("UPDATE bank_cards SET balance = :newBalance WHERE bankName = :bankName")
    suspend fun updateBalance(bankName: String, newBalance: Long)

    /** Moves a balance in one statement, so two writes at once cannot overwrite each other. */
    @Query("UPDATE bank_cards SET balance = balance + :delta WHERE bankName = :bankName")
    suspend fun adjustBalance(bankName: String, delta: Long)

    @Query("UPDATE bank_cards SET bankName = :newName WHERE id = :id")
    suspend fun rename(id: Int, newName: String)

    @Query("SELECT * FROM bank_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): BankCard?

    @Query("SELECT * FROM bank_cards")
    suspend fun getAll(): List<BankCard>

    @Query("SELECT * FROM bank_cards WHERE bankName = :bankName LIMIT 1")
    suspend fun getCardByBankName(bankName: String): BankCard?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllNow(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Int)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans")
    fun getAllLoans(): Flow<List<Loan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Int)

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Loan?
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM financial_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<FinancialGoal>>

    @Query("SELECT * FROM financial_goals WHERE isCompleted = 0 ORDER BY createdAt DESC LIMIT 1")
    fun getActiveGoal(): Flow<FinancialGoal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: FinancialGoal)

    @Query("DELETE FROM financial_goals WHERE id = :id")
    suspend fun deleteGoal(id: Int)

    @Query("DELETE FROM financial_goals")
    suspend fun deleteAllGoals()
}

@Database(
    entities = [
        Transaction::class,
        BankCard::class,
        Loan::class,
        FinancialGoal::class,
        BankSender::class,
        BankSmsParser::class,
        PendingTransaction::class,
        UnknownSms::class,
        Category::class,
        LoanPayment::class,
        RecurringTransaction::class,
        Debt::class,
        Person::class,
        Asset::class,
        Cheque::class,
        Bill::class,
        NetWorthSnapshot::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun bankCardDao(): BankCardDao
    abstract fun loanDao(): LoanDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun bankSenderDao(): BankSenderDao
    abstract fun bankSmsParserDao(): BankSmsParserDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun unknownSmsDao(): UnknownSmsDao
    abstract fun loanPaymentDao(): LoanPaymentDao
    abstract fun recurringDao(): RecurringDao
    abstract fun debtDao(): DebtDao
    abstract fun personDao(): PersonDao
    abstract fun assetDao(): AssetDao
    abstract fun chequeDao(): ChequeDao
    abstract fun billDao(): BillDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v8: instalment history (so a paid month gets no reminder and deleting
         * a payment gives it back to the loan), recurring transactions, debts
         * with people, a monthly budget per category, and the SMS «مانده» kept
         * on each pending transaction for balance checks.
         */
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN loanId INTEGER")
                db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_transactions ADD COLUMN balance INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS loan_payments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
                    "date INTEGER NOT NULL, jalaliYear INTEGER NOT NULL, jalaliMonth INTEGER NOT NULL, transactionId INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amount INTEGER NOT NULL, " +
                    "category TEXT NOT NULL, isExpense INTEGER NOT NULL, bankName TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, " +
                    "enabled INTEGER NOT NULL, lastRunKey INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, person TEXT NOT NULL, amount INTEGER NOT NULL, " +
                    "owedToMe INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, settled INTEGER NOT NULL, settledAt INTEGER)"
                )
                // Fresh v7 installs never got categories; give them the defaults now.
                val empty = db.query("SELECT COUNT(*) FROM categories").use { it.moveToFirst() && it.getInt(0) == 0 }
                if (empty) seedCategories(db)
            }
        }

        /**
         * v9: people get their own table, and each debt line points at one, so a
         * friend or relative has one running account. Existing debts are grouped
         * into people by the name they were written with.
         */
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS persons (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, relation TEXT NOT NULL, " +
                    "phone TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE debts ADD COLUMN personId INTEGER")
                db.execSQL("ALTER TABLE debts ADD COLUMN isRepayment INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE debts ADD COLUMN transactionId INTEGER")
                db.execSQL(
                    "INSERT INTO persons (name, relation, phone, note, createdAt) " +
                    "SELECT TRIM(person), 'سایر', '', '', MIN(createdAt) FROM debts GROUP BY TRIM(person)"
                )
                db.execSQL("UPDATE debts SET personId = (SELECT id FROM persons WHERE persons.name = TRIM(debts.person))")
            }
        }

        /** v10: holdings with live prices, a deadline for savings goals, a due date for loans to people. */
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE financial_goals ADD COLUMN deadline INTEGER")
                db.execSQL("ALTER TABLE debts ADD COLUMN dueDate INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                    "quantity REAL NOT NULL, buyPrice INTEGER NOT NULL, manualPrice INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        /** v11: tags and photos on transactions, credit cards, instalment purchases, cheques, bills, monthly net worth. */
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bank_cards ADD COLUMN creditLimit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bank_cards ADD COLUMN dueDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loans ADD COLUMN kind TEXT NOT NULL DEFAULT 'loan'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cheques (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, issued INTEGER NOT NULL, amount INTEGER NOT NULL, dueDate INTEGER NOT NULL, " +
                    "person TEXT NOT NULL, bankName TEXT NOT NULL, sayadi TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, " +
                    "status TEXT NOT NULL, transactionId INTEGER, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, billId TEXT NOT NULL, payId TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, dueDate INTEGER NOT NULL, periodMonths INTEGER NOT NULL, bankName TEXT NOT NULL, " +
                    "lastPaidAt INTEGER, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS net_worth_snapshots (" +
                    "monthKey INTEGER PRIMARY KEY NOT NULL, netWorth INTEGER NOT NULL, usdToman INTEGER NOT NULL, gold18Toman INTEGER NOT NULL, at INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v12: رسمی‌تر شدن نام دسته‌ها («خوراک» ← «غذا»، «درآمد دیگر» ← «سایر درآمدها»).
         * اگر کاربر از قبل دسته‌ای با نام تازه داشته باشد، چیزی تغییر نمی‌کند.
         */
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                CATEGORY_RENAMES.forEach { (old, new) ->
                    val free = "NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)"
                    listOf("transactions", "cheques", "recurring_transactions").forEach { t ->
                        db.execSQL("UPDATE $t SET category = ? WHERE category = ? AND $free", arrayOf<Any>(new, old, new))
                    }
                    db.execSQL("UPDATE categories SET name = ? WHERE name = ? AND $free", arrayOf<Any>(new, old, new))
                }
            }
        }

        /** v13: نسبت «فامیل» با واژهٔ رسمی «بستگان» */
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE persons SET relation = ? WHERE relation = ?", arrayOf<Any>("بستگان", "فامیل"))
            }
        }

        /** v14: تصویر پروفایل اشخاص */
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
            }
        }

        val CATEGORY_RENAMES = listOf("خوراک" to "غذا", "درآمد دیگر" to "سایر درآمدها", "قرض و طلب" to "حساب اشخاص")

        /** Categories a new user starts with; the add sheet is empty without them. */
        val DEFAULT_CATEGORIES: List<Category> = listOf(
            Category(name = "غذا", iconKey = "food", colorHex = "#B0532F", sortOrder = 0),
            Category(name = "حمل‌ونقل", iconKey = "transport", colorHex = "#1D6FA3", sortOrder = 1),
            Category(name = "خرید", iconKey = "shopping", colorHex = "#7A5AA8", sortOrder = 2),
            Category(name = "قبوض", iconKey = "bill", colorHex = "#0E9490", sortOrder = 3),
            Category(name = "سلامت", iconKey = "health", colorHex = "#B03060", sortOrder = 4),
            Category(name = "تفریح", iconKey = "fun", colorHex = "#D97706", sortOrder = 5),
            Category(name = "قسط", iconKey = "loan", colorHex = "#5C6B73", sortOrder = 6),
            Category(name = "سایر", iconKey = "other", colorHex = "#6C7C75", sortOrder = 7),
            Category(name = "حقوق", iconKey = "wage", colorHex = "#0B7A57", isIncome = true, sortOrder = 8),
            Category(name = "سایر درآمدها", iconKey = "saving", colorHex = "#0B7A57", isIncome = true, sortOrder = 9)
        )

        /** Inserts [DEFAULT_CATEGORIES] with plain SQL (onCreate, and after a full reset). */
        fun seedCategories(db: SupportSQLiteDatabase) {
            DEFAULT_CATEGORIES.forEach { c ->
                db.execSQL(
                    "INSERT INTO categories (name, iconKey, colorHex, isIncome, sortOrder, monthlyBudget) VALUES (?, ?, ?, ?, ?, 0)",
                    arrayOf<Any>(c.name, c.iconKey, c.colorHex, if (c.isIncome) 1 else 0, c.sortOrder)
                )
            }
        }

        /**
         * v7 lets an account be something other than a bank card. Cash is the
         * account most spending actually comes out of, and it has no card
         * number — the add form used to demand sixteen digits, so it could not
         * be recorded at all. Everything already in the table is a bank card.
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bank_cards ADD COLUMN accountType TEXT NOT NULL DEFAULT 'BANK'")
            }
        }

        /**
         * v6 separates what a transaction IS from which way the money went, and
         * gives categories a table of their own.
         *
         * The existing transfer rows are the reason this matters: every transfer
         * was stored as an expense row plus an income row, so a 5,000,000 move
         * between your own cards added 5,000,000 to the month's spending AND
         * 5,000,000 to its income. Re-typing them as TRANSFER takes them out of
         * both totals and out of the category breakdown. Past months will read
         * differently after this — that is the correction, not a regression.
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId TEXT")

                // Direction -> nature for ordinary rows.
                db.execSQL("UPDATE transactions SET type = 'INCOME' WHERE isExpense = 0")
                db.execSQL("UPDATE transactions SET type = 'EXPENSE' WHERE isExpense = 1")

                // Old transfers were only ever identifiable by their category and
                // title, since there was no type column to record them properly.
                db.execSQL(
                    "UPDATE transactions SET type = 'TRANSFER' " +
                    "WHERE category = 'انتقال' OR title LIKE 'انتقال به %' OR title LIKE 'انتقال از %'"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "iconKey TEXT NOT NULL DEFAULT 'other', " +
                    "colorHex TEXT NOT NULL DEFAULT '#0B7A57', " +
                    "isIncome INTEGER NOT NULL DEFAULT 0, " +
                    "sortOrder INTEGER NOT NULL DEFAULT 0)"
                )

                // Seed with exactly the six that were hard-coded in the add sheet,
                // so no existing transaction is left pointing at a category that
                // does not exist.
                val seed = listOf(
                    Triple("غذا", "food", "#B0532F"),
                    Triple("حقوق", "wage", "#0B7A57"),
                    Triple("پوشاک", "clothing", "#7A5AA8"),
                    Triple("تفریح", "fun", "#1D6FA3"),
                    Triple("قسط", "loan", "#D97706"),
                    Triple("سایر", "other", "#6C7C75")
                )
                seed.forEachIndexed { index, (name, icon, color) ->
                    val isIncome = if (name == "حقوق") 1 else 0
                    db.execSQL(
                        "INSERT INTO categories (name, iconKey, colorHex, isIncome, sortOrder) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(name, icon, color, isIncome, index)
                    )
                }
            }
        }

        /**
         * v5 adds the machine-readable instalment day. The old free-text
         * dueDate ("15 هر ماه", "۵ام") is parsed into it so nobody loses the
         * loans they already entered; anything unparseable falls back to 1.
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN dueDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE loans ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")

                val cursor = db.query("SELECT id, dueDate FROM loans")
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getInt(0)
                        val raw = it.getString(1) ?: ""
                        val day = extractDay(raw)
                        db.execSQL("UPDATE loans SET dueDay = ? WHERE id = ?", arrayOf<Any>(day, id))
                    }
                }
            }
        }

        /** Pull the first 1..31 number out of a label, tolerating Persian digits. */
        private fun extractDay(raw: String): Int {
            val normalized = raw.map { ch ->
                val persian = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
                val arabic = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
                when {
                    persian >= 0 -> ('0' + persian)
                    arabic >= 0 -> ('0' + arabic)
                    else -> ch
                }
            }.joinToString("")
            val match = Regex("\\d{1,2}").find(normalized)?.value?.toIntOrNull() ?: return 1
            return match.coerceIn(1, 31)
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fidar_finance_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                // Only the first test builds (v1-3) have no migration. Every later
                // version must have one: a destructive fallback wiped users' money
                // records silently whenever a migration was forgotten.
                .fallbackToDestructiveMigrationFrom(1, 2, 3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Written straight into the new file, so the categories exist before
            // the first screen reads them (INSTANCE may not be set yet here).
            seedCategories(db)
        }

        suspend fun populateDatabase(db: AppDatabase) {
            // No sample transactions — only the starting categories.
            if (db.categoryDao().count() == 0) db.categoryDao().insertAll(DEFAULT_CATEGORIES)
        }
    }
}
