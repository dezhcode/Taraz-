package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.Asset
import com.example.data.AssetKind
import com.example.data.FundMatch
import com.example.data.PriceCatalog
import com.example.data.PriceQuote
import com.example.utils.JalaliDate
import java.util.Locale
import kotlin.math.roundToLong

internal val KIND_LABEL = linkedMapOf(
    AssetKind.CURRENCY to "ارز",
    AssetKind.GOLD to "طلا و سکه",
    AssetKind.CRYPTO to "رمزارز",
    AssetKind.FUND to "صندوق"
)

internal fun kindColor(kind: String): Color = when (kind) {
    AssetKind.CURRENCY -> Color(0xFF0E9490)
    AssetKind.GOLD -> Color(0xFFD9A21B)
    AssetKind.CRYPTO -> Color(0xFF7A5AA8)
    else -> Color(0xFF0B7A57)
}

/** ۱۲.۵ → «۱۲٫۵»؛ تا ۸ رقم اعشار برای رمزارز، بدون صفرهای اضافه */
fun faQuantity(q: Double): String {
    val s = String.format(Locale.ENGLISH, "%.8f", q).trimEnd('0').trimEnd('.')
    return fa(s.replace('.', '٫'))
}

/** «۰٫۰۰۷» یا «۱۲۳۴٫۵» با رقم فارسی یا لاتین → عدد */
fun parseQuantity(text: String): Double {
    val latin = text.map { c ->
        val p = "۰۱۲۳۴۵۶۷۸۹".indexOf(c); val a = "٠١٢٣٤٥٦٧٨٩".indexOf(c)
        when { p >= 0 -> '0' + p; a >= 0 -> '0' + a; c == '٫' || c == ',' || c == '/' -> '.'; else -> c }
    }.joinToString("").filter { it.isDigit() || it == '.' }
    return latin.toDoubleOrNull() ?: 0.0
}

/**
 * قیمت هر واحد برای نمایش. قیمت‌های بزرگ گرد می‌شوند؛ قیمت کمتر از ۱۰ واحد (مثل شیبا به تومان یا
 * بیشتر رمزارزها به سنت در دفتر دلاری) با اعشار، تا چهار رقم معنادار.
 */
fun unitPriceText(exact: Double): String {
    val dec = com.example.data.Money.current.decimals
    val major = exact / Math.pow(10.0, dec.toDouble())
    if (major >= 10 || exact <= 0) return money(Math.round(exact))
    val digits = (3 - kotlin.math.floor(kotlin.math.log10(major)).toInt()).coerceIn(dec, 10)
    val s = java.math.BigDecimal(major).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return fa(s.replace('.', '٫'))
}

internal fun agoText(at: Long, now: Long = System.currentTimeMillis()): String {
    val sec = ((now - at) / 1000).toInt()
    val min = sec / 60
    return when {
        sec < 15 -> "هم‌اکنون"
        min < 1 -> "${fa(sec / 10 * 10)} ثانیه پیش"
        min < 60 -> "${fa(min)} دقیقه پیش"
        min < 24 * 60 -> "${fa(min / 60)} ساعت پیش"
        else -> JalaliDate.fromTimestamp(at).formatShort()
    }
}

/** نام منبع هر قیمت برای نمایش */
internal fun sourceLabel(s: String): String = when (s) {
    "tgju" -> "tgju"
    "nobitex" -> "نوبیتکس"
    "wallex" -> "والکس"
    "bitpin" -> "بیت‌پین"
    "tsetmc" -> "بورس"
    "fipiran" -> "فیپیران · قیمت ابطال"
    "intl" -> "نرخ جهانی"
    "intl-gold" -> "نرخ جهانی طلا"
    else -> if (s.endsWith("-usdt")) "نرخ تتر" else s
}

/** درصد با یک رقم اعشار و علامت: «+۱٫۲٪»، «−۰٫۳٪» */
internal fun signedPct(c: Double): String =
    ltrNum((if (c >= 0) "+" else "−") + fa(String.format(Locale.ENGLISH, "%.1f", kotlin.math.abs(c)).replace('.', '٫')) + "٪")

