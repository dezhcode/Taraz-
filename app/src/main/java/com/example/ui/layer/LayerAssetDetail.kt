package com.example.ui.layer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Asset
import com.example.data.AssetTrade
import com.example.data.BankCard
import com.example.data.Money
import com.example.data.PriceAlert
import com.example.data.PriceCatalog
import com.example.data.PriceHistory
import com.example.data.PriceQuote
import com.example.data.alertPriceText
import com.example.utils.JalaliDate
import kotlin.math.roundToLong

private fun dateText(ts: Long): String = JalaliDate.fromTimestamp(ts).let { "${it.formatShort()} ${fa(it.year)}" }

/** مبلغ کامل به واحد دفتر با برچسب: «۱۷۹٬۰۰۰٬۰۰۰ تومان» */
private fun bookText(v: Long): String = Money.withLabel(v)

/** نمودار خطی قیمت با سطح زیرش، و خط‌چین [ref] (میانگین خرید) */
@Composable
private fun PriceChart(values: List<Double>, ref: Double?, modifier: Modifier = Modifier) {
    val line = L.Primary
    val fill = L.PrimarySoft
    val refColor = L.Chevron
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        var lo = values.min()
        var hi = values.max()
        if (ref != null && ref > 0) { lo = minOf(lo, ref); hi = maxOf(hi, ref) }
        val pad = if (hi > lo) (hi - lo) * 0.08 else maxOf(hi * 0.01, 1e-9)
        lo -= pad; hi += pad
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        if (ref != null && ref > 0) {
            drawLine(
                refColor, Offset(0f, y(ref)), Offset(size.width, y(ref)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() / 2, 8.dp.toPx() / 2))
            )
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line, 4.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
        drawCircle(Color.White, 2.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
    }
}

private val RANGES = listOf(7 to "۱ هفته", 30 to "۱ ماه", 90 to "۳ ماه", 365 to "۱ سال")

/**
 * جزئیات یک دارایی یا یک قیمت بازار: قیمت روز (رمزارز به دلار)، نمودار قیمت از تاریخچه‌ای که تراز
 * هر روز ذخیره می‌کند، آنچه کاربر دارد و سودش، هشدارهای قیمت، و سابقهٔ خرید و فروش.
 */
