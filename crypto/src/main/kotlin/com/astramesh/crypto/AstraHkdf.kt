package com.astramesh.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF) using SHA-256.
 */
object AstraHkdf {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(actualSalt, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "Cannot expand to more than 255 * HASH_LEN bytes" }

        val n = ceil(length.toDouble() / HASH_LEN).toInt()
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var bytesWritten = 0

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()

            val toCopy = minOf(HASH_LEN, length - bytesWritten)
            System.arraycopy(t, 0, okm, bytesWritten, toCopy)
            bytesWritten += toCopy
        }

        return okm
    }

    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
