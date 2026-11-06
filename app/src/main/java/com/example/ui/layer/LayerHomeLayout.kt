package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun <T> List<T>.moved(i: Int, by: Int): List<T> {
    val j = i + by
    if (j !in indices) return this
    val m = toMutableList()
    val x = m.removeAt(i)
    m.add(j, x)
    return m
}

@Composable
private fun SmallIconButton(icon: ImageVector, label: String, bg: Color = L.Chip, tint: Color = L.Ink, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) bg else L.Chip.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (enabled) tint else L.Faint, modifier = Modifier.size(20.dp)) }
}

/**
 * چیدمان خانه: بخش‌ها را روشن/خاموش و جابه‌جا کنید، ابزارهای سنجاق‌شده را مرتب کنید یا بردارید.
 * کارت موجودی همیشه بالای صفحه می‌ماند.
 */
@Composable
fun HomeLayoutSheet(
    layout: List<Pair<String, Boolean>>,
    tools: List<String>,
    onLayout: (List<Pair<String, Boolean>>) -> Unit,
    onTools: (List<String>) -> Unit,
    onAddTool: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    LayerSheet(
        title = "چیدمان خانه",
        onDismiss = onDismiss,
        closeLabel = "تأیید",
        topGap = 40.dp
    ) {
        Text(
            "کارت موجودی همیشه بالای صفحه است. سایر بخش‌ها را فعال یا غیرفعال کنید و با پیکان‌ها جابه‌جا کنید.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
        )
        Text("بخش‌های صفحه", style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.padding(top = 4.dp))
        Column {
            layout.forEachIndexed { i, (id, on) ->
                val sec = HomeTools.section(id) ?: return@forEachIndexed
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sec.label, style = lt(14.sp, FontWeight.SemiBold, if (on) L.Ink else L.Faint))
                        Text(sec.sub, style = lt(12.sp, color = L.Muted), maxLines = 1)
                    }
                    SmallIconButton(Icons.Default.KeyboardArrowUp, "بالا بردن ${sec.label}", enabled = i > 0) { onLayout(layout.moved(i, -1)) }
                    SmallIconButton(Icons.Default.KeyboardArrowDown, "پایین بردن ${sec.label}", enabled = i < layout.lastIndex) { onLayout(layout.moved(i, 1)) }
                    LSwitch(on, sec.label) { v -> onLayout(layout.mapIndexed { k, p -> if (k == i) p.first to v else p }) }
                }
                if (i < layout.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
            }
        }

        Text(
            "ابزارهای من (${fa(tools.size)} از ${fa(HomeTools.MAX_PINNED)})",
            style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.padding(top = 10.dp)
        )
        if (tools.isEmpty()) {
            Text("ابزاری سنجاق نشده. از «همهٔ ابزارها» با ستاره اضافه کنید.", style = lt(13.sp, color = L.Muted))
        }
        Column {
            tools.forEachIndexed { i, id ->
                val t = HomeTools.find(id) ?: return@forEachIndexed
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconWell(t.icon, size = 36.dp, bg = L.PrimarySoft, fg = L.PrimaryText, iconSize = 18.dp)
                    Text(t.label, style = lt(14.sp, FontWeight.Medium), modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
                    SmallIconButton(Icons.Default.KeyboardArrowUp, "بالا بردن ${t.label}", enabled = i > 0) { onTools(tools.moved(i, -1)) }
                    SmallIconButton(Icons.Default.KeyboardArrowDown, "پایین بردن ${t.label}", enabled = i < tools.lastIndex) { onTools(tools.moved(i, 1)) }
                    SmallIconButton(Icons.Default.Close, "برداشتن ${t.label}", bg = L.RedSoft, tint = L.RedText) { onTools(tools - id) }
                }
                if (i < tools.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
            }
        }
        if (tools.size < HomeTools.MAX_PINNED) SoftBigButton("+ افزودن ابزار از همهٔ ابزارها", height = 46.dp, onClick = onAddTool)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onReset),
            contentAlignment = Alignment.Center
        ) { Text("برگشت به چیدمان پیش‌فرض", style = lt(13.sp, FontWeight.SemiBold, L.Muted)) }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * همهٔ ابزارهای تراز، گروه‌بندی‌شده و با جستجو. لمس هر ردیف ابزار را باز می‌کند؛
 * ستاره آن را به «ابزارهای من» در خانه می‌برد یا برمی‌دارد.
 */
@Composable
fun AllToolsSheet(
    pinned: List<String>,
    onToggle: (String) -> Unit,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = if (q.isEmpty()) HomeTools.all else HomeTools.all.filter { it.label.contains(q) || it.sub.contains(q) || it.group.contains(q) }
    val full = pinned.size >= HomeTools.MAX_PINNED

    LayerSheet(title = "همهٔ ابزارها", onDismiss = onDismiss, closeLabel = "بستن", topGap = 40.dp) {
        LField(query, { query = it }, "جستجوی ابزار", placeholder = "قبض، چک، اقساط…")
        Text(
            if (full) "«ابزارهای من» پر است؛ برای ستاره زدن یکی را بردارید." else "با ستاره هر ابزار را به «ابزارهای من» در صفحهٔ خانه می‌برید.",
            style = lt(12.sp, color = if (full) L.AmberInk else L.Muted, lineHeight = 20.sp)
        )
        if (list.isEmpty()) Text("ابزاری با این نام پیدا نشد.", style = lt(13.sp, color = L.Muted))
        HomeTools.groups.forEach { g ->
            val items = list.filter { it.group == g }
            if (items.isEmpty()) return@forEach
            Text(g, style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.padding(top = 8.dp))
            Column {
                items.forEachIndexed { i, t ->
                    val on = t.id in pinned
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onRun(t.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconWell(t.icon, size = 40.dp, bg = L.PrimarySoft, fg = L.PrimaryText, iconSize = 20.dp, radius = 12.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(t.label, style = lt(14.sp, FontWeight.SemiBold))
                            Text(t.sub, style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = on || !full) { onToggle(t.id) }
                                .semantics { contentDescription = if (on) "برداشتن ${t.label} از خانه" else "سنجاق ${t.label} به خانه" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (on) Icons.Default.Star else Icons.Default.StarBorder, null,
                                tint = if (on) L.Amber else L.Chevron, modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (i < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
