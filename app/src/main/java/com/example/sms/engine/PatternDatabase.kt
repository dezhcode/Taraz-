package com.example.sms.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * One message layout of one bank. Only the regexes the engine actually uses are
 * kept: the `Template` text, `Format`, `Before/AfterChar` and `Description` of the
 * source database never take part in extraction.
 */
class PatternTemplate(
    /** «برداشت» or «واریز». */
    val typeName: String?,
    /** Optional regex that must be found in the message for the type check to score. */
    val typeIdentity: String?,
    val account: String?,
    val amount: String?,
    val date: String?,
    val time: String?
)

class PatternBank(
    /** The bank's name as written in the database, e.g. «ملی». */
    val name: String,
    /** Sender numbers/IDs the bank is known to use. */
    val numbers: List<String>,
    val templates: List<PatternTemplate>
) {
    val key: String = SmsText.nameKey(name)
    private val canonicalNumbers: Set<String> =
        numbers.map { SmsSenders.canonical(it) }.filter { it.isNotEmpty() }.toSet()

    fun usesSender(sender: String?): Boolean {
        val c = SmsSenders.canonical(sender)
        return c.isNotEmpty() && c in canonicalNumbers
    }
}

/**
 * The bundled pattern database (`assets/sms/bank_patterns.json`): 47 banks, 1,077
 * message layouts and 354 sender numbers, in the schema of the original app so
 * that a newer export can simply replace the file.
 */
class PatternDatabase(val banks: List<PatternBank>) {

    private val byKey: Map<String, PatternBank> = banks.associateBy { it.key }

    fun byName(name: String?): PatternBank? = if (name == null) null else byKey[SmsText.nameKey(name)]

    /** Banks that list this sender among their numbers. */
    fun bySender(sender: String?): List<PatternBank> = banks.filter { it.usesSender(sender) }

    /**
     * `getSMSBankByNumber`: the banks a message from [sender] can belong to,
     * with the two special cases of the original.
     *  - Blu Bank texts come from several gateways; its own name in the text, or
     *    its gateway number, is enough.
     *  - Saman's number is also used by others, so Saman only stays when the text says «سامان».
     */
    fun forSender(sender: String?, message: String): List<PatternBank> {
        val result = ArrayList<PatternBank>()
        val bluGateway = SmsSenders.canonical("989999987641")
        val senderKey = SmsSenders.canonical(sender)
        for (bank in banks) {
            val isBlu = bank.name.contains("بلو")
            val matches = when {
                isBlu && (message.contains("بلوبانک") || message.contains("بلو بانک")) -> true
                isBlu && senderKey.isNotEmpty() && senderKey == bluGateway -> true
                else -> bank.usesSender(sender)
            }
            if (matches) result.add(bank)
        }
        if (result.any { it.name.contains("سامان") } && !message.contains("سامان")) {
            result.removeAll { it.name.contains("سامان") }
        }
        return result
    }

    companion object {
        const val ASSET_PATH = "sms/bank_patterns.json"

        /** Parses the database JSON; malformed input gives an empty database, like the original. */
        fun parse(json: String): PatternDatabase {
            val banks = ArrayList<PatternBank>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val b = array.optJSONObject(i) ?: continue
                    val name = b.optString("Name", "")
                    if (name.isBlank()) continue
                    val numbers = ArrayList<String>()
                    b.optJSONArray("Numbers")?.let { arr ->
                        for (n in 0 until arr.length()) {
                            val v = arr.optString(n, "")
                            if (v.isNotBlank()) numbers.add(v)
                        }
                    }
                    val templates = ArrayList<PatternTemplate>()
                    b.optJSONArray("TransactionTypes")?.let { arr ->
                        for (t in 0 until arr.length()) {
                            val o = arr.optJSONObject(t) ?: continue
                            templates.add(
                                PatternTemplate(
                                    typeName = o.str("TypeName"),
                                    typeIdentity = o.str("TypeIdentity"),
                                    account = o.regex("AccountNumber"),
                                    amount = o.regex("Amount"),
                                    date = o.regex("Date"),
                                    time = o.regex("Time")
                                )
                            )
                        }
                    }
                    banks.add(PatternBank(name, numbers, templates))
                }
            } catch (e: Exception) {
                return PatternDatabase(emptyList())
            }
            return PatternDatabase(banks)
        }

        private fun JSONObject.str(name: String): String? =
            if (isNull(name)) null else optString(name, "").ifEmpty { null }

        /**
         * A missing element or null regex means «cannot be extracted». An *empty*
         * regex is different: it matches the empty string, which is how layouts
         * without an account number (the Pod wallet) still pass the account check.
         */
        private fun JSONObject.regex(name: String): String? {
            val element = optJSONObject(name) ?: return null
            return if (element.isNull("Regex")) null else element.optString("Regex", "")
        }
    }
}
