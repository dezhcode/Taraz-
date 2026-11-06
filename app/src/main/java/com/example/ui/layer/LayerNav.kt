package com.example.ui.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Tab
import kotlin.math.roundToInt

private data class NavEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * منوی شناور قرصی + دکمهٔ گرد ثبت (طرح «لایه»). در RTL: منو سمت راست، دکمهٔ + سمت چپ.
 *
 * پس‌زمینهٔ روشنِ تب انتخاب‌شده یک قرص جداست که با فنر از تب قبلی به تب تازه می‌لغزد و
 * هم‌زمان پهن یا باریک می‌شود؛ برچسب تب تازه باز و برچسب تب قبلی جمع می‌شود.
 */
@Composable
fun LayerNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    onAssistant: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        NavEntry("خانه", Icons.Outlined.GridView) { onTabSelected(Tab.HOME) },
        NavEntry("تراکنش‌ها", Icons.AutoMirrored.Filled.List) { onTabSelected(Tab.TRANSACTIONS) },
        NavEntry("تعهدات", Icons.Default.AccountBalance) { onTabSelected(Tab.LOANS) },
        NavEntry("دستیار", Icons.Default.AutoAwesome, onAssistant)
    )
    val selectedIndex = when (currentTab) {
        Tab.TRANSACTIONS -> 1
        Tab.LOANS -> 2
        else -> 0 // خانه و گزارش
    }
    // جای هر تب داخل منو (از چپ، به پیکسل) تا قرص بداند کجا برود
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(16.dp, PillShape, ambientColor = Color(0x1F0E1116), spotColor = Color(0x1F0E1116))
                .clip(PillShape)
                .background(L.Surface)
                .padding(horizontal = 6.dp)
        ) {
            bounds[selectedIndex]?.let { (left, width) ->
                val springSpec = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                val x by animateFloatAsState(left, springSpec, label = "navX")
                val w by animateFloatAsState(width, springSpec, label = "navW")
                Box(
                    Modifier
                        .align(AbsoluteAlignment.CenterLeft)
                        .absoluteOffset { IntOffset(x.roundToInt(), 0) }
                        .width(with(density) { w.toDp() })
                        .height(48.dp)
                        .clip(PillShape)
                        .background(L.NavSelected)
                )
            }
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                entries.forEachIndexed { i, e ->
                    NavItem(
                        label = e.label,
                        icon = e.icon,
                        selected = i == selectedIndex,
                        onClick = e.onClick,
                        modifier = Modifier.onGloballyPositioned { c ->
                            bounds[i] = c.positionInParent().x to c.size.width.toFloat()
                        }
                    )
                }
            }
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
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(if (selected) L.Primary else L.Muted, label = "navTint")
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(PillShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics {
                this.selected = selected
                if (!selected) contentDescription = label
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (selected) 21.dp else 22.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(label, style = lt(13.sp, FontWeight.Bold, L.Primary), maxLines = 1)
            }
        }
    }
}
