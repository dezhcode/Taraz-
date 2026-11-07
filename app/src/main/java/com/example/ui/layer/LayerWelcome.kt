package com.example.ui.layer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BankCard
import com.example.data.Person
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.launch

/** رنگ برند تراز (همان آبی لوگو) */
private val Brand = Color(0xFF2250F4)

/** علامت سفید تراز (بدون کاشی) */
@Composable
private fun TarazMark(width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.logo_taraz_mark), contentDescription = null,
        colorFilter = ColorFilter.tint(Color.White),
        modifier = modifier.requiredWidth(width).height(width * 543f / 802f)
    )
}

// ─────────────────────────── اسپلش ───────────────────────────

/**
 * اسپلش طرح «صفحه خودِ لوگو»: کل صفحه آبی و علامت سفید تراز روی لبهٔ پایین صفحه نشسته،
 * درست مثل کاشی لوگو. بعد از اسپلش سیستمی اندروید (همان آبی با علامت وسط) نمایش داده می‌شود.
 */
@Composable
fun TarazSplash(animate: Boolean = true) {
    val rise = remember { Animatable(if (animate) 1f else 0f) }
    val fade = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            launch { rise.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
            fade.animateTo(1f, tween(500, delayMillis = 150))
        }
    }
    val dots = rememberInfiniteTransition(label = "dots")
    val phase by dots.animateFloat(0f, 3f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "phase")

    Box(
        Modifier.fillMaxSize().background(Brand)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .semantics { contentDescription = "تراز، دفتر مالی شخصی" }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 170.dp).alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("تراز", style = lt(64.sp, FontWeight.Bold, Color.White, lineHeight = 76.sp))
            Text("دفتر مالی شخصی", style = lt(15.sp, color = Color(0xFFDCE4FF)))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 3) {
                    val on = phase.toInt() == i
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = if (on) 1f else 0.35f)))
                }
            }
        }
        // علامت روی لبهٔ پایین؛ از پایین بالا می‌آید
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { translationY = rise.value * size.height }
        ) {
            BoxWithMark()
        }
    }
}

@Composable
private fun BoxWithMark() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 23.dp), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            TarazMark(maxWidth)
        }
    }
}

// ─────────────────────────── صفحهٔ شروع ───────────────────────────

/** صفحهٔ شروع: سربرگ آبی با علامت تراز روی لبهٔ پایینش، و زیرش نام و دکمهٔ «شروع» */
@Composable
fun WelcomeStart(name: String, onNameChange: (String) -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(L.Bg).imePadding()) {
        Box(
            Modifier.fillMaxWidth().height(380.dp)
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brand)
        ) {
            Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp)) {
                Text("به", style = lt(15.sp, color = Color(0xFFDCE4FF)))
                Text("تراز خوش آمدید", style = lt(36.sp, FontWeight.Bold, Color.White, lineHeight = 50.sp))
            }
            // جای علامت مثل طرح: کمی دورتر از لبهٔ چپ، روی لبهٔ پایین سربرگ
            TarazMark(250.dp, Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomLeft).absoluteOffset(x = 70.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "حساب‌ها، اقساط، چک‌ها و هزینه‌های روزانهٔ خود را یک‌جا مدیریت کنید.",
                style = lt(15.sp, color = L.Muted, lineHeight = 27.sp)
            )
            Text("نام شما", style = lt(13.sp, FontWeight.SemiBold), modifier = Modifier.padding(top = 4.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(L.Surface)
                    .border(1.dp, L.Rail, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (name.isEmpty()) Text("مثلاً علی رضایی", style = lt(15.sp, color = L.Placeholder))
                BasicTextField(
                    value = name, onValueChange = { onNameChange(it.take(40)) }, singleLine = true,
                    textStyle = lt(15.sp), cursorBrush = SolidColor(L.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onStart() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "نام شما" }
                )
            }
            Text("اطلاعات شما فقط روی همین دستگاه ذخیره می‌شود.", style = lt(12.sp, color = L.Muted))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            BigButton("شروع", height = 56.dp, onClick = onStart)
        }
    }
}

// ─────────────────────────── معرفی برنامه ───────────────────────────

