package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object ChequeStatus {
    const val PENDING = "PENDING"
    const val CLEARED = "CLEARED"
    const val BOUNCED = "BOUNCED"
}

/** A cheque the user wrote ([issued]) or received, with its due date and state. */
@Entity(tableName = "cheques")
data class Cheque(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val issued: Boolean,
    val amount: Long,
    val dueDate: Long,
    /** Who it is to (issued) or from (received). */
    val person: String,
    /** The account it is drawn on (issued) or will be deposited to (received). */
    val bankName: String,
    /** The 16-digit Sayadi number, if the user gave it. */
    val sayadi: String = "",
    val category: String = "سایر",
    val note: String = "",
    val status: String = ChequeStatus.PENDING,
    /** The account transaction recorded when it cleared. */
    val transactionId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** A utility bill that comes round every [periodMonths]: electricity, water, gas, phone… */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kind: String,
    val title: String,
    val billId: String = "",
    val payId: String = "",
    /** This period's amount, in toman; 0 = not known yet. */
    val amount: Long = 0,
    val dueDate: Long,
    val periodMonths: Int = 2,
    val bankName: String = "",
    val lastPaidAt: Long? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object BillKind {
    const val POWER = "برق"
    const val WATER = "آب"
    const val GAS = "گاز"
    const val PHONE = "تلفن ثابت"
    const val MOBILE = "موبایل"
    const val INTERNET = "اینترنت"
    const val OTHER = "سایر"
    val all = listOf(POWER, WATER, GAS, PHONE, MOBILE, INTERNET, OTHER)
}

/** Net worth and the day's dollar and gold prices, one row per Jalali month. */
@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val monthKey: Int,
    val netWorth: Long,
    val usdToman: Long,
    val gold18Toman: Long,
    val at: Long
)

@Dao
interface ChequeDao {
    @Query("SELECT * FROM cheques ORDER BY dueDate ASC")
    fun getAll(): Flow<List<Cheque>>

    @Query("SELECT * FROM cheques WHERE status = 'PENDING'")
    suspend fun pending(): List<Cheque>

    @Query("SELECT * FROM cheques")
    suspend fun getAllNow(): List<Cheque>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Cheque): Long

    @Query("DELETE FROM cheques WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE cheques SET bankName = :newName WHERE bankName = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun getAllNow(): List<Bill>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: Bill): Long

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE bills SET bankName = :newName WHERE bankName = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM net_worth_snapshots ORDER BY monthKey ASC")
    fun getAll(): Flow<List<NetWorthSnapshot>>

    @Query("SELECT * FROM net_worth_snapshots")
    suspend fun getAllNow(): List<NetWorthSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: NetWorthSnapshot)
}
