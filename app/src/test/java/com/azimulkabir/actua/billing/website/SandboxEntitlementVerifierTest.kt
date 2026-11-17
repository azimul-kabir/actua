package com.azimulkabir.actua.billing.website

import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class SandboxEntitlementVerifierTest {
    private val verifier = SandboxEntitlementVerifier()

    @Test fun rejectsUnsignedAndMalformedProofs() {
        listOf("", "a.b", "a.b.c.d", "a.b.=", "x".repeat(8193)).forEach { token ->
            assertThrows(Exception::class.java) { verifier.verify(token, 1000) }
        }
    }

    @Test fun rejectsAlgorithmAndKeySubstitution() {
        listOf(
            """{"alg":"none","typ":"JWT","kid":"sandbox-1"}""",
            """{"alg":"HS256","typ":"JWT","kid":"sandbox-1"}""",
            """{"alg":"ES256","typ":"JWT","kid":"attacker"}""",
        ).forEach { header ->
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
            assertThrows(IllegalArgumentException::class.java) { verifier.verify("$encoded.e30.AA", 1000) }
        }
    }

    @Test fun rejectsForgedSignatureWithOtherwiseEligibleClaims() {
        fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val header = encode("""{"alg":"ES256","typ":"JWT","kid":"sandbox-1"}""")
        val claims = encode("""{"iss":"https://actua.azimulkabir.com/paddle-sandbox","aud":"actua-supporter-sandbox","environment":"sandbox","sub":"synthetic","iat":900,"nbf":900,"exp":1100,"extras":["supporter"]}""")
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(64) { 1 })
        assertThrows(Exception::class.java) { verifier.verify("$header.$claims.$signature", 1000) }
    }

    @Test fun verifiesSignedProofAndRejectsExpiryAndClaimSubstitution() {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val keys = generator.generateKeyPair()
        val subject = SandboxEntitlementVerifier(keys.public)
        fun token(environment: String = "sandbox", expiry: Long = 1100): String {
            fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val header = encode("""{"alg":"ES256","typ":"JWT","kid":"sandbox-1"}""".toByteArray())
            val claims = encode("""{"iss":"https://actua.azimulkabir.com/paddle-sandbox","aud":"actua-supporter-sandbox","environment":"$environment","sub":"synthetic","iat":900,"nbf":900,"exp":$expiry,"extras":["supporter"]}""".toByteArray())
            val unsigned = "$header.$claims"
            val signer = java.security.Signature.getInstance("SHA256withECDSA")
            signer.initSign(keys.private)
            signer.update(unsigned.toByteArray())
            val der = signer.sign()
            var offset = 2
            fun integer(): ByteArray {
                check(der[offset++].toInt() == 2)
                val size = der[offset++].toInt() and 255
                val bytes = der.copyOfRange(offset, offset + size)
                offset += size
                val raw = ByteArray(32)
                val value = java.math.BigInteger(1, bytes).toByteArray().takeLast(32).toByteArray()
                value.copyInto(raw, 32 - value.size)
                return raw
            }
            val signature = integer() + integer()
            return "$unsigned.${encode(signature)}"
        }
        org.junit.Assert.assertEquals(1100L, subject.verify(token(), 1000))
        assertThrows(IllegalArgumentException::class.java) { subject.verify(token(), 1100) }
        assertThrows(IllegalArgumentException::class.java) { subject.verify(token(), 899) }
        assertThrows(IllegalArgumentException::class.java) { subject.verify(token("production"), 1000) }
        assertThrows(IllegalArgumentException::class.java) { subject.verify(token(expiry = 90000), 1000) }
    }
}
