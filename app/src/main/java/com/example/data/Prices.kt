package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * A unit price with its daily change, and when it was read. [price] is rounded to a whole unit for
 * display; [exact] keeps the fraction, which matters for cheap coins (one Shiba is about a toman, so
 * rounding it would be off by tens of percent on a large holding). Raw quotes are in toman.
 */
data class PriceQuote(
    val price: Long, val changePct: Double?, val at: Long, val source: String, val exact: Double = price.toDouble(),
    /** For crypto: the unit price in US dollars (tether), as the exchange quotes it; null for the rest. */
    val usd: Double? = null
) {
    companion object {
        fun of(exact: Double, changePct: Double?, at: Long, source: String, usd: Double? = null) =
            PriceQuote(Math.round(exact), changePct, at, source, exact, usd)
    }
}

/** Unit price of [a]: the user's own price, else the live one (fraction kept). 0 when unknown. */
fun unitPriceOf(a: Asset, prices: Map<String, PriceQuote>): Double =
    if (a.manualPrice > 0) a.manualPrice.toDouble() else prices[a.code]?.exact ?: 0.0

/** What [a] is worth today in the same unit as [prices]. */
fun valueOf(a: Asset, prices: Map<String, PriceQuote>): Long = Math.round(unitPriceOf(a, prices) * a.quantity)

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

    /**
     * Always from the network: no HTTP cache, and "no-cache" asked of every proxy and CDN on the way,
     * so a page opened twice a minute apart never shows the first answer again.
     */
    private fun get(url: String, referer: String? = null): String {
        val b = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", UA).header("Accept", "application/json, text/plain, */*")
            .header("Cache-Control", "no-cache").header("Pragma", "no-cache")
        if (referer != null) b.header("Referer", referer)
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw java.io.IOException("پاسخ خالی")
        }
    }

    /** پیام کوتاه خطا برای نمایش: «HTTP 403»، «مهلت تمام شد»، «بدون اینترنت»… */
    private fun reason(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "مهلت تمام شد"
        is java.net.UnknownHostException -> "سایت پیدا نشد"
        is javax.net.ssl.SSLException -> "خطای اتصال امن"
        is java.net.ConnectException -> "وصل نشد"
        is org.json.JSONException -> "پاسخ نامعتبر"
        else -> e.message?.take(40) ?: e.javaClass.simpleName
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
                val p = q.getLong("p")
                PriceQuote(
                    p, if (q.isNull("c")) null else q.getDouble("c"), q.getLong("at"), q.optString("s"), q.optDouble("e", p.toDouble()),
                    if (q.has("u")) q.getDouble("u") else null
                )
            }
        } catch (e: Exception) { emptyMap() }
    }

    private fun save(context: Context, quotes: Map<String, PriceQuote>) {
        val o = JSONObject()
        quotes.forEach { (k, q) ->
            o.put(k, JSONObject().put("p", q.price).put("c", q.changePct ?: JSONObject.NULL).put("at", q.at).put("s", q.source).put("e", q.exact).apply { q.usd?.let { put("u", it) } })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("quotes", o.toString()).apply()
    }

    /** CoinGecko ids for the international fallback. */
    private val GECKO = mapOf(
        "btc" to "bitcoin", "eth" to "ethereum", "usdt" to "tether", "ton" to "the-open-network", "trx" to "tron",
        "sol" to "solana", "bnb" to "binancecoin", "xrp" to "ripple", "doge" to "dogecoin", "ltc" to "litecoin",
        "ada" to "cardano", "shib" to "shiba-inu", "not" to "notcoin", "paxg" to "pax-gold"
    )

    /**
     * Fetches prices for [codes]. Returns every known quote (fresh ones merged
     * over the cache) and, for each source that failed, why.
     *
     * Each kind of price has more than one source, because Iranian sites
     * (tgju, Nobitex, Wallex, Bitpin, TSETMC) often refuse foreign IPs — a VPN
     * makes them fail — while international ones (Coinbase, CoinGecko) are
     * the reverse. Crypto and fiat from abroad are converted with the tether
     * rate; gold from abroad is worked out from the world ounce price.
     */
    suspend fun refresh(context: Context, codes: Set<String>, needed: Set<String> = codes): Pair<Map<String, PriceQuote>, List<String>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fresh: MutableMap<String, PriceQuote> = java.util.concurrent.ConcurrentHashMap()
        val usdPrices: MutableMap<String, Double> = java.util.concurrent.ConcurrentHashMap()
        val errors = java.util.Collections.synchronizedMap(linkedMapOf<String, String>())
        val cryptoCodes = codes.filter { PriceCatalog.find(it)?.kind == AssetKind.CRYPTO }.toSet() + "usdt" // تتر برای تبدیل قیمت‌های خارجی هم لازم است

        // ── مرحلهٔ ۱، هم‌زمان: ارز و طلا از tgju، رمزارز از نوبیتکس، صندوق‌ها از سایت بورس
        coroutineScope {
            val tgjuCodes = codes.filter { it in PriceCatalog.TGJU }
            if (tgjuCodes.isNotEmpty()) async {
                var cur: JSONObject? = null
                // سه نشانی یک سرویس؛ rev جلوی پاسخ ماندهٔ CDN را می‌گیرد
                for (host in listOf("call1", "call2", "call3")) {
                    try { cur = JSONObject(get("https://$host.tgju.org/ajax.json?rev=$now", referer = "https://www.tgju.org/")).getJSONObject("current"); break }
                    catch (e: Exception) { errors["tgju"] = reason(e) }
                }
                cur?.let { c ->
                    errors.remove("tgju")
                    for (code in tgjuCodes) {
                        val item = c.optJSONObject(PriceCatalog.TGJU.getValue(code)) ?: continue
                        val rial = num(item.opt("p"))?.takeIf { it > 0 } ?: continue
                        val dp = num(item.opt("dp"))?.let { if (item.optString("dt") == "low") -it else it }
                        fresh[code] = PriceQuote.of(rial / 10, dp, now, "tgju")
                    }
                }
            }
            async {
                try {
                    val src = URLEncoder.encode(cryptoCodes.joinToString(","), "UTF-8")
                    val stats = JSONObject(get("https://api.nobitex.ir/market/stats?srcCurrency=$src&dstCurrency=rls")).getJSONObject("stats")
                    for (code in cryptoCodes) {
                        val st = stats.optJSONObject("$code-rls") ?: continue
                        val rial = num(st.opt("latest"))?.takeIf { it > 0 } ?: continue
                        fresh[code] = PriceQuote.of(rial / 10, num(st.opt("dayChange")), now, "nobitex")
                    }
                } catch (e: Exception) { errors["نوبیتکس"] = reason(e) }
            }
            // قیمت دلاری (تتری) رمزارزها از بازار تتر نوبیتکس؛ اگر نرسید از قیمت تومانی ÷ نرخ تتر
            val usdtCoins = cryptoCodes - "usdt"
            if (usdtCoins.isNotEmpty()) async {
                try {
                    val src = URLEncoder.encode(usdtCoins.joinToString(","), "UTF-8")
                    val stats = JSONObject(get("https://api.nobitex.ir/market/stats?srcCurrency=$src&dstCurrency=usdt")).getJSONObject("stats")
                    for (code in usdtCoins) {
                        val st = stats.optJSONObject("$code-usdt") ?: continue
                        num(st.opt("latest"))?.takeIf { it > 0 }?.let { usdPrices[code] = it }
                    }
                } catch (_: Exception) {}
            }
            for (code in codes.filter { PriceCatalog.isFund(it) }) async {
                val ins = PriceCatalog.fundInsCode(code)
                try {
                    val info = JSONObject(get("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/$ins", referer = TSE_REFERER))
                        .getJSONObject("closingPriceInfo")
                    // آخرین معامله؛ پیش از اولین معاملهٔ روز، قیمت پایانی
                    val last = num(info.opt("pDrCotVal"))?.takeIf { it > 0 } ?: num(info.opt("pClosing"))?.takeIf { it > 0 } ?: return@async
                    val yesterday = num(info.opt("priceYesterday"))?.takeIf { it > 0 }
                    fresh[code] = PriceQuote.of(last / 10, yesterday?.let { (last - it) * 100 / it }, now, "tsetmc")
                } catch (e: Exception) {
                    // نشانی دوم سایت بورس: «زمان،وضعیت،آخرین،پایانی،اولین،دیروز،…»
                    try {
                        val f = get("https://old.tsetmc.com/tsev2/data/instinfofast.aspx?i=$ins&c=27", referer = "https://old.tsetmc.com/")
                            .substringBefore(';').split(',')
                        val last = num(f.getOrNull(2))?.takeIf { it > 0 } ?: num(f.getOrNull(3))?.takeIf { it > 0 } ?: throw java.io.IOException("پاسخ نامعتبر")
                        val yesterday = num(f.getOrNull(5))?.takeIf { it > 0 }
                        fresh[code] = PriceQuote.of(last / 10, yesterday?.let { (last - it) * 100 / it }, now, "tsetmc")
                    } catch (e2: Exception) { errors["بورس"] = reason(e) + " / " + reason(e2) }
                }
            }
        }

        // ── مرحلهٔ ۲: نرخ تتر از صرافی‌های دیگر اگر نوبیتکس جواب نداد (هر دو هم‌زمان، اولی که رسید)
        if ("usdt" !in fresh) coroutineScope {
            val wallex = async {
                try {
                    val sym = JSONObject(get("https://api.wallex.ir/v1/markets")).getJSONObject("result").getJSONObject("symbols")
                    val stats = sym.getJSONObject("USDTTMN").getJSONObject("stats")
                    num(stats.opt("lastPrice"))?.takeIf { it > 0 }?.let { PriceQuote.of(it, num(stats.opt("24h_ch")), now, "wallex") }
                } catch (e: Exception) { errors["والکس"] = reason(e); null }
            }
            val bitpin = async {
                try {
                    val arr = JSONObject(get("https://api.bitpin.ir/v1/mkt/markets/")).getJSONArray("results")
                    (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("code") == "USDT_IRT" }
                        ?.let { m -> num(m.opt("price"))?.takeIf { it > 0 }?.let { PriceQuote.of(it, null, now, "bitpin") } }
                } catch (e: Exception) { errors["بیت‌پین"] = reason(e); null }
            }
            (wallex.await() ?: bitpin.await())?.let { fresh["usdt"] = it }
        }
        // دلار: tgju، وگرنه تتر
        if ("usd" in codes && "usd" !in fresh) fresh["usdt"]?.let { fresh["usd"] = it.copy(source = it.source + "-usdt") }
        val usdToman = (fresh["usd"] ?: fresh["usdt"])?.exact

        // ── مرحلهٔ ۳: منبع‌های خارجی (با فیلترشکن کار می‌کنند): قیمت دلاری × نرخ تتر
        val needCrypto = codes.filter { PriceCatalog.find(it)?.kind == AssetKind.CRYPTO && it !in fresh }
        val needFiat = codes.filter { PriceCatalog.find(it)?.kind == AssetKind.CURRENCY && it !in fresh && it != "usd" }
        val needGold = codes.filter { it in setOf("gold18", "gold24", "mesghal") && it !in fresh }
        if (usdToman != null && (needCrypto.isNotEmpty() || needFiat.isNotEmpty() || needGold.isNotEmpty())) {
            val (rates, gecko) = coroutineScope {
                // واحد در برابر یک دلار
                val r = async {
                    try { JSONObject(get("https://api.coinbase.com/v2/exchange-rates?currency=USD")).getJSONObject("data").getJSONObject("rates") }
                    catch (e: Exception) { errors["Coinbase"] = reason(e); null }
                }
                val g = async {
                    if (needCrypto.isEmpty() && needGold.isEmpty()) null
                    else try {
                        val ids = (needCrypto.mapNotNull { GECKO[it] } + "pax-gold").distinct().joinToString(",")
                        JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=usd&include_24hr_change=true"))
                    } catch (e: Exception) { errors["CoinGecko"] = reason(e); null }
                }
                r.await() to g.await()
            }
            fun usdPriceOf(code: String): Pair<Double, Double?>? {
                gecko?.optJSONObject(GECKO[code] ?: "")?.let { o -> num(o.opt("usd"))?.takeIf { it > 0 }?.let { return it to num(o.opt("usd_24h_change")) } }
                rates?.let { r -> num(r.opt(code.uppercase()))?.takeIf { it > 0 }?.let { return (1.0 / it) to null } }
                return null
            }
            for (code in needCrypto) usdPriceOf(code)?.let { (usd, ch) ->
                fresh[code] = PriceQuote.of(usd * usdToman, ch, now, "intl", usd = usd)
            }
            for (code in needFiat) rates?.let { r ->
                num(r.opt(code.uppercase()))?.takeIf { it > 0 }?.let { perUsd -> fresh[code] = PriceQuote.of(usdToman / perUsd, null, now, "intl") }
            }
            usdPriceOf("paxg")?.let { (ounceUsd, ch) ->
                val gram24 = ounceUsd / 31.1035 * usdToman
                for (code in needGold) {
                    val v = when (code) {
                        "gold24" -> gram24
                        "gold18" -> gram24 * 0.75
                        else -> gram24 * 0.705 * 4.6083 // مثقال آب‌شده
                    }
                    fresh[code] = PriceQuote.of(v, ch, now, "intl-gold")
                }
            }
        }

        // قیمت دلاری هر رمزارز: بازار تتر، وگرنه قیمت تومانی ÷ نرخ تتر
        val tetherToman = fresh["usdt"]?.exact
        for (code in codes.filter { PriceCatalog.find(it)?.kind == AssetKind.CRYPTO && it != "usdt" }) {
            val q = fresh[code] ?: continue
            if (q.usd != null) continue
            val usd = usdPrices[code] ?: tetherToman?.takeIf { it > 0 }?.let { q.exact / it }
            if (usd != null) fresh[code] = q.copy(usd = usd)
        }

        // فقط خطاهایی را نشان بده که قیمتی را که کاربر لازم دارد بی‌جواب گذاشته‌اند
        val missing = needed.filter { it !in fresh }
        val failed = if (missing.isEmpty()) emptyList() else errors.map { (src, why) -> "$src: $why" }

        val merged = cached(context) + fresh
        if (fresh.isNotEmpty()) {
            save(context, merged)
            PriceHistory.record(context, fresh)
        }
        merged to failed
    }

    /** Funds and other instruments on the stock exchange whose name or symbol matches [query]. */
    private const val TSE_REFERER = "https://www.tsetmc.com/"

    private fun faLetters(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').trim()

    /**
     * Funds and other instruments on the stock exchange whose name or symbol matches [query]. Tries
     * the exchange site's API, then its older address; if both fail, throws with both reasons so the
     * screen can say what went wrong ("HTTP 403", "سایت پیدا نشد"…).
     */
    suspend fun searchFunds(query: String): List<FundMatch> = withContext(Dispatchers.IO) {
        // TSETMC writes with Arabic ي and ك.
        val q = query.trim().replace('ی', 'ي').replace('ک', 'ك')
        if (q.length < 2) return@withContext emptyList()
        val enc = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        val why = mutableListOf<String>()
        try {
            val arr: JSONArray = JSONObject(get("https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/$enc", referer = TSE_REFERER))
                .optJSONArray("instrumentSearch") ?: JSONArray()
            return@withContext (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                FundMatch(insCode = o.optString("insCode"), symbol = faLetters(o.optString("lVal18AFC")), name = faLetters(o.optString("lVal30")))
            }.filter { it.insCode.isNotBlank() }.take(20)
        } catch (e: Exception) { why += "tsetmc: " + reason(e) }
        try {
            // «نماد,نام,کد,…;نماد,نام,کد,…»
            val text = get("https://old.tsetmc.com/tsev2/data/search.aspx?skey=$enc", referer = "https://old.tsetmc.com/")
            return@withContext text.split(';').mapNotNull { rec ->
                val f = rec.split(',')
                val ins = f.firstOrNull { it.trim().length in 12..20 && it.trim().all(Char::isDigit) }?.trim() ?: return@mapNotNull null
                FundMatch(insCode = ins, symbol = faLetters(f.getOrNull(0).orEmpty()), name = faLetters(f.getOrNull(1).orEmpty()))
            }.distinctBy { it.insCode }.take(20)
        } catch (e: Exception) { why += "old.tsetmc: " + reason(e) }
        throw java.io.IOException(why.joinToString(" · "))
    }
}

