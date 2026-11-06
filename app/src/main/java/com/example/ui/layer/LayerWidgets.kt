package com.example.ui.layer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ابزارهای مشترک صفحه‌های فرعی طرح نهایی «تراز»:
 * هدر با دکمهٔ بازگشت، کلید روشن/خاموش، دکمهٔ بزرگ، فیلد ورودی و چیپ.
 */

/** هدر استاندارد: دکمهٔ بازگشت سفید + عنوان + زیرعنوان + محتوای انتهایی */
@Composable
fun LayerHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            HeaderButton("بازگشت", onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = L.Ink, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = lt(20.sp, FontWeight.Bold))
            if (subtitle != null) Text(subtitle, style = lt(12.sp, color = L.Muted))
        }
        trailing?.invoke()
    }
}

/**
 * قالب یک صفحهٔ تمام‌صفحه: پس‌زمینه، هدر، محتوای اسکرولی و نوار پایین ثابت (اختیاری).
 */
@Composable
fun LayerScreen(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(L.Bg).statusBarsPadding()
    ) {
        LayerHeader(title, subtitle, onBack, trailing)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .edgeGlass(top = 22.dp, bottom = if (bottomBar != null) 22.dp else 0.dp, solidTop = 0f, solidBottom = 0f, glassTop = 28.dp)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        if (bottomBar != null) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** کارت سفید با حاشیهٔ افقی ۲۰dp — واحد پایهٔ همهٔ صفحه‌ها */
@Composable
fun LayerCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.padding(horizontal = 20.dp).fillMaxWidth().cardSurface(radius).padding(padding),
        content = content
    )
}

/** عنوان بخش */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = lt(15.sp, FontWeight.Bold), modifier = modifier.padding(horizontal = 24.dp))
}

/** کلید روشن/خاموش ۴۸×۳۰ */
@Composable
fun LSwitch(checked: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(48.dp).height(30.dp)
            .clip(CircleShape)
            .background(if (checked && enabled) L.Primary else if (checked) L.PrimaryDisabled else L.SwitchOff)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(3.dp)
            .semantics {
                role = Role.Switch
                contentDescription = label
                stateDescription = if (checked) "روشن" else "خاموش"
            },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
    }
}

/** دکمهٔ بزرگ پایین صفحه */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = L.Primary,
    foreground: Color = Color.White,
    height: Dp = 54.dp,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) background else L.Disabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(16.sp, FontWeight.Bold, if (enabled) foreground else L.Faint))
    }
}

/** دکمهٔ ثانویهٔ ملایم (آبی کمرنگ) تمام‌عرض */
@Composable
fun SoftBigButton(text: String, modifier: Modifier = Modifier, height: Dp = 50.dp, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(L.PrimarySoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(15.sp, FontWeight.Bold, L.PrimaryText))
    }
}

/** فیلد متنی سفید با برچسب بالا */
@Composable
fun LField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    suffix: String? = null,
    singleLine: Boolean = true,
    hint: String? = null,
    /** برای شمارهٔ کارت و مانند آن: ارقام از چپ به راست نوشته شوند */
    ltr: Boolean = false
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = lt(13.sp, FontWeight.SemiBold))
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (singleLine) 52.dp else 96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(L.Surface)
                .border(1.dp, L.Rail, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
        ) {
            val dir = if (ltr) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.platform.LocalLayoutDirection.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides dir) {
                Box(Modifier.weight(1f).then(if (singleLine) Modifier else Modifier.padding(top = 14.dp))) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = lt(14.sp, color = L.Placeholder).let { if (ltr) it.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr) else it })
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        singleLine = singleLine,
                        textStyle = lt(15.sp).let {
                            if (ltr) it.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr, letterSpacing = 1.sp) else it
                        },
                        cursorBrush = SolidColor(L.Primary),
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                    )
                }
            }
            if (suffix != null) Text(suffix, style = lt(13.sp, color = L.Muted))
        }
        if (hint != null) Text(hint, style = lt(12.sp, color = L.Muted))
    }
}

/** چیپ قابل انتخاب (قرص) */
@Composable
fun LChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(if (selected) L.Primary else L.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, L.Rail, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(13.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) Color.White else L.Ink))
    }
}

