package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VazirmatnFontFamily
import com.example.utils.JalaliDate
import java.util.Calendar
import kotlin.math.abs

/**
 * The Taraz design language ("لایه + روزشمار"): white cards on a cool grey
 * ground, one cobalt accent, and buttons that always carry words.
 *
 * Buttons come in three fixed levels so the user can always tell what the main
 * action on a screen is: [PrimaryButton] (solid cobalt), [SecondaryButton]
 * (cobalt tint) and [TextAction] (plain cobalt text).
 */
object Tz {
    val Ground = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0E1116)
    val Muted = Color(0xFF5D6573)       // 5.9:1 on white
    val Faint = Color(0xFF6E7684)       // placeholders, disabled text
    val Hairline = Color(0xFFECEEF2)
    val Border = Color(0xFFE1E4EA)
    val Field = Color(0xFFF1F3F6)
    val Cobalt = Color(0xFF2250F4)
    val CobaltText = Color(0xFF1D44D8)
    val CobaltTint = Color(0xFFEEF2FF)
    val CobaltTint2 = Color(0xFFE8EDFF)
    val CobaltOnDark = Color(0xFFDCE4FF)
    val Green = Color(0xFF0E8A5F)
    val GreenText = Color(0xFF0B6E4B)
    val GreenTint = Color(0xFFE3F5EC)
    val Amber = Color(0xFFB45309)
    val AmberDot = Color(0xFFF59E0B)
    val AmberTint = Color(0xFFFEF3E2)
    val Red = Color(0xFFC22B33)
    val RedText = Color(0xFF7A1D22)
    val RedTint = Color(0xFFFFF1F1)
    val Disabled = Color(0xFFE1E4EA)
    val BarSoft = Color(0xFFBCC8F2)
    val BarGrey = Color(0xFFC6CEE6)
    val Rail = Color(0xFFDDE2EA)
    val Scrim = Color(0x8A0E1116)

    val CardShape = RoundedCornerShape(22.dp)
    val RowShape = RoundedCornerShape(18.dp)
    val ButtonShape = RoundedCornerShape(16.dp)
}

/** One text style factory so every screen uses the same font and weights. */
fun tzText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Tz.Ink,
    lineHeight: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified
) = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontSize = size,
    fontWeight = weight,
    color = color,
    lineHeight = lineHeight,
    textAlign = align,
    letterSpacing = letterSpacing
)

// ---------------------------------------------------------------- formatting

object TzFormat {
    private val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun fa(value: Any): String = buildString {
        for (ch in value.toString()) {
            if (ch in '0'..'9') append(digits[ch - '0']) else append(ch)
        }
    }

    /** 1234567 -> "۱٬۲۳۴٬۵۶۷" */
    fun money(value: Long): String {
        val plain = abs(value).toString()
        val grouped = plain.reversed().chunked(3).joinToString("٬").reversed()
        return fa(grouped)
    }

    fun signed(value: Long, isExpense: Boolean): String =
        (if (isExpense) "−" else "+") + money(value)

    /** 42_000_000 -> "۴۲٫۰ میلیون"; 860_000 -> "۸۶۰ هزار" */
    fun compact(value: Long): String {
        val a = abs(value)
        return when {
            a >= 1_000_000_000L -> fa(oneDecimal(a / 1_000_000_000.0)) + " میلیارد"
            a >= 1_000_000L -> fa(oneDecimal(a / 1_000_000.0)) + " میلیون"
            a >= 1_000L -> fa(a / 1_000L) + " هزار"
            else -> fa(a)
        }
    }

    /** 42_000_000 -> "۴۲٫۰م" for tight spots like chart labels. */
    fun short(value: Long): String {
        val a = abs(value)
        return when {
            a >= 1_000_000L -> fa(oneDecimal(a / 1_000_000.0)) + "م"
            a >= 1_000L -> fa(a / 1_000L) + "ه"
            else -> fa(a)
        }
    }

    private fun oneDecimal(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        val whole = r.toLong()
        val frac = Math.round((r - whole) * 10)
        return "$whole٫$frac"
    }

    fun time(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return fa("%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
    }

    private val weekdayNames = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
    private val weekdayLetters = listOf("ی", "د", "س", "چ", "پ", "ج", "ش")

    fun weekday(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return weekdayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun weekdayLetter(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return weekdayLetters[c.get(Calendar.DAY_OF_WEEK) - 1]
    }

    /** "امروز" / "دیروز" / "پنجشنبه ۲۱ آبان" */
    fun dayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = JalaliDate.daysUntil(timestamp, now)
        return when (diff) {
            0 -> "امروز"
            -1 -> "دیروز"
            1 -> "فردا"
            else -> weekday(timestamp) + " " + JalaliDate.fromTimestamp(timestamp).formatShort()
        }
    }

    fun digitsToLatin(input: String): String = buildString {
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> append(ch)
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                else -> Unit
            }
        }
    }
}