/**
 * عدد علامت‌دار یا اعشاری میان دو نشانهٔ LRM، تا در متن فارسی علامت پیش از عدد و ممیز سر جایش بماند
 * («+۱۳٫۳٪» نه «۱۳+٫۳٪»). نویسه‌های isolate (U+2066) در همهٔ نسخه‌ها رعایت نمی‌شوند؛ LRM می‌شود.
 */
internal fun ltrNum(s: String): String = "\u200E" + s + "\u200E"

/**
 * مبلغ دلاری: «$۱۰۸٬۷۳۵»، «$۲٫۴۵»، «$۰٫۰۰۰۰۱۲۳۴» (زیر یک دلار تا چهار رقم معنادار). جدا از متن
 * راست‌به‌چپ تا «$» کنار عدد بماند.
 */
fun dollarText(v: Double): String = com.example.data.usdText(v)

/** رمزارزها (جز خود تتر) به دلار نشان داده می‌شوند، مگر دفتر خودش دلاری باشد */
internal fun inDollars(code: String): Boolean =
    com.example.data.Money.current != com.example.data.AppCurrency.USD && code != "usdt" && PriceCatalog.find(code)?.kind == AssetKind.CRYPTO

/** مقدار برای نمایش، با جداکنندهٔ هزارگان: «۵٬۰۰۰٬۰۰۰»، «۰٫۰۱۲» */
internal fun faQuantityShown(q: Double): String {
    val s = String.format(Locale.ENGLISH, "%.8f", q).trimEnd('0').trimEnd('.')
    val whole = s.substringBefore('.')
    val frac = s.substringAfter('.', "")
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return fa((grouped + if (frac.isNotEmpty()) ".$frac" else "").replace(',', '٬').replace('.', '٫'))
}

