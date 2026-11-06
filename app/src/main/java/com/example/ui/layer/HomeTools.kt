package com.example.ui.layer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/** یک ابزار تراز که می‌شود آن را به خانه سنجاق کرد */
data class HomeTool(val id: String, val label: String, val sub: String, val icon: ImageVector, val group: String)

/** بخش‌های صفحهٔ خانه؛ کارت موجودی همیشه بالای صفحه است و جزو این‌ها نیست */
data class HomeSection(val id: String, val label: String, val sub: String)

object HomeTools {
    const val G_RECORD = "ثبت"
    const val G_PAY = "پرداخت و حساب"
    const val G_PEOPLE = "آدم‌ها"
    const val G_PLAN = "برنامه‌ریزی و دارایی"
    const val G_REPORT = "گزارش"
    const val G_KEEP = "نگه‌داری"
    val groups = listOf(G_RECORD, G_PAY, G_PEOPLE, G_PLAN, G_REPORT, G_KEEP)

    val all = listOf(
        HomeTool("expense", "ثبت هزینه", "با دسته، برچسب و عکس فاکتور", Icons.Default.RemoveCircleOutline, G_RECORD),
        HomeTool("income", "ثبت درآمد", "حقوق، فروش، هدیه", Icons.Default.AddCircleOutline, G_RECORD),
        HomeTool("voice", "با صدا", "«دیروز ۲۰۰ تومن به علی قرض دادم»", Icons.Default.Mic, G_RECORD),
        HomeTool("transfer", "انتقال", "بین حساب‌ها؛ در جمع هزینه نیست", Icons.AutoMirrored.Filled.CompareArrows, G_RECORD),
        HomeTool("sms", "پیامک بانک", "تأیید برداشت و واریزها", Icons.Default.Sms, G_RECORD),
        HomeTool("recurring", "تکراری‌ها", "اجاره، حقوق، اشتراک‌ها", Icons.Default.Autorenew, G_RECORD),

        HomeTool("pay_loan", "پرداخت قسط", "وام‌ها و خریدهای قسطی", Icons.Default.EventAvailable, G_PAY),
        HomeTool("bills", "پرداخت قبض", "برق، آب، گاز، تلفن، اینترنت", Icons.AutoMirrored.Filled.ReceiptLong, G_PAY),
        HomeTool("cheque", "ثبت چک", "داده‌شده یا گرفته‌شده، با سررسید", Icons.Default.Payments, G_PAY),
        HomeTool("cheques", "چک‌ها", "فهرست چک‌ها و وضعیتشان", Icons.Default.CalendarMonth, G_PAY),
        HomeTool("add_loan", "وام یا قسطی", "ثبت قرارداد و تقویم اقساط", Icons.Default.ShoppingCart, G_PAY),
        HomeTool("accounts", "حساب‌ها", "کارت‌ها، نقد و اعتباری", Icons.Default.CreditCard, G_PAY),
        HomeTool("add_account", "حساب تازه", "کارت بانکی، اعتباری یا نقد", Icons.Default.AddCard, G_PAY),

        HomeTool("add_person", "شخص جدید", "حساب جدا برای هر نفر", Icons.Default.PersonAdd, G_PEOPLE),
        HomeTool("person_entry", "قرض دادم / گرفتم", "و پس دادن، با موعد", Icons.Default.Groups, G_PEOPLE),
        HomeTool("split", "تقسیم خرج", "سهم من هزینه، سهم بقیه طلب", Icons.AutoMirrored.Filled.CallSplit, G_PEOPLE),
        HomeTool("people", "حساب اشخاص", "چه کسی به چه کسی بدهکار است", Icons.Default.NotificationsActive, G_PEOPLE),

        HomeTool("budget", "بودجه", "سقف روزانه و ماهانهٔ دسته‌ها", Icons.Default.PieChart, G_PLAN),
        HomeTool("goals", "هدف پس‌انداز", "با موعد و پیشنهاد ماهانه", Icons.Default.Flag, G_PLAN),
        HomeTool("categories", "دسته‌ها", "ساختن و ویرایش دسته", Icons.Default.Category, G_PLAN),
        HomeTool("assets", "دارایی‌ها", "دلار، طلا، رمزارز، صندوق", Icons.AutoMirrored.Filled.TrendingUp, G_PLAN),

        HomeTool("report", "گزارش ماه", "دسته‌ها و مقایسه با ماه قبل", Icons.Default.BarChart, G_REPORT),
        HomeTool("weekly", "خلاصهٔ هفته", "هر جمعه ساعت ۸ شب", Icons.Default.Schedule, G_REPORT),
        HomeTool("year", "سال من", "مرور یک سال", Icons.Default.CalendarMonth, G_REPORT),
        HomeTool("share", "فرستادن گزارش", "عکس یا PDF، با پنهان کردن مبلغ", Icons.Default.Share, G_REPORT),
        HomeTool("assistant", "دستیار", "پرسیدن به زبان خودمانی", Icons.Default.AutoAwesome, G_REPORT),

        HomeTool("search", "جستجو و فیلتر", "نوع، تاریخ، مبلغ، برچسب", Icons.Default.Search, G_KEEP),
        HomeTool("tags", "برچسب‌ها", "#سفر، #خانه؛ در گزارش‌ها", Icons.AutoMirrored.Filled.Label, G_KEEP),
        HomeTool("backup", "پشتیبان", "دستی یا خودکار هفتگی", Icons.Default.Shield, G_KEEP),
        HomeTool("wallet", "کیف پول", "موجودی همهٔ حساب‌ها", Icons.Default.AccountBalanceWallet, G_KEEP)
    )

