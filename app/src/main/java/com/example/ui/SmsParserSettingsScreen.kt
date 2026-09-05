package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sms.domain.model.BankSenderModel
import com.example.services.SmsTransactionCategorizerService
import com.example.sms.permission.SmsPermissionHelper
import com.example.sms.presentation.SmsParserViewModel
import com.example.ui.components.IranianBankLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsParserSettingsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Instantiate our clean architecture SMS Parser ViewModel
    val smsViewModel: SmsParserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val scannedSenders by smsViewModel.scannedSenders.collectAsState()
    val savedSenders by smsViewModel.savedSelectedSenders.collectAsState()
    val pendingTxs by smsViewModel.pendingTransactions.collectAsState()
    val unknownSmsList by smsViewModel.unknownSmsList.collectAsState()
    val isLoading by smsViewModel.isLoading.collectAsState()
    val errorMessage by smsViewModel.errorMessage.collectAsState()
    val searchQuery by smsViewModel.searchQuery.collectAsState()

    var activeSubTab by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(SmsPermissionHelper.hasSmsPermissions(context)) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isReadGranted = permissions[android.Manifest.permission.READ_SMS] ?: false
        val isReceiveGranted = permissions[android.Manifest.permission.RECEIVE_SMS] ?: false
        hasPermission = isReadGranted && isReceiveGranted
        if (hasPermission) {
            smsViewModel.triggerScan()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "سیستم هوشمند تراکنش‌های پیامکی",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("sms_settings_back_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundLight)
                    .padding(innerPadding)
            ) {
                // Header Activation block
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, IceSlate)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "دریافت خودکار تراکنش‌ها",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "با فعالسازی این بخش تراز پیامک‌های تراکنشی بانک‌های انتخاب شده را به صورت کاملاً لوکال و آفلاین به تراکنش مالی تبدیل می‌کند.",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateGray, lineHeight = 16.sp)
                            )
                        }
                    }
                }

                // Sub Tabs
                ScrollableTabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = BackgroundLight,
                    contentColor = EmeraldPrimary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                            color = EmeraldPrimary
                        )
                    }
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فرستنده‌ها (${scannedSenders.size})")
                            }
                        },
                        modifier = Modifier.testTag("subtab_senders")
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تأیید تراکنش (${pendingTxs.size})")
                            }
                        },
                        modifier = Modifier.testTag("subtab_pending")
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ناشناخته‌ها (${unknownSmsList.size})")
                            }
                        },
                        modifier = Modifier.testTag("subtab_unknown")
                    )
                    Tab(
                        selected = activeSubTab == 3,
                        onClick = { activeSubTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("شبیه‌ساز تست")
                            }
                        },
                        modifier = Modifier.testTag("subtab_simulator")
                    )
                }

                // Sub Tab Contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeSubTab) {
                        0 -> {
                            SendersTabContent(
                                hasPermission = hasPermission,
                                onRequestPermission = {
                                    permissionLauncher.launch(SmsPermissionHelper.REQUIRED_PERMISSIONS)
                                },
                                scannedSenders = scannedSenders,
                                searchQuery = searchQuery,
                                onSearchChange = { smsViewModel.setSearchQuery(it) },
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onToggle = { smsViewModel.toggleSenderSelection(it.senderNumber) },
                                onSelectAll = { smsViewModel.selectAllSenders() },
                                onDeselectAll = { smsViewModel.deselectAllSenders() },
                                onRefresh = { smsViewModel.triggerScan() },
                                onSave = {
                                    smsViewModel.saveChoices()
                                    android.widget.Toast.makeText(context, "تغییرات با موفقیت ذخیره شدند", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        1 -> {
                            PendingTransactionsTabContent(
                                pendingTxs = pendingTxs,
                                onApprove = { tx, selectedCategory ->
                                    viewModel.approvePendingTransaction(
                                        pendingTx = tx,
                                        finalTitle = tx.title,
                                        finalCategory = selectedCategory,
                                        finalAmount = tx.amount,
                                        finalCard = tx.card,
                                        isExpense = tx.isExpense
                                    )
                                },
                                onReject = { tx ->
                                    smsViewModel.deletePendingTransaction(tx.id)
                                }
                            )
                        }
                        2 -> {
                            UnknownSmsTabContent(
                                unknownSmsList = unknownSmsList,
                                onIgnore = { smsViewModel.ignoreUnknownSms(it.id) },
                                onDelete = { smsViewModel.deleteUnknownSms(it.id) }
                            )
                        }
                        3 -> {
                            SimulatorTabContent(scannedSenders)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SendersTabContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    scannedSenders: List<BankSenderModel>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onToggle: (BankSenderModel) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit
) {
    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "دسترسی به پیامک‌ها مورد نیاز است",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "برنامه تراز برای یافتن خودکار و استخراج پیامک‌های بانکی به صورت امن نیاز به مجوز پیامک دارد.",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("درخواست مجوز دسترسی", style = MaterialTheme.typography.labelLarge.copy(color = Color.White))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search and control panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("جستجوی بانک یا فرستنده...", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray)) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SlateGray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = IceSlate,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, IceSlate, RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "بروزرسانی", tint = EmeraldPrimary)
                }
            }

            // Selection toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = EmeraldPrimary)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("انتخاب همه")
                }
                TextButton(
                    onClick = onDeselectAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("لغو انتخاب همه")
                }
            }

            // Senders List
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("در حال اسکن پیامک‌ها...", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray))
                    }
                } else if (!errorMessage.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.SmsFailed, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage, style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed), textAlign = TextAlign.Center)
                    }
                } else if (scannedSenders.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SlateGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("هیچ فرستنده پیامک بانکی کشف نشد", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(scannedSenders, key = { it.senderNumber }) { sender ->
                            SenderItem(sender = sender, onToggle = { onToggle(sender) })
                        }
                    }
                }
            }

            // Action save button sticky at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ذخیره بانک‌های انتخابی", style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun SenderItem(
    sender: BankSenderModel,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, IceSlate)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-fidelity Iranian Bank Logo using the sprite sheet
            IranianBankLogo(
                bankName = sender.bankName,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sender.bankName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
                Text(
                    text = "شماره: ${sender.senderNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateGray)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${sender.messageCount} پیامک",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                )
                Checkbox(
                    checked = sender.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                )
            }
        }
    }
}

