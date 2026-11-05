package com.example.ui.layer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.ui.Tab

/**
 * منوی شناور قرصی + دکمهٔ گرد ثبت (طرح «لایه»).
 * در RTL: منو سمت راست، دکمهٔ + سمت چپ.
 */
@Composable
fun LayerNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    onAssistant: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(16.dp, PillShape, ambientColor = Color(0x1F0E1116), spotColor = Color(0x1F0E1116))
                .clip(PillShape)
                .background(L.Surface)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("خانه", Icons.Outlined.GridView, currentTab == Tab.HOME || currentTab == Tab.REPORTS) { onTabSelected(Tab.HOME) }
            NavItem("تراکنش‌ها", Icons.AutoMirrored.Filled.List, currentTab == Tab.TRANSACTIONS) { onTabSelected(Tab.TRANSACTIONS) }
            NavItem("اقساط", Icons.Default.AccountBalance, currentTab == Tab.LOANS) { onTabSelected(Tab.LOANS) }
            NavItem("دستیار", Icons.Default.AutoAwesome, false, onAssistant)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(14.dp, PillShape, ambientColor = Color(0x592250F4), spotColor = Color(0x592250F4))
                .clip(PillShape)
                .background(L.Primary)
                .clickable(onClick = onAdd)
                .semantics { contentDescription = "ثبت تراکنش" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(PillShape)
                .background(L.NavSelected)
                .clickable(onClick = onClick)
                .semantics { this.selected = true }
                .padding(horizontal = 14.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = L.Primary, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = lt(13.sp, FontWeight.Bold, L.Primary))
        }
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(PillShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = L.Muted, modifier = Modifier.size(22.dp))
        }
    }
}
