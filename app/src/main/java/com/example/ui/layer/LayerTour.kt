package com.example.ui.layer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * راهنمای گام‌به‌گام هر صفحه (طرح «گفت‌وگویی»): صفحه تیره می‌شود، بخشِ مورد نظر روشن می‌ماند،
 * یک پیکان خط‌چین از پیام به آن اشاره می‌کند و لوگوی تراز در سمت راست توضیح می‌دهد.
 * هر صفحه فقط بار اول نمایش داده می‌شود؛ از تنظیمات می‌توان همه را دوباره دید.
 */
data class TourStep(val target: String?, val title: String, val body: String)

class TourState {
    /** جای هر بخشِ نشانه‌گذاری‌شده روی صفحه */
    val bounds = mutableStateMapOf<String, Rect>()
    /** صفحه‌هایی که الان روی صفحه‌اند؛ آخری بالاترین است */
    val screens = mutableStateListOf<Pair<String, List<TourStep>>>()
    var step by mutableIntStateOf(0)
    /** برای تصویرهای راهنما: صرف‌نظر از دیده‌شدن، همین گام نمایش داده شود */
    var preview by mutableStateOf<Pair<String, Int>?>(null)
    /** هر بار که «نمایش دوبارهٔ راهنما» زده شود بالا می‌رود تا صفحهٔ فعلی دوباره راهنما بگیرد */
    var resetTick by mutableIntStateOf(0)
}

val LocalTour = staticCompositionLocalOf<TourState?> { null }

object TourPrefs {
    private fun p(c: Context) = c.getSharedPreferences("taraz_tour", Context.MODE_PRIVATE)
    fun seen(c: Context, key: String) = p(c).getBoolean("seen_$key", false)
    fun markSeen(c: Context, key: String) = p(c).edit().putBoolean("seen_$key", true).apply()
    fun resetAll(c: Context) = p(c).edit().clear().apply()
}

/** این بخش را برای راهنما نشانه‌گذاری می‌کند */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current
    if (tour == null) this else this.onGloballyPositioned { tour.bounds[key] = it.boundsInRoot() }
}

/** صفحه‌ای که راهنما دارد این را صدا می‌زند؛ تا وقتی صفحه روی صفحه است، راهنمایش در دسترس است */
@Composable
fun ScreenTour(key: String, steps: List<TourStep>) {
    val tour = LocalTour.current ?: return
    DisposableEffect(key) {
        val entry = key to steps
        tour.screens.removeAll { it.first == key }
        tour.screens.add(entry)
        tour.step = 0
        onDispose { tour.screens.removeAll { it.first == key } }
    }
}

/** لایهٔ راهنما؛ در بالاترین لایهٔ برنامه قرار می‌گیرد */
@Composable
fun TourHost(tour: TourState) {
    val context = LocalContext.current
    val preview = tour.preview
    val top = if (preview != null) tour.screens.lastOrNull { it.first == preview.first } else tour.screens.lastOrNull()
    val key = top?.first
    // کمی صبر تا صفحه جا بیفتد و اندازه‌ها معلوم شود
    var ready by remember(key, tour.resetTick) { mutableStateOf(preview != null) }
    var dismissed by remember(key, tour.resetTick) { mutableStateOf(false) }
    LaunchedEffect(key, tour.resetTick) {
        if (preview == null) { delay(700); ready = true }
    }
    val show = top != null && ready && !dismissed && (preview != null || !TourPrefs.seen(context, top.first))
    AnimatedVisibility(visible = show, enter = fadeIn(), exit = fadeOut()) {
        if (top == null) return@AnimatedVisibility
        val steps = top.second
        val index = (preview?.second ?: tour.step).coerceIn(0, steps.lastIndex)
        val step = steps[index]
        fun finish() {
            TourPrefs.markSeen(context, top.first)
            tour.step = 0
            dismissed = true
        }
        TourOverlay(
            step = step,
            index = index,
            count = steps.size,
            target = step.target?.let { tour.bounds[it] },
            onNext = { if (index < steps.lastIndex) tour.step = index + 1 else finish() },
            onClose = { finish() }
        )
    }
}

