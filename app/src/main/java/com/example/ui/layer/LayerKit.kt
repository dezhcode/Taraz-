package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
 * طرح نهایی «تراز» — توکن‌های رنگ در دو حالت روشن و تیره.
 * هر توکن یک getter است که به [LayerTheme.dark] نگاه می‌کند؛ با عوض شدن حالت،
 * همهٔ صفحه‌ها خودشان دوباره رسم می‌شوند.
 */
object LayerTheme {
    /** حالت تیره روشن است؟ (state کامپوز؛ خواندنش در کامپوزیشن ردگیری می‌شود) */
    var dark by androidx.compose.runtime.mutableStateOf(false)
}

object L {
    val Bg: Color get() = if (LayerTheme.dark) Color(0xFF0B0E13) else Color(0xFFDDE1E8)
    val Surface: Color get() = if (LayerTheme.dark) Color(0xFF161A22) else Color(0xFFFFFFFF)
    val Ink: Color get() = if (LayerTheme.dark) Color(0xFFECEFF4) else Color(0xFF0E1116)
    val Muted: Color get() = if (LayerTheme.dark) Color(0xFF9AA3B2) else Color(0xFF5D6573)
    val Faint: Color get() = if (LayerTheme.dark) Color(0xFF838C9C) else Color(0xFF6E7684)
    val Line: Color get() = if (LayerTheme.dark) Color(0xFF252B36) else Color(0xFFECEEF2)
    val Rail: Color get() = if (LayerTheme.dark) Color(0xFF323A48) else Color(0xFFD3D9E2)
    val Chip: Color get() = if (LayerTheme.dark) Color(0xFF1F2430) else Color(0xFFF1F3F6)
    val ChipPressed: Color get() = if (LayerTheme.dark) Color(0xFF262C38) else Color(0xFFEEF1F5)
    val Primary: Color get() = if (LayerTheme.dark) Color(0xFF3D66FF) else Color(0xFF2250F4)
    val PrimaryText: Color get() = if (LayerTheme.dark) Color(0xFF9AB0FF) else Color(0xFF1D44D8)
    val PrimarySoft: Color get() = if (LayerTheme.dark) Color(0xFF1C2546) else Color(0xFFEEF2FF)
    val PrimaryTint: Color get() = if (LayerTheme.dark) Color(0xFF18203A) else Color(0xFFF1F4FF)
    val NavSelected: Color get() = if (LayerTheme.dark) Color(0xFF22305E) else Color(0xFFE8EDFF)
    val OnPrimarySoft: Color get() = if (LayerTheme.dark) Color(0xFFDCE4FF) else Color(0xFFDCE4FF)
    val PrimaryDisabled: Color get() = if (LayerTheme.dark) Color(0xFF2B3766) else Color(0xFFAEBDF7)
    val BarIdle: Color get() = if (LayerTheme.dark) Color(0xFF3A4672) else Color(0xFFBCC8F2)
    val Amber: Color get() = if (LayerTheme.dark) Color(0xFFF5A524) else Color(0xFFF59E0B)
    val AmberRing: Color get() = if (LayerTheme.dark) Color(0xFFF5B14C) else Color(0xFFF5B14C)
    val AmberText: Color get() = if (LayerTheme.dark) Color(0xFFF7B955) else Color(0xFFB45309)
    val AmberSoft: Color get() = if (LayerTheme.dark) Color(0xFF33270F) else Color(0xFFFEF3E2)
    val AmberInk: Color get() = if (LayerTheme.dark) Color(0xFFF7C77A) else Color(0xFF8A4300)
    val AmberHalo: Color get() = if (LayerTheme.dark) Color(0xFF4A3712) else Color(0xFFFCE3BC)
    val Green: Color get() = if (LayerTheme.dark) Color(0xFF2BB283) else Color(0xFF0E8A5F)
    val GreenText: Color get() = if (LayerTheme.dark) Color(0xFF5BD3A6) else Color(0xFF0B6E4B)
    val GreenSoft: Color get() = if (LayerTheme.dark) Color(0xFF10291F) else Color(0xFFE3F5EC)
    val GreenInk: Color get() = if (LayerTheme.dark) Color(0xFF7FE0BB) else Color(0xFF0B5E40)
    val GreenInk2: Color get() = if (LayerTheme.dark) Color(0xFF9BDDC3) else Color(0xFF1F5C45)
    val Red: Color get() = if (LayerTheme.dark) Color(0xFFF06368) else Color(0xFFE5484D)
    val RedText: Color get() = if (LayerTheme.dark) Color(0xFFFF8085) else Color(0xFFC22B33)
    val RedSoft: Color get() = if (LayerTheme.dark) Color(0xFF3A1A1C) else Color(0xFFFFF1F1)
    val RedInk: Color get() = if (LayerTheme.dark) Color(0xFFFFB3B6) else Color(0xFF7A1D22)
    val Info: Color get() = if (LayerTheme.dark) Color(0xFFC9D4FF) else Color(0xFF1E2B5C)
    val Scrim: Color get() = if (LayerTheme.dark) Color(0xFF000000) else Color(0xFF8E939B)
    val Placeholder: Color get() = if (LayerTheme.dark) Color(0xFF5B6372) else Color(0xFFC1C6CF)
    val Track: Color get() = if (LayerTheme.dark) Color(0xFF0F1218) else Color(0xFFE9ECF1)
    val Handle: Color get() = if (LayerTheme.dark) Color(0xFF3A414E) else Color(0xFFD5D9E0)
    val Chevron: Color get() = if (LayerTheme.dark) Color(0xFF6E7787) else Color(0xFF8E95A2)
    val Disabled: Color get() = if (LayerTheme.dark) Color(0xFF262B35) else Color(0xFFE1E4EA)
    val SwitchOff: Color get() = if (LayerTheme.dark) Color(0xFF3A414E) else Color(0xFFC9CED6)
    val BarSoft: Color get() = if (LayerTheme.dark) Color(0xFF2E3A63) else Color(0xFFC6CEE6)
    val Halo: Color get() = if (LayerTheme.dark) Color(0xFF2A3A72) else Color(0xFFD3DCFF)
    val CardShadow: Color get() = if (LayerTheme.dark) Color(0x00000000) else Color(0x0A0E1116)

