package com.astramesh.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoModuleTest {

    @Test
    fun `ecdh shared secret agreement produces identical secret on both peers`() {
        val aliceKey = AstraKeyPair.generate()
        val bobKey = AstraKeyPair.generate()

        val aliceShared = AstraKeyAgreement.calculateSharedSecret(aliceKey.privateKey, bobKey.publicKey)
        val bobShared = AstraKeyAgreement.calculateSharedSecret(bobKey.privateKey, aliceKey.publicKey)

        assertThat(aliceShared).isEqualTo(bobShared)
        assertThat(aliceShared.size).isGreaterThan(0)
    }

    @Test
    fun `hkdf extract and expand produces deterministic keys`() {
        val ikm = "InputKeyingMaterial".toByteArray(Charsets.UTF_8)
        val salt = "SaltValue12345".toByteArray(Charsets.UTF_8)
        val info = "ApplicationInfo".toByteArray(Charsets.UTF_8)

        val key1 = AstraHkdf.deriveKey(salt, ikm, info, 32)
        val key2 = AstraHkdf.deriveKey(salt, ikm, info, 32)

        assertThat(key1).hasLength(32)
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `aead encryption and decryption round trip succeeds with matching AAD`() {
        val key = AstraHkdf.deriveKey(null, "SecretMasterKey".toByteArray(Charsets.UTF_8), "aead-key".toByteArray(Charsets.UTF_8), 32)
        val nonce = AstraAead.generateNonce()
        val plaintext = "Hello AstraMesh Secure Decentralized World!".toByteArray(Charsets.UTF_8)
        val aad = "MetadataPacketHeader".toByteArray(Charsets.UTF_8)

        val ciphertext = AstraAead.encrypt(key, nonce, plaintext, aad)
        assertThat(ciphertext).isNotEqualTo(plaintext)

        val decrypted = AstraAead.decrypt(key, nonce, ciphertext, aad)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `aead decryption fails if ciphertext or AAD is modified`() {
        val key = AstraHkdf.deriveKey(null, "SecretMasterKey".toByteArray(Charsets.UTF_8), "aead-key".toByteArray(Charsets.UTF_8), 32)
        val nonce = AstraAead.generateNonce()
        val plaintext = "Sensitive Data".toByteArray(Charsets.UTF_8)
        val aad = "OriginalAAD".toByteArray(Charsets.UTF_8)

        val ciphertext = AstraAead.encrypt(key, nonce, plaintext, aad)

        // Tamper with ciphertext
        ciphertext[0] = (ciphertext[0].toInt() xor 0xFF).toByte()

        assertThrows(Exception::class.java) {
            AstraAead.decrypt(key, nonce, ciphertext, aad)
        }
    }

    @Test
    fun `double ratchet encrypts and decrypts continuous messages with forward secrecy`() {
        val aliceIdentity = AstraKeyPair.generate()
        val bobIdentity = AstraKeyPair.generate()

        val rootSharedSecret = AstraKeyAgreement.calculateSharedSecret(aliceIdentity.privateKey, bobIdentity.publicKey)

        val aliceEngine = AstraRatchetEngine(aliceIdentity, bobIdentity.publicKey, rootSharedSecret, isInitiator = true)
        val bobEngine = AstraRatchetEngine(bobIdentity, aliceIdentity.publicKey, rootSharedSecret, isInitiator = false)

        // Alice sends message 1 to Bob
        val msg1 = "Hello Bob!".toByteArray(Charsets.UTF_8)
        val encrypted1 = aliceEngine.encrypt(msg1)
        val decrypted1 = bobEngine.decrypt(encrypted1)
        assertThat(decrypted1).isEqualTo(msg1)

        // Alice sends message 2 to Bob
        val msg2 = "How is the mesh network today?".toByteArray(Charsets.UTF_8)
        val encrypted2 = aliceEngine.encrypt(msg2)
        val decrypted2 = bobEngine.decrypt(encrypted2)
        assertThat(decrypted2).isEqualTo(msg2)

        // Bob responds to Alice
        val msg3 = "Working smoothly and completely offline!".toByteArray(Charsets.UTF_8)
        val encrypted3 = bobEngine.encrypt(msg3)
        val decrypted3 = aliceEngine.decrypt(encrypted3)
        assertThat(decrypted3).isEqualTo(msg3)
    }

    @Test
    fun `replay protection window rejects duplicate and old packets`() {
        val window = ReplayProtectionWindow(windowSize = 64)

        assertThat(window.isDuplicateOrReplay(10)).isFalse()
        window.markReceived(10)

        // Immediate duplicate
        assertThat(window.isDuplicateOrReplay(10)).isTrue()

        // Newer sequence numbers
        assertThat(window.isDuplicateOrReplay(15)).isFalse()
        window.markReceived(15)

        // Out-of-order within window
        assertThat(window.isDuplicateOrReplay(12)).isFalse()
        window.markReceived(12)
        assertThat(window.isDuplicateOrReplay(12)).isTrue()

        // Way too old sequence number
        assertThat(window.isDuplicateOrReplay(0)).isFalse()
        window.markReceived(100)
        // Now sequence 10 is > 64 steps behind 100
        assertThat(window.isDuplicateOrReplay(10)).isTrue()
    }

    @Test
    fun `qr pairing serialization and sas derivation matches`() {
        val aliceKey = AstraKeyPair.generate()
        val bobKey = AstraKeyPair.generate()

        val sasAlice = QrPairingCodec.generateSas(aliceKey.publicKey, bobKey.publicKey)
        val sasBob = QrPairingCodec.generateSas(bobKey.publicKey, aliceKey.publicKey)

        assertThat(sasAlice).isEqualTo(sasBob)
        assertThat(sasAlice).hasLength(6)

        val payload = QrPairingPayload(
            version = 1,
            rootPublicKey = aliceKey.publicKey,
            displayName = "Alice Node",
            sasFingerprint = sasAlice
        )

        val qrString = QrPairingCodec.encode(payload)
        assertThat(qrString).startsWith("ASTRA:")

        val decoded = QrPairingCodec.decode(qrString)
        assertThat(decoded.version).isEqualTo(1)
        assertThat(decoded.displayName).isEqualTo("Alice Node")
        assertThat(decoded.rootPublicKey).isEqualTo(aliceKey.publicKey)
    }
}