@Composable
private fun TourOverlay(step: TourStep, index: Int, count: Int, target: Rect?, onNext: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current
    var bubble by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            // لمس‌ها به صفحهٔ زیرین نمی‌رسد
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        val screenH = with(density) { maxHeight.toPx() }
        val screenW = with(density) { maxWidth.toPx() }
        val visible = target != null && target.bottom > 0f && target.top < screenH && target.width > 0f
        // پیام پایین صفحه، مگر اینکه بخش مورد نظر خودش پایین باشد
        val bubbleOnTop = visible && target!!.center.y > screenH * 0.55f
        val pad = with(density) { 8.dp.toPx() }

        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color(0xC7050709))
            if (visible) {
                val r = target!!
                val hole = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                val radius = CornerRadius(minOf(28.dp.toPx(), hole.height / 2), minOf(28.dp.toPx(), hole.height / 2))
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(Color(0xFF9AB0FF), hole.topLeft, hole.size, radius, style = Stroke(2.dp.toPx()))
                // پیکان خط‌چین از پیام به بخش
                bubble?.let { b ->
                    val start = if (bubbleOnTop) Offset(b.center.x, b.bottom + 6.dp.toPx()) else Offset(b.center.x, b.top - 6.dp.toPx())
                    val end = if (bubbleOnTop) Offset(hole.center.x, hole.top - 6.dp.toPx()) else Offset(hole.center.x, hole.bottom + 6.dp.toPx())
                    if (kotlin.math.abs(end.y - start.y) > 24.dp.toPx()) {
                        val mid = (start.y + end.y) / 2
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(start.x, mid, end.x, mid, end.x, end.y)
                        }
                        val blue = Color(0xFF9AB0FF)
                        drawPath(
                            path, blue,
                            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
                        )
                        val dir = if (bubbleOnTop) 1f else -1f
                        val head = Path().apply {
                            moveTo(end.x - 9.dp.toPx(), end.y - dir * 10.dp.toPx())
                            lineTo(end.x, end.y)
                            lineTo(end.x + 9.dp.toPx(), end.y - dir * 10.dp.toPx())
                        }
                        drawPath(head, blue, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }

        Column(
            Modifier
                .align(if (bubbleOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (bubbleOnTop) Modifier.statusBarsPadding().padding(top = 16.dp) else Modifier.navigationBarsPadding().padding(bottom = 28.dp))
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .onGloballyPositioned { bubble = it.boundsInRoot() },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // لوگو سمت راست؛ گوشهٔ تیز پیام رو به لوگو
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painterResource(com.example.R.drawable.logo_taraz_app), contentDescription = "راهنمای تراز",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                )
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .semantics { contentDescription = "${step.title}. ${step.body}" },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // دو متن جدا تا جای «·» و عددها در راست‌به‌چپ به هم نریزد
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("راهنمای تراز", style = lt(12.sp, FontWeight.SemiBold, Color(0xFF5D6573)), modifier = Modifier.weight(1f))
                        Text("گام ${fa(index + 1)} از ${fa(count)}", style = lt(12.sp, FontWeight.SemiBold, Color(0xFF2250F4)))
                    }
                    Text(step.title, style = lt(16.sp, FontWeight.Bold, Color(0xFF0E1116)))
                    Text(step.body, style = lt(14.sp, color = Color(0xFF2B313B), lineHeight = 25.sp))
                }
            }
            Row(Modifier.padding(start = 58.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3D66FF)).clickable(onClick = onNext),
                    contentAlignment = Alignment.Center
                ) { Text(if (index < count - 1) "ادامه" else "پایان راهنما", style = lt(15.sp, FontWeight.Bold, Color.White)) }
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF22262F)).clickable(onClick = onClose).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("بستن راهنما", style = lt(14.sp, FontWeight.SemiBold, Color(0xFFC9D0DC))) }
            }
        }
    }
}