/** مبلغ کوتاه برای فهرست‌های فشرده: «۱۲۵٫۴ میلیون»، «۱۰٫۴۵ میلیارد» (در دفتر دلاری همان مبلغ کامل) */
internal fun compact(v: Long): String {
    if (com.example.data.Money.current == com.example.data.AppCurrency.USD) return money(v)
    val a = kotlin.math.abs(v)
    fun one(x: Double, d: Int) = fa(java.math.BigDecimal(x).setScale(d, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', '٫'))
    val s = when {
        a >= 1_000_000_000L -> one(a / 1e9, 2) + " میلیارد"
        a >= 10_000_000L -> one(a / 1e6, 1) + " میلیون"
        else -> money(a)
    }
    return (if (v < 0) "−" else "") + s
}

/** نمودار خطی کوچک قیمت روزهای اخیر */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            // زمان از چپ به راست، مثل نمودارهای بازار
            val x = size.width * i / (values.size - 1)
            val y = (size.height - 2f) - ((v - lo) / span).toFloat() * (size.height - 4f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * دارایی‌ها (طرح «بازار و سبد»): سربرگ آبی با ارزش سبد، تغییر امروز و سود کل؛ تب «بازار» با نرخ
 * لحظه‌ای ارز، طلا، سکه، رمزارز و صندوق‌ها و نمودار چند روز اخیر؛ تب «سبد من» با دارایی‌ها به
 * تفکیک نوع، دارایی خالص و مقایسه با تورم.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerAssetsScreen(
    assets: List<Asset>,
    prices: Map<String, PriceQuote>,
    refreshing: Boolean,
    errors: List<String>,
    accountsTotal: Long,
    debtsTotal: Long,
    hideAmounts: Boolean,
    onBack: () -> Unit,
    onRefresh: (force: Boolean) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Asset) -> Unit,
    snapshots: List<com.example.data.NetWorthSnapshot> = emptyList(),
    history: Map<String, List<Pair<Int, Double>>> = emptyMap(),
    onAddCode: (String) -> Unit = { onAdd() },
    /** صفحهٔ جزئیات یک دارایی یا یک قیمت بازار */
    onOpenDetail: (String) -> Unit = {}
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    // هر بار که صفحه دیده می‌شود (باز شدن، برگشتن از برنامهٔ دیگر) قیمت تازه، و تا وقتی باز است هر دو دقیقه
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                onRefresh(false)
                kotlinx.coroutines.delay(120_000)
            }
        }
    }
    // «۲۰ ثانیه پیش» با گذر زمان به‌روز بماند
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10_000); clock = System.currentTimeMillis() } }

    fun unit(a: Asset) = com.example.data.unitPriceOf(a, prices)
    fun value(a: Asset) = com.example.data.valueOf(a, prices)
    fun amt(v: Long) = if (hideAmounts) "••••••" else money(v)
    val assetsTotal = assets.sumOf { value(it) }
    val net = accountsTotal + assetsTotal - debtsTotal
    // تغییر امروز: از درصد تغییر روزانهٔ هر قیمت زنده
    val todayChange = assets.filter { it.manualPrice <= 0 }.sumOf { a ->
        val c = prices[a.code]?.changePct ?: return@sumOf 0L
        val v = value(a).toDouble()
        (v - v / (1 + c / 100)).roundToLong()
    }
    val bought = assets.filter { it.buyPrice > 0 && unit(it) > 0 }
    val buyTotal = bought.sumOf { it.buyPrice }
    val profitTotal = bought.sumOf { value(it) - it.buyPrice }
    val held = assets.filter { it.manualPrice <= 0 }.map { it.code }
    val lastAt = (if (held.isNotEmpty()) held.mapNotNull { prices[it]?.at }.minOrNull() else prices["usd"]?.at)

    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (assets.isEmpty()) "market" else "mine") }
    var kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }

    Column(Modifier.fillMaxSize().background(L.Bg)) {
        // ─── سربرگ آبی
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(L.Primary)
                .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "بازگشت" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("دارایی‌ها", style = lt(20.sp, FontWeight.ExtraBold, Color.White), modifier = Modifier.weight(1f))
                val fresh = lastAt != null && clock - lastAt < 5 * 60_000L
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        .clickable(enabled = !refreshing) { onRefresh(true) }
                        .semantics { contentDescription = "به‌روزرسانی قیمت‌ها" }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (refreshing) Color.White else if (fresh) Color(0xFF7FE0BB) else L.AmberRing))
                    Text(
                        when {
                            refreshing -> "در حال دریافت…"
                            lastAt == null -> "به‌روزرسانی"
                            fresh -> "زنده · ${agoText(lastAt ?: clock, clock)}"
                            else -> agoText(lastAt ?: clock, clock)
                        },
                        style = lt(12.sp, color = Color.White)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (assets.isNotEmpty()) Text("ارزش سبد شما", style = lt(13.sp, color = L.OnPrimarySoft))
                if (assets.isNotEmpty()) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(amt(assetsTotal), style = lt(34.sp, FontWeight.Light, Color.White, letterSpacing = (-1).sp))
                    Text(com.example.data.Money.label, style = lt(13.sp, color = L.OnPrimarySoft), modifier = Modifier.padding(bottom = 6.dp))
                }
                val parts = buildList {
                    if (todayChange != 0L && assetsTotal > 0) {
                        val pct = todayChange * 100.0 / (assetsTotal - todayChange).coerceAtLeast(1)
                        add("امروز " + (if (hideAmounts) "" else ltrNum((if (todayChange > 0) "+" else "−") + money(kotlin.math.abs(todayChange))) + " ") + "(${signedPct(pct)})")
                    }
                    if (buyTotal > 0) add("سود کل " + signedPct(profitTotal * 100.0 / buyTotal))
                }
                Text(
                    if (assets.isEmpty()) "دارایی‌ای ثبت نشده؛ از «بازار» یا دکمهٔ پایین اضافه کنید." else parts.joinToString(" · ").ifEmpty { "${fa(assets.size)} دارایی" },
                    style = lt(13.sp, FontWeight.SemiBold, Color.White)
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("market" to "بازار", "mine" to "سبد من").forEach { (id, label) ->
                    val on = tab == id
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (on) Color.White else Color.Transparent)
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { tab = id },
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = lt(14.sp, FontWeight.Bold, if (on) Color(0xFF1D44D8) else Color.White)) }
                }
            }
        }

        // ─── محتوا
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (errors.isNotEmpty()) {
                Callout(
                    "بعضی قیمت‌ها به‌روز نشد",
                    "آخرین قیمت ثبت‌شده نمایش داده می‌شود.\n" +
                        "در صورت روشن بودن فیلترشکن، برای دریافت قیمت آن را خاموش کنید؛ سایت‌های قیمت داخلی به نشانی‌های خارجی پاسخ نمی‌دهند. " +
                        "در صورت تداوم مشکل، از این کادر تصویر تهیه کرده و برای پشتیبانی ارسال کنید.\n" +
                        errors.joinToString(" · "),
                    L.AmberSoft, L.AmberInk, Modifier.padding(horizontal = 20.dp)
                )
            }
            if (tab == "market") MarketTab(assets, prices, history, kind, { kind = it }, clock, onAddCode, onOpenDetail)
            else MineTab(assets, prices, hideAmounts, clock, accountsTotal, debtsTotal, net, assetsTotal, snapshots) { onOpenDetail(it.code) }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp)) {
            BigButton("افزودن دارایی", onClick = onAdd)
        }
    }
}

