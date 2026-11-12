package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.Asset
import com.example.data.AssetKind
import com.example.data.FundMatch
import com.example.data.PriceCatalog
import com.example.data.PriceQuote
import com.example.utils.JalaliDate
import java.util.Locale
import kotlin.math.roundToLong

private val KIND_LABEL = linkedMapOf(
    AssetKind.CURRENCY to "ارز",
    AssetKind.GOLD to "طلا و سکه",
    AssetKind.CRYPTO to "رمزارز",
    AssetKind.FUND to "صندوق"
)

private fun kindColor(kind: String): Color = when (kind) {
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

private fun agoText(at: Long): String {
    val min = ((System.currentTimeMillis() - at) / 60_000).toInt()
    return when {
        min < 1 -> "هم‌اکنون"
        min < 60 -> "${fa(min)} دقیقه پیش"
        min < 24 * 60 -> "${fa(min / 60)} ساعت پیش"
        else -> JalaliDate.fromTimestamp(at).formatShort()
    }
}

/**
 * دارایی‌ها: دلار و ارز، طلا و سکه، رمزارز و صندوق‌ها با قیمت روز به تومان،
 * و دارایی خالص (حساب‌ها + دارایی‌ها − قرض و قسط).
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
    snapshots: List<com.example.data.NetWorthSnapshot> = emptyList()
) {
    LaunchedEffect(Unit) { onRefresh(false) }
    fun unit(a: Asset) = if (a.manualPrice > 0) a.manualPrice else prices[a.code]?.price ?: 0L
    fun value(a: Asset) = (unit(a) * a.quantity).roundToLong()
    val assetsTotal = assets.sumOf { value(it) }
    val net = accountsTotal + assetsTotal - debtsTotal
    val lastAt = assets.filter { it.manualPrice <= 0 }.mapNotNull { prices[it.code]?.at }.minOrNull()
    fun amt(v: Long) = if (hideAmounts) "••••••" else money(v)

    LayerScreen(
        title = "دارایی‌ها",
        subtitle = if (refreshing) "در حال دریافت قیمت‌های روز…"
        else lastAt?.let { "قیمت‌ها: ${agoText(it)}" } ?: "ارز، طلا، رمزارز و صندوق با قیمت روز",
        onBack = onBack,
        trailing = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !refreshing) { onRefresh(true) }
                    .semantics { contentDescription = "به‌روزرسانی قیمت‌ها" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Refresh, null, tint = if (refreshing) L.Faint else L.Ink, modifier = Modifier.size(20.dp)) }
        },
        bottomBar = { BigButton("افزودن دارایی", onClick = onAdd) }
    ) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("دارایی خالص", style = lt(13.sp, color = L.Muted))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(amt(net), style = lt(36.sp, FontWeight.Light, if (net < 0) L.RedText else L.Ink, letterSpacing = (-1.5).sp))
                Text("${com.example.data.Money.label}", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                "حساب‌ها ${millions(accountsTotal)} + دارایی‌ها ${millions(assetsTotal)}" + if (debtsTotal > 0) " − بدهی‌ها ${millions(debtsTotal)}" else "",
                style = lt(12.sp, color = L.Muted)
            )
        }

        // سهم هر نوع
        val byKind = KIND_LABEL.keys.associateWith { k -> assets.filter { it.kind == k }.sumOf { value(it) } }.filterValues { it > 0 }
        val parts = (listOf("حساب‌ها" to (accountsTotal.coerceAtLeast(0) to L.Primary)) +
            byKind.map { (k, v) -> KIND_LABEL.getValue(k) to (v to kindColor(k)) }).filter { it.second.first > 0 }
        val sum = parts.sumOf { it.second.first }.coerceAtLeast(1)
        if (parts.size > 1) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    parts.forEach { (_, p) -> Box(Modifier.weight(p.first.toFloat()).height(12.dp).background(p.second)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    parts.forEach { (label, p) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(p.second))
                            Text("$label ${fa((p.first * 100 / sum).toInt())}٪", style = lt(11.sp, color = L.Muted))
                        }
                    }
                }
            }
        }

        InflationCard(snapshots, hideAmounts)

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

        if (assets.isEmpty()) {
            Text(
                "دلار، طلا، سکه، رمزارز یا واحدهای صندوق خود را اضافه کنید؛ ارزش ${com.example.data.Money.label}ی آن با قیمت روز محاسبه می‌شود.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp), modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        KIND_LABEL.forEach { (kind, label) ->
            val list = assets.filter { it.kind == kind }
            if (list.isNotEmpty()) {
                SectionTitle("$label · ${amt(list.sumOf { value(it) })}")
                LayerCard(radius = 20.dp, padding = 0.dp) {
                    list.forEachIndexed { i, a ->
                        val q = prices[a.code]
                        val u = unit(a)
                        val v = value(a)
                        val profit = if (a.buyPrice > 0 && u > 0) v - a.buyPrice else null
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(kindColor(kind)), contentAlignment = Alignment.Center) {
                                Text(a.name.take(1), style = lt(16.sp, FontWeight.Bold, Color.White))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.name, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
                                Text(
                                    "${faQuantity(a.quantity)} ${PriceCatalog.find(a.code)?.unit ?: "واحد"} · " +
                                        (if (u > 0) "هر واحد ${money(u)}" else "قیمت نامعلوم") +
                                        (if (a.manualPrice > 0) " (دستی)" else ""),
                                    style = lt(11.sp, color = L.Muted), maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (u > 0) amt(v) else "—", style = lt(14.sp, FontWeight.Bold))
                                when {
                                    profit != null && !hideAmounts -> Text(
                                        (if (profit >= 0) "+" else "−") + millions(kotlin.math.abs(profit)) + " سود",
                                        style = lt(11.sp, color = if (profit >= 0) L.GreenText else L.RedText)
                                    )
                                    q?.changePct != null && a.manualPrice <= 0 -> {
                                        val c = q.changePct
                                        Text(
                                            (if (c >= 0) "+" else "−") + fa(String.format(Locale.ENGLISH, "%.1f", kotlin.math.abs(c)).replace('.', '٫')) + "٪ امروز",
                                            style = lt(11.sp, color = if (c >= 0) L.GreenText else L.RedText)
                                        )
                                    }
                                }
                            }
                        }
                        if (i < list.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                    }
                }
            }
        }

        Text(
            "قیمت‌ها از بازار آزاد خوانده می‌شوند (ارز و طلا از tgju، رمزارز از نوبیتکس، صندوق‌های بورسی از سایت بورس) و ممکن است با قیمت معاملهٔ شما اندکی تفاوت داشته باشند.",
            style = lt(11.sp, color = L.Muted, lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
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
    onDelete: (Asset) -> Unit
) {
    var kind by remember { mutableStateOf(existing?.kind ?: AssetKind.CURRENCY) }
    var code by remember { mutableStateOf(existing?.code) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var qtyText by remember { mutableStateOf(existing?.quantity?.let { faQuantity(it) }.orEmpty()) }
    var buyText by remember { mutableStateOf(existing?.buyPrice?.takeIf { it > 0 }?.let { money(it) }.orEmpty()) }
    var manualText by remember { mutableStateOf(existing?.manualPrice?.takeIf { it > 0 }?.let { money(it) }.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FundMatch>>(emptyList()) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    val qty = parseQuantity(qtyText)
    val manual = parseAmount(manualText)
    val livePrice = code?.let { prices[it]?.price }
    val unit = if (manual > 0) manual else livePrice ?: 0L
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
                    hint = "صندوق‌هایی که در بورس معامله می‌شوند قیمت روز دارند؛ سایر صندوق‌ها را با قیمت دستی ثبت کنید."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(if (searching) "در حال جست‌وجو…" else "جست‌وجو در بورس", height = 38.dp) {
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
                            val on = code == "fund:${r.insCode}"
                            Row(
                                Modifier.fillMaxWidth().clickable { code = "fund:${r.insCode}"; name = r.symbol.ifBlank { r.name } }
                                    .background(if (on) L.PrimarySoft else Color.Transparent).padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.symbol, style = lt(14.sp, FontWeight.SemiBold, if (on) L.PrimaryText else L.Ink))
                                    Text(r.name, style = lt(11.sp, color = L.Muted), maxLines = 1)
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
        if (unit > 0 && qty > 0) {
            Text(
                "ارزش امروز: ${money((unit * qty).roundToLong())} ${com.example.data.Money.label}" + if (manual <= 0 && livePrice != null) " (هر واحد ${money(livePrice)})" else "",
                style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)
            )
        }
        LField(
            value = buyText, onChange = { buyText = groupedInput(it) }, label = "بهای تمام‌شده (اختیاری، ${com.example.data.Money.label})",
            keyboardOptions = moneyKeyboard, hint = "برای محاسبهٔ سود یا زیان"
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
