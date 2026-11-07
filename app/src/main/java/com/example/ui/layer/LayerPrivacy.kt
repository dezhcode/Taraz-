package com.example.ui.layer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * متن سیاست حریم خصوصی تراز.
 * همین متن در docs/privacy-policy.html هم هست تا به‌صورت لینک در صفحهٔ مایکت قرار بگیرد؛
 * اگر یکی را تغییر دادید، دیگری را هم به‌روز کنید.
 */
object PrivacyPolicy {
    const val UPDATED = "آخرین به‌روزرسانی: ۱۶ آبان ۱۴۰۵"

    const val INTRO = "تراز یک دفتر مالی شخصی است که بدون حساب کاربری آنلاین کار می‌کند. " +
        "این صفحه توضیح می‌دهد چه اطلاعاتی در برنامه ثبت می‌شود، کجا نگهداری می‌شود و چه زمانی برنامه به اینترنت متصل می‌شود."

    val sections: List<Pair<String, String>> = listOf(
        "اطلاعاتی که ثبت می‌کنید" to
            "حساب‌ها، تراکنش‌ها، اقساط، چک‌ها، قبض‌ها، اهداف، بودجه و اطلاعات اشخاص (نام، شمارهٔ تماس و تصویر) و تصاویر رسیدها " +
            "فقط در حافظهٔ داخلی همین دستگاه ذخیره می‌شوند و به هیچ سروری، از جمله سرور توسعه‌دهنده، ارسال نمی‌شوند.",
        "اتصال به اینترنت" to
            "تراز فقط برای نمایش قیمت لحظه‌ای ارز، طلا، سکه، صندوق‌های سرمایه‌گذاری و رمزارزها در صفحهٔ دارایی‌ها به اینترنت متصل می‌شود " +
            "و این قیمت‌ها را از منابع عمومی قیمت دریافت می‌کند. در این درخواست‌ها هیچ اطلاعات شخصی یا مالی شما ارسال نمی‌شود.",
        "مجوزهای برنامه" to
            "اینترنت: برای دریافت قیمت‌ها.\n" +
            "اثر انگشت: برای قفل برنامه؛ احراز هویت را سیستم‌عامل انجام می‌دهد و اثر انگشت شما در دسترس تراز قرار نمی‌گیرد.\n" +
            "اعلان: برای یادآوری سررسید اقساط، چک‌ها و قبض‌ها.\n" +
            "دوربین و گالری فقط با انتخاب خود شما و از طریق برنامه‌های دستگاه برای افزودن تصویر رسید یا تصویر اشخاص باز می‌شوند.",
        "پشتیبان و خروجی" to
            "فایل پشتیبان و خروجی اکسل فقط در محلی که خودتان انتخاب می‌کنید ذخیره می‌شود و فقط با انتخاب شما به اشتراک گذاشته می‌شود. " +
            "این فایل‌ها شامل اطلاعات مالی شما هستند؛ آن‌ها را در محل امن نگهداری کنید.",
        "تبلیغات و ردیابی" to
            "تراز هیچ تبلیغ، ابزار تحلیل رفتار کاربر یا ردیاب ندارد و اطلاعات شما را به هیچ شخص یا شرکتی نمی‌فروشد یا واگذار نمی‌کند.",
        "حذف اطلاعات" to
            "از مسیر «تنظیمات ← حذف همهٔ داده‌ها» می‌توانید تمام اطلاعات ثبت‌شده را حذف کنید. با حذف برنامه نیز همهٔ اطلاعات آن از دستگاه پاک می‌شود.",
        "تغییرات این سیاست" to
            "در صورت تغییر نحوهٔ استفاده از اطلاعات، این صفحه در برنامه و در صفحهٔ تراز در مایکت به‌روزرسانی می‌شود.",
        "ارتباط با ما" to
            "برای پرسش دربارهٔ حریم خصوصی، از بخش ارتباط با توسعه‌دهنده در صفحهٔ تراز در مایکت با ما در تماس باشید."
    )
}

@Composable
fun LayerPrivacyScreen(onBack: () -> Unit) {
    LayerScreen(title = "سیاست حریم خصوصی", subtitle = PrivacyPolicy.UPDATED, onBack = onBack) {
        LayerCard(radius = 20.dp) {
            Text(PrivacyPolicy.INTRO, style = lt(14.sp, color = L.Muted, lineHeight = 25.sp))
        }
        PrivacyPolicy.sections.forEach { (title, body) ->
            LayerCard(radius = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = lt(15.sp, FontWeight.Bold))
                    Text(body, style = lt(13.sp, color = L.Muted, lineHeight = 24.sp))
                }
            }
        }
        Text(
            "تراز · دفتر مالی شخصی",
            style = lt(12.sp, color = L.Muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
        )
    }
}
