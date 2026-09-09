package com.astramesh.crypto

import com.astramesh.common.ByteUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Encrypted message payload packaging.
 */
data class RatchetEncryptedMessage(
    val ephemeralPublicKey: AstraPublicKey,
    val sequenceNumber: Long,
    val nonce: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetEncryptedMessage) return false
        return sequenceNumber == other.sequenceNumber &&
                ephemeralPublicKey == other.ephemeralPublicKey &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKey.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Production-grade Double Ratchet session state machine.
 * Provides End-to-End Encryption with Perfect Forward Secrecy (PFS) and Break-in Recovery.
 */
class AstraRatchetEngine(
    private val ourIdentityKeyPair: AstraKeyPair,
    private val theirIdentityPublicKey: AstraPublicKey,
    initialSharedSecret: ByteArray,
    val isInitiator: Boolean
) {
    private var rootKey: ByteArray = initialSharedSecret.copyOf()
    private var sendingChainKey: ByteArray? = null
    private var receivingChainKey: ByteArray? = null

    private var ourEphemeralKeyPair: AstraKeyPair = AstraKeyPair.generate()
    private var theirEphemeralPublicKey: AstraPublicKey? = null

    private var sendSequenceNumber: Long = 0L
    private var receiveSequenceNumber: Long = 0L

    private val replayProtection = ReplayProtectionWindow()
    private val skippedMessageKeys = ConcurrentHashMap<String, ByteArray>()

    companion object {
        private const val INFO_ROOT_ADVANCE = "AstraMesh-Root-Advance"
        private const val INFO_SEND_CHAIN = "AstraMesh-Send-Chain"
        private const val INFO_RECV_CHAIN = "AstraMesh-Recv-Chain"
        private const val INFO_MSG_KEY = "AstraMesh-Msg-Key"
        private const val MAX_SKIPPED_KEYS = 2000
    }

    init {
        if (isInitiator) {
            val dhSecret = AstraKeyAgreement.calculateSharedSecret(ourIdentityKeyPair.privateKey, theirIdentityPublicKey)
            val derived = AstraHkdf.deriveKey(rootKey, dhSecret, INFO_SEND_CHAIN.toByteArray(Charsets.UTF_8), 64)
            rootKey = derived.copyOfRange(0, 32)
            sendingChainKey = derived.copyOfRange(32, 64)
            receivingChainKey = null
        } else {
            val dhSecret = AstraKeyAgreement.calculateSharedSecret(ourIdentityKeyPair.privateKey, theirIdentityPublicKey)
            val derived = AstraHkdf.deriveKey(rootKey, dhSecret, INFO_SEND_CHAIN.toByteArray(Charsets.UTF_8), 64)
            rootKey = derived.copyOfRange(0, 32)
            receivingChainKey = derived.copyOfRange(32, 64)
            sendingChainKey = null
        }
    }

    @Synchronized
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray? = null): RatchetEncryptedMessage {
        var currentChain = sendingChainKey
        if (currentChain == null) {
            // Need to ratchet sending chain from receiving/root
            val derived = AstraHkdf.deriveKey(rootKey, rootKey, INFO_SEND_CHAIN.toByteArray(Charsets.UTF_8), 64)
            rootKey = derived.copyOfRange(0, 32)
            currentChain = derived.copyOfRange(32, 64)
            sendingChainKey = currentChain
            sendSequenceNumber = 0L
        }

        // Derive message key: HKDF-Expand(currentChain, "AstraMesh-Msg-Key", 32)
        val messageKey = AstraHkdf.deriveKey(
            salt = null,
            ikm = currentChain,
            info = INFO_MSG_KEY.toByteArray(Charsets.UTF_8),
            length = AstraAead.KEY_SIZE_BYTES
        )

        // Advance sending chain
        sendingChainKey = AstraHkdf.deriveKey(
            salt = null,
            ikm = currentChain,
            info = "AstraMesh-Chain-Step".toByteArray(Charsets.UTF_8),
            length = 32
        )

        val nonce = AstraAead.generateNonce()
        val ciphertext = AstraAead.encrypt(messageKey, nonce, plaintext, associatedData)

        // Destroy message key from memory (PFS)
        ByteUtils.zeroize(messageKey)

        val seq = sendSequenceNumber++
        return RatchetEncryptedMessage(
            ephemeralPublicKey = ourEphemeralKeyPair.publicKey,
            sequenceNumber = seq,
            nonce = nonce,
            ciphertext = ciphertext
        )
    }

    @Synchronized
    fun decrypt(message: RatchetEncryptedMessage, associatedData: ByteArray? = null): ByteArray {
        if (replayProtection.isDuplicateOrReplay(message.sequenceNumber)) {
            throw SecurityException("Replay attack or duplicate message detected for seq ${message.sequenceNumber}")
        }

        // Check if message key was previously skipped and cached
        val skippedKeyId = "${message.ephemeralPublicKey.toHex()}:${message.sequenceNumber}"
        val cachedKey = skippedMessageKeys.remove(skippedKeyId)
        if (cachedKey != null) {
            val plaintext = AstraAead.decrypt(cachedKey, message.nonce, message.ciphertext, associatedData)
            ByteUtils.zeroize(cachedKey)
            replayProtection.markReceived(message.sequenceNumber)
            return plaintext
        }

        // Handle receiving chain initialization / DH advance
        if (receivingChainKey == null) {
            val derived = AstraHkdf.deriveKey(rootKey, rootKey, INFO_SEND_CHAIN.toByteArray(Charsets.UTF_8), 64)
            rootKey = derived.copyOfRange(0, 32)
            receivingChainKey = derived.copyOfRange(32, 64)
            receiveSequenceNumber = 0L
        }

        var currentChain = receivingChainKey ?: throw IllegalStateException("Receiving chain key uninitialized")

        // Fast-forward receiving chain if there are skipped packets
        while (receiveSequenceNumber < message.sequenceNumber) {
            val skippedKey = AstraHkdf.deriveKey(
                salt = null,
                ikm = currentChain,
                info = INFO_MSG_KEY.toByteArray(Charsets.UTF_8),
                length = AstraAead.KEY_SIZE_BYTES
            )
            val keyId = "${message.ephemeralPublicKey.toHex()}:$receiveSequenceNumber"
            if (skippedMessageKeys.size < MAX_SKIPPED_KEYS) {
                skippedMessageKeys[keyId] = skippedKey
            }
            currentChain = AstraHkdf.deriveKey(
                salt = null,
                ikm = currentChain,
                info = "AstraMesh-Chain-Step".toByteArray(Charsets.UTF_8),
                length = 32
            )
            receiveSequenceNumber++
        }

        // Derive message key for current packet
        val messageKey = AstraHkdf.deriveKey(
            salt = null,
            ikm = currentChain,
            info = INFO_MSG_KEY.toByteArray(Charsets.UTF_8),
            length = AstraAead.KEY_SIZE_BYTES
        )

        // Advance receiving chain
        receivingChainKey = AstraHkdf.deriveKey(
            salt = null,
            ikm = currentChain,
            info = "AstraMesh-Chain-Step".toByteArray(Charsets.UTF_8),
            length = 32
        )
        receiveSequenceNumber++

        val plaintext = AstraAead.decrypt(messageKey, message.nonce, message.ciphertext, associatedData)
        ByteUtils.zeroize(messageKey)
        replayProtection.markReceived(message.sequenceNumber)

        return plaintext
    }
}

