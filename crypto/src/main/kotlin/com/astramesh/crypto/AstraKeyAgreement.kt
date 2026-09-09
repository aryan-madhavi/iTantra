package com.astramesh.crypto

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Elliptic Curve Diffie-Hellman key agreement calculation.
 */
object AstraKeyAgreement {

    fun calculateSharedSecret(ourPrivateKey: AstraPrivateKey, theirPublicKey: AstraPublicKey): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(ourPrivateKey.rawBytes)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)

        val publicKeySpec = X509EncodedKeySpec(theirPublicKey.rawBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)

        return keyAgreement.generateSecret()
    }
}