/** متن راهنمای هر صفحه */
object Tours {
    val home = listOf(
        TourStep(null, "به تراز خوش آمدید", "در چند گام کوتاه، بخش‌های صفحهٔ خانه معرفی می‌شود. هر زمان بخواهید می‌توانید راهنما را ببندید."),
        TourStep("home.card", "کارت موجودی", "موجودی همهٔ حساب‌ها، مبالغ بستانکار و بدهکار و جمع کل در این کارت نمایش داده می‌شود. با لمس کارت، فهرست حساب‌ها باز می‌شود."),
        TourStep("home.search", "جستجو", "تراکنش‌ها، اشخاص، قبوض، چک‌ها و ابزارها را از این بخش جستجو کنید."),
        TourStep("home.settings", "تنظیمات", "ظاهر برنامه، قفل اثر انگشت، اعلان‌ها، پشتیبان‌گیری و دسته‌بندی‌ها در تنظیمات قرار دارد."),
        TourStep("home.glance", "خلاصهٔ وضعیت", "هزینه‌های ماه جاری و مانده‌حساب بدهکار و بستانکار در این بخش خلاصه شده است. با لمس هر ردیف، جزئیات آن باز می‌شود."),
        TourStep("nav.add", "ثبت تراکنش", "برای ثبت هزینه، درآمد یا انتقال وجه بین حساب‌ها، این دکمه را بزنید."),
        TourStep("nav.bar", "منوی اصلی", "از این منو به صفحه‌های خانه، تراکنش‌ها و تعهدات (اقساط، اشخاص و چک‌ها) دسترسی دارید. بخش‌های صفحهٔ خانه نیز از «چیدمان خانه» در پایین صفحه قابل تنظیم است.")
    )
    val transactions = listOf(
        TourStep("tx.search", "جستجو در تراکنش‌ها", "عنوان، دسته، حساب یا مبلغ تراکنش را وارد کنید تا نتایج نمایش داده شود."),
        TourStep("tx.filter", "فیلتر", "تراکنش‌ها را بر اساس نوع، بازهٔ تاریخ، دسته، حساب، مبلغ و برچسب محدود کنید؛ جمع هزینه و درآمد نتایج و خروجی اکسل همان‌جا در دسترس است."),
        TourStep("tx.recurring", "تراکنش‌های تکراری و قبوض", "اجاره، حقوق و اشتراک‌ها را یک بار ثبت کنید تا هر ماه به‌طور خودکار ثبت شوند. قبوض نیز با یادآوری موعد در بخش بعدی قرار دارد."),
        TourStep("tx.week", "نوار هفته", "هزینهٔ هر روز هفته نمایش داده می‌شود. با لمس هر روز، تراکنش‌های همان روز فهرست می‌شود و با لمس هر تراکنش، امکان ویرایش یا حذف آن وجود دارد.")
    )
    val obligations = listOf(
        TourStep("obl.summary", "بدهکار و بستانکار", "جمع بدهی‌ها (وام، اشخاص، چک‌های پرداختی و کارت اعتباری) و جمع مطالبات شما در این دو کادر نمایش داده می‌شود."),
        TourStep("obl.tabs", "اقساط، اشخاص و چک‌ها", "برای مشاهدهٔ وام‌ها و خریدهای اقساطی، حساب اشخاص یا چک‌ها، زبانهٔ مربوط را انتخاب کنید."),
        TourStep(null, "ثبت مورد جدید", "وام، قسط، حساب شخص یا چک جدید را از این بخش ثبت کنید. یادآوری سررسیدها به‌طور خودکار ارسال می‌شود.")
    )
    val reports = listOf(
        TourStep("rep.month", "انتخاب ماه", "با پیکان‌ها، گزارش ماه‌های قبل را مشاهده کنید."),
        TourStep("rep.saved", "پس‌انداز ماه", "تفاوت درآمد و هزینهٔ ماه و درصد پس‌انداز از درآمد در این کارت نمایش داده می‌شود."),
        TourStep(null, "هزینه‌ها به تفکیک دسته", "در ادامهٔ صفحه، سهم هر دسته از هزینه‌ها، مقایسه با ماه قبل، جریان وجوه و پیشرفت هدف پس‌انداز آمده است.")
    )
    val accounts = listOf(
        TourStep("acc.total", "موجودی کل", "جمع موجودی همهٔ حساب‌ها در این بخش نمایش داده می‌شود. با دکمهٔ چشم در بالای صفحه، مبالغ پنهان می‌شوند."),
        TourStep("acc.assets", "دارایی‌ها", "ارز، طلا، سکه، رمزارز و واحدهای صندوق خود را با قیمت روز در این بخش ثبت و پیگیری کنید."),
        TourStep("acc.actions", "افزودن حساب", "کارت بانکی، کارت اعتباری یا صندوق وجه نقد جدید اضافه کنید. با لمس هر حساب، تراکنش‌ها، ویرایش، انتقال یا بازپرداخت آن در دسترس است.")
    )
    val add = listOf(
        TourStep("add.type", "نوع تراکنش", "هزینه، درآمد یا انتقال بین حساب‌ها را انتخاب کنید."),
        TourStep("add.amount", "مبلغ", "مبلغ را با صفحه‌کلید پایین به ${com.example.data.Money.label} وارد کنید؛ معادل حروفی آن زیر عدد نمایش داده می‌شود."),
        TourStep("add.cats", "دسته", "دستهٔ تراکنش را انتخاب کنید. در صورت نبود دستهٔ مورد نظر، «دستهٔ جدید» را بزنید."),
        TourStep("add.save", "ثبت", "حساب، تاریخ، یادداشت و برچسب را بررسی کنید و سپس تراکنش را ثبت کنید.")
    )
}