@Composable
fun PendingTransactionsTabContent(
    pendingTxs: List<com.example.data.PendingTransaction>,
    onApprove: (com.example.data.PendingTransaction, String) -> Unit,
    onReject: (com.example.data.PendingTransaction) -> Unit
) {
    if (pendingTxs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("هیچ تراکنش معلقی وجود ندارد", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
            Spacer(modifier = Modifier.height(4.dp))
            Text("تمام پیامک‌های تراکنشی پردازش و وارد حساب‌های مالی شده‌اند.", style = MaterialTheme.typography.bodySmall.copy(color = SlateGray))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pendingTxs, key = { it.id }) { tx ->
                PendingTransactionItem(
                    tx = tx,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
    }
}

@Composable
fun PendingTransactionItem(
    tx: com.example.data.PendingTransaction,
    onApprove: (com.example.data.PendingTransaction, String) -> Unit,
    onReject: (com.example.data.PendingTransaction) -> Unit
) {
    var selectedCategory by remember { 
        mutableStateOf(SmsTransactionCategorizerService.categorize(tx.smsBody, tx.title, tx.isExpense)) 
    }
    
    val categories = if (tx.isExpense) {
        listOf("غذا", "پوشاک", "تفریح", "قسط", "سایر")
    } else {
        listOf("حقوق", "سایر")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, IceSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (tx.isExpense) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (tx.isExpense) ErrorRed else EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tx.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
                }
                Text(
                    text = "${formatNumber(tx.amount)} تومان",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (tx.isExpense) ErrorRed else EmeraldPrimary)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundLight)
                    .padding(10.dp)
            ) {
                Text(text = tx.smsBody, style = MaterialTheme.typography.bodySmall.copy(color = SlateGray))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "دسته‌بندی تراکنش (پیشنهاد هوشمند تراز):",
                style = MaterialTheme.typography.labelSmall.copy(color = SlateGray, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EmeraldPrimary.copy(alpha = 0.12f) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldPrimary else IceSlate,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isSelected) EmeraldPrimary else SlateGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onReject(tx) }, colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رد کردن")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onApprove(tx, selectedCategory) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تأیید و ثبت")
                }
            }
        }
    }
}