/**
 * One price per day for each code (the last one read that day, in toman), kept on the phone so the
 * market list can draw the last days and a holding can show its chart. Nothing is downloaded for it:
 * the history starts the day the app first reads a price and grows with each day it is opened.
 */
object PriceHistory {
    private const val FILE = "price_history.json"
    private const val KEEP_DAYS = 400
    private const val TEHRAN_OFFSET_MS = 12_600_000L // +3:30

    fun dayOf(ms: Long): Int = ((ms + TEHRAN_OFFSET_MS) / 86_400_000L).toInt()

    @Synchronized
    fun load(context: Context): Map<String, List<Pair<Int, Double>>> = try {
        val f = java.io.File(context.filesDir, FILE)
        if (!f.exists()) emptyMap() else {
            val o = JSONObject(f.readText())
            o.keys().asSequence().associateWith { k ->
                val arr = o.getJSONArray(k)
                (0 until arr.length()).map { i -> arr.getJSONArray(i).let { it.getInt(0) to it.getDouble(1) } }
            }
        }
    } catch (e: Exception) { emptyMap() }

    /** Today's entry for each quote, replacing an earlier one from the same day. */
    @Synchronized
    fun record(context: Context, quotes: Map<String, PriceQuote>) {
        val all = load(context).mapValues { it.value.toMutableList() }.toMutableMap()
        for ((code, q) in quotes) {
            if (q.exact <= 0) continue
            val list = all.getOrPut(code) { mutableListOf() }
            val day = dayOf(q.at)
            list.removeAll { it.first == day }
            list += day to q.exact
            list.sortBy { it.first }
            while (list.size > KEEP_DAYS) list.removeAt(0)
        }
        val o = JSONObject()
        all.forEach { (k, list) -> o.put(k, JSONArray().apply { list.forEach { (d, v) -> put(JSONArray().put(d).put(v)) } }) }
        try {
            val f = java.io.File(context.filesDir, FILE)
            val tmp = java.io.File(context.filesDir, "$FILE.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (_: Exception) {}
    }

    /** The prices of the last [days] days that have one, oldest first. */
    fun lastDays(history: List<Pair<Int, Double>>?, today: Int, days: Int): List<Double> =
        history.orEmpty().filter { it.first > today - days }.map { it.second }
}
