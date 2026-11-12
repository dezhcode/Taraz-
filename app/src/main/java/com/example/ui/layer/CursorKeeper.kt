package com.example.ui.layer

/**
 * جای مکان‌نما وقتی متن کادر بعد از تایپ دوباره قالب‌بندی می‌شود (۵۵۸۸ ← ۵٬۵۸۸، یا شمارهٔ کارت
 * ۶۱۰۴۳۳ ← ۶۱۰۴ ۳۳). جداکننده‌ها شمرده نمی‌شوند: مکان‌نما بعد از همان تعداد رقم/حرفی می‌ماند که
 * کاربر پیش از آن تایپ کرده بود، پس رقم بعدی همیشه سر جای درست می‌نشیند.
 */
object CursorKeeper {

    fun isSeparator(c: Char): Boolean = c == '٬' || c == ',' || c == '،' || c == ' ' || c == ' '

    /** چند نویسهٔ معنادار (غیر جداکننده) پیش از [index] آمده است. */
    fun significantBefore(text: String, index: Int): Int {
        val end = index.coerceIn(0, text.length)
        var n = 0
        for (i in 0 until end) if (!isSeparator(text[i])) n++
        return n
    }

    /** جایی در [text] که درست بعد از [count] نویسهٔ معنادار است. */
    fun indexAfterSignificant(text: String, count: Int): Int {
        if (count <= 0) return 0
        var seen = 0
        for (i in text.indices) {
            if (!isSeparator(text[i])) {
                seen++
                if (seen == count) return i + 1
            }
        }
        return text.length
    }

    /**
     * کاربر [edited] را با مکان‌نما در [cursor] ساخت و برنامه آن را به [formatted] تبدیل کرد؛
     * مکان‌نما در [formatted] کجا باشد.
     */
    fun cursorAfterFormat(edited: String, cursor: Int, formatted: String): Int =
        indexAfterSignificant(formatted, significantBefore(edited, cursor)).coerceIn(0, formatted.length)
}
