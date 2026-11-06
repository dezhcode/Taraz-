package com.example.ui.layer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.data.Transaction
import com.example.data.WeeklyDigest
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.utils.JalaliDate
import java.io.File

/** آنچه در تصویر گزارش ماه می‌آید */
data class MonthReport(
    val monthTitle: String,
    val income: Long,
    val expense: Long,
    val categories: List<Pair<String, Long>>
) {
    val saved: Long get() = income - expense

    companion object {
        fun of(transactions: List<Transaction>, monthTs: Long): MonthReport {
            val (s, e) = JalaliDate.monthRange(monthTs)
            val list = transactions.filter { it.date in s until e }
            val spent = list.filter { it.countsAsExpense }
            return MonthReport(
                JalaliDate.fromTimestamp(monthTs).formatMonthYear(),
                list.filter { it.countsAsIncome }.sumOf { it.amount },
                spent.sumOf { it.amount },
                spent.groupBy { it.category.ifBlank { "سایر" } }.mapValues { (_, l) -> l.sumOf { it.amount } }
                    .toList().sortedByDescending { it.second }
            )
        }
    }
}

/**
 * گزارش ماه به شکل یک تصویر ۱۰۸۰ پیکسلی برای فرستادن در پیام‌رسان؛ با
 * [hide] به جای مبلغ‌ها فقط درصدها دیده می‌شود.
 */
object ReportRenderer {
    private const val W = 1080
    private const val M = 72f

    private val palette = intArrayOf(0xFF2F5BEA.toInt(), 0xFF0E9490.toInt(), 0xFFE08A1E.toInt(), 0xFF8A5CF6.toInt(), 0xFFDB4C77.toInt(), 0xFF5C6B73.toInt())

    private fun amount(v: Long) = WeeklyDigest.amount(v)
    private fun pct(part: Long, whole: Long): String =
        if (whole <= 0) "—" else JalaliDate.toPersianDigits((part * 100 / whole).toInt()) + "٪"

