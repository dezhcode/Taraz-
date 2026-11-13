package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Kinds of holding; the asset screen groups by these. */
object AssetKind {
    const val CURRENCY = "currency"
    const val GOLD = "gold"
    const val CRYPTO = "crypto"
    const val FUND = "fund"
}

/**
 * Something the user owns outside bank accounts: dollars, grams of gold, coins,
 * crypto, investment-fund units. [code] says where its price comes from (see
 * PriceCatalog); a fund's code is "fund:<TSETMC instrument code>".
 */
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val kind: String,
    /** Units held: dollars, grams, coins, crypto units, fund units. */
    val quantity: Double,
    /** What the user paid for all of it, in toman; 0 when not given. */
    val buyPrice: Long = 0,
    /** A unit price the user typed in, used when no live price exists; 0 = use the live price. */
    val manualPrice: Long = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY kind ASC, createdAt ASC")
    fun getAll(): Flow<List<Asset>>

    @Query("SELECT * FROM assets")
    suspend fun getAllNow(): List<Asset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: Asset): Long

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: Int): Asset?
}

/**
 * One purchase or sale of a holding. [total] is the whole amount paid or received in the book's
 * currency; for a sale, [costBasis] is what the units sold had cost (average cost), so the realised
 * profit is `total - costBasis`. With [bankName], the money was moved on that account by
 * [transactionId] (a transfer: buying gold is not spending).
 */
@Entity(tableName = "asset_trades")
data class AssetTrade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assetId: Int,
    val code: String,
    val name: String,
    val kind: String,
    val isBuy: Boolean,
    val quantity: Double,
    val total: Long,
    val costBasis: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val bankName: String? = null,
    val transactionId: Int? = null,
    val note: String = ""
)

@Dao
interface AssetTradeDao {
    @Query("SELECT * FROM asset_trades ORDER BY date DESC, id DESC")
    fun getAll(): Flow<List<AssetTrade>>

    @Query("SELECT * FROM asset_trades")
    suspend fun getAllNow(): List<AssetTrade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trade: AssetTrade): Long

    @Query("DELETE FROM asset_trades WHERE id = :id")
    suspend fun delete(id: Int)
}

/**
 * "Tell me when gold passes X": fires once, then switches itself off. Thresholds are in toman, or in
 * dollars when [inUsd] (crypto), so changing the book's currency does not move them. 0 = not set.
 */
@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val above: Double = 0.0,
    val below: Double = 0.0,
    val inUsd: Boolean = false,
    val enabled: Boolean = true,
    val lastFiredAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM price_alerts")
    suspend fun getAllNow(): List<PriceAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceAlert): Long

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: Int)
}

/** Which alerts the latest prices set off: the price that crossed, and which way. */
object PriceAlerts {
    data class Hit(val alert: PriceAlert, val price: Double, val up: Boolean)

    fun hits(alerts: List<PriceAlert>, quotes: Map<String, PriceQuote>): List<Hit> = alerts.filter { it.enabled }.mapNotNull { a ->
        val q = quotes[a.code] ?: return@mapNotNull null
        val p = (if (a.inUsd) q.usd else q.exact)?.takeIf { it > 0 } ?: return@mapNotNull null
        when {
            a.above > 0 && p >= a.above -> Hit(a, p, true)
            a.below > 0 && p <= a.below -> Hit(a, p, false)
            else -> null
        }
    }
}