@Composable
fun UnknownSmsTabContent(
    unknownSmsList: List<com.example.data.UnknownSms>,
    onIgnore: (com.example.data.UnknownSms) -> Unit,
    onDelete: (com.example.data.UnknownSms) -> Unit
) {
    if (unknownSmsList.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Default.SentimentSatisfiedAlt, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("پیامک ناشناخته‌ای یافت نشد", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
            Spacer(modifier = Modifier.height(4.dp))
            Text("همه ساختارهای پیامک‌های دریافتی مچ شده‌اند.", style = MaterialTheme.typography.bodySmall.copy(color = SlateGray))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(unknownSmsList, key = { it.id }) { sms ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, IceSlate)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("فرستنده: ${sms.senderNumber}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            IconButton(onClick = { onDelete(sms) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundLight)
                                .padding(10.dp)
                        ) {
                            Text(text = sms.smsBody, style = MaterialTheme.typography.bodySmall.copy(color = SlateGray))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onIgnore(sms) }, colors = ButtonDefaults.textButtonColors(contentColor = SlateGray)) {
                                Text("نادیده گرفتن")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatorTabContent(
    senders: List<BankSenderModel>
) {
    var selectedSender by remember { mutableStateOf(senders.firstOrNull()?.senderNumber ?: "Melli") }
    var smsBodyInput by remember { mutableStateOf("بانک ملی\nبرداشت از حساب 0102\nمبلغ: 25,339,753 ریال\nکارت: 3142\nمانده: 279,488,680 ریال\n1405/04/09 14:01") }
    var parsedResult by remember { mutableStateOf<com.example.sms.domain.model.ParsedTransactionModel?>(null) }
    var runSimulationTriggered by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("شبیه‌ساز و تست محلی الگوریتم پارسر", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
        Spacer(modifier = Modifier.height(4.dp))
        Text("در این بخش می‌توانید فرآیند پارسینگ و استخراج اطلاعات پیامک‌ها را به صورت کاملاً لوکال بر روی هر متنی آزمایش کنید.", style = MaterialTheme.typography.bodySmall.copy(color = SlateGray))
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = selectedSender,
            onValueChange = { selectedSender = it },
            label = { Text("شماره فرستنده آزمایشی") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = smsBodyInput,
            onValueChange = { smsBodyInput = it },
            label = { Text("متن پیامک فرضی") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                runSimulationTriggered = true
                val parser = com.example.sms.data.parser.CompositeParser()
                parsedResult = parser.parse(smsBodyInput, "بانک شبیه‌سازی")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("اجرای الگوریتم پارسر لوکال")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (runSimulationTriggered) {
            val result = parsedResult
            if (result != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("خروجی پارس شده با موفقیت:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldPrimary))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("نوع تراکنش: ${if (result.transactionType == "withdrawal") "برداشت/خرید" else "واریز"}", style = MaterialTheme.typography.bodyMedium)
                        Text("مبلغ استخراجی: ${result.amount} ریال", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("مانده حساب: ${result.balance} ریال", style = MaterialTheme.typography.bodyMedium)
                        Text("۴ رقم کارت: ${result.cardNumber}", style = MaterialTheme.typography.bodyMedium)
                        Text("کد مرجع: ${result.reference}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        Text("الگوریتم پارسر قادر به تشخیص فیلدهای مالی در این متن نبود. ساختار پیامک با الگوها مچ نشد.", style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed))
                    }
                }
            }
        }
    }
}