@Composable
fun LayerAssetDetailScreen(
    code: String,
    name: String,
    holding: Asset?,
    /** قیمت روز به واحد دفتر (و دلار، برای رمزارز) */
    quote: PriceQuote?,
    /** همان قیمت به تومان، برای بردن تاریخچه (که به تومان است) به واحد نمایش */
    tomanNow: Double?,
    history: List<Pair<Int, Double>>,
    trades: List<AssetTrade>,
    alerts: List<PriceAlert>,
    usdToman: Long?,
    hideAmounts: Boolean,
    onBack: () -> Unit,
    onEdit: (Asset) -> Unit,
    onTrade: (buy: Boolean) -> Unit,
    onDeleteTrade: (AssetTrade) -> Unit,
    onAddAlert: () -> Unit,
    onToggleAlert: (PriceAlert) -> Unit,
    onDeleteAlert: (PriceAlert) -> Unit
) {
    fun amt(v: Long) = if (hideAmounts) "••••••" else bookText(v)
    val dollars = inDollars(code) && quote?.usd != null
    val unitName = PriceCatalog.find(code)?.unit ?: "واحد"
    val manual = holding?.manualPrice?.takeIf { it > 0 }
    // قیمت هر واحد به واحد نمایش (دلار برای رمزارز، وگرنه واحد دفتر)
    val shown: Double? = when {
        manual != null -> manual.toDouble()
        dollars -> quote!!.usd
        else -> quote?.exact
    }
    fun priceText(v: Double): String = when {
        dollars && manual == null -> dollarText(v)
        v >= 1_000_000_000 -> "${compact(Math.round(v))} ${Money.label}"
        else -> "${unitPriceText(v)} ${Money.label}"
    }
    var range by rememberSaveable { mutableStateOf(1) }
    var tradeToDelete by remember { mutableStateOf<AssetTrade?>(null) }
    var alertToDelete by remember { mutableStateOf<PriceAlert?>(null) }

    LayerScreen(
        title = name,
        subtitle = quote?.let { "${sourceLabel(it.source)} · ${agoText(it.at)}" } ?: if (manual != null) "قیمت دستی" else "قیمت هنوز نرسیده",
        onBack = onBack,
        trailing = {
            if (holding != null) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(L.Surface).clickable { onEdit(holding) }
                        .semantics { contentDescription = "ویرایش" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Edit, null, tint = L.Ink, modifier = Modifier.size(19.dp)) }
            }
        },
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(if (holding == null) "ثبت خرید" else "خرید بیشتر", modifier = Modifier.weight(1f)) { onTrade(true) }
                if (holding != null) SoftBigButton("فروش", modifier = Modifier.weight(1f), height = 54.dp) { onTrade(false) }
            }
        }
    ) {
        // ─── قیمت و نمودار
        LayerCard(radius = 24.dp, padding = 16.dp) {
            Text(if (manual != null) "قیمت هر $unitName (دستی)" else "قیمت هر $unitName", style = lt(13.sp, color = L.Muted))
            Text(shown?.let { priceText(it) } ?: "—", style = lt(28.sp, FontWeight.Light, letterSpacing = (-0.5).sp))
            if (dollars && manual == null && quote != null) {
                Text("${compact(quote.price)} ${Money.label}", style = lt(12.sp, color = L.Muted))
            }
            quote?.changePct?.takeIf { manual == null }?.let { c ->
                Text("${signedPct(c)} امروز", style = lt(12.sp, FontWeight.Bold, if (c >= 0) L.GreenText else L.RedText))
            }

            val days = RANGES[range].first
            val scale = if (shown != null && tomanNow != null && tomanNow > 0 && manual == null) shown / tomanNow else null
            val points = if (scale == null) emptyList()
            else PriceHistory.lastDays(history, PriceHistory.dayOf(System.currentTimeMillis()), days).map { it * scale }
            // میانگین قیمت خرید، به همان واحد نمودار
            val avgBook = holding?.takeIf { it.buyPrice > 0 && it.quantity > 0 }?.let { it.buyPrice / it.quantity }
            val avgShown = avgBook?.let { a -> if (dollars && quote != null && quote.exact > 0) a * quote.usd!! / quote.exact else a }
            Spacer(Modifier.height(10.dp))
            if (points.size >= 2) {
                val change = (points.last() - points.first()) / points.first() * 100
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(signedPct(change), style = lt(13.sp, FontWeight.Bold, if (change >= 0) L.GreenText else L.RedText))
                    Text("در ${RANGES[range].second} گذشته", style = lt(12.sp, color = L.Muted))
                }
                PriceChart(points, avgShown, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("کمترین ${priceText(points.min())}", style = lt(11.sp, color = L.Muted), modifier = Modifier.weight(1f))
                    Text("بیشترین ${priceText(points.max())}", style = lt(11.sp, color = L.Muted))
                }
                if (avgShown != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.width(14.dp).height(1.dp).background(L.Chevron))
                        Text("میانگین خرید شما: ${priceText(avgShown)}", style = lt(11.sp, color = L.Muted))
                    }
                }
            } else {
                Text(
                    if (manual != null) "برای قیمت دستی نمودار رسم نمی‌شود."
                    else "تراز قیمت را هر روز ذخیره می‌کند؛ نمودار از روز دوم دیده می‌شود و با هر روز کامل‌تر می‌شود.",
                    style = lt(12.sp, color = L.Muted, lineHeight = 20.sp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(L.Chip).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RANGES.forEachIndexed { i, (_, label) ->
                    val on = i == range
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp)).background(if (on) L.Surface else Color.Transparent)
                            .clickable { range = i },
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = lt(12.sp, FontWeight.Bold, if (on) L.PrimaryText else L.Muted)) }
                }
            }
        }

        // ─── آنچه دارید
        if (holding != null) {
            val value = com.example.data.valueOf(holding, mapOfNotNull(code, quote))
            val profit = if (holding.buyPrice > 0 && value > 0) value - holding.buyPrice else null
            LayerCard(radius = 24.dp, padding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("مقدار", "${faQuantityShown(holding.quantity)} $unitName", Modifier.weight(1f))
                    Stat(
                        "ارزش امروز",
                        if (hideAmounts) "••••••" else if (dollars && manual == null) dollarText(quote!!.usd!! * holding.quantity) else bookText(value),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("میانگین قیمت خرید", if (holding.buyPrice > 0) amt(Math.round(holding.buyPrice / holding.quantity)) else "ثبت نشده", Modifier.weight(1f))
                    Stat(
                        "سود و زیان",
                        when {
                            profit == null -> "—"
                            hideAmounts -> "••••••"
                            else -> (if (profit >= 0) "+" else "−") + compact(kotlin.math.abs(profit)) + " (" + signedPct(profit * 100.0 / holding.buyPrice) + ")"
                        },
                        Modifier.weight(1f),
                        color = when { profit == null -> L.Ink; profit >= 0 -> L.GreenText; else -> L.RedText }
                    )
                }
                val realised = trades.filter { !it.isBuy && it.costBasis > 0 }.sumOf { it.total - it.costBasis }
                if (trades.any { !it.isBuy && it.costBasis > 0 } && !hideAmounts) {
                    Text(
                        "سود فروش‌های گذشته: " + (if (realised >= 0) "+" else "−") + bookText(kotlin.math.abs(realised)),
                        style = lt(12.sp, color = if (realised >= 0) L.GreenText else L.RedText), modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        // ─── هشدار قیمت
        LayerCard(radius = 24.dp, padding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(L.AmberSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, null, tint = L.AmberInk, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("هشدار قیمت", style = lt(14.sp, FontWeight.Bold))
                    Text(
                        if (alerts.isEmpty()) "وقتی قیمت از حدی بالاتر یا پایین‌تر رفت، اعلان بگیرید." else "هر هشدار یک بار اعلان می‌دهد و خاموش می‌شود.",
                        style = lt(11.sp, color = L.Muted, lineHeight = 18.sp)
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(L.PrimarySoft).clickable(onClick = onAddAlert).padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("افزودن", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
            }
            alerts.forEach { a ->
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(L.Line))
                Row(
                    Modifier.fillMaxWidth().clickable { alertToDelete = a }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(
                                a.above.takeIf { it > 0 }?.let { "بالاتر از ${alertPriceText(it, a.inUsd, usdToman)}" },
                                a.below.takeIf { it > 0 }?.let { "پایین‌تر از ${alertPriceText(it, a.inUsd, usdToman)}" }
                            ).joinToString(" یا "),
                            style = lt(13.sp, FontWeight.SemiBold)
                        )
                        if (!a.enabled && a.lastFiredAt > 0) Text("اعلان داده شد · ${dateText(a.lastFiredAt)}", style = lt(11.sp, color = L.Muted))
                    }
                    LSwitch(a.enabled, "هشدار قیمت") { onToggleAlert(a) }
                }
            }
        }

        // ─── خرید و فروش
        if (trades.isNotEmpty()) {
            SectionTitle("خرید و فروش · ${fa(trades.size)} مورد")
            LayerCard(radius = 24.dp, padding = 0.dp) {
                trades.forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { tradeToDelete = t }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.width(46.dp).clip(RoundedCornerShape(8.dp)).background(if (t.isBuy) L.GreenSoft else L.RedSoft).padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(if (t.isBuy) "خرید" else "فروش", style = lt(12.sp, FontWeight.Bold, if (t.isBuy) L.GreenInk else L.RedInk)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${faQuantityShown(t.quantity)} $unitName · هر واحد ${unitPriceText(t.total / t.quantity)}",
                                style = lt(13.sp, FontWeight.SemiBold), maxLines = 1
                            )
                            Text(
                                dateText(t.date) + (t.bankName?.let { (if (t.isBuy) " · از " else " · به ") + it } ?: "") +
                                    (if (!t.isBuy && t.costBasis > 0 && !hideAmounts) " · سود " + compact(t.total - t.costBasis) else ""),
                                style = lt(11.sp, color = L.Muted), maxLines = 1
                            )
                        }
                        Text(amt(t.total), style = lt(13.sp, FontWeight.Bold))
                    }
                    if (i < trades.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    tradeToDelete?.let { t ->
        LayerSheet(
            title = "این ${if (t.isBuy) "خرید" else "فروش"} حذف شود؟", onDismiss = { tradeToDelete = null }, topGap = 320.dp,
            bottomBar = { BigButton("بله، حذف شود", background = L.RedText) { onDeleteTrade(t); tradeToDelete = null } }
        ) {
            Text(
                "${faQuantityShown(t.quantity)} $unitName · ${bookText(t.total)}\n" +
                    (if (t.isBuy) "این مقدار از دارایی کم می‌شود" else "این مقدار به دارایی برمی‌گردد") +
                    (if (t.transactionId != null) " و مبلغ آن به حساب ${t.bankName} برگردانده می‌شود." else "."),
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
            )
        }
    }
    alertToDelete?.let { a ->
        LayerSheet(
            title = "این هشدار حذف شود؟", onDismiss = { alertToDelete = null }, topGap = 360.dp,
            bottomBar = { BigButton("بله، حذف شود", background = L.RedText) { onDeleteAlert(a); alertToDelete = null } }
        ) {
            Text("برای خاموش کردن موقت، از کلید کنار هشدار استفاده کنید.", style = lt(13.sp, color = L.Muted, lineHeight = 23.sp))
        }
    }
}

private fun mapOfNotNull(code: String, q: PriceQuote?): Map<String, PriceQuote> = if (q == null) emptyMap() else mapOf(code to q)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = L.Ink) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = lt(11.sp, color = L.Muted))
        Text(value, style = lt(15.sp, FontWeight.Bold, color))
    }
}

