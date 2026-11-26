package com.pocketworker.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

class AiProxyConfigTest {

    @Test
    fun `parses http proxy with user, password, ip, and port`() {
        val res = parseAiProxyUrl("http://alice:s3cr3t@185.10.20.30:8080")
        assertTrue(res is ProxyParseResult.Valid)
        val cfg = (res as ProxyParseResult.Valid).config
        assertEquals(Proxy.Type.HTTP, cfg.type)
        assertEquals("185.10.20.30", cfg.host)
        assertEquals(8080, cfg.port)
        assertEquals("alice", cfg.username)
        assertEquals("s3cr3t", cfg.password)
        assertNotNull(cfg.basicAuthorizationHeader())
        assertTrue(cfg.basicAuthorizationHeader()!!.startsWith("Basic "))
    }

    @Test
    fun `parses http proxy without explicit port and handles password with at-sign`() {
        val res = parseAiProxyUrl("http://bob:p@ss:123@10.0.0.5")
        assertTrue(res is ProxyParseResult.Valid)
        val cfg = (res as ProxyParseResult.Valid).config
        assertEquals(Proxy.Type.HTTP, cfg.type)
        assertEquals("10.0.0.5", cfg.host)
        assertEquals(80, cfg.port)
        assertEquals("bob", cfg.username)
        assertEquals("p@ss:123", cfg.password)
    }

    @Test
    fun `parses socks5 proxy and host without credentials`() {
        val socks = (parseAiProxyUrl("socks5://user:pass@proxy.local:1080") as ProxyParseResult.Valid).config
        assertEquals(Proxy.Type.SOCKS, socks.type)
        assertEquals("proxy.local", socks.host)
        assertEquals(1080, socks.port)
        assertNull(socks.basicAuthorizationHeader())

        val plain = (parseAiProxyUrl("192.168.1.2:3128") as ProxyParseResult.Valid).config
        assertEquals(Proxy.Type.HTTP, plain.type)
        assertEquals("192.168.1.2", plain.host)
        assertEquals(3128, plain.port)
        assertNull(plain.username)
    }

    @Test
    fun `rejects empty, unsupported scheme, or out-of-range port`() {
        assertTrue(parseAiProxyUrl("") is ProxyParseResult.Invalid)
        assertTrue(parseAiProxyUrl("ftp://1.2.3.4:21") is ProxyParseResult.Invalid)
        assertTrue(parseAiProxyUrl("http://1.2.3.4:99999") is ProxyParseResult.Invalid)
        assertTrue(parseAiProxyUrl("http://user:pass@") is ProxyParseResult.Invalid)
    }
}
