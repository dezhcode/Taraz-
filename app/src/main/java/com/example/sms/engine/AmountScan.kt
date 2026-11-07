package com.example.sms.engine

/**
 * Both engines read the amount as «the first number written with thousands
 * separators». That is right for nearly every bank, which prints the amount
 * before the balance — but a message that leads with «مانده: 12,000,000» and
 * only then gives the amount would be recorded as a 12,000,000 transaction.
 *
 * A figure that sits right after a balance label is therefore not an amount.
 * If every figure in the message is a balance (a balance-inquiry reply), there
 * is no transaction at all and the caller gets null instead of a phantom one.
 */
internal object AmountScan {

    private val BALANCE_LABELS = listOf("مانده", "موجودی")
    private const val LOOK_BEHIND = 30

    /** First regex match that is not labelled as a balance; null when there is none. */
    fun firstTransactionMatch(text: String, regex: Regex): MatchResult? {
        var match = regex.find(text)
        while (match != null) {
            if (isAmountShaped(match.value) && !isBalanceFigure(text, match.range.first)) return match
            match = match.next()
        }
        return null
    }

    private val AMOUNT_SHAPE = Regex("""[+-]?(\d{1,3}(,\d{3})+|\d+)[+-]?""")

    /**
     * The database's amount regex also matches «12,» (a number and a comma with
     * no group after it), which is what an account written as `12,34,56,78`
     * looks like. A real amount is digits, or digits grouped in threes.
     */
    private fun isAmountShaped(matched: String): Boolean = AMOUNT_SHAPE.matches(matched)

    fun isBalanceFigure(text: String, start: Int): Boolean {
        val lineStart = if (start <= 0) 0 else text.lastIndexOf('\n', start - 1) + 1
        val from = maxOf(lineStart, start - LOOK_BEHIND)
        if (from >= start) return false
        val behind = text.substring(from, start)
        // A label belongs to the number right after it: once another figure sits
        // in between, whatever came before that figure is its label, not ours.
        val lastDigit = behind.indexOfLast { it.isDigit() }
        val window = if (lastDigit >= 0) behind.substring(lastDigit + 1) else behind
        return BALANCE_LABELS.any { SmsText.normalizeLetters(window).contains(it) }
    }

    /** "5,000,000-" / "+1,200" / "3,500" to its absolute value, or null if it is not a number. */
    fun toAmount(matched: String): Long? =
        matched.replace("+", "").replace("-", "").replace(",", "").toLongOrNull()
}