/** نمونهٔ داده برای پیش‌نمایش اجزای واقعی برنامه در صفحه‌های معرفی */
private object Samples {
    private val now get() = System.currentTimeMillis()
    val cards = listOf(
        BankCard(id = 1, bankName = "ملت", cardNumber = "6104337412345678", balance = 3_200_000),
        BankCard(id = 2, bankName = "سامان", cardNumber = "6219861012345678", balance = 1_450_000),
        BankCard(id = 3, bankName = "وجه نقد", balance = 527_412, accountType = "CASH")
    )
    val transactions: List<Transaction>
        get() {
            val day = DAY_MS
            val t = startOfDay(now) + 12 * 3_600_000L
            return listOf(
                Transaction(id = 1, title = "ناهار", amount = 420_000, category = "غذا", date = t, isExpense = true, bankName = "ملت"),
                Transaction(id = 2, title = "خرید", amount = 1_850_000, category = "خرید", date = t - day, isExpense = true, bankName = "ملت"),
                Transaction(id = 3, title = "تاکسی", amount = 260_000, category = "حمل‌ونقل", date = t - 2 * day, isExpense = true, bankName = "سامان"),
                Transaction(id = 4, title = "قبض برق", amount = 385_000, category = "قبوض", date = t - 3 * day, isExpense = true, bankName = "ملت"),
                Transaction(id = 5, title = "رستوران", amount = 2_900_000, category = "غذا", date = t - 4 * day, isExpense = true, bankName = "سامان"),
                Transaction(id = 6, title = "داروخانه", amount = 640_000, category = "سلامت", date = t - 5 * day, isExpense = true, bankName = "ملت"),
                Transaction(id = 7, title = "حقوق", amount = 13_900_000, category = "حقوق", date = t - 6 * day, isExpense = false, bankName = "ملت", type = TransactionType.INCOME)
            )
        }
    val persons = listOf(
        Person(id = 1, name = "علی رضایی", relation = "دوست"),
        Person(id = 2, name = "مریم احمدی", relation = "همکار"),
        Person(id = 3, name = "رضا کریمی", relation = "بستگان")
    )
    val balances = mapOf(1 to 2_000_000L, 2 to -800_000L, 3 to 0L)
    val upcoming: List<UpcomingItem>
        get() = listOf(
            UpcomingItem("قسط", "وام خودرو · ملت", 4_200_000, now + 2 * DAY_MS, "پرداخت", 0, primary = true) {},
            UpcomingItem("قبض", "قبض برق", 385_000, now + 3 * DAY_MS, "ثبت پرداخت", 1) {},
            UpcomingItem("پرداخت چک", "به علی رضایی", 20_000_000, now + 6 * DAY_MS, "وصول شد؟", 2) {}
        )
}

private data class IntroPage(val title: String, val body: String, val preview: @Composable () -> Unit)

/**
 * صفحه‌های معرفی برنامه؛ پیش‌نمایش هر صفحه از اجزای واقعی خود برنامه با دادهٔ نمونه ساخته شده،
 * پس کاربر همان چیزی را می‌بیند که بعداً در برنامه دارد.
 */
@Composable
fun WelcomeIntro(onFinish: () -> Unit, initialPage: Int = 0) {
    val pages = remember {
        listOf(
            IntroPage(
                "همهٔ حساب‌ها در یک نگاه",
                "موجودی کارت‌ها و وجه نقد، مبالغ بستانکار و بدهکار، جمع کل و سهم هزینه از دریافتی ماه، همه در کارت موجودی صفحهٔ خانه."
            ) {
                val s = Samples
                WalletStack(
                    cards = s.cards, totalBalance = s.cards.sumOf { it.balance }, monthlyIncome = 13_900_000, monthlyExpense = 11_240_000,
                    owed = 2_000_000, owe = 800_000, onOpenAccounts = {}, onOpenObligations = {},
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            },
            IntroPage(
                "هزینه‌های روزانه زیر نظر",
                "هزینهٔ هر روز هفته و مقایسه با سقف روزانه؛ با نزدیک شدن به سقف، تراز اطلاع می‌دهد."
            ) {
                TodayCard(
                    transactions = Samples.transactions, todayStart = startOfDay(System.currentTimeMillis()),
                    todayExpense = 420_000, dailyBudget = 2_000_000, onOpenDay = {}, onOpenReports = {}, onSetBudget = {},
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            },
            IntroPage(
                "سررسیدها پیش از موعد",
                "اقساط وام، قبوض و چک‌های هفتهٔ آینده یک‌جا نمایش داده می‌شود و پیش از هر سررسید یادآوری دریافت می‌کنید."
            ) {
                Column { UpcomingSection(Samples.upcoming, System.currentTimeMillis()) }
            },
            IntroPage(
                "حساب اشخاص",
                "برای هر شخص حساب جداگانه با مانده‌حساب بستانکار یا بدهکار؛ همراه با تقسیم هزینه‌های گروهی و یادآوری موعد."
            ) {
                Column { PeopleSection(Samples.persons, Samples.balances, {}, {}, {}, {}) }
            },
            IntroPage(
                "گزارش و جریان وجوه",
                "درآمد ماه، محل مصرف آن به تفکیک دسته و مبلغ پس‌انداز؛ همراه با گزارش سالانه و ارسال گزارش به‌صورت تصویر یا PDF."
            ) {
                MoneyFlowCard(
                    income = 13_900_000, expense = 11_240_000,
                    byCategory = listOf("غذا" to 4_200_000L, "خرید" to 3_100_000L, "قبوض" to 1_640_000L, "حمل‌ونقل" to 1_300_000L, "سلامت" to 1_000_000L)
                )
            }
        )
    }
    val pager = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(L.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_taraz_app), null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
            Spacer(Modifier.width(10.dp))
            Text("تراز", style = lt(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onFinish).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("رد کردن", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText)) }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize()) {
                // پیش‌نمایش: اجزای واقعی برنامه، بدون امکان لمس، با محو شدن لبهٔ پایین
                Box(
                    Modifier.padding(top = 18.dp).fillMaxWidth().weight(1f)
                        .clipToBounds()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).padding(top = 4.dp)) { p.preview() }
                    Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {})
                }
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.title, style = lt(24.sp, FontWeight.Bold, lineHeight = 34.sp))
                    Text(p.body, style = lt(15.sp, color = L.Muted, lineHeight = 27.sp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
            for (i in pages.indices) {
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(8.dp).width(if (on) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(if (on) L.Primary else L.Rail)
                )
            }
        }
        Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(if (last) "شروع" else "بعدی", Modifier.weight(1f), height = 56.dp) {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
            if (pager.currentPage > 0) {
                SoftBigButton("قبلی", Modifier.width(96.dp), height = 56.dp) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
            }
        }
    }
}
