package com.example.sms.engine

/**
 * Sender numbers («سرشماره») as they appear in the inbox.
 *
 * The same bank sender shows up as `+98500019000`, `98500019000`, `0500019000`
 * or a bare `500019000` depending on the operator and the phone, and as plain
 * text (`Bank Melli`, `b.pasargad`) for alphanumeric sender IDs. Two senders are
 * the same when their canonical forms are equal.
 */
object SmsSenders {

    /**
     * Numeric senders lose `+`, `00`, one leading `0` and the `98` country code;
     * alphanumeric senders are lower-cased with spaces removed.
     */
    fun canonical(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val s = SmsText.convertEn(SmsText.stripBidi(raw)).trim().lowercase()
        val compact = s.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
        val numeric = compact.isNotEmpty() && compact.removePrefix("+").all { it in '0'..'9' }
        if (!numeric) return s.filterNot { it.isWhitespace() }

        var d = compact.removePrefix("+")
        if (d.startsWith("00")) d = d.substring(2)
        if (d.startsWith("0") && d.length > 1) d = d.substring(1)
        if (d.startsWith("98") && d.length >= 5) d = d.substring(2)
        return d
    }

    fun same(a: String?, b: String?): Boolean {
        val ca = canonical(a)
        return ca.isNotEmpty() && ca == canonical(b)
    }

    /** True for senders made only of digits (and an optional +), i.e. not a name like «Melli». */
    fun isNumeric(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val c = SmsText.convertEn(raw).filterNot { it.isWhitespace() || it == '-' }
        return c.removePrefix("+").all { it in '0'..'9' } && c.removePrefix("+").isNotEmpty()
    }
}
