package com.example.logic

import com.example.data.PriceCatalog
import com.example.data.PriceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** فهرست صندوق‌های فیپیران: قیمت ابطال (ریال)، نماد، کد بورس؛ صندوق بی‌قیمت کنار می‌رود. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FundDirectoryTest {

    @Test
    fun readsNavSymbolAndCodes() {
        val json = """
            {"items":[
              {"regNo":"11477","name":"صندوق سرمايه گذاري طلاي عيار","smallSymbolName":"عيار","insCode":"34144395039913458",
               "cancelNav":213000,"issueNav":214500,"dailyEfficiency":0.62},
              {"regNo":"10589","name":"صندوق درآمد ثابت نمونه","smallSymbolName":null,"insCode":null,"statisticalNav":"1,050,000"},
              {"regNo":"99999","name":"صندوق بی‌قیمت","cancelNav":0}
            ]}
        """.trimIndent()
        val list = PriceService.parseFunds(json)
        assertEquals(2, list.size)
        val ayar = list[0]
        assertEquals("عیار", ayar.symbol)
        assertTrue(ayar.name.contains("طلای عیار"))
        assertEquals("34144395039913458", ayar.insCode)
        assertEquals(213_000.0, ayar.navRial, 0.0)
        assertEquals(0.62, ayar.changePct!!, 1e-9)
        val fixed = list[1]
        assertEquals("", fixed.insCode)
        assertEquals(1_050_000.0, fixed.navRial, 0.0)
        assertNull(fixed.changePct)
        assertTrue(PriceCatalog.isFipiran("fund:fip:10589") && PriceCatalog.isFund("fund:fip:10589"))
        assertEquals("10589", PriceCatalog.fipRegNo("fund:fip:10589"))
    }

    @Test
    fun acceptsAPlainArray() {
        assertEquals(1, PriceService.parseFunds("""[{"regNo":"1","name":"الف","cancelNav":10}]""").size)
    }
}
