package com.astramesh.crypto

import com.astramesh.common.ByteUtils
import java.nio.ByteBuffer

/**
 * Derives anonymous, rotating 8-byte ephemeral node IDs to shield the peer's permanent
 * identity key from physical radio surveillance over Bluetooth Low Energy.
 */
object EphemeralIdDeriver {

    private const val EPOCH_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    private val INFO_PREFIX = "AstraMesh-Rotating-NodeId-v1".toByteArray(Charsets.UTF_8)

    fun getCurrentEpoch(timestampMillis: Long = System.currentTimeMillis()): Long {
        return timestampMillis / EPOCH_DURATION_MS
    }

    fun deriveEphemeralId(rootPrivateKey: AstraPrivateKey, epoch: Long): Long {
        val epochBuffer = ByteBuffer.allocate(8).putLong(epoch).array()
        val info = ByteUtils.concat(INFO_PREFIX, epochBuffer)

        val derivedBytes = AstraHkdf.deriveKey(
            salt = null,
            ikm = rootPrivateKey.rawBytes,
            info = info,
            length = 8
        )

        return ByteUtils.getUInt64BE(derivedBytes, 0)
    }

    fun verifyEphemeralId(
        candidateId: Long,
        senderRootPublicKey: AstraPublicKey,
        sharedSecret: ByteArray,
        epoch: Long
    ): Boolean {
        val epochBuffer = ByteBuffer.allocate(8).putLong(epoch).array()
        val info = ByteUtils.concat(INFO_PREFIX, epochBuffer, senderRootPublicKey.rawBytes)

        val expectedBytes = AstraHkdf.deriveKey(
            salt = null,
            ikm = sharedSecret,
            info = info,
            length = 8
        )

        return ByteUtils.getUInt64BE(expectedBytes, 0) == candidateId
    }
}
