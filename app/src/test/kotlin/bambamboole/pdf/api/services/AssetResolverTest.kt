package bambamboole.pdf.api.services

import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AssetResolverTest {

    private val resolver = AssetResolver(
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    )

    @Test
    fun blocksLocalhostIp() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://127.0.0.1/file"))
        }
    }

    @Test
    fun blocksLocalhostHostname() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://localhost/file"))
        }
    }

    @Test
    fun blocksPrivateIp10() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://10.0.0.1/file"))
        }
    }

    @Test
    fun blocksPrivateIp172() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://172.16.0.1/file"))
        }
    }

    @Test
    fun blocksPrivateIp192() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://192.168.1.1/file"))
        }
    }

    @Test
    fun blocksLinkLocalIp() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("https://169.254.1.1/file"))
        }
    }

    @Test
    fun blocksFileScheme() {
        assertFailsWith<IllegalArgumentException> {
            resolver.validateUrl(URI.create("file:///etc/passwd"))
        }
    }

    @Test
    fun blocksNonWhitelistedDomain() {
        val restricted = AssetResolver(
            httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            allowedDomains = setOf("cdn.example.com")
        )
        assertFailsWith<IllegalArgumentException> {
            restricted.validateUrl(URI.create("https://evil.com/file"))
        }
    }

    @Test
    fun allowsWhitelistedDomain() {
        val restricted = AssetResolver(
            httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            allowedDomains = setOf("example.com")
        )
        restricted.validateUrl(URI.create("https://example.com/file"))
    }

    @Test
    fun allowsPublicDomainWhenWhitelistEmpty() {
        resolver.validateUrl(URI.create("https://example.com/file"))
    }
}
