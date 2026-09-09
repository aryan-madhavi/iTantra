package com.astramesh.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AEAD encryption using AES-256-GCM.
 */
object AstraAead {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    const val NONCE_SIZE_BYTES = 12
    const val TAG_SIZE_BITS = 128
    const val KEY_SIZE_BYTES = 32

    private val secureRandom = SecureRandom()

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key must be 32 bytes, got ${key.size}" }
        require(nonce.size == NONCE_SIZE_BYTES) { "GCM nonce must be 12 bytes, got ${nonce.size}" }

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, nonce)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(plaintext)
    }

    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key must be 32 bytes, got ${key.size}" }
        require(nonce.size == NONCE_SIZE_BYTES) { "GCM nonce must be 12 bytes, got ${nonce.size}" }

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, nonce)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertext)
    }
}
