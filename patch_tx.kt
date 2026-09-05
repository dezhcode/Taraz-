@Composable
fun TransactionItemCard(
    transaction: Transaction,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val df = SimpleDateFormat("dd MMM", Locale("fa", "IR"))
    val formattedDate = df.format(Date(transaction.date))
    val (icon, bgColor, iconColor) = when (transaction.category) {
        "غذا" -> Triple(Icons.Default.RestaurantMenu, Color(0xFFFEF3C7), Color(0xFFD97706)) // Amber
        "حقوق" -> Triple(Icons.Default.AttachMoney, Color(0xFFD1FAE5), Color(0xFF059669)) // Emerald
        "پوشاک" -> Triple(Icons.Default.Checkroom, Color(0xFFE0F2FE), Color(0xFF0284C7)) // Sky Blue
        "تفریح" -> Triple(Icons.Default.Movie, Color(0xFFFCE7F3), Color(0xFFDB2777)) // Pink
        "قسط" -> Triple(Icons.Default.Receipt, Color(0xFFFEE2E2), Color(0xFFDC2626)) // Red
        else -> Triple(Icons.Default.Category, Color(0xFFF1F5F9), Color(0xFF475569)) // Slate
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
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) ErrorRed else Color.Transparent,
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
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Visual Circle Icon Indicator
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(IceSlate, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = transaction.bankName,
                                    style = MaterialTheme.typography.labelMedium.copy(color = SlateGray, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelMedium.copy(color = SlateGray)
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        val prefix = if (transaction.isExpense) "-" else "+"
                        val color = if (transaction.isExpense) ErrorRed else SuccessGreen
                        Text(
                            text = "$prefix ${formatNumber(transaction.amount)} تومان",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = color,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            ),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    )
}