private val MARKET_KINDS = listOf("all" to "همه", AssetKind.CURRENCY to "ارز", AssetKind.GOLD to "طلا و سکه", AssetKind.CRYPTO to "رمزارز", AssetKind.FUND to "صندوق")

@Composable
private fun MarketTab(
    assets: List<Asset>,
    prices: Map<String, PriceQuote>,
    history: Map<String, List<Pair<Int, Double>>>,
    kind: String,
    onKind: (String) -> Unit,
    clock: Long,
    onAddCode: (String) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MARKET_KINDS.forEach { (id, label) -> LChip(label, kind == id) { onKind(id) } }
    }
    // فهرست بازار و صندوق‌هایی که کاربر از بورس اضافه کرده
    val funds = assets.filter { PriceCatalog.isFund(it.code) }.distinctBy { it.code }.map { com.example.data.CatalogItem(it.code, it.name, AssetKind.FUND, "واحد") }
    // آن‌هایی که هنوز قیمت ندارند، آخر فهرست
    val items = (PriceCatalog.items + funds).filter { kind == "all" || it.kind == kind }.sortedBy { if (it.code in prices) 0 else 1 }
    val today = com.example.data.PriceHistory.dayOf(clock)
    if (items.isEmpty()) {
        Text(
            "صندوق‌های بورسی که به سبد اضافه کنید، این‌جا با قیمت روز دیده می‌شوند.",
            style = lt(13.sp, color = L.Muted, lineHeight = 23.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
        return
    }
    LayerCard(radius = 22.dp, padding = 0.dp) {
        items.forEachIndexed { i, item ->
            val q = prices[item.code]
            val c = q?.changePct
            val tint = if (c != null && c < 0) L.RedText else L.GreenText
            Row(
                Modifier.fillMaxWidth().clickable { onOpenDetail(item.code) }.padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = lt(14.sp, FontWeight.Bold), maxLines = 1)
                    Text(
                        if (q == null) "قیمت هنوز نرسیده" else "${sourceLabel(q.source)} · ${agoText(q.at, clock)}",
                        style = lt(11.sp, color = if (q != null && clock - q.at > 30 * 60_000L) L.AmberInk else L.Muted), maxLines = 1
                    )
                }
                val points = com.example.data.PriceHistory.lastDays(history[item.code], today, 7)
                if (points.size >= 2) Sparkline(points, if (points.last() >= points.first()) L.Green else L.Red, Modifier.width(60.dp).height(26.dp))
                Column(Modifier.width(108.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when {
                            q == null -> "—"
                            inDollars(item.code) && q.usd != null -> dollarText(q.usd)
                            q.exact >= 1_000_000_000L -> compact(q.price)
                            else -> unitPriceText(q.exact)
                        },
                        style = lt(14.sp, FontWeight.Bold), maxLines = 1
                    )
                    if (c != null) Text(signedPct(c), style = lt(11.sp, FontWeight.Bold, tint))
                }
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(L.PrimarySoft)
                        .clickable { onAddCode(item.code) }.semantics { contentDescription = "افزودن ${item.name} به سبد" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp)) }
            }
            if (i < items.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
        }
    }
    Text(
        "قیمت هر واحد به ${com.example.data.Money.label}" +
            (if (com.example.data.Money.current != com.example.data.AppCurrency.USD) "؛ رمزارزها به دلار (بازار تتر نوبیتکس)" else "") +
            ". نمودار کوچک: قیمت ۷ روز اخیر، از روزی که تراز قیمت‌ها را ذخیره می‌کند. " +
            "ارز و طلا از tgju، رمزارز از نوبیتکس، صندوق از سایت بورس؛ با هر بار باز کردن صفحه و هر ۲ دقیقه به‌روز می‌شود.",
        style = lt(11.sp, color = L.Muted, lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun MineTab(
    assets: List<Asset>,
    prices: Map<String, PriceQuote>,
    hideAmounts: Boolean,
    clock: Long,
    accountsTotal: Long,
    debtsTotal: Long,
    net: Long,
    assetsTotal: Long,
    snapshots: List<com.example.data.NetWorthSnapshot>,
    onOpen: (Asset) -> Unit
) {
    fun unit(a: Asset) = com.example.data.unitPriceOf(a, prices)
    fun value(a: Asset) = com.example.data.valueOf(a, prices)
    fun amt(v: Long) = if (hideAmounts) "••••••" else money(v)

    if (assets.isEmpty()) {
        Text(
            "دلار، طلا، سکه، رمزارز یا واحدهای صندوق خود را اضافه کنید؛ ارزش ${com.example.data.Money.label}ی آن با قیمت روز محاسبه می‌شود.",
            style = lt(13.sp, color = L.Muted, lineHeight = 23.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
    }

    KIND_LABEL.forEach { (kind, label) ->
        val list = assets.filter { it.kind == kind }.sortedByDescending { value(it) }
        if (list.isEmpty()) return@forEach
        val total = list.sumOf { value(it) }
        val bought = list.filter { it.buyPrice > 0 && unit(it) > 0 }
        val buy = bought.sumOf { it.buyPrice }
        LayerCard(radius = 22.dp, padding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().background(L.PrimaryTint).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(kindColor(kind)))
                Text(label, style = lt(13.sp, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                // رمزارز: جمع به دلار، اگر همه قیمت دلاری دارند
                val usdTotal = if (list.all { inDollars(it.code) && it.manualPrice <= 0 && prices[it.code]?.usd != null })
                    list.sumOf { prices.getValue(it.code).usd!! * it.quantity } else null
                Text(if (usdTotal != null && !hideAmounts) dollarText(usdTotal) else amt(total), style = lt(13.sp, FontWeight.Bold))
                if (buy > 0) {
                    val p = (bought.sumOf { value(it) } - buy) * 100.0 / buy
                    Text(signedPct(p), style = lt(12.sp, FontWeight.Bold, if (p >= 0) L.GreenText else L.RedText))
                }
            }
            list.forEach { a ->
                val q = prices[a.code]
                val u = unit(a)
                val v = value(a)
                val profit = if (a.buyPrice > 0 && u > 0) v - a.buyPrice else null
                val stale = q != null && a.manualPrice <= 0 && clock - q.at > 30 * 60_000L
                val usd = q?.usd?.takeIf { inDollars(a.code) && a.manualPrice <= 0 }
                Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.name, style = lt(14.sp, FontWeight.Bold), maxLines = 1)
                        Text(
                            "${faQuantityShown(a.quantity)} ${PriceCatalog.find(a.code)?.unit ?: "واحد"} · " +
                                (if (usd != null) "هر واحد ${dollarText(usd)}" else if (u > 0) "هر واحد ${unitPriceText(u)}" else "قیمت نامعلوم") +
                                (if (a.manualPrice > 0) " (دستی)" else "") +
                                (if (stale) " · ${agoText(q!!.at, clock)}" else ""),
                            style = lt(11.sp, color = if (stale) L.AmberInk else L.Muted), maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (usd != null) {
                            Text(if (hideAmounts) "••••••" else dollarText(usd * a.quantity), style = lt(14.sp, FontWeight.Bold))
                            if (!hideAmounts) Text("${compact(v)} ${com.example.data.Money.label}", style = lt(11.sp, color = L.Muted))
                        } else Text(if (u > 0) amt(v) else "—", style = lt(14.sp, FontWeight.Bold))
                        when {
                            profit != null && !hideAmounts -> Text(
                                (if (profit >= 0) "سود " else "زیان ") + compact(kotlin.math.abs(profit)),
                                style = lt(11.sp, color = if (profit >= 0) L.GreenText else L.RedText)
                            )
                            q?.changePct != null && a.manualPrice <= 0 -> Text(
                                signedPct(q.changePct) + " امروز",
                                style = lt(11.sp, color = if (q.changePct >= 0) L.GreenText else L.RedText)
                            )
                        }
                    }
                }
            }
        }
    }

    // دارایی خالص: حساب‌ها + دارایی‌ها − بدهی‌ها
    LayerCard(padding = 16.dp) {
        Text("دارایی خالص", style = lt(13.sp, color = L.Muted))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(amt(net), style = lt(26.sp, FontWeight.Light, if (net < 0) L.RedText else L.Ink, letterSpacing = (-1).sp))
            Text(com.example.data.Money.label, style = lt(13.sp), modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            "حساب‌ها ${amt(accountsTotal)} + دارایی‌ها ${amt(assetsTotal)}" + if (debtsTotal > 0) " − بدهی‌ها ${amt(debtsTotal)}" else "",
            style = lt(12.sp, color = L.Muted, lineHeight = 20.sp)
        )
    }

    InflationCard(snapshots, hideAmounts)
}

/** افزودن یا ویرایش یک دارایی: نوع، مورد (یا جست‌وجوی صندوق)، مقدار، قیمت خرید و قیمت دستی */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetSheet(
    existing: Asset?,
    prices: Map<String, PriceQuote>,
    onDismiss: () -> Unit,
    onSearchFunds: (String, (List<FundMatch>, String?) -> Unit) -> Unit,
    onSave: (Asset) -> Unit,
    onDelete: (Asset) -> Unit,
    /** از دکمهٔ «+» بازار: (کد، نام) از پیش انتخاب‌شده */
    preset: Pair<String, String>? = null
) {
    val presetKind = preset?.first?.let { if (PriceCatalog.isFund(it)) AssetKind.FUND else PriceCatalog.find(it)?.kind }
    var kind by remember { mutableStateOf(existing?.kind ?: presetKind ?: AssetKind.CURRENCY) }
    var code by remember { mutableStateOf(existing?.code ?: preset?.first) }
    var name by remember { mutableStateOf(existing?.name ?: preset?.second.orEmpty()) }
    var qtyText by remember { mutableStateOf(existing?.quantity?.let { faQuantity(it) }.orEmpty()) }
    var buyText by remember { mutableStateOf(existing?.buyPrice?.takeIf { it > 0 }?.let { money(it) }.orEmpty()) }
    var manualText by remember { mutableStateOf(existing?.manualPrice?.takeIf { it > 0 }?.let { money(it) }.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FundMatch>>(emptyList()) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    val qty = parseQuantity(qtyText)
    val manual = parseAmount(manualText)
    var pickedPrice by remember { mutableStateOf<Double?>(null) }
    val livePrice = code?.let { prices[it]?.exact } ?: pickedPrice
    val unit = if (manual > 0) manual.toDouble() else livePrice ?: 0.0
    // صندوق بدون انتخاب از بورس هم با قیمت دستی قابل ثبت است
    val ok = qty > 0 && name.isNotBlank() && (code != null || manual > 0)

    LayerSheet(
        title = if (existing == null) "دارایی جدید" else "ویرایش ${existing.name}",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            BigButton("ذخیره", enabled = ok) {
                onSave(
                    (existing ?: Asset(code = "", name = "", kind = kind, quantity = 0.0)).copy(
                        code = code ?: "manual:${System.currentTimeMillis()}",
                        name = name.trim(), kind = kind, quantity = qty,
                        buyPrice = parseAmount(buyText), manualPrice = manual
                    )
                )
                onDismiss()
            }
            if (existing != null) SoftBigButton("حذف این دارایی") { onDelete(existing); onDismiss() }
        }
    ) {
        if (existing == null) {
            LSegmented(KIND_LABEL.values.toList(), KIND_LABEL.keys.indexOf(kind), { i ->
                kind = KIND_LABEL.keys.elementAt(i); code = null; name = ""; results = emptyList(); searchMsg = null
            })
            if (kind != AssetKind.FUND) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceCatalog.items.filter { it.kind == kind }.forEach { item ->
                        LChip(item.name, code == item.code) { code = item.code; name = item.name }
                    }
                }
            } else {
                LField(
                    value = query, onChange = { query = it }, label = "نام یا نماد صندوق",
                    placeholder = "مثلاً عیار، طلا، کهربا، فیروزه",
                    hint = "قیمت هر واحد خودکار است: صندوق‌های بورسی با قیمت بازار، بقیه با قیمت ابطال (فیپیران)."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(if (searching) "در حال جست‌وجو…" else "جست‌وجوی صندوق", height = 38.dp) {
                        if (!searching && query.trim().length >= 2) {
                            searching = true
                            onSearchFunds(query) { list, err ->
                                searching = false; results = list
                                searchMsg = err ?: if (list.isEmpty()) "موردی یافت نشد." else null
                            }
                        }
                    }
                }
                searchMsg?.let { Text(it, style = lt(12.sp, color = L.Muted)) }
                if (results.isNotEmpty()) {
                    LayerCard(radius = 16.dp, padding = 0.dp) {
                        results.forEachIndexed { i, r ->
                            val on = code == r.code
                            Row(
                                Modifier.fillMaxWidth().clickable { code = r.code; name = r.symbol.ifBlank { r.name }; pickedPrice = r.price }
                                    .background(if (on) L.PrimarySoft else Color.Transparent).padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.symbol, style = lt(14.sp, FontWeight.SemiBold, if (on) L.PrimaryText else L.Ink), maxLines = 1)
                                    Text(r.name, style = lt(11.sp, color = L.Muted), maxLines = 1)
                                }
                                // قیمت هر واحد و تغییر امروز
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(r.price?.let { unitPriceText(it) } ?: "—", style = lt(13.sp, FontWeight.Bold))
                                    Text(
                                        r.changePct?.let { signedPct(it) } ?: if (r.price != null) "هر واحد" else "بدون قیمت",
                                        style = lt(10.sp, color = when { r.changePct == null -> L.Muted; r.changePct >= 0 -> L.GreenText; else -> L.RedText })
                                    )
                                }
                            }
                            if (i < results.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
                        }
                    }
                }
                LField(value = name, onChange = { name = it }, label = "نام صندوق برای نمایش", placeholder = "مثلاً صندوق درآمد ثابت")
            }
        }

        LField(
            value = qtyText, onChange = { qtyText = it.take(20) },
            label = "مقدار (${code?.let { PriceCatalog.find(it)?.unit } ?: if (kind == AssetKind.FUND) "واحد" else "واحد"})",
            placeholder = "مثلاً ۱۰ یا ۰٫۰۰۵",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (kind == AssetKind.FUND && manual <= 0 && livePrice != null && qty <= 0) {
            Text("قیمت هر واحد: ${unitPriceText(livePrice)} ${com.example.data.Money.label} (خودکار)", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText))
        }
        if (unit > 0 && qty > 0) {
            Text(
                "ارزش امروز: ${money((unit * qty).roundToLong())} ${com.example.data.Money.label}" +
                    (code?.let { c -> prices[c]?.usd?.takeIf { manual <= 0 && inDollars(c) } }?.let { " · ${dollarText(it * qty)}" } ?: "") +
                    if (manual <= 0 && livePrice != null) " (هر واحد ${unitPriceText(livePrice)})" else "",
                style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)
            )
        }
        LField(
            value = buyText, onChange = { buyText = groupedInput(it) }, label = "بهای تمام‌شده (اختیاری، ${com.example.data.Money.label})",
            keyboardOptions = moneyKeyboard, hint = "مبلغ کلی که برای این مقدار پرداختید؛ سود یا زیان با قیمت روز حساب می‌شود."
        )
        LField(
            value = manualText, onChange = { manualText = groupedInput(it) }, label = "قیمت هر واحد به‌صورت دستی (اختیاری)",
            keyboardOptions = moneyKeyboard,
            hint = "در صورت خالی بودن، قیمت روز اعمال می‌شود."
        )
        Spacer(Modifier.height(4.dp))
    }
}

