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
}
