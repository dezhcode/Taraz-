package com.example

import com.example.services.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The base URL decides where every balance, loan figure and chat message is
 * sent. These cases are the reason it is not a free-text field any more.
 *
 * Plain JUnit on purpose: [ApiConfig] uses java.net.URI, not android.net.Uri,
 * so the rule can be exercised without an emulator or Robolectric.
 */
class ApiConfigAllowlistTest {

    private fun accepts(url: String): Boolean = ApiConfig.isAllowed(ApiConfig.sanitizeUrl(url))

    @Test
    fun `the production address is accepted`() {
        assertTrue(accepts("https://dezhcode.pyho.ir/taraz"))
    }

    @Test
    fun `a trailing slash does not matter`() {
        assertTrue(accepts("https://dezhcode.pyho.ir/taraz/"))
    }

    @Test
    fun `http is upgraded to https rather than refused`() {
        assertEquals("https://dezhcode.pyho.ir/taraz", ApiConfig.sanitizeUrl("http://dezhcode.pyho.ir/taraz"))
        assertTrue(accepts("http://dezhcode.pyho.ir/taraz"))
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(accepts("https://DEZHCODE.PYHO.IR/taraz"))
    }

    @Test
    fun `the chat endpoint is trimmed back to the base`() {
        assertEquals("https://dezhcode.pyho.ir/taraz", ApiConfig.sanitizeUrl("https://dezhcode.pyho.ir/taraz/c/api/chat"))
    }

    @Test
    fun `a foreign host is refused`() {
        assertFalse(accepts("https://evil.com/taraz"))
    }

    @Test
    fun `a subdomain suffix attack is refused`() {
        // The old code used contains(); this is the string it let through.
        assertFalse(accepts("https://dezhcode.pyho.ir.evil.com/taraz"))
    }

    @Test
    fun `userinfo cannot disguise a foreign host`() {
        assertFalse(accepts("https://dezhcode.pyho.ir@evil.com/taraz"))
    }

    @Test
    fun `the right host with the wrong path is refused`() {
        assertFalse(accepts("https://dezhcode.pyho.ir/"))
        assertFalse(accepts("https://dezhcode.pyho.ir/admin"))
    }

    @Test
    fun `blank and malformed input are refused`() {
        assertFalse(accepts(""))
        assertFalse(accepts("   "))
        assertFalse(accepts("not a url at all"))
    }
}
