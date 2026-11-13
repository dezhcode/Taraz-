package com.example.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Asset
import com.example.data.AssetKind
import com.example.data.PriceHistory
import com.example.data.PriceQuote
import com.example.ui.layer.LayerAssetsScreen
import com.example.ui.layer.LayerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** صفحهٔ دارایی‌ها (طرح بازار و سبد) با داده‌های نمونه */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class AssetShots {
    @get:Rule val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private fun q(p: Double, c: Double?, src: String, ageMin: Long = 0, usd: Double? = null) = PriceQuote.of(p, c, now - ageMin * 60_000, src, usd)
    private val prices = mapOf(
        "usd" to q(95_400.0, 0.4, "tgju"), "eur" to q(109_800.0, 0.2, "tgju"), "aed" to q(25_970.0, 0.1, "tgju"),
        "gold18" to q(8_250_000.0, 0.8, "tgju"), "coin_emami" to q(89_500_000.0, 1.2, "tgju"), "coin_half" to q(47_200_000.0, 0.9, "tgju"),
        "usdt" to q(96_100.0, 0.3, "nobitex"), "btc" to q(10_450_000_000.0, 2.9, "nobitex", usd = 108_740.0), "eth" to q(395_000_000.0, -1.1, "nobitex", usd = 4_110.5),
        "shib" to q(1.72, -2.4, "nobitex", ageMin = 95, usd = 0.00001789), "fund:123" to q(21_300.0, 0.6, "tsetmc", ageMin = 5)
    )
    private val history: Map<String, List<Pair<Int, Double>>> = run {
        val today = PriceHistory.dayOf(now)
        fun h(vararg v: Double) = v.mapIndexed { i, x -> (today - v.size + 1 + i) to x }
        mapOf(
            "usd" to h(93_100.0, 93_800.0, 94_000.0, 93_600.0, 94_500.0, 95_000.0, 95_400.0),
            "coin_emami" to h(87.0e6, 87.6e6, 87.2e6, 88.1e6, 88.0e6, 88.4e6, 89.5e6),
            "btc" to h(9.9e9, 10.1e9, 9.8e9, 10.0e9, 10.2e9, 10.15e9, 10.45e9),
            "eth" to h(402e6, 405e6, 399e6, 401e6, 398e6, 399.4e6, 395e6)
        )
    }
    private val assets = listOf(
        Asset(id = 1, code = "coin_emami", name = "سکهٔ امامی", kind = AssetKind.GOLD, quantity = 2.0, buyPrice = 158_000_000),
        Asset(id = 2, code = "gold18", name = "طلای ۱۸ عیار", kind = AssetKind.GOLD, quantity = 15.0, buyPrice = 104_000_000),
        Asset(id = 3, code = "btc", name = "بیت‌کوین", kind = AssetKind.CRYPTO, quantity = 0.012, buyPrice = 98_000_000),
        Asset(id = 4, code = "shib", name = "شیبا", kind = AssetKind.CRYPTO, quantity = 5_000_000.0),
        Asset(id = 5, code = "fund:123", name = "عیار", kind = AssetKind.FUND, quantity = 5_000.0, buyPrice = 92_000_000),
        Asset(id = 6, code = "usd", name = "دلار آمریکا", kind = AssetKind.CURRENCY, quantity = 800.0, buyPrice = 62_400_000)
    )

    private fun shot(name: String, dark: Boolean, held: List<Asset>, tapMarket: Boolean = false, chip: String? = null) {
        LayerTheme.dark = dark
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LayerAssetsScreen(
                    assets = held, prices = prices, refreshing = false, errors = emptyList(),
                    accountsTotal = 260_000_000, debtsTotal = 48_000_000, hideAmounts = false,
                    onBack = {}, onRefresh = {}, onAdd = {}, onOpen = {}, history = history
                )
            }
        }
        compose.mainClock.advanceTimeBy(800)
        if (tapMarket) {
            compose.onNodeWithText("بازار").performClick()
            compose.mainClock.advanceTimeBy(800)
        }
        if (chip != null) {
            compose.onNodeWithText(chip).performClick()
            compose.mainClock.advanceTimeBy(800)
        }
        compose.onRoot().captureRoboImage("build/tourshots/7_assets_$name.png")
    }

    @Test fun mine() = shot("1_mine", dark = false, held = assets)
    @Test fun mineDark() = shot("1b_mine_dark", dark = true, held = assets)
    @Test fun market() = shot("2_market", dark = false, held = assets, tapMarket = true)
    @Test fun marketCrypto() = shot("2c_market_crypto", dark = false, held = assets, tapMarket = true, chip = "رمزارز")
    @Test fun marketEmpty() = shot("2b_market_new_user_dark", dark = true, held = emptyList())
}
