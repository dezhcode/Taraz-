package com.example.ui.layer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.data.BankBin
import com.example.data.BankCard
import com.example.data.isCash

/**
 * لوگوی بانک‌ها از مجموعهٔ «LogoS – Iran Banks» (مجوز CC0)، در assets/banklogos.
 *
 * بانک اول از روی شش رقم اول شمارهٔ کارت پیدا می‌شود (مطمئن‌ترین راه)، و اگر کارت
 * نبود یا پیشوندش را نمی‌شناختیم، از روی نام حساب. اگر هیچ‌کدام جواب نداد null
 * برمی‌گردد و جای لوگو همان رنگ کارت می‌ماند؛ هیچ‌وقت لوگوی حدسی نشان نمی‌دهیم.
 */
object BankLogos {

    /** مسیر لوگو داخل assets، یا null */
    fun assetFor(name: String, cardNumber: String = ""): String? {
        BankBin.bankFor(cardNumber)?.let { bank -> byName(bank)?.let { return it } }
        return byName(name)
    }

    /** برای نشان دادن «شناسایی شد» و پیش‌نمایش: فقط از روی شمارهٔ کارت */
    fun assetForCard(cardNumber: String): String? = BankBin.bankFor(cardNumber)?.let { byName(it) }

    private fun norm(s: String): String = s
        .replace('ي', 'ی').replace('ك', 'ک').replace('آ', 'ا').replace('أ', 'ا')
        .replace('‌', ' ').replace('-', ' ').replace('_', ' ')
        .lowercase().trim()

    // ترتیب مهم است: نام‌های بلندتر و خاص‌تر اول (مثلاً «توسعه صادرات» پیش از «صادرات»).
    private val rules: List<Pair<List<String>, String>> = listOf(
        listOf("توسعه صادرات", "tosee saderat", "edbi") to "tosee_saderat",
        listOf("توسعه تعاون", "tosee taavon") to "tosee_taavon",
        listOf("اعتباری توسعه", "tosee") to "tosee",
        listOf("صنعت و معدن", "صنعت ومعدن", "صنعت معدن", "sanat") to "sanat_madan",
        listOf("مهر اقتصاد", "mehr eghtesad") to "mehr_eghtesad",
        listOf("مهر ایران", "mehr iran") to "mehr_iran",
        listOf("اقتصاد نوین", "اقتصادنوین", "eghtesad") to "eghtesad_novin",
        listOf("ایران زمین", "ایرانزمین", "iran zamin") to "iran_zamin",
        listOf("ونزوئلا", "ونزوعلا", "venezuela") to "iran_venezuela",
        listOf("ایران و اروپا", "اروپا", "europe") to "iran_europe",
        listOf("خاورمیانه", "خاور میانه", "khavar", "middle east") to "khavar_mianeh",
        listOf("تعاون اسلامی", "taavon eslami") to "taavon_eslami",
        listOf("استاندارد چارترد", "standard chartered") to "standard_chartered",
        listOf("فیوچر", "future") to "futurebank",
        listOf("اینده", "ayandeh", "ayande") to "ayandeh",
        listOf("بانک مرکزی", "markazi", "central bank") to "bank_markazi",
        listOf("کاسپین", "caspian") to "caspian",
        listOf("کوثر", "kosar", "kowsar") to "kosar",
        listOf("ملل", "melal") to "melall",
        listOf("ملی", "melli") to "melli",
        listOf("ملت", "mellat") to "mellat",
        listOf("پارسیان", "parsian") to "parsian",
        listOf("پاسارگاد", "pasargad") to "pasargad",
        listOf("پست بانک", "پستبانک", "postbank", "post bank") to "postbank",
        listOf("رفاه", "refah") to "refah",
        listOf("رسالت", "resalat") to "resalat",
        listOf("صادرات", "saderat") to "saderat",
        listOf("سامان", "saman") to "saman",
        listOf("سرمایه", "sarmayeh", "sarmaye") to "sarmayeh",
        listOf("سپه", "sepah") to "sepah",
        listOf("سینا", "sina") to "sina",
        listOf("تجارت", "tejarat") to "tejarat",
        listOf("حکمت", "hekmat") to "hekmat",
        listOf("قوامین", "ghavamin") to "ghavamin",
        listOf("گردشگری", "gardeshgari") to "gardeshgari",
        listOf("کارافرین", "karafarin") to "karafarin",
        listOf("کشاورزی", "keshavarzi") to "keshavarzi",
        listOf("مسکن", "maskan") to "maskan",
        listOf("انصار", "ansar") to "ansar"
    )

    // کلمه‌های کوتاهی که فقط به‌صورت کلمهٔ کامل معنی بانک می‌دهند («دی» در «نقدی» نباید بانک دی شود)
    private val wordRules: Map<String, String> = mapOf(
        "دی" to "dey", "dey" to "dey",
        "نور" to "noor", "noor" to "noor",
        "شهر" to "shahr", "shahr" to "shahr",
        "پست" to "postbank", "post" to "postbank"
    )

    private fun byName(raw: String): String? {
        val n = norm(raw)
        if (n.isEmpty()) return null
        rules.firstOrNull { (keys, _) -> keys.any { n.contains(it) } }?.let { return "banklogos/${it.second}.svg" }
        val words = n.split(' ').filter { it.isNotEmpty() }
        words.firstNotNullOfOrNull { wordRules[it] }?.let { return "banklogos/$it.svg" }
        // بلو (بانک دیجیتال سامان) در این مجموعه نیست؛ لوگوی قدیمی خود برنامه را داریم.
        if (n.contains("بلو") || words.contains("blu")) return "banks/blu.svg"
        return null
    }
}

private object BankLogoLoader {
    @Volatile private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
            .also { loader = it }
    }
}

/** خود لوگو، بدون قاب */
@Composable
fun BankLogo(asset: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val request = remember(asset) {
        ImageRequest.Builder(context).data("file:///android_asset/$asset").build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        imageLoader = BankLogoLoader.get(context),
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

/**
 * نشان کوچک حساب کنار نامش (در انتخاب حساب، پرداخت قسط، ویرایش تراکنش و …):
 * اگر بانکش را بشناسیم لوگو روی زمینهٔ سفید، وگرنه همان مستطیل رنگی کارت.
 */
@Composable
fun AccountSwatch(
    bankName: String,
    cards: List<BankCard>,
    width: Dp = 26.dp,
    height: Dp = 18.dp,
    color: Color = cardColorFor(bankName, cards)
) {
    val card = cards.firstOrNull { it.bankName == bankName }
    val logo = remember(bankName, card?.cardNumber, card?.accountType) {
        if (card?.isCash == true) null else BankLogos.assetFor(bankName, card?.cardNumber.orEmpty())
    }
    LogoOrColor(logo, color, width, height)
}

@Composable
fun LogoOrColor(logo: String?, color: Color, width: Dp, height: Dp) {
    if (logo != null) {
        Box(
            Modifier.width(width).height(height).clip(RoundedCornerShape(5.dp)).background(Color.White)
                .border(1.dp, L.Line, RoundedCornerShape(5.dp)).padding(2.dp),
            contentAlignment = Alignment.Center
        ) { BankLogo(logo, Modifier.fillMaxSize()) }
    } else {
        Box(Modifier.width(width).height(height).clip(RoundedCornerShape(4.dp)).background(color))
    }
}
