package com.example.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * «وضعیت دریافت» پیامک‌خوان: چه چیزی جلوی رسیدن پیامک یا نوتیفیکیشن را گرفته، و میان‌بُر به همان
 * صفحهٔ تنظیمات. روی شیائومی/ردمی/پوکو بدون «اجرای خودکار» و مجوز «پیامک‌های اعلان»، اندروید پیامک
 * بانک را به برنامه نمی‌دهد؛ این دو صفحه مخصوص MIUI/HyperOS است.
 */
object SmsHealth {

    data class Status(
        val notificationsOn: Boolean,
        val batteryUnrestricted: Boolean,
        val xiaomi: Boolean,
        /** آخرین باری که پیامک تازه مستقیم به تراز رسید؛ ۰ یعنی هرگز. */
        val lastBroadcast: Long,
        /** آخرین پیامکی که بررسی صندوق ورودی دید؛ ۰ یعنی هنوز. */
        val lastScan: Long
    )

    fun read(c: Context): Status {
        val pm = c.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return Status(
            notificationsOn = NotificationManagerCompat.from(c).areNotificationsEnabled(),
            batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(c.packageName) ?: true,
            xiaomi = isXiaomi(),
            lastBroadcast = SmsPrefs.lastBroadcast(c),
            lastScan = SmsPrefs.lastInboxScan(c)
        )
    }

    fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return "xiaomi" in m || "redmi" in m || "poco" in m
    }

    fun openNotificationSettings(c: Context) {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        } else appDetails(c)
        start(c, i)
    }

    /** فهرست «بهینه‌سازی باتری»؛ تراز را «بدون محدودیت» بگذارید تا بررسی پس‌زمینه اجرا شود. */
    fun openBatterySettings(c: Context) = start(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /** شیائومی: «اجرای خودکار». */
    fun openAutostart(c: Context) = start(
        c,
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
    )

    /** شیائومی: مجوزهای برنامه، جایی که «پیامک‌های اعلان» است. */
    fun openMiuiPermissions(c: Context) = start(
        c,
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", c.packageName)
    )

    private fun appDetails(c: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null))

    /** صفحهٔ خواسته‌شده، وگرنه صفحهٔ اطلاعات برنامه (روی هر گوشی هست). */
    private fun start(c: Context, intent: Intent) {
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            c.startActivity(appDetails(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: SecurityException) {
            c.startActivity(appDetails(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
