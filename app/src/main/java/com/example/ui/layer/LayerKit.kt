package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.ui.CategoryIcons
import com.example.ui.theme.IranYekanFontFamily
import com.example.utils.JalaliDate
import java.util.Calendar
import java.util.Locale

/**
 * طرح «لایه + روزشمار» — توکن‌های رنگ و کمک‌تابع‌های مشترک.
 * رنگ‌ها دقیقاً از بوم طراحی برداشته شده‌اند.
 */
object L {
    val Bg = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0E1116)
    val Muted = Color(0xFF5D6573)
    val Faint = Color(0xFF6E7684)
    val Line = Color(0xFFECEEF2)
    val Rail = Color(0xFFDDE2EA)
    val Chip = Color(0xFFF1F3F6)
    val ChipPressed = Color(0xFFEEF1F5)

    val Primary = Color(0xFF2250F4)
    val PrimaryText = Color(0xFF1D44D8)
    val PrimarySoft = Color(0xFFEEF2FF)
    val PrimaryTint = Color(0xFFF1F4FF)
    val NavSelected = Color(0xFFE8EDFF)
    val OnPrimarySoft = Color(0xFFDCE4FF)
    val PrimaryDisabled = Color(0xFFAEBDF7)
    val BarIdle = Color(0xFFBCC8F2)

    val Amber = Color(0xFFF59E0B)
    val AmberRing = Color(0xFFF5B14C)
    val AmberText = Color(0xFFB45309)
    val AmberSoft = Color(0xFFFEF3E2)
    val Green = Color(0xFF0E8A5F)
    val GreenText = Color(0xFF0B6E4B)
    val GreenSoft = Color(0xFFE3F5EC)
    val Red = Color(0xFFE5484D)
    val RedText = Color(0xFFC22B33)
    val Info = Color(0xFF1E2B5C)
    val Scrim = Color(0xFF8E939B)
    val Placeholder = Color(0xFFC1C6CF)

    /** رنگ کارت‌ها در کیف پول، به ترتیب */
    val CardColors = listOf(
        Color(0xFF2250F4), Color(0xFFB4232F), Color(0xFF0F3B66),
        Color(0xFF0E8A5F), Color(0xFF7A5AA8), Color(0xFF5C6B73)
    )

    val CardShadow = Color(0x0A0E1116)
}

/** متن با فونت ایران‌یکان */
fun lt(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = L.Ink,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) = TextStyle(
    fontFamily = IranYekanFontFamily,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight
)

fun fa(value: Any): String = JalaliDate.toPersianDigits(value)

/** ۱۲۳٬۴۵۶ */
fun money(value: Long): String =
    fa(String.format(Locale.ENGLISH, "%,d", value).replace(',', '٬'))

/** ۱۲۳٬۴۵۶ با علامت: −۱۲۳٬۴۵۶ / +۱۲۳٬۴۵۶ */
fun signedMoney(value: Long, isExpense: Boolean): String =
    (if (isExpense) "−" else "+") + money(value)

/** ۴۲٫۰م */
fun millions(value: Long): String {
    val m = value / 1_000_000.0
    return fa(String.format(Locale.ENGLISH, "%.1f", m).replace('.', '٫')) + "م"
}

fun timeOfDay(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return fa(String.format(Locale.ENGLISH, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
}

private val WEEKDAY_NAMES = mapOf(
    Calendar.SATURDAY to "شنبه", Calendar.SUNDAY to "یکشنبه", Calendar.MONDAY to "دوشنبه",
    Calendar.TUESDAY to "سه‌شنبه", Calendar.WEDNESDAY to "چهارشنبه", Calendar.THURSDAY to "پنجشنبه",
    Calendar.FRIDAY to "جمعه"
)
private val WEEKDAY_SHORT = mapOf(
    Calendar.SATURDAY to "ش", Calendar.SUNDAY to "ی", Calendar.MONDAY to "د",
    Calendar.TUESDAY to "س", Calendar.WEDNESDAY to "چ", Calendar.THURSDAY to "پ",
    Calendar.FRIDAY to "ج"
)

fun weekdayName(ts: Long): String =
    WEEKDAY_NAMES[Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)].orEmpty()

fun weekdayShort(ts: Long): String =
    WEEKDAY_SHORT[Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)].orEmpty()

/** ستون روز در تقویم شنبه‌اول: شنبه=۰ … جمعه=۶ */
fun saturdayFirstIndex(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK) % 7

const val DAY_MS = 24L * 60 * 60 * 1000

/** شروع روز (نیمه‌شب) برای [ts] */
fun startOfDay(ts: Long): Long = JalaliDate.dayRange(ts).first

/** آیکن دسته: اول از جدول دسته‌های کاربر، بعد از روی نام */
fun categoryIcon(name: String, categories: List<Category>): ImageVector {
    val key = categories.firstOrNull { it.name == name }?.iconKey ?: when {
        name.contains("خوراک") || name.contains("غذا") || name.contains("رستوران") || name.contains("کافه") -> "food"
        name.contains("حمل") || name.contains("تاکسی") || name.contains("رفت") || name.contains("سوخت") -> "transport"
        name.contains("قبض") || name.contains("قبوض") -> "bill"
        name.contains("خرید") -> "shopping"
        name.contains("سلامت") || name.contains("دارو") || name.contains("پزشک") -> "health"
        name.contains("حقوق") || name.contains("درآمد") -> "wage"
        name.contains("قسط") || name.contains("وام") -> "loan"
        name.contains("هدیه") -> "gift"
        name.contains("تفریح") -> "fun"
        else -> "other"
    }
    return CategoryIcons.vectorFor(key)
}

fun cardColor(index: Int): Color = L.CardColors[index.mod(L.CardColors.size)]

fun cardColorFor(bankName: String, cards: List<BankCard>): Color {
    val idx = cards.indexOfFirst { it.bankName == bankName }
    return if (idx >= 0) cardColor(idx) else L.Muted
}

/** «ملت ۴۲۱۸» */
fun shortAccountLabel(card: BankCard): String =
    if (card.cardNumber.length >= 4) "${card.bankName} ${fa(card.cardNumber.takeLast(4))}" else card.bankName

/** چاه آیکن گرد‌گوشه */
@Composable
fun IconWell(
    icon: ImageVector,
    size: Dp = 36.dp,
    bg: Color = L.Chip,
    fg: Color = L.Ink,
    iconSize: Dp = 18.dp,
    radius: Dp = 10.dp
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

val SoftCardShape = RoundedCornerShape(24.dp)
val ItemShape = RoundedCornerShape(18.dp)
val ButtonShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(999.dp)

/** ارتفاع منوی شناور + فاصله؛ پایین هر صفحه این‌قدر فضا خالی می‌ماند */
val NavClearance = 112.dp
