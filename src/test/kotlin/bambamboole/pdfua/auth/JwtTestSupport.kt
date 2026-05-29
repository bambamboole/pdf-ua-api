package bambamboole.pdfua.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

/**
 * Test-only JWT utilities. Generates an RSA key pair, exposes an in-memory
 * JwkProvider backed by its public key, and signs tokens with the private key.
 * Production code never signs tokens; only tests do.
 */
object JwtTestSupport {
    const val KEY_ID = "test-key-id"

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    private val wrongKeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val wrongPublicKey = wrongKeyPair.public as RSAPublicKey
    private val wrongPrivateKey = wrongKeyPair.private as RSAPrivateKey

    val jwkProvider: JwkProvider =
        JwkProvider { keyId ->
            require(keyId == KEY_ID) { "Unknown key id: $keyId" }
            Jwk.fromValues(
                mapOf(
                    "kty" to "RSA",
                    "kid" to KEY_ID,
                    "alg" to "RS256",
                    "use" to "sig",
                    "n" to base64Url(publicKey.modulus),
                    "e" to base64Url(publicKey.publicExponent),
                ),
            )
        }

    fun token(
        issuer: String,
        audience: String? = null,
        expiresAt: Date? = Date(System.currentTimeMillis() + 3_600_000),
    ): String {
        val builder =
            JWT
                .create()
                .withKeyId(KEY_ID)
                .withIssuer(issuer)
                .withSubject("test-subject")
        audience?.let { builder.withAudience(it) }
        expiresAt?.let { builder.withExpiresAt(it) }
        return builder.sign(Algorithm.RSA256(publicKey, privateKey))
    }

    /**
     * A token whose `kid` matches the published key but is signed with a different
     * private key, so signature verification fails.
     */
    fun tokenWithWrongSignature(issuer: String): String =
        JWT
            .create()
            .withKeyId(KEY_ID)
            .withIssuer(issuer)
            .withSubject("test-subject")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.RSA256(wrongPublicKey, wrongPrivateKey))

    private fun base64Url(value: BigInteger): String {
        var bytes = value.toByteArray()
        if (bytes.size > 1 && bytes[0].toInt() == 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
