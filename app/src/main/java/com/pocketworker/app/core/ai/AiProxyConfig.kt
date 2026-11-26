package com.pocketworker.app.core.ai

import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class AiProxyConfig(
    val type: Proxy.Type,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
) {
    fun toJavaProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))

    fun basicAuthorizationHeader(): String? {
        if (type != Proxy.Type.HTTP) return null
        val user = username ?: return null
        val raw = "$user:${password.orEmpty()}".toByteArray(StandardCharsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    fun redactedDisplay(): String {
        val scheme = if (type == Proxy.Type.SOCKS) "socks5" else "http"
        return "$scheme://$host:$port"
    }
}

sealed interface ProxyParseResult {
    data class Valid(val config: AiProxyConfig) : ProxyParseResult
    data class Invalid(val message: String) : ProxyParseResult
}

/**
 * Parses proxy addresses such as:
 * - `http://user:password@1.2.3.4:8080`
 * - `http://user:password@1.2.3.4`
 * - `https://user:password@proxy.example.com:8443`
 * - `socks5://user:password@1.2.3.4:1080`
 * - `1.2.3.4:8080`
 */
fun parseAiProxyUrl(rawInput: String): ProxyParseResult {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
        return ProxyParseResult.Invalid("Укажите адрес прокси, например http://user:password@ip:port")
    }

    val schemeSepIdx = trimmed.indexOf("://")
    val (scheme, restWithPath) = if (schemeSepIdx >= 0) {
        trimmed.substring(0, schemeSepIdx).lowercase(Locale.ROOT) to trimmed.substring(schemeSepIdx + 3)
    } else {
        "http" to trimmed
    }

    val proxyType = when (scheme) {
        "http", "https" -> Proxy.Type.HTTP
        "socks", "socks5", "socks5h", "socks4" -> Proxy.Type.SOCKS
        else -> return ProxyParseResult.Invalid("Неподдерживаемая схема прокси «$scheme». Используйте http:// или socks5://.")
    }

    val authority = restWithPath.substringBefore('/').substringBefore('?').substringBefore('#').trim()
    if (authority.isEmpty()) {
        return ProxyParseResult.Invalid("Не удалось определить адрес хоста прокси.")
    }

    // Use lastIndexOf('@') so passwords containing an unencoded '@' still parse cleanly
    val atIdx = authority.lastIndexOf('@')
    val userInfoRaw = if (atIdx >= 0) authority.substring(0, atIdx) else null
    val hostPortRaw = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

    if (hostPortRaw.isBlank()) {
        return ProxyParseResult.Invalid("Не указан IP или домен прокси-сервера.")
    }

    val defaultPort = when (scheme) {
        "https" -> 443
        "socks", "socks5", "socks5h", "socks4" -> 1080
        else -> 80
    }

    val (host, port) = if (hostPortRaw.startsWith("[")) {
        // IPv6 literal: [::1]:8080 or [::1]
        val closeBracket = hostPortRaw.indexOf(']')
        if (closeBracket < 0) {
            return ProxyParseResult.Invalid("Некорректный IPv6-адрес прокси.")
        }
        val h = hostPortRaw.substring(1, closeBracket)
        val after = hostPortRaw.substring(closeBracket + 1)
        val p = if (after.startsWith(":")) {
            after.substring(1).toIntOrNull()
                ?: return ProxyParseResult.Invalid("Некорректный порт прокси.")
        } else {
            defaultPort
        }
        h to p
    } else {
        val colonIdx = hostPortRaw.lastIndexOf(':')
        if (colonIdx > 0) {
            val h = hostPortRaw.substring(0, colonIdx).trim()
            val p = hostPortRaw.substring(colonIdx + 1).trim().toIntOrNull()
                ?: return ProxyParseResult.Invalid("Некорректный порт прокси.")
            h to p
        } else {
            hostPortRaw.trim() to defaultPort
        }
    }

    if (host.isEmpty() || host.any { it.isWhitespace() }) {
        return ProxyParseResult.Invalid("Некорректный хост прокси-сервера.")
    }
    if (port !in 1..65535) {
        return ProxyParseResult.Invalid("Порт прокси должен быть в диапазоне 1..65535.")
    }

    var username: String? = null
    var password: String? = null
    if (!userInfoRaw.isNullOrEmpty()) {
        val sep = userInfoRaw.indexOf(':')
        if (sep >= 0) {
            username = decodePercentSafe(userInfoRaw.substring(0, sep))
            password = decodePercentSafe(userInfoRaw.substring(sep + 1))
        } else {
            username = decodePercentSafe(userInfoRaw)
            password = ""
        }
    }

    return ProxyParseResult.Valid(
        AiProxyConfig(
            type = proxyType,
            host = host,
            port = port,
            username = username,
            password = password
        )
    )
}

private fun decodePercentSafe(value: String): String {
    if ('%' !in value) return value
    return runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}

/**
 * Installs a JVM-wide [Authenticator] that responds strictly to [Authenticator.RequestorType.PROXY]
 * challenges (HTTP 407 on HTTPS CONNECT tunnels and SOCKS5 auth) for active [AiProxyConfig] entries.
 */
internal object AiProxyAuthenticatorRegistry {
    private val credentialsByHostPort = ConcurrentHashMap<String, PasswordAuthentication>()
    @Volatile
    private var installed = false

    fun register(config: AiProxyConfig) {
        val user = config.username ?: return
        ensureInstalled()
        val key = keyOf(config.host, config.port)
        credentialsByHostPort[key] = PasswordAuthentication(user, (config.password ?: "").toCharArray())
    }

    private fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            // Allow Basic authentication over HTTPS CONNECT proxy tunnels on JVM/Android
            runCatching { System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "") }
            runCatching { System.setProperty("jdk.http.auth.proxying.disabledSchemes", "") }
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestorType != RequestorType.PROXY) return null
                    val host = requestingHost?.lowercase(Locale.ROOT).orEmpty()
                    val port = requestingPort
                    credentialsByHostPort[keyOf(host, port)]?.let { return it }
                    // Fallback by host alone if port is unspecified (-1) by some SOCKS/HTTP stacks
                    return credentialsByHostPort.entries
                        .firstOrNull { it.key.startsWith("$host:") }
                        ?.value
                }
            })
            installed = true
        }
    }

    private fun keyOf(host: String, port: Int): String = "${host.lowercase(Locale.ROOT)}:$port"
}
