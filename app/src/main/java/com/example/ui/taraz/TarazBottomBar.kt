package com.example.ui.taraz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Tab

enum class TarazNav { HOME, TRANSACTIONS, LOANS, ASSISTANT }

/**
 * The "لایه" bottom bar: a floating white pill with four destinations and a
 * separate cobalt "+" for the action people take most — recording money.
 * The current destination opens up into a tinted pill with its name.
 */
@Composable
fun TarazBottomBar(
    current: TarazNav,
    onSelect: (TarazNav) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .testTag("taraz_bottom_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(14.dp, CircleShape, ambientColor = Color(0x330E1116), spotColor = Color(0x330E1116))
                .clip(CircleShape)
                .background(Tz.Surface)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Filled.GridView, "خانه", current == TarazNav.HOME) { onSelect(TarazNav.HOME) }
            NavItem(Icons.Filled.ReceiptLong, "تراکنش‌ها", current == TarazNav.TRANSACTIONS) { onSelect(TarazNav.TRANSACTIONS) }
            NavItem(Icons.Filled.AccountBalance, "اقساط", current == TarazNav.LOANS) { onSelect(TarazNav.LOANS) }
            NavItem(Icons.Filled.AutoAwesome, "دستیار", current == TarazNav.ASSISTANT) { onSelect(TarazNav.ASSISTANT) }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(12.dp, CircleShape, ambientColor = Tz.Cobalt, spotColor = Tz.Cobalt)
                .clip(CircleShape)
                .background(Tz.Cobalt)
                .clickable(role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "ثبت تراکنش" }
                .testTag("bottom_bar_add"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (selected) Tz.CobaltTint2 else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .padding(horizontal = if (selected) 14.dp else 12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Tz.Cobalt else Tz.Muted, modifier = Modifier.size(22.dp))
        AnimatedVisibility(visible = selected) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(label, style = tzText(13.sp, FontWeight.Bold, Tz.Cobalt))
            }
        }
    }
}

/**
 * Space screens leave at the bottom so the floating bar never hides content,
 * including the system navigation bar on phones with three-button navigation.
 */
val BottomBarClearance = 140.dp

fun Tab.toNav(): TarazNav = when (this) {
    Tab.HOME, Tab.REPORTS -> TarazNav.HOME
    Tab.TRANSACTIONS -> TarazNav.TRANSACTIONS
    Tab.LOANS -> TarazNav.LOANS
}
