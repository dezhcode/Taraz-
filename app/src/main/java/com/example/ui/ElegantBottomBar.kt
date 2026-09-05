package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EbayBluePrimary
import com.example.ui.theme.EbayBlueLight
import com.example.ui.theme.EbayDarkText
import com.example.ui.theme.EbaySecondaryText
import com.example.ui.theme.EbayBorderGray
import com.example.ui.theme.SurfaceWhite

@Composable
fun ElegantBottomBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(84.dp)
            .testTag("ebay_evo_bottom_bar"),
        contentAlignment = Alignment.BottomCenter
    ) {
        // eBay Evo Style Floating Dock Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .background(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = EbayBorderGray,
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Home
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    EvoTabItem(
                        selected = currentTab == Tab.HOME,
                        icon = Icons.Default.GridView,
                        title = "داشبورد",
                        onClick = { onTabSelected(Tab.HOME) }
                    )
                }

                // Tab 2: Transactions
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    EvoTabItem(
                        selected = currentTab == Tab.TRANSACTIONS,
                        icon = Icons.Default.CreditCard,
                        title = "تراکنش‌ها",
                        onClick = { onTabSelected(Tab.TRANSACTIONS) }
                    )
                }

                // Center Gap for FAB
                Spacer(modifier = Modifier.width(60.dp))

                // Tab 3: Reports
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    EvoTabItem(
                        selected = currentTab == Tab.REPORTS,
                        icon = Icons.Default.BarChart,
                        title = "گزارش‌ها",
                        onClick = { onTabSelected(Tab.REPORTS) }
                    )
                }

                // Tab 4: Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    EvoTabItem(
                        selected = currentTab == Tab.SETTINGS,
                        icon = Icons.Default.Person,
                        title = "تنظیمات",
                        onClick = { onTabSelected(Tab.SETTINGS) }
                    )
                }
            }
        }

        // eBay Evo Primary Action FAB
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 12.dp)
                .size(54.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    spotColor = EbayBluePrimary.copy(alpha = 0.35f)
                )
                .background(
                    color = EbayBluePrimary,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAddClick
                )
                .testTag("add_transaction_bottom_fab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "ثبت تراکنش",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EvoTabItem(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val activeBg = if (selected) EbayBlueLight else Color.Transparent
    val activeColor = if (selected) EbayBluePrimary else EbaySecondaryText

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(activeBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = activeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) EbayDarkText else EbaySecondaryText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