/** نشان کوچک وضعیت */
@Composable
fun StatusPill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.height(22.dp).clip(CircleShape).background(bg).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(11.sp, FontWeight.Bold, fg))
    }
}

/** ردیف جدول‌مانند داخل کارت: عنوان + توضیح + مقدار/محتوای انتهایی */
@Composable
fun LRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = lt(14.sp, FontWeight.Medium))
                if (subtitle != null) Text(subtitle, style = lt(12.sp, color = L.Muted))
            }
            trailing?.invoke()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
    }
}

/** نوار پیشرفت گرد */
@Composable
fun LProgress(fraction: Float, color: Color = L.Primary, track: Color = L.Line, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

/** کادر هشدار/اطلاعات رنگی */
@Composable
fun Callout(
    title: String,
    body: String? = null,
    bg: Color,
    titleColor: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = lt(14.sp, FontWeight.Bold, titleColor))
            if (body != null) Text(body, style = lt(12.sp, color = titleColor, lineHeight = 20.sp))
        }
        trailing?.invoke()
    }
}

/** شروع/پایان ماه جلالی با جابه‌جایی [delta] ماه از [ts] */
fun shiftJalaliMonth(ts: Long, delta: Int): Long {
    val d = com.example.utils.JalaliDate.fromTimestamp(ts)
    var idx = d.year * 12 + (d.month - 1) + delta
    val y = Math.floorDiv(idx, 12)
    val m = Math.floorMod(idx, 12) + 1
    return com.example.utils.JalaliDate.toTimestamp(y, m, 1)
}

/** عدد از متن ورودی (ارقام فارسی/عربی هم قبول است)؛ ورودی نامعتبر = ۰ */
fun parseAmount(text: String): Long {
    val sb = StringBuilder()
    for (ch in text) {
        val p = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
        val a = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
        when {
            ch in '0'..'9' -> sb.append(ch)
            p >= 0 -> sb.append(('0' + p))
            a >= 0 -> sb.append(('0' + a))
        }
    }
    return sb.toString().take(15).toLongOrNull() ?: 0L
}

/** ۱۲٬۵۰۰٬۰۰۰ از متن ورودی؛ برای نمایش داخل فیلد */
fun groupedInput(text: String): String {
    val n = parseAmount(text)
    return if (n == 0L && text.none { it.isDigit() || it in "۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩" }) "" else money(n)
}

/**
 * شیت پایین‌آمدنی تمام‌عرض: پس‌زمینهٔ تیره، گوشه‌های گرد، دستگیره، عنوان و «انصراف».
 * ضربه روی پس‌زمینه بسته می‌کند.
 */
@Composable
fun LayerSheet(
    title: String,
    onDismiss: () -> Unit,
    topGap: Dp = 80.dp,
    closeLabel: String = "انصراف",
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color(0x990E1116))
            .androidx_tapOutside(onDismiss)
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = topGap)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(L.Surface)
                .androidx_tapOutside { }
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(40.dp).height(5.dp).clip(CircleShape).background(L.Handle))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = lt(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(closeLabel, style = lt(14.sp, FontWeight.SemiBold, L.Muted)) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            if (bottomBar != null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = bottomBar
                )
            }
        }
    }
}

/** ضربه روی خود این ناحیه (نه فرزندان قابل‌کلیک) */
fun Modifier.androidx_tapOutside(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onTap() } }

/** سه‌گزینه‌ای قرصی (مثل «کارت / نقد / دیگر») */
@Composable
fun LSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(L.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) L.Surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = lt(14.sp, if (on) FontWeight.Bold else FontWeight.Normal, if (on) L.Ink else L.Muted))
            }
        }
    }
}

/** لایهٔ «ردیف با دکمهٔ تغییر» */
@Composable
fun ChangeRow(label: String, value: @Composable () -> Unit, actionLabel: String = "تغییر", divider: Boolean = true, onAction: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = lt(13.sp, color = L.Muted), modifier = Modifier.width(56.dp))
            Box(Modifier.weight(1f)) { value() }
            if (onAction != null) {
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(L.PrimarySoft).clickable(onClick = onAction).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(actionLabel, style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
    }
}
