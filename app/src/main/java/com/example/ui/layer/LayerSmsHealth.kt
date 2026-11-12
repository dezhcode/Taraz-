package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.SmsCatchUp
import com.example.data.SmsHealth
import com.example.utils.JalaliDate
import kotlinx.coroutines.launch

/**
 * «وضعیت دریافت پیامک» در تنظیمات پیامک‌خوان: آیا پیامک تازه مستقیم می‌رسد، آخرین بررسی صندوق
 * ورودی، نوتیفیکیشن، محدودیت باتری و (روی شیائومی) «اجرای خودکار» و «پیامک‌های اعلان»، هر کدام با
 * میان‌بُر به همان تنظیمات. «بررسی الان» صندوق ورودی را همین حالا می‌خواند.
 */
@Composable
fun SmsHealthSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // با برگشتن از صفحهٔ تنظیمات دوباره خوانده می‌شود
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val status = remember(tick) { SmsHealth.read(context) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SectionTitle("وضعیت دریافت پیامک")
    LayerCard(radius = 20.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            HealthRow(
                ok = status.lastBroadcast > 0,
                title = "دریافت فوری پیامک",
                subtitle = if (status.lastBroadcast > 0) "کار می‌کند · آخرین: ${stamp(status.lastBroadcast)}"
                else if (status.xiaomi) "هنوز پیامکی مستقیم نرسیده؛ دو مورد شیائومی پایین را روشن کنید."
                else "هنوز پیامکی مستقیم نرسیده؛ بررسی صندوق پیامک جبرانش می‌کند."
            )
            HealthRow(
                ok = true,
                title = "بررسی صندوق پیامک",
                subtitle = "هر ۱۵ دقیقه و با هر بار باز شدن تراز" +
                    (if (status.lastScan > 0) " · آخرین پیامک دیده‌شده: ${stamp(status.lastScan)}" else "")
            )
            HealthRow(
                ok = status.notificationsOn,
                title = "نوتیفیکیشن تراکنش جدید",
                subtitle = if (status.notificationsOn) "روشن است" else "خاموش است؛ تراکنش تازه خبر داده نمی‌شود.",
                action = if (status.notificationsOn) null else "روشن کردن" to { SmsHealth.openNotificationSettings(context) }
            )
            HealthRow(
                ok = status.batteryUnrestricted,
                title = "محدودیت باتری",
                subtitle = if (status.batteryUnrestricted) "بدون محدودیت" else "محدود است؛ بررسی پس‌زمینه ممکن است اجرا نشود. تراز را «بدون محدودیت» کنید.",
                action = if (status.batteryUnrestricted) null else "تنظیم" to { SmsHealth.openBatterySettings(context) }
            )
            if (status.xiaomi) {
                HealthRow(
                    ok = null,
                    title = "شیائومی: اجرای خودکار",
                    subtitle = "تراز را در فهرست «اجرای خودکار» روشن کنید.",
                    action = "باز کردن" to { SmsHealth.openAutostart(context) }
                )
                HealthRow(
                    ok = null,
                    title = "شیائومی: پیامک‌های اعلان",
                    subtitle = "در مجوزهای تراز، «پیامک‌های اعلان» (Notification SMS) را روی «اجازه» بگذارید؛ پیامک بانک از همین نوع است.",
                    action = "باز کردن" to { SmsHealth.openMiuiPermissions(context) },
                    divider = false
                )
            }
        }
    }
    Box(Modifier.padding(horizontal = 20.dp)) {
        SoftBigButton(if (checking) "در حال بررسی…" else "بررسی پیامک‌ها همین حالا") {
            if (checking) return@SoftBigButton
            checking = true
            result = null
            scope.launch {
                val found = SmsCatchUp.run(context, notify = false)
                checking = false
                tick++
                result = if (found.isEmpty()) "تراکنش تازه‌ای پیدا نشد." else "${fa(found.size)} تراکنش تازه پیدا شد."
            }
        }
    }
    result?.let { Text(it, style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp)) }
}

/** [ok]: true سبز، false نارنجی، null خاکستری (وضعیتش را اندروید به برنامه نمی‌گوید). */
@Composable
private fun HealthRow(
    ok: Boolean?,
    title: String,
    subtitle: String,
    action: Pair<String, () -> Unit>? = null,
    divider: Boolean = true
) {
    LRow(
        title, subtitle, divider = divider,
        leading = {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    when (ok) {
                        true -> L.Green
                        false -> L.AmberText
                        null -> L.Muted
                    }
                )
            )
        },
        trailing = action?.let { (label, onClick) ->
            @Composable {
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(L.PrimarySoft).clickable(onClick = onClick)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, style = lt(12.sp, FontWeight.Bold, L.PrimaryText)) }
            }
        }
    )
}

private fun stamp(ts: Long): String = "${JalaliDate.fromTimestamp(ts).formatShort()} ${timeOfDay(ts)}"
