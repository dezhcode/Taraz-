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
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

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

    @Query("SELECT * FROM bank_cards WHERE bankName = :bankName LIMIT 1")
    suspend fun getCardByBankName(bankName: String): BankCard?
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans")
    fun getAllLoans(): Flow<List<Loan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Int)
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
        UnknownSms::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun bankCardDao(): BankCardDao
    abstract fun loanDao(): LoanDao
    abstract fun goalDao(): GoalDao
    abstract fun bankSenderDao(): BankSenderDao
    abstract fun bankSmsParserDao(): BankSmsParserDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun unknownSmsDao(): UnknownSmsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
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
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        suspend fun populateDatabase(db: AppDatabase) {
            // No sample data populated to ensure the app only displays authentic user data.
        }
    }
}
