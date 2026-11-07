package com.example.sms.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the engine over one generated message per layout in the pattern
 * database (see [TemplateSamples]). Every bank the database knows is exercised
 * through the same entry point the app uses.
 */
class TemplateCorpusTest {

    private val db = TemplateSamples.loadDatabase()
    private val engine = BankSmsEngine(db) { 1405 }
    private val samples = TemplateSamples.all()

    @Test
    fun databaseHas47BanksAndEveryTemplateIsLoaded() {
        assertEquals(47, db.banks.size)
        assertEquals(1077, db.banks.sumOf { it.templates.size })
        assertEquals(354, db.banks.flatMap { it.numbers }.toSet().size)
    }

    @Test
    fun almostEveryLayoutParsesToTheRightAmountAndDirection() {
        assertTrue("corpus too small: ${samples.size}", samples.size > 1000)

        val problems = ArrayList<String>()
        for (s in samples) {
            val r = engine.parse(s.message, s.sender, s.bank)
            when {
                r == null -> problems.add("no result  ${s.bank}#${s.index}")
                r.amount != 1_250_000L -> problems.add("amount ${r.amount}  ${s.bank}#${s.index}")
                r.isIncome != s.isIncome -> problems.add("direction  ${s.bank}#${s.index}")
            }
        }
        // The few misses are layouts whose generated text carries no wording or
        // sign at all (so nothing can tell income from spending), one template
        // with a broken placeholder in the source data, and one message that
        // contains both «واریز» and «برداشت».
        val allowed = 6
        assertTrue("too many misses (${problems.size}/${samples.size}): ${problems.take(20)}", problems.size <= allowed)
    }

    @Test
    fun datesAndTimesAreReadWhenTheLayoutHasThem() {
        var withDate = 0
        var wrongDate = 0
        var withTime = 0
        var wrongTime = 0
        for (s in samples) {
            val r = engine.parse(s.message, s.sender, s.bank) ?: continue
            if (r.date != null) {
                withDate++
                if (r.date != "1405-04-11") wrongDate++
            }
            if (r.time != null) {
                withTime++
                if (r.time != "20:35") wrongTime++
            }
        }
        assertTrue("few dates read: $withDate", withDate > 800)
        assertTrue("few times read: $withTime", withTime > 800)
        assertTrue("wrong dates: $wrongDate of $withDate", wrongDate <= withDate / 50)
        assertTrue("wrong times: $wrongTime of $withTime", wrongTime <= withTime / 50)
    }

    @Test
    fun everyBankWithLayoutsIsInTheCatalogAndBack() {
        for (bank in db.banks.filter { it.templates.isNotEmpty() }) {
            assertTrue("catalog lacks ${bank.name}", BankCatalog.find(bank.name) != null)
        }
        for (entry in BankCatalog.banks) {
            assertTrue("database lacks ${entry.patternName}", db.byName(entry.patternName) != null)
        }
    }

    @Test
    fun everyScriptKeyIsBackedByAParser() {
        val script = ScriptEngine { 1405 }
        for (entry in BankCatalog.banks) {
            val key = entry.scriptKey ?: continue
            assertTrue("no parser for $key", script.supports(key))
        }
        assertEquals(24, script.supportedKeys.size)
    }
}
