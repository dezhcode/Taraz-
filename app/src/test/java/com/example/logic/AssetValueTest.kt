package com.example.logic

import com.example.data.Asset
import com.example.data.AssetKind
import com.example.data.PriceQuote
import com.example.data.unitPriceOf
import com.example.data.valueOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** ارزش دارایی با قیمت روز: کسر قیمت رمزارزهای ارزان گم نشود. */
class AssetValueTest {

    private fun asset(code: String, qty: Double, manual: Long = 0) =
        Asset(code = code, name = code, kind = AssetKind.CRYPTO, quantity = qty, manualPrice = manual)

    @Test
    fun keepsTheFractionOfCheapCoins() {
        // یک شیبا ۱٫۷ تومان؛ گرد کردن به ۱ یا ۲ تومان روی ۵۰ میلیون شیبا ده‌ها میلیون خطا می‌داد
        val q = PriceQuote.of(1.7, null, 0, "nobitex")
        assertEquals(2L, q.price)
        assertEquals(85_000_000L, valueOf(asset("shib", 50_000_000.0), mapOf("shib" to q)))
    }

    @Test
    fun manualPriceWinsAndMissingPriceIsZero() {
        val prices = mapOf("btc" to PriceQuote.of(9_000_000_000.0, 1.2, 0, "nobitex"))
        assertEquals(5_000.0, unitPriceOf(asset("btc", 1.0, manual = 5_000), prices), 0.0)
        assertEquals(0L, valueOf(asset("eth", 3.0), prices))
        assertEquals(900_000_000L, valueOf(asset("btc", 0.1), prices))
    }
}
