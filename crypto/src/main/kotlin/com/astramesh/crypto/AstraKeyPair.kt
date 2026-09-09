package com.astramesh.crypto

import com.astramesh.common.ByteUtils
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Public key representation in AstraMesh.
 */
data class AstraPublicKey(val rawBytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AstraPublicKey) return false
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int = rawBytes.contentHashCode()

    fun toHex(): String = ByteUtils.toHexString(rawBytes)

    companion object {
        fun fromHex(hex: String): AstraPublicKey = AstraPublicKey(ByteUtils.hexToByteArray(hex))
    }
}

/**
 * Private key representation with secure memory zeroization.
 */
data class AstraPrivateKey(val rawBytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AstraPrivateKey) return false
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int = rawBytes.contentHashCode()

    fun destroy() {
        ByteUtils.zeroize(rawBytes)
    }
}

/**
 * Key pair holding public and private keys.
 */
data class AstraKeyPair(
    val publicKey: AstraPublicKey,
    val privateKey: AstraPrivateKey
) {
    companion object {
        private const val EC_CURVE = "secp256r1"

        fun generate(): AstraKeyPair {
            val keyGen = KeyPairGenerator.getInstance("EC")
            keyGen.initialize(ECGenParameterSpec(EC_CURVE))
            val kp = keyGen.generateKeyPair()

            return AstraKeyPair(
                publicKey = AstraPublicKey(kp.public.encoded),
                privateKey = AstraPrivateKey(kp.private.encoded)
            )
        }

        fun fromEncoded(publicEncoded: ByteArray, privateEncoded: ByteArray): AstraKeyPair {
            return AstraKeyPair(
                publicKey = AstraPublicKey(publicEncoded),
                privateKey = AstraPrivateKey(privateEncoded)
            )
        }
    }

    fun toJavaPublicKey(): ECPublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(publicKey.rawBytes)) as ECPublicKey
    }

    fun toJavaPrivateKey(): ECPrivateKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey.rawBytes)) as ECPrivateKey
    }
}