private fun pct(from: Double, to: Double): String {
    if (from <= 0.0) return "—"
    val p = (to - from) / from * 100
    val r = kotlin.math.round(p * 10) / 10
    return (if (r > 0) "+" else if (r < 0) "−" else "") + fa(String.format(java.util.Locale.ENGLISH, "%.1f", kotlin.math.abs(r)).replace('.', '٫')) + "٪"
}

/**
 * پس‌انداز در برابر تورم: دارایی خالص هر ماه به تومان، به دلار و به گرم طلا.
 * اگر به تومان بالا رفته ولی به دلار پایین آمده، یعنی ارزش واقعی کم شده.
 */
@Composable
private fun InflationCard(snapshots: List<com.example.data.NetWorthSnapshot>, hide: Boolean) {
    val list = snapshots.filter { it.usdToman > 0 && it.gold18Toman > 0 }.sortedBy { it.monthKey }.takeLast(6)
    LayerCard(padding = 16.dp) {
        Text("پس‌انداز در برابر تورم", style = lt(14.sp, FontWeight.Bold))
        if (list.size < 2) {
            Text(
                "تراز هر ماه دارایی خالص شما را با قیمت روز دلار و طلا ثبت می‌کند. از ماه دوم در این بخش مشخص می‌شود که ارزش دارایی شما به دلار و طلا افزایش یافته یا تنها مبلغ ${com.example.data.Money.label}ی آن بیشتر شده است." +
                    if (list.size == 1) "\nارزش ${JalaliDate.MONTH_NAMES[list[0].monthKey % 100 - 1]} ثبت شد." else "",
                style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(top = 6.dp)
            )
            return@LayerCard
        }
        val a = list.first()
        val b = list.last()
        val fromLabel = JalaliDate.MONTH_NAMES[a.monthKey % 100 - 1]
        Text("از $fromLabel تا امروز", style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        // دارایی خالص به واحد دفتر ذخیره شده و نرخ‌ها به تومان؛ برای مقایسه همه به تومان برده می‌شوند.
        fun toman(s: com.example.data.NetWorthSnapshot) = (com.example.data.Money.toToman(s.netWorth, s.usdToman) ?: 0L).toDouble()
        fun whole(v: Double) = com.example.data.Money.format(v.toLong(), com.example.data.AppCurrency.TOMAN)
        val usdA = toman(a) / a.usdToman
        val usdB = toman(b) / b.usdToman
        val goldA = toman(a) / a.gold18Toman
        val goldB = toman(b) / b.gold18Toman
        val book = com.example.data.Money.current
        listOf(
            if (book == com.example.data.AppCurrency.USD)
                Triple("به تومان", pct(toman(a), toman(b)), if (hide) "••••" else "${whole(toman(b))} تومان")
            else Triple("به ${book.label}", pct(a.netWorth.toDouble(), b.netWorth.toDouble()), if (hide) "••••" else millions(b.netWorth)),
            Triple("به دلار", pct(usdA, usdB), if (hide) "••••" else "${whole(usdB)} دلار"),
            Triple("به طلای ۱۸", pct(goldA, goldB), if (hide) "••••" else "${whole(goldB)} گرم")
        ).forEach { (label, change, now) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = lt(13.sp), modifier = Modifier.weight(1f))
                Text(now, style = lt(12.sp, color = L.Muted))
                Text(
                    change, style = lt(13.sp, FontWeight.Bold, if (change.startsWith("−")) L.RedText else L.Green),
                    modifier = Modifier.padding(start = 10.dp).width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
        // دارایی خالص به دلار در هر ماه
        val maxUsd = list.maxOf { toman(it) / it.usdToman }.coerceAtLeast(1.0)
        Row(
            Modifier.fillMaxWidth().height(70.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom
        ) {
            list.forEach { snap ->
                val v = (toman(snap) / snap.usdToman).coerceAtLeast(0.0)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height((44 * v / maxUsd).coerceAtLeast(2.0).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (snap == b) L.Primary else L.PrimarySoft)
                    )
                    Text(JalaliDate.MONTH_NAMES[snap.monthKey % 100 - 1].take(3), style = lt(10.sp, color = L.Muted), maxLines = 1)
                }
            }
        }
        val usdChange = if (usdA > 0) (usdB - usdA) / usdA else 0.0
        Text(
            when {
                b.netWorth > a.netWorth && usdChange < 0 -> "ارزش ${com.example.data.Money.label}ی افزایش یافته اما ارزش دلاری کاهش یافته است؛ یعنی نرخ تورم از رشد پس‌انداز بیشتر بوده است."
                usdChange > 0 -> "ارزش واقعی دارایی شما افزایش یافته است."
                else -> "ارزش دارایی شما به دلار و طلا کاهش یافته است."
            },
            style = lt(12.sp, color = L.Muted, lineHeight = 20.sp), modifier = Modifier.padding(top = 8.dp)
        )
    }
}
