package com.example.logic

import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.ui.layer.HomeTools
import com.example.ui.layer.TxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home layout storage and the transaction filter (Robolectric only because the tool list carries icons). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAndFilterTest {

    @Test
    fun aSavedLayoutComesBackInOrderAndNewSectionsAreAppended() {
        val saved = listOf("recent" to true, "glance" to false, "tools" to true)
        val back = HomeTools.decodeLayout(HomeTools.encodeLayout(saved))
        assertEquals(saved, back.take(3))
        // every section is present exactly once; the ones not saved come after, switched on
        assertEquals(HomeTools.sections.map { it.id }.toSet(), back.map { it.first }.toSet())
        assertEquals(back.size, back.map { it.first }.distinct().size)
        assertTrue(back.drop(3).all { it.second })
    }

    @Test
    fun unknownOrBrokenLayoutEntriesAreIgnored() {
        val back = HomeTools.decodeLayout("nonsense:1,glance:0,,today")
        assertEquals("glance" to false, back.first())
        assertFalse(back.any { it.first == "nonsense" })
        assertEquals(HomeTools.defaultLayout, HomeTools.decodeLayout(null))
    }

    @Test
    fun pinnedToolsKeepTheirOrderDropUnknownsAndRespectTheLimit() {
        assertEquals(HomeTools.defaultTools, HomeTools.decodeTools(null))
        assertEquals(emptyList<String>(), HomeTools.decodeTools(""))
        assertEquals(listOf("bills", "voice"), HomeTools.decodeTools("bills,ghost,voice,bills"))
        val many = HomeTools.all.joinToString(",") { it.id }
        assertEquals(HomeTools.MAX_PINNED, HomeTools.decodeTools(many).size)
    }

    private fun tx(amount: Long, type: String, category: String = "خوراک", bank: String = "ملت", tags: String = "", date: Long = 1_000L) =
        Transaction(
            title = "t", amount = amount, category = category, date = date,
            isExpense = type != TransactionType.INCOME, bankName = bank, type = type, tags = tags
        )

    @Test
    fun theFilterMatchesTypeCategoryAccountTagAmountAndDates() {
        val e = tx(300_000, TransactionType.EXPENSE, tags = "سفر,خانه", date = 5_000)
        val i = tx(9_000_000, TransactionType.INCOME, category = "حقوق")
        val t = tx(1_000_000, TransactionType.TRANSFER, category = "انتقال")

        assertTrue(TxFilter().matches(e) && TxFilter().matches(t))
        assertFalse(TxFilter().active)

        assertTrue(TxFilter(type = "expense").matches(e))
        assertFalse(TxFilter(type = "expense").matches(t))   // a transfer is not spending
        assertFalse(TxFilter(type = "income").matches(t))
        assertTrue(TxFilter(type = "transfer").matches(t))

        assertTrue(TxFilter(categories = setOf("خوراک")).matches(e))
        assertFalse(TxFilter(categories = setOf("خوراک")).matches(i))
        assertFalse(TxFilter(accounts = setOf("نقد")).matches(e))
        assertTrue(TxFilter(tag = "سفر").matches(e))
        assertFalse(TxFilter(tag = "سف").matches(e))          // whole tags only

        assertTrue(TxFilter(minAmount = 300_000, maxAmount = 300_000).matches(e))
        assertFalse(TxFilter(minAmount = 300_001).matches(e))
        assertFalse(TxFilter(maxAmount = 299_999).matches(e))

        assertTrue(TxFilter(from = 5_000, to = 5_001).matches(e))
        assertFalse(TxFilter(from = 5_001).matches(e))
        assertFalse(TxFilter(to = 5_000).matches(e))          // the end is exclusive
        assertTrue(TxFilter(type = "expense", tag = "خانه").active)
    }
}
