package com.azimulkabir.actua.billing.website

import org.json.JSONObject
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/** Verifies sandbox purchase proofs. Callers must gate sandbox activation to test builds. */
internal class SandboxEntitlementVerifier(private val trustedKey: java.security.PublicKey? = null) {
    fun verify(token: String, nowSeconds: Long): Long {
        require(token.length <= 8192)
        val parts = token.split('.')
        require(parts.size == 3 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val header = JSONObject(String(decode(parts[0]), Charsets.UTF_8))
        require(header.getString("alg") == "ES256")
        require(header.getString("typ") == "JWT")
        require(header.getString("kid") == "sandbox-1")
        require(!header.has("crit"))
        val raw = decode(parts[2])
        require(raw.size == 64)
        val r = BigInteger(1, raw.copyOfRange(0, 32)).toByteArray()
        val s = BigInteger(1, raw.copyOfRange(32, 64)).toByteArray()
        val der = byteArrayOf(0x30, (4 + r.size + s.size).toByte(), 0x02, r.size.toByte()) +
            r + byteArrayOf(0x02, s.size.toByte()) + s
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(trustedKey ?: publicKey)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        require(verifier.verify(der))
        val claims = JSONObject(String(decode(parts[1]), Charsets.UTF_8))
        require(claims.getString("iss") == "https://actua.azimulkabir.com/paddle-sandbox")
        require(claims.getString("aud") == "actua-supporter-sandbox")
        require(claims.getString("environment") == "sandbox")
        require(claims.getString("sub").isNotBlank())
        val issued = claims.getLong("iat")
        val start = claims.getLong("nbf")
        val expiry = claims.getLong("exp")
        require(issued >= 0 && issued <= nowSeconds && start == issued)
        require(expiry > nowSeconds && expiry > issued && expiry - issued <= 86400)
        val extras = claims.getJSONArray("extras")
        require(extras.length() == 1 && extras.getString(0) == "supporter")
        return expiry
    }

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private val publicKey by lazy {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        val point = ECPoint(
            BigInteger(1, decode("YeveGgtkgMs5mkTCGRvRbbmBXRA5pswzixTKZnDv5h4")),
            BigInteger(1, decode("hSdCHgWZVRXetTg3YmJhZFxEuLZV7anzhlkyLBsH2D4")),
        )
        KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec::class.java)),
        )
    }
}
