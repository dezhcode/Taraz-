package com.example.logic

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Asset
import com.example.data.AssetKind
import com.example.data.BankCard
import com.example.data.FinanceRepository
import com.example.data.PriceAlert
import com.example.data.PriceAlerts
import com.example.data.PriceQuote
import com.example.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** خرید و فروش دارایی: مقدار، بهای تمام‌شده، حساب بانکی و برگرداندن؛ و مهاجرت v15. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssetTradeDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun migrationCreatesTheTablesRoomExpects() {
        // جدول‌هایی که Room خودش می‌سازد، در برابر همان‌ها که مهاجرت روی دیتابیس قدیمی می‌سازد
        val roomDb = db.openHelper.writableDatabase
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null).callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        )
        val old = helper.writableDatabase
        AppDatabase.MIGRATION_14_15.migrate(old)
        for (t in listOf("asset_trades", "price_alerts")) {
            assertEquals(TableInfo.read(roomDb, t), TableInfo.read(old, t))
        }
        helper.close()
    }

    @Test
    fun buyAndSellMoveQuantityCostAndTheAccount() = runBlocking {
        repo.insertCard(BankCard(bankName = "ملت", balance = 500_000_000))
        // خرید ۲ سکه به ۸۰ میلیون از حساب ملت
        repo.recordTrade(null, "coin_emami", "سکهٔ امامی", AssetKind.GOLD, isBuy = true, quantity = 2.0, total = 160_000_000, date = 1L, bankName = "ملت")
        var asset = db.assetDao().getAllNow().single()
        assertEquals(2.0, asset.quantity, 0.0)
        assertEquals(160_000_000L, asset.buyPrice)
        assertEquals(340_000_000L, db.bankCardDao().getAllCards().first().single().balance)
        val tx = db.transactionDao().getAllTransactions().first().single()
        assertEquals(TransactionType.TRANSFER, tx.type)

        // فروش ۱ سکه به ۹۰ میلیون: بهای تمام‌شدهٔ آن ۸۰ میلیون، سود ۱۰ میلیون
        repo.recordTrade(asset, asset.code, asset.name, asset.kind, isBuy = false, quantity = 1.0, total = 90_000_000, date = 2L, bankName = "ملت")
        asset = db.assetDao().getAllNow().single()
        assertEquals(1.0, asset.quantity, 0.0)
        assertEquals(80_000_000L, asset.buyPrice)
        val sale = db.assetTradeDao().getAllNow().first { !it.isBuy }
        assertEquals(80_000_000L, sale.costBasis)
        assertEquals(430_000_000L, db.bankCardDao().getAllCards().first().single().balance)

        // حذف فروش: سکه و پول حساب برمی‌گردد
        repo.deleteTrade(sale)
        asset = db.assetDao().getAllNow().single()
        assertEquals(2.0, asset.quantity, 0.0)
        assertEquals(160_000_000L, asset.buyPrice)
        assertEquals(340_000_000L, db.bankCardDao().getAllCards().first().single().balance)

        // فروش همه: دارایی از فهرست می‌رود، سابقه می‌ماند
        repo.recordTrade(asset, asset.code, asset.name, asset.kind, isBuy = false, quantity = 2.0, total = 200_000_000, date = 3L, bankName = null)
        assertTrue(db.assetDao().getAllNow().isEmpty())
        assertEquals(2, db.assetTradeDao().getAllNow().size)
    }

    @Test
    fun editingATradeRedoesItAndDeletingTheAssetCanUndoTheMoney() = runBlocking {
        repo.insertCard(BankCard(bankName = "ملت", balance = 500_000_000))
        repo.recordTrade(null, "gold18", "طلا", AssetKind.GOLD, isBuy = true, quantity = 10.0, total = 80_000_000, date = 1L, bankName = "ملت")
        var asset = db.assetDao().getAllNow().single()
        repo.recordTrade(asset, asset.code, asset.name, asset.kind, isBuy = false, quantity = 10.0, total = 90_000_000, date = 2L, bankName = "ملت")
        assertTrue(db.assetDao().getAllNow().isEmpty())

        // فروشی که همه را فروخته بود به ۴ گرم اصلاح می‌شود: ۶ گرم برمی‌گردد
        val sale = db.assetTradeDao().getAllNow().first { !it.isBuy }
        repo.updateTrade(sale, isBuy = false, quantity = 4.0, total = 36_000_000, date = 2L, bankName = "ملت")
        asset = db.assetDao().getAllNow().single()
        assertEquals(6.0, asset.quantity, 1e-9)
        assertEquals(48_000_000L, asset.buyPrice)
        assertEquals(456_000_000L, db.bankCardDao().getAllCards().first().single().balance)
        assertEquals(2, db.assetTradeDao().getAllNow().size)

        // حذف دارایی با برگرداندن پول: موجودی به قبل از هر دو معامله برمی‌گردد
        repo.deleteAssetWithTrades(asset, undoMoney = true)
        assertTrue(db.assetDao().getAllNow().isEmpty())
        assertTrue(db.assetTradeDao().getAllNow().isEmpty())
        assertEquals(500_000_000L, db.bankCardDao().getAllCards().first().single().balance)
    }

    @Test
    fun alertsFireOnceTheyCross() {
        val gold = PriceAlert(id = 1, code = "gold18", name = "طلا", above = 8_000_000.0)
        val btc = PriceAlert(id = 2, code = "btc", name = "بیت‌کوین", below = 100_000.0, inUsd = true)
        val off = PriceAlert(id = 3, code = "usd", name = "دلار", above = 1.0, enabled = false)
        val quotes = mapOf(
            "gold18" to PriceQuote.of(8_250_000.0, null, 0, "tgju"),
            "btc" to PriceQuote.of(10e9, null, 0, "nobitex", usd = 108_000.0),
            "usd" to PriceQuote.of(95_000.0, null, 0, "tgju")
        )
        val hits = PriceAlerts.hits(listOf(gold, btc, off), quotes)
        assertEquals(listOf(1), hits.map { it.alert.id })
        assertTrue(hits.single().up)
        assertNull(PriceAlerts.hits(listOf(btc), quotes).firstOrNull())
    }
}