    private val byId = all.associateBy { it.id }
    fun find(id: String): HomeTool? = byId[id]

    const val MAX_PINNED = 12
    val defaultTools = listOf("voice", "transfer", "bills", "person_entry", "cheque", "assets", "report")

    val sections = listOf(
        HomeSection("glance", "یک نگاه", "خرج ماه، بدهکارم، طلبکارم"),
        HomeSection("upcoming", "پیش رو", "قسط، قبض، چک و طلبِ ۷ روز آینده"),
        HomeSection("sms", "پیامک‌های منتظر تأیید", "فقط وقتی پیامکی هست"),
        HomeSection("today", "خرج امروز", "نوار هفته و سقف روزانه"),
        HomeSection("tools", "ابزارهای من", "دکمه‌هایی که خودتان انتخاب کرده‌اید"),
        HomeSection("plan", "هدف و بودجه", "پیشرفت هدف و دستهٔ نزدیک سقف"),
        HomeSection("recent", "آخرین تراکنش‌ها", "چهار تراکنش آخر")
    )
    private val sectionById = sections.associateBy { it.id }
    fun section(id: String): HomeSection? = sectionById[id]

    /** id → روشن؛ به ترتیب نمایش */
    val defaultLayout: List<Pair<String, Boolean>> = sections.map { it.id to (it.id != "plan") }

    fun encodeLayout(l: List<Pair<String, Boolean>>) = l.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" }

    /** بخش‌های تازه‌ای که در نسخه‌های بعد اضافه شوند، روشن به آخر فهرست می‌روند */
    fun decodeLayout(s: String?): List<Pair<String, Boolean>> {
        if (s.isNullOrBlank()) return defaultLayout
        val saved = s.split(",").mapNotNull { p ->
            val (id, on) = p.split(":").let { if (it.size == 2) it[0] to it[1] else return@mapNotNull null }
            if (section(id) == null) null else id to (on == "1")
        }.distinctBy { it.first }
        return saved + sections.filter { sec -> saved.none { it.first == sec.id } }.map { it.id to true }
    }

    fun decodeTools(s: String?): List<String> =
        if (s == null) defaultTools else s.split(",").filter { find(it) != null }.distinct().take(MAX_PINNED)
}
