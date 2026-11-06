package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** A unit price in toman with its daily change, and when it was read. */
data class PriceQuote(val price: Long, val changePct: Double?, val at: Long, val source: String)

data class CatalogItem(val code: String, val name: String, val kind: String, val unit: String)

/** A fund found on the stock exchange (TSETMC). */
data class FundMatch(val insCode: String, val symbol: String, val name: String)

/** What can be priced automatically, and where each price is read from. */
object PriceCatalog {
    val items: List<CatalogItem> = listOf(
        CatalogItem("usd", "دلار آمریکا", AssetKind.CURRENCY, "دلار"),
        CatalogItem("eur", "یورو", AssetKind.CURRENCY, "یورو"),
        CatalogItem("aed", "درهم امارات", AssetKind.CURRENCY, "درهم"),
        CatalogItem("gbp", "پوند انگلیس", AssetKind.CURRENCY, "پوند"),
        CatalogItem("try", "لیر ترکیه", AssetKind.CURRENCY, "لیر"),
        CatalogItem("gold18", "طلای ۱۸ عیار", AssetKind.GOLD, "گرم"),
        CatalogItem("gold24", "طلای ۲۴ عیار", AssetKind.GOLD, "گرم"),
        CatalogItem("mesghal", "مثقال طلا", AssetKind.GOLD, "مثقال"),
        CatalogItem("coin_emami", "سکهٔ امامی", AssetKind.GOLD, "عدد"),
        CatalogItem("coin_bahar", "سکهٔ بهار آزادی", AssetKind.GOLD, "عدد"),
        CatalogItem("coin_half", "نیم‌سکه", AssetKind.GOLD, "عدد"),
        CatalogItem("coin_quarter", "ربع‌سکه", AssetKind.GOLD, "عدد"),
        CatalogItem("coin_gerami", "سکهٔ گرمی", AssetKind.GOLD, "عدد"),
        CatalogItem("btc", "بیت‌کوین", AssetKind.CRYPTO, "BTC"),
        CatalogItem("eth", "اتریوم", AssetKind.CRYPTO, "ETH"),
        CatalogItem("usdt", "تتر", AssetKind.CRYPTO, "USDT"),
        CatalogItem("ton", "تون‌کوین", AssetKind.CRYPTO, "TON"),
        CatalogItem("trx", "ترون", AssetKind.CRYPTO, "TRX"),
        CatalogItem("sol", "سولانا", AssetKind.CRYPTO, "SOL"),
        CatalogItem("bnb", "بایننس‌کوین", AssetKind.CRYPTO, "BNB"),
        CatalogItem("xrp", "ریپل", AssetKind.CRYPTO, "XRP"),
        CatalogItem("doge", "دوج‌کوین", AssetKind.CRYPTO, "DOGE"),
        CatalogItem("ltc", "لایت‌کوین", AssetKind.CRYPTO, "LTC"),
        CatalogItem("ada", "کاردانو", AssetKind.CRYPTO, "ADA"),
        CatalogItem("shib", "شیبا", AssetKind.CRYPTO, "SHIB"),
        CatalogItem("not", "نات‌کوین", AssetKind.CRYPTO, "NOT")
    )

    fun find(code: String): CatalogItem? = items.firstOrNull { it.code == code }

    /** Keys in tgju's market feed (prices there are in rials). */
    internal val TGJU: Map<String, String> = mapOf(
        "usd" to "price_dollar_rl", "eur" to "price_eur", "aed" to "price_aed", "gbp" to "price_gbp", "try" to "price_try",
        "gold18" to "geram18", "gold24" to "geram24", "mesghal" to "mesghal",
        "coin_emami" to "sekee", "coin_bahar" to "sekeb", "coin_half" to "nim", "coin_quarter" to "rob", "coin_gerami" to "gerami"
    )

    fun isFund(code: String) = code.startsWith("fund:")
    fun fundInsCode(code: String) = code.removePrefix("fund:")
}

/**
 * Reads today's prices from public market feeds and keeps the last answer, so
 * the screen always has something to show offline (with the time it was read).
 *
 *  - dollar, euro, gold, coins: tgju market feed
 *  - crypto and tether: Nobitex market stats (also the fallback for the dollar)
 *  - exchange-traded funds: TSETMC closing price
 *
 * None of these is an official API; when one fails the others still update and
 * the user can type a price by hand for anything.
 */
