package com.example

import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.data.isTransfer
import com.example.data.netFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transfers used to be counted twice — once as spending, once as income — so the
 * month's totals moved by money that never left the user. These pin the fix.
 */
class TransactionTypeTest {

    private fun tx(amount: Long, isExpense: Boolean, type: String, category: String = "سایر") =
        Transaction(
            title = "t", amount = amount, category = category,
            isExpense = isExpense, bankName = "بانک ملی", type = type
        )

    @Test
    fun ordinaryRowsCountNormally() {
        val expense = tx(1000, true, TransactionType.EXPENSE)
        val income = tx(2000, false, TransactionType.INCOME)
        assertTrue(expense.countsAsExpense)
        assertFalse(expense.countsAsIncome)
        assertTrue(income.countsAsIncome)
        assertFalse(income.countsAsExpense)
    }

    @Test
    fun transferCountsAsNeitherSide() {
        val out = tx(5_000_000, true, TransactionType.TRANSFER, "انتقال")
        val into = tx(5_000_000, false, TransactionType.TRANSFER, "انتقال")
        assertTrue(out.isTransfer)
        assertFalse(out.countsAsExpense)
        assertFalse(into.countsAsIncome)
    }

    @Test
    fun aTransferDoesNotMoveTheMonthlyTotals() {
        val ledger = listOf(
            tx(3_000_000, true, TransactionType.EXPENSE),
            tx(10_000_000, false, TransactionType.INCOME),
            tx(5_000_000, true, TransactionType.TRANSFER, "انتقال"),
            tx(5_000_000, false, TransactionType.TRANSFER, "انتقال")
        )
        val expense = ledger.filter { it.countsAsExpense }.sumOf { it.amount }
        val income = ledger.filter { it.countsAsIncome }.sumOf { it.amount }
        assertEquals(3_000_000L, expense)
        assertEquals(10_000_000L, income)
    }

    @Test
    fun transfersContributeNothingToNetWorth() {
        val out = tx(5_000_000, true, TransactionType.TRANSFER, "انتقال")
        val into = tx(5_000_000, false, TransactionType.TRANSFER, "انتقال")
        assertEquals(0L, out.netFlow)
        assertEquals(0L, into.netFlow)
        assertEquals(-1000L, tx(1000, true, TransactionType.EXPENSE).netFlow)
        assertEquals(2000L, tx(2000, false, TransactionType.INCOME).netFlow)
    }

    @Test
    fun transfersStayOutOfTheCategoryBreakdown() {
        val ledger = listOf(
            tx(1_000_000, true, TransactionType.EXPENSE, "غذا"),
            tx(5_000_000, true, TransactionType.TRANSFER, "انتقال")
        )
        val breakdown = ledger.filter { it.countsAsExpense }.groupBy { it.category }
        assertTrue(breakdown.containsKey("غذا"))
        assertFalse(breakdown.containsKey("انتقال"))
    }

    @Test
    fun defaultTypeIsExpenseSoOldCallSitesStayValid() {
        val t = Transaction(
            title = "t", amount = 1, category = "سایر", isExpense = true, bankName = "b"
        )
        assertEquals(TransactionType.EXPENSE, t.type)
        assertEquals(null, t.transferGroupId)
    }
}
