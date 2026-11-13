package com.example.logic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PriceHistory
import com.example.data.PriceQuote
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** قیمت روزانه برای نمودار کوچک بازار: یک قیمت در هر روز، آخرینِ همان روز. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PriceHistoryTest {

    @Test
    fun keepsOnePricePerDayAndTheLastDays() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val day = 86_400_000L
        val t0 = 1_790_000_000_000L
        PriceHistory.record(ctx, mapOf("usd" to PriceQuote.of(90_000.0, null, t0, "tgju")))
        PriceHistory.record(ctx, mapOf("usd" to PriceQuote.of(91_000.0, null, t0 + day, "tgju")))
        // همان روز دوباره: جایگزین می‌شود، اضافه نمی‌شود
        PriceHistory.record(ctx, mapOf("usd" to PriceQuote.of(92_500.0, null, t0 + day + 60_000, "tgju")))
        PriceHistory.record(ctx, mapOf("btc" to PriceQuote.of(1.5, null, t0 + day, "nobitex")))

        val all = PriceHistory.load(ctx)
        assertEquals(listOf(90_000.0, 92_500.0), all["usd"]!!.map { it.second })
        val today = PriceHistory.dayOf(t0 + day)
        assertEquals(listOf(92_500.0), PriceHistory.lastDays(all["usd"], today, 1))
        assertEquals(listOf(90_000.0, 92_500.0), PriceHistory.lastDays(all["usd"], today, 7))
        assertEquals(listOf(1.5), all["btc"]!!.map { it.second })
    }

    @Test
    fun backupRoundTripMergesWithThisPhone() {
        val dir = java.io.File(ApplicationProvider.getApplicationContext<Context>().filesDir, "h_test").apply { deleteRecursively(); mkdirs() }
        PriceHistory.merge(dir, mapOf("usd" to listOf(10 to 90.0, 11 to 91.0)))
        val json = PriceHistory.toJson(PriceHistory.load(dir))
        // گوشی تازه که یک روز خودش دارد
        val other = java.io.File(dir, "other").apply { mkdirs() }
        PriceHistory.merge(other, mapOf("usd" to listOf(12 to 95.0)))
        PriceHistory.merge(other, PriceHistory.fromJson(org.json.JSONObject(json.toString())))
        assertEquals(listOf(10 to 90.0, 11 to 91.0, 12 to 95.0), PriceHistory.load(other)["usd"])
    }
}
