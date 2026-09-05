package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    onDeleteTransaction: (Transaction) -> Unit,
    onAddTransactionClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, EXPENSE, INCOME

    val filteredTransactions = transactions.filter { tx ->
        val matchesSearch = tx.title.contains(searchQuery, ignoreCase = true) || tx.bankName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "EXPENSE" -> tx.isExpense
            "INCOME" -> !tx.isExpense
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.isExpense }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
    ) {
        // eBay Evo Header Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "تاریخچه تراکنش‌ها",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 20.sp
                    )
                )
                Text(
                    text = "${transactions.size} تراکنش ثبت شده",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 12.sp
                    )
                )
            }

            // eBay Evo Action Chip (Add Transaction)
            Surface(
                onClick = onAddTransactionClick,
                shape = RoundedCornerShape(20.dp),
                color = EbayBluePrimary,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "جدید",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // Search Bar in eBay Evo Pill Style
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    "جستجو بر اساس عنوان یا بانک...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText, fontSize = 13.sp)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = EbaySecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "پاک کردن",
                            tint = EbaySecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = SurfaceWhite,
                focusedContainerColor = SurfaceWhite,
                unfocusedBorderColor = EbayBorderGray,
                focusedBorderColor = EbayBluePrimary
            ),
            singleLine = true
        )

        // Filter Chips Bar (eBay Evo Pill Style)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                EvoFilterChip(
                    selected = selectedFilter == "ALL",
                    label = "همه تراکنش‌ها",
                    onClick = { selectedFilter = "ALL" }
                )
            }
            item {
                EvoFilterChip(
                    selected = selectedFilter == "EXPENSE",
                    label = "برداشت‌ها",
                    onClick = { selectedFilter = "EXPENSE" },
                    activeColor = EbayRed
                )
            }
            item {
                EvoFilterChip(
                    selected = selectedFilter == "INCOME",
                    label = "واریزها",
                    onClick = { selectedFilter = "INCOME" },
                    activeColor = EbayGreen
                )
            }
        }

        // Summary Stats Strip (eBay Evo Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EvoSummaryMiniCard(
                modifier = Modifier.weight(1f),
                title = "مجموع واریز",
                amount = totalIncome,
                isIncome = true
            )
            EvoSummaryMiniCard(
                modifier = Modifier.weight(1f),
                title = "مجموع برداشت",
                amount = totalExpense,
                isIncome = false
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = EbaySecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "تراکنشی با این مشخصات یافت نشد",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = EbaySecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        onDelete = { onDeleteTransaction(tx) },
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EvoFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    activeColor: Color = EbayBluePrimary
) {
    val bgColor = if (selected) activeColor else EbayChipBackground
    val textColor = if (selected) Color.White else EbayDarkText

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = if (selected) null else BorderStroke(1.dp, EbayBorderGray)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                fontSize = 12.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun EvoSummaryMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Long,
    isIncome: Boolean
) {
    val accentColor = if (isIncome) EbayGreen else EbayRed
    val bgTint = if (isIncome) EbayGreenLight else EbayRedLight
    val formatted = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, EbayBorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formatted تومان",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 13.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bgTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemCard(
    transaction: Transaction,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val df = SimpleDateFormat("dd MMM", Locale("fa", "IR"))
    val formattedDate = df.format(Date(transaction.date))
    val formattedAmount = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(transaction.amount)

    val (icon, bgColor, iconColor) = when (transaction.category) {
        "غذا" -> Triple(Icons.Default.RestaurantMenu, EbayYellowLight, EbayYellow)
        "حقوق" -> Triple(Icons.Default.AttachMoney, EbayGreenLight, EbayGreen)
        "پوشاک" -> Triple(Icons.Default.Checkroom, EbayBlueLight, EbayBluePrimary)
        "تفریح" -> Triple(Icons.Default.Movie, Color(0xFFFCE7F3), Color(0xFFDB2777))
        "قسط" -> Triple(Icons.Default.Receipt, EbayRedLight, EbayRed)
        else -> Triple(Icons.Default.Category, EbayChipBackground, EbaySecondaryText)
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) EbayRed else Color.Transparent,
                label = "delete_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .testTag("transaction_item_card_${transaction.id}"),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, EbayBorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(EbayChipBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = transaction.bankName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = EbaySecondaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = EbaySecondaryText,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val prefix = if (transaction.isExpense) "-" else "+"
                        val color = if (transaction.isExpense) EbayRed else EbayGreen
                        Text(
                            text = "$prefix $formattedAmount تومان",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = color,
                                fontSize = 14.sp
                            ),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    )
}


