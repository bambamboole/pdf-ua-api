package bambamboole.pdfua.services

import java.net.InetAddress
import java.net.URI

/**
 * Shared SSRF guard for outbound user-supplied URLs (asset fetching and document upload).
 * Rejects non-http(s) schemes, hosts outside an optional allowlist, and any host that
 * resolves to a loopback, private, link-local, or wildcard address.
 */
internal fun validatePublicHttpUrl(
    uri: URI,
    allowedDomains: Set<String>,
) {
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "Only http/https schemes are allowed, got: $scheme"
    }

    val host = uri.host ?: throw IllegalArgumentException("URL has no host: $uri")

    if (allowedDomains.isNotEmpty()) {
        require(host.lowercase() in allowedDomains) {
            "Domain not in allowed list: $host"
        }
    }

    val addresses = InetAddress.getAllByName(host)
    for (addr in addresses) {
        require(!addr.isLoopbackAddress) { "Loopback addresses are blocked: $host" }
        require(!addr.isSiteLocalAddress) { "Private network addresses are blocked: $host" }
        require(!addr.isLinkLocalAddress) { "Link-local addresses are blocked: $host" }
        require(!addr.isAnyLocalAddress) { "Wildcard addresses are blocked: $host" }
    }
}