/**
 * ثبت خرید یا فروش: مقدار، مبلغ کل (با قیمت روز پیش‌پر می‌شود)، تاریخ، و حسابی که پول از آن رفت یا
 * به آن آمد (یا هیچ حسابی). مبلغ کل به‌جای قیمت هر واحد پرسیده می‌شود تا رمزارزهای ارزان هم دقیق ثبت شوند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeSheet(
    name: String,
    unitName: String,
    holding: Asset?,
    startBuy: Boolean,
    /** قیمت روز هر واحد به واحد دفتر */
    livePrice: Double?,
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    onSave: (isBuy: Boolean, quantity: Double, total: Long, date: Long, bankName: String?) -> Unit
) {
    var isBuy by remember { mutableStateOf(startBuy || holding == null) }
    var qtyText by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var totalTouched by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    var bank by remember { mutableStateOf<String?>(null) }

    val qty = parseQuantity(qtyText)
    val total = parseAmount(totalText)
    fun fillTotal(q: Double) {
        if (!totalTouched && livePrice != null && livePrice > 0 && q > 0) totalText = groupedInput(Money.toInput((q * livePrice).roundToLong()))
    }
    val tooMuch = !isBuy && holding != null && qty > holding.quantity + 1e-9
    val ok = qty > 0 && total > 0 && !tooMuch

    LayerSheet(
        title = if (isBuy) "ثبت خرید $name" else "ثبت فروش $name",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            BigButton(if (isBuy) "ثبت خرید" else "ثبت فروش", enabled = ok) {
                onSave(isBuy, qty, total, date, bank)
                onDismiss()
            }
        }
    ) {
        if (holding != null) {
            LSegmented(listOf("خرید", "فروش"), if (isBuy) 0 else 1, { isBuy = it == 0 })
        }
        LField(
            value = qtyText, onChange = { qtyText = it.take(20); fillTotal(parseQuantity(it)) },
            label = "مقدار ($unitName)", placeholder = "مثلاً ۲ یا ۰٫۰۰۵",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            hint = if (!isBuy && holding != null) "موجودی شما: ${faQuantityShown(holding.quantity)} $unitName" else null
        )
        if (!isBuy && holding != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25 to "یک‌چهارم", 0.5 to "نصف", 1.0 to "همه").forEach { (f, label) ->
                    LChip(label, false) {
                        val q = holding.quantity * f
                        qtyText = faQuantity(q); fillTotal(q)
                    }
                }
            }
        }
        if (tooMuch) Text("بیشتر از مقداری است که دارید.", style = lt(12.sp, color = L.RedText))
        LField(
            value = totalText, onChange = { totalText = groupedInput(it); totalTouched = true },
            label = if (isBuy) "مبلغ کل پرداختی (${Money.label})" else "مبلغ کل دریافتی (${Money.label})",
            keyboardOptions = moneyKeyboard,
            hint = when {
                qty > 0 && total > 0 -> "هر واحد ${unitPriceText(total.toDouble() / qty)} ${Money.label}"
                livePrice != null -> "با قیمت روز پر می‌شود؛ مبلغ معاملهٔ خودتان را بنویسید."
                else -> null
            }
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { pickDate = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("تاریخ", style = lt(13.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(dateText(date), style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText))
        }

        Text(if (isBuy) "از کدام حساب پرداخت شد؟" else "به کدام حساب واریز شد؟", style = lt(13.sp, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, L.Rail, RoundedCornerShape(18.dp))) {
            val options = cards.map { it.bankName to "موجودی ${money(it.balance)}" } +
                (null to (if (isBuy) "از حسابی کم نشود؛ فقط به دارایی‌ها اضافه شود" else "به حسابی واریز نشد"))
            options.forEachIndexed { i, (b, sub) ->
                val on = bank == b
                Row(
                    Modifier.fillMaxWidth().clickable { bank = b }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(L.Surface)
                            .border(if (on) 7.dp else 2.dp, if (on) L.Primary else L.Placeholder, CircleShape)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(b ?: "هیچ حسابی", style = lt(14.sp, FontWeight.Bold))
                        Text(sub, style = lt(11.sp, color = L.Muted))
                    }
                }
                if (i < options.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
            }
        }
        if (bank != null) {
            Text(
                "به‌صورت انتقال ثبت می‌شود: موجودی حساب تغییر می‌کند ولی جزو هزینه یا درآمد ماه حساب نمی‌شود.",
                style = lt(11.sp, color = L.Muted, lineHeight = 19.sp)
            )
        }

        // خلاصه
        if (qty > 0 && total > 0 && !tooMuch) {
            val lines = if (isBuy) {
                val after = (holding?.quantity ?: 0.0) + qty
                val cost = (holding?.buyPrice ?: 0L) + total
                listOfNotNull(
                    "بعد از خرید: ${faQuantityShown(after)} $unitName",
                    if (holding == null || holding.buyPrice > 0) "میانگین قیمت خرید: ${unitPriceText(cost / after)} ${Money.label}" else null
                )
            } else {
                val basis = if (holding!!.buyPrice > 0) Math.round(holding.buyPrice * qty / holding.quantity) else null
                listOfNotNull(
                    "باقی‌مانده: ${faQuantityShown((holding.quantity - qty).coerceAtLeast(0.0))} $unitName",
                    basis?.let { b -> (if (total >= b) "سود این فروش: +" else "زیان این فروش: −") + bookText(kotlin.math.abs(total - b)) }
                )
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.PrimaryTint).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { lines.forEach { Text(it, style = lt(13.sp, FontWeight.SemiBold)) } }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (pickDate) {
        JalaliDatePickerSheet(initial = date, onDismiss = { pickDate = false }, onPick = { date = it }, title = "تاریخ معامله", showTime = false)
    }
}

/**
 * هشدار تازه: «بالاتر از» و/یا «پایین‌تر از». برای رمزارز به دلار، برای بقیه به واحد پول دفتر.
 * [onSave] حدها را به همان واحدی که وارد شده می‌گیرد (۰ = خالی).
 */
@Composable
fun AlertSheet(
    name: String,
    inUsd: Boolean,
    /** قیمت فعلی به همان واحد (برای راهنما) */
    current: Double?,
    onDismiss: () -> Unit,
    onSave: (above: Double, below: Double) -> Unit
) {
    var aboveText by remember { mutableStateOf("") }
    var belowText by remember { mutableStateOf("") }
    fun parse(t: String): Double = if (inUsd) parseQuantity(t) else parseAmount(t).toDouble()
    val above = parse(aboveText)
    val below = parse(belowText)
    val wrong = current != null && ((above > 0 && above <= current) || (below > 0 && below >= current))
    val ok = (above > 0 || below > 0) && !wrong
    val unit = if (inUsd) "دلار" else Money.label
    fun show(v: Double) = if (inUsd) dollarText(v) else "${unitPriceText(v)} $unit"
    val keyboard = if (inUsd) KeyboardOptions(keyboardType = KeyboardType.Decimal) else moneyKeyboard
    fun input(t: String) = if (inUsd) t.take(20) else groupedInput(t)

    LayerSheet(
        title = "هشدار قیمت $name",
        onDismiss = onDismiss,
        topGap = 120.dp,
        bottomBar = { BigButton("ذخیرهٔ هشدار", enabled = ok) { onSave(above, below); onDismiss() } }
    ) {
        current?.let { Text("قیمت فعلی: ${show(it)}", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
        LField(aboveText, { aboveText = input(it) }, "وقتی بالاتر رفت از ($unit)", keyboardOptions = keyboard, placeholder = "خالی = بدون این حد")
        LField(belowText, { belowText = input(it) }, "وقتی پایین‌تر آمد از ($unit)", keyboardOptions = keyboard, placeholder = "خالی = بدون این حد")
        if (wrong) Text("حد بالا باید بیشتر و حد پایین کمتر از قیمت فعلی باشد.", style = lt(12.sp, color = L.RedText))
        Text(
            "تا وقتی هشدار روشن است، تراز هر نیم ساعت (با اینترنت) و هر بار که صفحهٔ دارایی‌ها را باز کنید قیمت را بررسی می‌کند و یک بار اعلان می‌دهد.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
        )
        Spacer(Modifier.height(4.dp))
    }
}
