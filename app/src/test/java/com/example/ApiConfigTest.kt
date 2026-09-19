package com.example

import com.example.services.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {

    @Test
    fun `getBaseUrl returns exact default base URL`() {
        assertEquals("https://dezhcode.pyho.ir/taraz", ApiConfig.getBaseUrl())
    }

    @Test
    fun `getHttpUrl formats endpoint with or without leading slash correctly`() {
        assertEquals("https://dezhcode.pyho.ir/taraz/c/api/chat", ApiConfig.getHttpUrl("c/api/chat"))
        assertEquals("https://dezhcode.pyho.ir/taraz/c/api/chat", ApiConfig.getHttpUrl("/c/api/chat"))
    }
}