object PriceService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36 Taraz/2.0"
    private const val PREFS = "taraz_prices"

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw java.io.IOException("empty")
        }
    }

    private fun num(v: Any?): Double? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toDouble()
        else -> v.toString().replace(",", "").replace("٬", "").trim().toDoubleOrNull()
    }

    // ─── cache ───

    fun cached(context: Context): Map<String, PriceQuote> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("quotes", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { k ->
                val q = o.getJSONObject(k)
                PriceQuote(q.getLong("p"), if (q.isNull("c")) null else q.getDouble("c"), q.getLong("at"), q.optString("s"))
            }
        } catch (e: Exception) { emptyMap() }
    }

    private fun save(context: Context, quotes: Map<String, PriceQuote>) {
        val o = JSONObject()
        quotes.forEach { (k, q) ->
            o.put(k, JSONObject().put("p", q.price).put("c", q.changePct ?: JSONObject.NULL).put("at", q.at).put("s", q.source))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("quotes", o.toString()).apply()
    }

    /**
     * Fetches prices for [codes]. Returns every known quote (fresh ones merged
     * over the cache) and the names of the sources that failed, if any.
     */
    suspend fun refresh(context: Context, codes: Set<String>): Pair<Map<String, PriceQuote>, List<String>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fresh = mutableMapOf<String, PriceQuote>()
        val failed = mutableListOf<String>()

        val tgjuCodes = codes.filter { it in PriceCatalog.TGJU }
        if (tgjuCodes.isNotEmpty()) {
            val current = listOf("https://call1.tgju.org/ajax.json", "https://call2.tgju.org/ajax.json", "https://call3.tgju.org/ajax.json")
                .firstNotNullOfOrNull { url -> runCatching { JSONObject(get(url)).getJSONObject("current") }.getOrNull() }
            if (current == null) failed += "قیمت ارز و طلا"
            else for (code in tgjuCodes) {
                val item = current.optJSONObject(PriceCatalog.TGJU.getValue(code)) ?: continue
                val rial = num(item.opt("p")) ?: continue
                val dp = num(item.opt("dp"))?.let { if (item.optString("dt") == "low") -it else it }
                fresh[code] = PriceQuote((rial / 10).toLong(), dp, now, "tgju")
            }
        }

        val cryptoCodes = codes.filter { PriceCatalog.find(it)?.kind == AssetKind.CRYPTO }.toMutableSet()
        if ("usd" in codes && "usd" !in fresh) cryptoCodes += "usdt"
        if (cryptoCodes.isNotEmpty()) {
            try {
                val src = URLEncoder.encode(cryptoCodes.joinToString(","), "UTF-8")
                val stats = JSONObject(get("https://api.nobitex.ir/market/stats?srcCurrency=$src&dstCurrency=rls")).getJSONObject("stats")
                for (code in cryptoCodes) {
                    val s = stats.optJSONObject("$code-rls") ?: continue
                    val rial = num(s.opt("latest")) ?: continue
                    fresh[code] = PriceQuote((rial / 10).toLong(), num(s.opt("dayChange")), now, "nobitex")
                }
                // No market dollar today: tether is the closest free-market rate.
                if ("usd" in codes && "usd" !in fresh) fresh["usdt"]?.let { fresh["usd"] = it.copy(source = "nobitex-usdt") }
            } catch (e: Exception) {
                failed += "قیمت رمزارز"
            }
        }

        for (code in codes.filter { PriceCatalog.isFund(it) }) {
            try {
                val info = JSONObject(get("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/${PriceCatalog.fundInsCode(code)}"))
                    .getJSONObject("closingPriceInfo")
                val last = num(info.opt("pDrCotVal"))?.takeIf { it > 0 } ?: num(info.opt("pClosing")) ?: continue
                val yesterday = num(info.opt("priceYesterday"))?.takeIf { it > 0 }
                fresh[code] = PriceQuote((last / 10).toLong(), yesterday?.let { (last - it) * 100 / it }, now, "tsetmc")
            } catch (e: Exception) {
                if ("قیمت صندوق‌ها" !in failed) failed += "قیمت صندوق‌ها"
            }
        }

        val merged = cached(context) + fresh
        if (fresh.isNotEmpty()) save(context, merged)
        merged to failed
    }

    /** Funds and other instruments on the stock exchange whose name or symbol matches [query]. */
    suspend fun searchFunds(query: String): List<FundMatch> = withContext(Dispatchers.IO) {
        // TSETMC writes with Arabic ي and ك.
        val q = query.trim().replace('ی', 'ي').replace('ک', 'ك')
        if (q.length < 2) return@withContext emptyList()
        val arr: JSONArray = JSONObject(get("https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")))
            .optJSONArray("instrumentSearch") ?: return@withContext emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            FundMatch(
                insCode = o.optString("insCode"),
                symbol = o.optString("lVal18AFC").replace('ي', 'ی').replace('ك', 'ک'),
                name = o.optString("lVal30").replace('ي', 'ی').replace('ك', 'ک')
            )
        }.filter { it.insCode.isNotBlank() }.take(20)
    }
}
