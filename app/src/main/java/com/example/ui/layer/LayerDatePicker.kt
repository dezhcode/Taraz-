package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.JalaliDate
import java.util.Calendar

/** ستون روز در تقویم شنبه‌اول: شنبه ۰ … جمعه ۶ */
private fun weekColumn(ts: Long): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK) // یکشنبه ۱ … شنبه ۷
    return dow % 7
}

/**
 * انتخاب تاریخ و ساعت شمسی: «امروز / دیروز»، تقویم ماه با ورق زدن، و ساعت.
 * روزهای آینده انتخاب نمی‌شوند.
 */
@Composable
fun JalaliDatePickerSheet(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val today = JalaliDate.today()
    val init = JalaliDate.fromTimestamp(initial)
    val initCal = Calendar.getInstance().apply { timeInMillis = initial }
    var viewKey by remember { mutableIntStateOf(init.monthKey()) }
    var selY by remember { mutableIntStateOf(init.year) }
    var selM by remember { mutableIntStateOf(init.month) }
    var selD by remember { mutableIntStateOf(init.day) }
    var hour by remember { mutableIntStateOf(initCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initCal.get(Calendar.MINUTE) / 5 * 5) }

    fun result(): Long = JalaliDate.toTimestamp(selY, selM, selD) + hour * 3_600_000L + minute * 60_000L
    val vy = viewKey / 100
    val vm = viewKey % 100
    val dim = JalaliDate.daysInMonth(vy, vm)
    val lead = weekColumn(JalaliDate.toTimestamp(vy, vm, 1))
    val canNext = viewKey < today.monthKey()
    val picked = JalaliDate(selY, selM, selD)

    LayerSheet(
        title = "تاریخ و ساعت",
        onDismiss = onDismiss,
        topGap = 70.dp,
        bottomBar = {
            BigButton("انتخاب · ${weekdayName(result())} ${picked.formatShort()}، ${fa("%02d:%02d".format(hour, minute))}") {
                onPick(result().coerceAtMost(now)); onDismiss()
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("امروز", picked == today) {
                selY = today.year; selM = today.month; selD = today.day; viewKey = today.monthKey()
            }
            val y = JalaliDate.fromTimestamp(now - DAY_MS)
            LChip("دیروز", picked == y) {
                selY = y.year; selM = y.month; selD = y.day; viewKey = y.monthKey()
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(L.Bg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface)
                        .clickable { viewKey = if (vm == 1) (vy - 1) * 100 + 12 else viewKey - 1 }
                        .semantics { contentDescription = "ماه قبل" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = L.Ink) }
                Text("${JalaliDate.MONTH_NAMES[vm - 1]} ${fa(vy)}", style = lt(15.sp, FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (canNext) L.Surface else L.Chip)
                        .clickable(enabled = canNext) { viewKey = JalaliDate.nextMonthKey(viewKey) }
                        .semantics { contentDescription = "ماه بعد" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (canNext) L.Ink else L.Faint) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                    Text(it, style = lt(11.sp, color = L.Muted), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val rows = (lead + dim + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..dim) {
                                val future = vy * 10000 + vm * 100 + day > today.year * 10000 + today.month * 100 + today.day
                                val on = vy == selY && vm == selM && day == selD
                                val isToday = vy == today.year && vm == today.month && day == today.day
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (on) L.Primary else Color.Transparent)
                                        .then(if (isToday && !on) Modifier.border(1.5.dp, L.Primary, CircleShape) else Modifier)
                                        .clickable(enabled = !future) { selY = vy; selM = vm; selD = day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        fa(day),
                                        style = lt(13.sp, if (on || isToday) FontWeight.Bold else FontWeight.Normal,
                                            when { on -> Color.White; future -> L.Faint; isToday -> L.Primary; else -> L.Ink })
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ساعت", style = lt(13.sp, color = L.Muted), modifier = Modifier.weight(1f))
            Stepper(fa("%02d".format(minute)), "دقیقه", onMinus = { minute = (minute + 55) % 60 }, onPlus = { minute = (minute + 5) % 60 })
            Text(" : ", style = lt(16.sp, FontWeight.Bold))
            Stepper(fa("%02d".format(hour)), "ساعت", onMinus = { hour = (hour + 23) % 24 }, onPlus = { hour = (hour + 1) % 24 })
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Stepper(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(L.Surface).clickable(onClick = onMinus).semantics { contentDescription = "کم کردن $label" },
            contentAlignment = Alignment.Center
        ) { Text("−", style = lt(16.sp, FontWeight.Bold)) }
        Text(value, style = lt(16.sp, FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(L.Surface).clickable(onClick = onPlus).semantics { contentDescription = "زیاد کردن $label" },
            contentAlignment = Alignment.Center
        ) { Text("+", style = lt(16.sp, FontWeight.Bold)) }
    }
}