    fun render(c: Context, r: MonthReport, hide: Boolean): Bitmap {
        val regular = runCatching { ResourcesCompat.getFont(c, com.example.R.font.iranyekan_regular) }.getOrNull() ?: Typeface.DEFAULT
        val bold = runCatching { ResourcesCompat.getFont(c, com.example.R.font.iranyekan_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val cats = r.categories.take(6)
        val h = 760 + cats.size * 112 + 140
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(0xFFF4F5F7.toInt())

        fun paint(size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.RIGHT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = tf; textAlign = align
        }
        val ink = 0xFF0E1116.toInt()
        val muted = 0xFF6B7280.toInt()
        val right = W - M

        cv.drawText("تراز", right, M + 40, paint(40f, 0xFF2F5BEA.toInt(), bold))
        cv.drawText("گزارش ${r.monthTitle}", right, M + 140, paint(64f, ink, bold))
        cv.drawText(if (hide) "مبالغ پنهان است؛ فقط نسبت‌ها" else "به تومان", right, M + 200, paint(32f, muted, regular))

        // سه کادر: درآمد، هزینه، پس‌انداز
        val boxW = (W - 2 * M - 2 * 24f) / 3
        val top = M + 250
        val boxes = listOf(
            Triple("درآمد", if (hide) "۱۰۰٪" else amount(r.income), 0xFF15803D.toInt()),
            Triple("هزینه", if (hide) pct(r.expense, r.income) else amount(r.expense), 0xFFDC2626.toInt()),
            Triple("پس‌انداز", if (hide) pct(r.saved.coerceAtLeast(0), r.income) else amount(r.saved), if (r.saved >= 0) 0xFF2F5BEA.toInt() else 0xFFDC2626.toInt())
        )
        boxes.forEachIndexed { i, (label, value, color) ->
            val x1 = right - i * (boxW + 24f)
            val rect = RectF(x1 - boxW, top, x1, top + 200)
            cv.drawRoundRect(rect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
            cv.drawText(label, x1 - 32, top + 64, paint(32f, muted, regular))
            val vp = paint(44f, color, bold)
            while (vp.measureText(value) > boxW - 48 && vp.textSize > 24f) vp.textSize -= 2f
            cv.drawText(value, x1 - 32, top + 150, vp)
        }

        // دسته‌ها
        var y = top + 300
        cv.drawText("هزینه‌ها به تفکیک دسته", right, y, paint(42f, ink, bold))
        y += 40
        val card = RectF(M, y, right, y + 40 + cats.size * 112f + 20)
        cv.drawRoundRect(card, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        if (cats.isEmpty()) {
            cv.drawText("هزینه‌ای ثبت نشده است.", right - 36, y + 90, paint(32f, muted, regular))
        }
        val maxCat = cats.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        cats.forEachIndexed { i, (name, v) ->
            val rowTop = y + 40 + i * 112f
            cv.drawText(name, right - 36, rowTop + 40, paint(34f, ink, bold))
            cv.drawText(if (hide) pct(v, r.expense) else "${amount(v)}  ·  ${pct(v, r.expense)}", M + 36, rowTop + 40, paint(30f, muted, regular, Paint.Align.LEFT))
            val barRight = right - 36
            val barLeft = M + 36
            val track = RectF(barLeft, rowTop + 62, barRight, rowTop + 80)
            cv.drawRoundRect(track, 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEEF0F3.toInt() })
            val len = (barRight - barLeft) * v / maxCat
            cv.drawRoundRect(RectF(barRight - len, rowTop + 62, barRight, rowTop + 80), 9f, 9f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[i % palette.size] })
        }

        cv.drawText("ساخته‌شده با تراز · دفتر مالی شخصی", W / 2f, h - 60f, paint(28f, muted, regular, Paint.Align.CENTER))
        return bmp
    }

    private fun sharedFile(c: Context, name: String) = File(File(c.cacheDir, "shared").apply { mkdirs() }, name)

    private fun share(c: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, "فرستادن گزارش").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sharePng(c: Context, bmp: Bitmap) {
        val f = sharedFile(c, "taraz-report.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        share(c, f, "image/png")
    }

    fun sharePdf(c: Context, bmp: Bitmap) {
        val doc = PdfDocument()
        // A4-wide page in points, the picture scaled to fit
        val pw = 595
        val ph = (bmp.height * pw.toFloat() / bmp.width).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
        page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)
        val f = sharedFile(c, "taraz-report.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        share(c, f, "application/pdf")
    }
}

/** پیش‌نمایش و فرستادن گزارش ماه به شکل عکس یا PDF */
@Composable
fun ShareReportSheet(transactions: List<Transaction>, monthTs: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hide by remember { mutableStateOf(false) }
    val report = remember(transactions, monthTs) { MonthReport.of(transactions, monthTs) }
    val bmp = remember(report, hide) { ReportRenderer.render(context, report, hide) }
    LayerSheet(
        title = "ارسال گزارش ${report.monthTitle}",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("تصویر", Modifier.weight(1f)) { runCatching { ReportRenderer.sharePng(context, bmp) } }
                SoftBigButton("PDF", Modifier.weight(1f), height = 54.dp) { runCatching { ReportRenderer.sharePdf(context, bmp) } }
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("پنهان کردن مبلغ‌ها", style = lt(14.sp, FontWeight.SemiBold))
                Text("فقط درصدها نمایش داده می‌شود", style = lt(12.sp, color = L.Muted))
            }
            LSwitch(hide, "پنهان کردن مبلغ‌ها") { hide = it }
        }
        Image(
            bmp.asImageBitmap(), contentDescription = "پیش‌نمایش گزارش",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** خلاصهٔ هفته در خود برنامه، با تحلیل اختیاری دستیار */
@Composable
fun WeeklySummarySheet(digest: WeeklyDigest.Digest, onDismiss: () -> Unit, onAskAssistant: (String) -> Unit) {
    LayerSheet(
        title = "خلاصهٔ این هفته",
        onDismiss = onDismiss,
        topGap = 90.dp,
        bottomBar = {
            BigButton("تحلیل هفته با دستیار") {
                onAskAssistant(
                    "خلاصهٔ هفتهٔ من این است:\n" + digest.lines().joinToString("\n") +
                        "\nکوتاه بگو این هفته چطور بود و برای هفتهٔ بعد دو پیشنهاد مشخص بده."
                )
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(L.RedSoft).padding(12.dp)) {
                Text("هزینهٔ هفت روز اخیر", style = lt(12.sp, color = L.RedText))
                Text(money(digest.spent), style = lt(19.sp, FontWeight.Bold, L.RedText))
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(12.dp)) {
                Text("هفتهٔ پیش از آن", style = lt(12.sp, color = L.Muted))
                Text(money(digest.prevSpent), style = lt(19.sp, FontWeight.Bold))
            }
        }
        digest.lines().forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•  ", style = lt(14.sp, FontWeight.Bold, L.Primary))
                Text(line, style = lt(14.sp, lineHeight = 24.sp))
            }
        }
        if (digest.upcoming.isNotEmpty()) {
            Text("سررسیدهای هفت روز آینده", style = lt(13.sp, FontWeight.Bold))
            digest.upcoming.forEach { u ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(u.title, style = lt(13.sp), modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${weekdayName(u.at)} · ${if (u.amount > 0) money(u.amount) else "—"}", style = lt(13.sp, color = L.Muted))
                }
            }
        }
        Text("این خلاصه هر جمعه ساعت ۲۰ از طریق اعلان نیز ارسال می‌شود.", style = lt(12.sp, color = L.Muted))
        Spacer(Modifier.height(4.dp))
    }
}
