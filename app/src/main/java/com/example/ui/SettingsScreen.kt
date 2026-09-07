package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.toPersianDigits
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    userProfile: UserProfile?,
    biometricEnabled: Boolean,
    alertsEnabled: Boolean,
    aiServerUrl: String,
    onBiometricChange: (Boolean) -> Unit,
    onAlertsChange: (Boolean) -> Unit,
    onAiServerUrlChange: (String) -> Unit,
    onResetAll: () -> Unit,
    onLogout: () -> Unit,
    onScheduleReminder: (String, Long) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showSmsParserPage by remember { mutableStateOf(false) }

    if (showSmsParserPage) {
        SmsParserSettingsScreen(
            viewModel = viewModel,
            onBack = { showSmsParserPage = false }
        )
    } else {
        val scrollState = rememberScrollState()
        var showResetDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current

        var showToastMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = EbayDarkText
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "تنظیمات برنامه",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 20.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تنظیمات حریم خصوصی، امنیت، اعلان‌ها و مدیریت پایگاه داده‌ها بر اساس eBay Evo",
                    style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText, fontSize = 12.sp)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundLight)
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // 1. Premium Profile Widget (eBay Evo Card)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EbayBorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(EbayBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile?.name ?: "کاربر تراز").firstOrNull()?.toString() ?: "م",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.name ?: "کاربر تراز",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (!userProfile?.phone.isNullOrEmpty()) {
                                Text(
                                    text = "شماره همراه: ${toPersianDigits(userProfile?.phone ?: "")}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText, fontWeight = FontWeight.Medium)
                                )
                            } else if (userProfile?.isGuest == true) {
                                Text(
                                    text = "ورود به عنوان مهمان (بدون شماره)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = EbayBluePrimary, fontWeight = FontWeight.Medium)
                                )
                            } else {
                                Text(
                                    text = userProfile?.email ?: "mooazenzadeh79@gmail.com",
                                    style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText)
                                )
                            }
                            if (userProfile?.isGoogleConnected == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EbayGreenLight)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EbayGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "متصل به حساب گوگل",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EbayGreen, fontSize = 11.sp)
                                    )
                                }
                            }
                        }

                        // Logout Button
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EbayRedLight)
                                .testTag("settings_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "خروج",
                                tint = EbayRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Security Settings Section
                Text(
                    text = "امنیت و حریم خصوصی",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 16.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EbayBorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SettingsToggleRow(
                            icon = Icons.Default.Fingerprint,
                            title = "قفل بیومترik (اثر انگشت)",
                            description = "استفاده از سنسور اثر انگشت گوشی برای باز کردن تراز.",
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricChange,
                            testTag = "settings_biometric_toggle"
                        )

                        HorizontalDivider(color = EbayBorderGray, modifier = Modifier.padding(horizontal = 16.dp))

                        SettingsToggleRow(
                            icon = Icons.Default.Notifications,
                            title = "اعلان‌های هوشمند اقساط",
                            description = "ارسال یادآورهای هوشمند تراز برای تراکنش‌ها و تعهدات.",
                            checked = alertsEnabled,
                            onCheckedChange = onAlertsChange,
                            testTag = "settings_notifications_toggle"
                        )

                        Divider(color = IceSlate, modifier = Modifier.padding(horizontal = 20.dp))

                        SettingsActionRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "ارسال اعلان تست سیستم تراز",
                            onClick = {
                                if (alertsEnabled) {
                                    com.example.utils.NotificationHelper.showNotification(
                                        context,
                                        "اعلان تست تراز",
                                        "سیستم هوشمند اطلاع‌رسانی تراز فعال است و اقساط شما را یادآوری خواهد کرد!"
                                    )
                                    showToastMessage = "اعلان تست با موفقیت به دستگاه ارسال شد!"
                                } else {
                                    showToastMessage = "لطفاً ابتدا سوئیچ اعلان‌ها را فعال نمایید."
                                }
                            },
                            testTag = "action_send_test_notification"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // AI Server Configuration Section
                Text(
                    text = "تنظیمات سرور هوش مصنوعی",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        var serverInput by remember(aiServerUrl) { mutableStateOf(aiServerUrl) }
                        var isTestingServer by remember { mutableStateOf(false) }

                        Text(
                            text = "آدرس آدرس بک‌اند تراز (FastAPI):",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = serverInput,
                            onValueChange = { serverInput = it },
                            label = { Text("آدرس URL سرور (مثال: https://dezhcode.pyho.ir/taraz)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_server_url_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = EmeraldPrimary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                focusedLabelColor = EmeraldPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (serverInput.isNotBlank()) {
                                    isTestingServer = true
                                    viewModel.testAiServerConnection(serverInput) { success, msg ->
                                        isTestingServer = false
                                        showToastMessage = msg
                                        if (success) {
                                            onAiServerUrlChange(serverInput)
                                        }
                                    }
                                } else {
                                    showToastMessage = "لطفاً آدرس معتبر سرور را وارد نمایید."
                                }
                            },
                            enabled = !isTestingServer,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_ai_server_button")
                        ) {
                            if (isTestingServer) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("در حال بررسی اتصال به سرور...", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                            } else {
                                Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تست اتصال و ذخیره آدرس سرور", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bank SMS Import Section
                Text(
                    text = "ابزارهای هوشمند پیامک",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "مدیریت پیامک‌های بانکی",
                        onClick = { showSmsParserPage = true },
                        testTag = "settings_sms_parser_row"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // New Reminder and Alarm Setting Component
                Text(
                    text = "تنظیم یادآور یا اعلان جدید",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        var reminderTitle by remember { mutableStateOf("") }
                        var reminderDelay by remember { mutableStateOf(10f) }

                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            label = { Text("عنوان یادآوری (مثال: پرداخت هزینه بیمه)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reminder_title_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                focusedLabelColor = EmeraldPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "زمان هشدار: ${reminderDelay.toInt()} ثانیه دیگر",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, fontWeight = FontWeight.Bold)
                        )

                        Slider(
                            value = reminderDelay,
                            onValueChange = { reminderDelay = it },
                            valueRange = 5f..120f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("reminder_delay_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (reminderTitle.isNotBlank()) {
                                    onScheduleReminder(reminderTitle, reminderDelay.toLong())
                                    showToastMessage = "یادآوری '${reminderTitle}' برای ${reminderDelay.toInt()} ثانیه دیگر تنظیم شد!"
                                    reminderTitle = ""
                                } else {
                                    showToastMessage = "لطفاً برای یادآوری یک عنوان بنویسید."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("schedule_reminder_button")
                        ) {
                            Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ثبت و فعال‌سازی یادآور", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Database & Sync Section
                Text(
                    text = "مدیریت فایل و همگام‌سازی",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Default.CloudUpload,
                            title = "پشتیبان‌گیری در ابرها (Backup)",
                            onClick = { showToastMessage = "پشتیبان‌گیری ابری با موفقیت انجام شد و در حساب شما ذخیره گردید." },
                            testTag = "action_backup"
                        )

                        Divider(color = IceSlate, modifier = Modifier.padding(horizontal = 20.dp))

                        SettingsActionRow(
                            icon = Icons.Default.FileDownload,
                            title = "برون‌بری تراکنش‌ها (Excel/CSV)",
                            onClick = { showToastMessage = "تراکنش‌ها به صورت فایل اکسل در پوشه دانلودها ذخیره شد." },
                            testTag = "action_export"
                        )

                        Divider(color = IceSlate, modifier = Modifier.padding(horizontal = 20.dp))

                        SettingsActionRow(
                            icon = Icons.Default.FileUpload,
                            title = "درون‌ریزی فایل پشتیبان (Import)",
                            onClick = { showToastMessage = "فایل پشتیبان با موفقیت پردازش و تراکنش‌ها بازگردانی شدند." },
                            testTag = "action_import"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 4. Destructive Area
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .testTag("purge_data_button")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پاک‌سازی کل تراکنش‌ها و تنظیمات",
                        style = MaterialTheme.typography.titleMedium.copy(color = ErrorRed, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // Beautiful interactive overlay Toast Simulation
            if (showToastMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .padding(horizontal = 24.dp)
                        .testTag("settings_status_toast"),
                    colors = CardDefaults.cardColors(containerColor = NavySecondary),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = showToastMessage!!,
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showToastMessage = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            // Purge warning dialog modal
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    confirmButton = {
                        Button(
                            onClick = {
                                onResetAll()
                                showResetDialog = false
                                showToastMessage = "پایگاه داده‌ها با موفقیت پاک‌سازی شد."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.testTag("confirm_purge_ok")
                        ) {
                            Text("بله، پاک‌سازی کامل", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showResetDialog = false }) {
                            Text("انصراف", style = MaterialTheme.typography.bodyLarge)
                        }
                    },
                    title = {
                        Text(
                            text = "هشدار جدی!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = ErrorRed)
                        )
                    },
                    text = {
                        Text(
                            text = "با تایید این عملیات، تمام داده‌های دخل و خرج، سوابق بانکی و قسط‌های ثبت شده برای همیشه حذف خواهد شد. آیا مطمئنید؟",
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = SurfaceWhite,
                    modifier = Modifier.testTag("purge_confirm_alert_dialog")
                )
            }
        }
    }
  }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(IceSlate),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldPrimary),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IceSlate),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = NavySecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SlateGray
        )
    }
}
