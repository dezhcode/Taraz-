package com.example.sms.engine

import org.json.JSONArray
import java.io.File

/**
 * Builds a realistic SMS from each layout in the pattern database by filling the
 * template's placeholders with values that match the layout's own regexes. It is
 * the only large corpus of real bank message shapes available offline, so it is
 * what the engines are checked against.
 */
object TemplateSamples {

    data class Sample(
        val bank: String,
        val sender: String,
        val index: Int,
        val isIncome: Boolean,
        val message: String
    )

    fun databaseFile(): File {
        val configured = System.getProperty("patterns.path")
        val candidates = listOfNotNull(
            configured,
            "src/main/assets/sms/bank_patterns.json",
            "app/src/main/assets/sms/bank_patterns.json"
        )
        return candidates.map { File(it) }.first { it.exists() }
    }

    fun loadDatabase(): PatternDatabase = PatternDatabase.parse(databaseFile().readText(Charsets.UTF_8))

    const val AMOUNT = "1,250,000"
    const val BALANCE = "84,184,943"

    /** Samples for every layout that has an amount placeholder and a regex I can invert. */
    fun all(): List<Sample> {
        val raw = JSONArray(databaseFile().readText(Charsets.UTF_8))
        val out = ArrayList<Sample>()
        for (b in 0 until raw.length()) {
            val bank = raw.getJSONObject(b)
            val name = bank.getString("Name")
            val numbers = bank.getJSONArray("Numbers")
            if (numbers.length() == 0) continue
            val sender = numbers.getString(0)
            val types = bank.getJSONArray("TransactionTypes")
            for (t in 0 until types.length()) {
                val tt = types.getJSONObject(t)
                val template = tt.optString("Template", "")
                if (!template.contains("[[amount]]")) continue
                val income = tt.getInt("Type") == 1
                val message = fill(tt, template) ?: continue
                out.add(Sample(name, sender, t, income, message))
            }
        }
        return out
    }

    private fun regexOf(tt: org.json.JSONObject, field: String): String =
        tt.optJSONObject(field)?.optString("Regex", "") ?: ""

    private fun fill(tt: org.json.JSONObject, template: String): String? {
        val account = sampleFor(regexOf(tt, "AccountNumber")) ?: return null
        val date = dateSample(regexOf(tt, "Date")) ?: return null
        val time = if (regexOf(tt, "Time").contains("\\d{2}:\\d{2}:\\d{2}") && !regexOf(tt, "Time").contains("{1,2}")) "20:35:10" else "20:35"
        val typeName = tt.optString("TypeName", "برداشت")

        var s = template.replace("\r\n", "\n")
        // Balance placeholders sit after a «مانده/موجودی» label.
        s = Regex("""(مانده|موجودی|موجودي|مانده حساب)([^\n\[]{0,6})\[\[var\]\]""").replace(s) {
            it.groupValues[1] + it.groupValues[2] + BALANCE
        }
        // Layouts without a type placeholder carry the verb in free text.
        if (!s.contains("[[tType]]")) {
            val i = s.indexOf("[[var]]")
            if (i >= 0) s = s.substring(0, i) + typeName + s.substring(i + "[[var]]".length)
        }
        s = s.replace("[[amount]]", AMOUNT)
            .replace("[[accountNumber]]", account)
            .replace("[[date]]", date)
            .replace("[[time]]", time)
            .replace("[[tType]]", typeName)
            .replace("[[var]]", "متن")
        return s
    }

    private fun dateSample(regex: String): String? = when (regex) {
        "\\d{4}/\\d{2}/\\d{2}", "\\d{4}/\\d{1,2}/\\d{2}", "\\d{4}/\\d{2}/\\d{1,2}", "\\d{4}/\\d{1,2}/\\d{1,2}",
        "\\d{2,4}/\\d{1,2}/\\d{1,2}", "\\d{2,4}/\\d{2}/\\d{2}", "\\d{0,4}/\\d{1,2}/\\d{1,2}" -> "1405/04/11"
        "/\\d{4}/\\d{2}/\\d{2}" -> "/1405/04/11"
        "\\d{2}/\\d{2}/\\d{2}", "\\d{1}/\\d{2}/\\d{2}", "\\d{2}/\\d{1,2}/\\d{1,2}" -> "05/04/11"
        "\\d{2}/\\d{2}", "\\d{1,2}/\\d{1,2}", "\\d{1,2}/\\d{2}", "\\d{2}/\\d{1,2}", "\\d{0,2}/\\d{0,2}" -> "04/11"
        "\\d{4}-", "\\d{4}", "\\n\\d{4}-" -> "0411"
        "\\d{6}-", "\\d{6}" -> "050411"
        "\\d{2},\\d{2},\\d{2}" -> "05,04,11"
        "\\d{4}.\\d{2}.\\d{2}" -> "1405.04.11"
        else -> null // exotic shapes (month names, 9xxxxx) are not generated
    }

    /** A minimal inverse of the regexes used for account numbers. */
    private fun sampleFor(regex: String): String? {
        if (regex.isEmpty()) return "123456"
        val sb = StringBuilder()
        var i = 0
        var seed = 1
        fun digits(n: Int) { repeat(n) { sb.append(('0' + (seed++ % 10)).let { if (it == '0') '5' else it }) } }
        while (i < regex.length) {
            val c = regex[i]
            when {
                regex.startsWith("\\d", i) -> {
                    i += 2
                    when {
                        i < regex.length && regex[i] == '{' -> {
                            val end = regex.indexOf('}', i)
                            if (end < 0) return null
                            val body = regex.substring(i + 1, end)
                            val n = body.split(',')[0].ifEmpty { "0" }.toInt()
                            digits(maxOf(n, if (body.contains(',')) n else n))
                            i = end + 1
                        }
                        i < regex.length && regex[i] == '+' -> { digits(2); i++ }
                        else -> digits(1)
                    }
                }
                regex.startsWith("\\*", i) -> { sb.append('*'); i += 2 }
                regex.startsWith("\\.", i) -> { sb.append('.'); i += 2 }
                regex.startsWith("\\-", i) -> { sb.append('-'); i += 2 }
                regex.startsWith("\\+", i) -> { sb.append('+'); i += 2 }
                regex.startsWith("\\s", i) -> { sb.append(' '); i += 2 }
                c == '.' -> { sb.append('.'); i++ }
                c == '-' || c == '/' || c == ',' || c == '*' -> { sb.append(c); i++ }
                else -> return null
            }
        }
        return sb.toString()
    }
}