/** Brand-ish colour for an account so cards are recognisable at a glance. */
fun accountColor(name: String, isCash: Boolean = false): Color {
    if (isCash) return Color(0xFF0E8A5F)
    val n = name.replace("بانک", "").trim()
    return when {
        n.startsWith("ملت") -> Color(0xFFB4232F)
        n.startsWith("سامان") -> Color(0xFF0F3B66)
        n.startsWith("بلو") -> Color(0xFF1E6BFF)
        n.startsWith("ملی") -> Color(0xFF16466F)
        n.startsWith("صادرات") -> Color(0xFF1C3F94)
        n.startsWith("تجارت") -> Color(0xFF1F4E9C)
        n.startsWith("پاسارگاد") -> Color(0xFF8A6D1E)
        n.startsWith("پارسیان") -> Color(0xFF7A1F2B)
        n.startsWith("سپه") -> Color(0xFF13406B)
        n.startsWith("رسالت") -> Color(0xFF0E7C78)
        n.startsWith("مسکن") -> Color(0xFFB85C1E)
        n.startsWith("کشاورزی") -> Color(0xFF2E6B2F)
        n.startsWith("آینده") -> Color(0xFF6B3E8E)
        n.startsWith("رفاه") -> Color(0xFF2C5E8A)
        n.startsWith("شهر") -> Color(0xFFB0273A)
        n.startsWith("اقتصاد") -> Color(0xFF6A1B6E)
        else -> Color(0xFF5C6B73)
    }
}

// ---------------------------------------------------------------- components

@Composable
fun TzCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, Tz.CardShape, ambientColor = Color(0x140E1116), spotColor = Color(0x140E1116))
            .clip(Tz.CardShape)
            .background(Tz.Surface)
            .padding(padding),
        content = content
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Tz.ButtonShape)
            .background(if (enabled) Tz.Cobalt else Tz.Disabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Tz.Faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = tzText(16.sp, FontWeight.Bold, if (enabled) Color.White else Tz.Faint))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    fill: Boolean = true,
    icon: ImageVector? = null,
    fontSize: TextUnit = 15.sp
) {
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(RoundedCornerShape(if (height < 44.dp) 10.dp else 14.dp))
            .background(Tz.CobaltTint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Tz.CobaltText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = tzText(fontSize, FontWeight.Bold, Tz.CobaltText))
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tz.CobaltText,
    fontSize: TextUnit = 14.sp
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = tzText(fontSize, FontWeight.SemiBold, color))
    }
}

/** A switch drawn to the design (thumb moves toward the reading end when on). */
@Composable
fun TzSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (checked) Tz.Cobalt else Color(0xFFC9CED6))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { contentDescription = label }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Back button + title + optional subtitle, used by every pushed screen. */
@Composable
fun TzTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(1.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tz.Surface)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "بازگشت" },
                contentAlignment = Alignment.Center
            ) {
                // Auto-mirrored: in RTL the back arrow points right.
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Tz.Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = tzText(if (onBack == null) 26.sp else 20.sp, FontWeight.Bold))
            if (subtitle != null) {
                Text(subtitle, style = tzText(12.sp, color = Tz.Muted))
            }
        }
        trailing()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Text(title, style = tzText(17.sp, FontWeight.Bold))
        if (subtitle != null) Text(subtitle, style = tzText(12.sp, color = Tz.Muted))
    }
}

/** Small rounded label: "۲ روز دیگر", "آماده", … */
@Composable
fun TzTag(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = tzText(12.sp, FontWeight.Bold, color))
    }
}

/** Segmented control: a grey track with the chosen option raised in white. */
@Composable
fun <T> TzSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE9ECF1))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Tz.Surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = tzText(14.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Tz.Ink else Tz.Muted))
            }
        }
    }
}

/** Icon in a soft rounded square, used in rows and lists. */
@Composable
fun IconWell(icon: ImageVector, background: Color = Tz.Field, tint: Color = Tz.Ink, size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Thin progress bar. */
@Composable
fun TzProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Tz.Cobalt, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Tz.Hairline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** A 1dp divider line. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Tz.Hairline)
    )
}

/** Outline used for text fields. */
fun Modifier.tzField(focused: Boolean = false): Modifier = this
    .clip(RoundedCornerShape(14.dp))
    .background(Tz.Surface)
    .border(if (focused) 1.5.dp else 1.dp, if (focused) Tz.Cobalt else Tz.Border, RoundedCornerShape(14.dp))
