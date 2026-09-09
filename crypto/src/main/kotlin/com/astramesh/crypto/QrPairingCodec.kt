package com.astramesh.crypto

import com.astramesh.common.ByteUtils
import java.security.MessageDigest
import java.util.Base64

/**
 * Compact payload representation for Out-Of-Band (OOB) QR code pairing.
 */
data class QrPairingPayload(
    val version: Int,
    val rootPublicKey: AstraPublicKey,
    val displayName: String,
    val sasFingerprint: String
)

/**
 * Serializer and Short Authentication String (SAS) generator for QR verification.
 */
object QrPairingCodec {

    private const val QR_VERSION = 1

    fun generateSas(ourPublicKey: AstraPublicKey, theirPublicKey: AstraPublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Sort keys lexicographically so both parties compute the exact same SAS
        val sortedKeys = listOf(ourPublicKey.rawBytes, theirPublicKey.rawBytes)
            .sortedWith { a, b ->
                for (i in 0 until minOf(a.size, b.size)) {
                    val comp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
                    if (comp != 0) return@sortedWith comp
                }
                a.size.compareTo(b.size)
            }

        digest.update(sortedKeys[0])
        digest.update(sortedKeys[1])
        val hash = digest.digest()

        // Produce a 6-digit verification code
        val num = ((hash[0].toInt() and 0x7F) shl 24) or
                ((hash[1].toInt() and 0xFF) shl 16) or
                ((hash[2].toInt() and 0xFF) shl 8) or
                (hash[3].toInt() and 0xFF)

        return String.format("%06d", num % 1_000_000)
    }

    fun encode(payload: QrPairingPayload): String {
        val nameBytes = payload.displayName.toByteArray(Charsets.UTF_8)
        val buffer = java.nio.ByteBuffer.allocate(1 + 2 + payload.rootPublicKey.rawBytes.size + 2 + nameBytes.size)

        buffer.put(payload.version.toByte())
        buffer.putShort(payload.rootPublicKey.rawBytes.size.toShort())
        buffer.put(payload.rootPublicKey.rawBytes)
        buffer.putShort(nameBytes.size.toShort())
        buffer.put(nameBytes)

        return "ASTRA:" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array())
    }

    fun decode(qrString: String): QrPairingPayload {
        require(qrString.startsWith("ASTRA:")) { "Invalid QR prefix, expected ASTRA:" }
        val rawBase64 = qrString.substring("ASTRA:".length)
        val data = Base64.getUrlDecoder().decode(rawBase64)
        val buffer = java.nio.ByteBuffer.wrap(data)

        val version = buffer.get().toInt() and 0xFF
        val keyLen = buffer.short.toInt() and 0xFFFF
        val keyBytes = ByteArray(keyLen)
        buffer.get(keyBytes)

        val nameLen = buffer.short.toInt() and 0xFFFF
        val nameBytes = ByteArray(nameLen)
        buffer.get(nameBytes)
        val displayName = String(nameBytes, Charsets.UTF_8)

        val pubKey = AstraPublicKey(keyBytes)
        // Generate SAS with itself as placeholder until counterpart public key is known
        val sas = generateSas(pubKey, pubKey)

        return QrPairingPayload(
            version = version,
            rootPublicKey = pubKey,
            displayName = displayName,
            sasFingerprint = sas
        )
    }
}
