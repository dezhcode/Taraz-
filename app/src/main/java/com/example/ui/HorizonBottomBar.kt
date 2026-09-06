package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * The Horizon bottom bar.
 *
 * Four destinations around a square add button. Instalments get a tab of their
 * own because they are the app's headline feature and a weekly errand; cards are
 * set up once and their balances already live in the dashboard carousel.
 *
 * The old "تنظیمات" tab is gone — it led to an empty placeholder while the real
 * settings screen opened from the header gear. One less dead end.
 */
@Composable
fun HorizonBottomBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(86.dp)
            .testTag("horizon_bottom_bar"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(HorizonSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HorizonBorder)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                HorizonTabItem(
                    icon = Icons.Default.GridView,
                    label = "داشبورد",
                    selected = currentTab == Tab.HOME,
                    onClick = { onTabSelected(Tab.HOME) },
                    testTag = "tab_home"
                )
                HorizonTabItem(
                    icon = Icons.Default.ReceiptLong,
                    label = "تراکنش‌ها",
                    selected = currentTab == Tab.TRANSACTIONS,
                    onClick = { onTabSelected(Tab.TRANSACTIONS) },
                    testTag = "tab_transactions"
                )

                // Placeholder that reserves the add button's slot in the row.
                Spacer(modifier = Modifier.width(54.dp))

                HorizonTabItem(
                    icon = Icons.Default.AccountBalance,
                    label = "اقساط",
                    selected = currentTab == Tab.LOANS,
                    onClick = { onTabSelected(Tab.LOANS) },
                    testTag = "tab_loans"
                )
                HorizonTabItem(
                    icon = Icons.Default.BarChart,
                    label = "گزارش‌ها",
                    selected = currentTab == Tab.REPORTS,
                    onClick = { onTabSelected(Tab.REPORTS) },
                    testTag = "tab_reports"
                )
            }
        }

        // Square, not round: it reads as a button rather than a floating blob,
        // and the rounded-square echoes the card radii used across the app.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(54.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = HorizonGreen.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(HorizonGreen)
                .clickable { onAddClick() }
                .semantics { contentDescription = "افزودن تراکنش" }
                .testTag("bottom_bar_add"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun HorizonTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tint = if (selected) HorizonGreen else Color(0xFF9AAAA2)
    val labelColor = if (selected) HorizonGreen else HorizonInkMuted

    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = label }
            .testTag(testTag)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 9.5.sp,
                color = labelColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}
