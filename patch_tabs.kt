                    // Expense Tab
                    val expenseBg by animateColorAsState(if (isExpense) Color(0xFFE5E5EA) else Color.Transparent)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(expenseBg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpense = true
                                if (selectedCategory !in listOf("غذا", "پوشاک", "تفریح", "قسط", "سایر")) {
                                    selectedCategory = "غذا"
                                }
                            }
                            .testTag("sheet_toggle_expense"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "هزینه",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavySecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isExpense) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(modifier = Modifier.width(40.dp).height(3.dp).background(ErrorRed, RoundedCornerShape(1.5.dp)))
                            }
                        }
                    }

                    // Income Tab
                    val incomeBg by animateColorAsState(if (!isExpense) Color(0xFFE5E5EA) else Color.Transparent)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(incomeBg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpense = false
                                if (selectedCategory !in listOf("حقوق", "یارانه", "سود سپرده", "فروش کالا", "هدیه", "سایر")) {
                                    selectedCategory = "حقوق"
                                }
                            }
                            .testTag("sheet_toggle_income"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "درآمد",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavySecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (!isExpense) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(modifier = Modifier.width(40.dp).height(3.dp).background(SuccessGreen, RoundedCornerShape(1.5.dp)))
                            }
                        }
                    }
