package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.Features

/** برچسب کوچک «به زودی» کنار قابلیت‌های خاموش */
@Composable
fun SoonBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(PillShape).background(L.AmberSoft).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) { Text(Features.SOON, style = lt(11.sp, FontWeight.Bold, L.AmberInk)) }
}

/** صفحهٔ «به زودی» برای دستیار هوشمند (به‌جای چت، تا وقتی دستیار روشن شود) */
@Composable
fun ComingSoonPanel(
    title: String,
    body: String,
    points: List<String>,
    onClose: () -> Unit,
    icon: ImageVector = Icons.Default.AutoAwesome
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(L.PrimarySoft),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = L.PrimaryText, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        SoonBadge()
        Spacer(Modifier.height(10.dp))
        Text(title, style = lt(22.sp, FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = lt(14.sp, color = L.Muted, lineHeight = 24.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().cardSurface(18.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            points.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(PillShape).background(L.Primary))
                    Text(p, style = lt(14.sp, lineHeight = 22.sp), modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BigButton("باشه", onClick = onClose)
        Spacer(Modifier.height(8.dp))
    }
}