    /** رنگ کارت‌ها در کیف پول، به ترتیب */
    val CardColors = listOf(
        Color(0xFF2250F4), Color(0xFFB4232F), Color(0xFF0F3B66),
        Color(0xFF0E8A5F), Color(0xFF7A5AA8), Color(0xFF5C6B73)
    )
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

/**
 * پیش‌تر «۴۲٫۰م» بود و کاربر را گیج می‌کرد؛ حالا همیشه عدد کامل: ۴۲٬۰۰۰٬۰۰۰
 * (برای جاهای تنگ، کنارش [FitText] بگذارید تا خودش کوچک شود).
 */
fun millions(value: Long): String = money(value)

/** عدد کامل با واحد: ۴۲٬۰۰۰٬۰۰۰ تومان */
fun toman(value: Long): String = money(value) + " تومان"

/**
 * متن یک‌خطی که اگر جا نشود، اندازه‌اش را کم‌کم کوچک می‌کند تا کامل دیده شود
 * (برای مبلغ‌های کامل در کاشی‌های باریک).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 10.sp,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    var scale by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var ready by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableStateOf(false) }
    val size = (style.fontSize.value * scale).sp
    val lh = if (style.lineHeight.isSp) (style.lineHeight.value * scale).sp else style.lineHeight
    val ls = if (style.letterSpacing.isSp) (style.letterSpacing.value * scale).sp else style.letterSpacing
    androidx.compose.material3.Text(
        text,
        style = style.copy(fontSize = size, lineHeight = lh, letterSpacing = ls),
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value > minSize.value) scale *= 0.92f else ready = true
        }
    )
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
