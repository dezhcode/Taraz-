package com.example.ui.layer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * نوار «برگردان» پایین صفحه بعد از حذف؛ نوار نازک زمانِ باقی‌مانده را نشان می‌دهد.
 * روی همه‌چیز می‌نشیند ولی جز خودش لمسی را نمی‌گیرد.
 */
@Composable
fun UndoBar(title: String, detail: String, shownAt: Long, windowMs: Long, onUndo: () -> Unit) {
    val progress = remember(shownAt) { Animatable(1f) }
    LaunchedEffect(shownAt) {
        val left = (windowMs - (System.currentTimeMillis() - shownAt)).coerceAtLeast(0)
        progress.snapTo(left.toFloat() / windowMs)
        progress.animateTo(0f, tween(left.toInt(), easing = LinearEasing))
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 104.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x470E1116), spotColor = Color(0x470E1116))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1B2029))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = lt(14.sp, FontWeight.SemiBold, Color.White), maxLines = 1)
                    Text(detail, style = lt(12.sp, color = Color(0xFFB9C1CE)), maxLines = 1)
                }
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3D66FF)).clickable(onClick = onUndo).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("برگردان", style = lt(14.sp, FontWeight.Bold, Color.White)) }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333A46))) {
                Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().background(Color(0xFF9AB0FF)))
            }
        }
    }
}
