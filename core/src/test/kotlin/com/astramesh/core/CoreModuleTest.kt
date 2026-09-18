package com.astramesh.core

import com.astramesh.crypto.AstraKeyPair
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoreModuleTest {

    @Test
    fun `node id serialization and broadcast checks match`() {
        val broadcast = NodeId.BROADCAST
        assertThat(broadcast.isBroadcast).isTrue()

        val id = NodeId(0x1234567890ABCDEFL)
        assertThat(id.isBroadcast).isFalse()

        val hex = id.toHex()
        assertThat(hex).isEqualTo("1234567890abcdef")

        val restored = NodeId.fromHex(hex)
        assertThat(restored).isEqualTo(id)
    }

    @Test
    fun `node id derived from public key is consistent`() {
        val keyPair = AstraKeyPair.generate()
        val nodeId1 = NodeId.fromPublicKey(keyPair.publicKey)
        val nodeId2 = NodeId.fromPublicKey(keyPair.publicKey)

        assertThat(nodeId1).isEqualTo(nodeId2)
    }

    @Test
    fun `packet id deterministic derivation from parameters`() {
        val origin = NodeId(0x1122334455667788L)
        val seq = 42L
        val timestamp = 1700000000000L

        val pid1 = PacketId.generate(origin, seq, timestamp)
        val pid2 = PacketId.generate(origin, seq, timestamp)

        assertThat(pid1).isEqualTo(pid2)

        val hex = pid1.toHex()
        assertThat(PacketId.fromHex(hex)).isEqualTo(pid1)
    }

    @Test
    fun `battery status normalization works correctly`() {
        val status = BatteryStatus(levelPercent = 85, isCharging = true, isPowerSaveMode = false)
        assertThat(status.normalizedLevel).isEqualTo(0.85f)

        val clampedLow = BatteryStatus(levelPercent = -10, isCharging = false, isPowerSaveMode = true)
        assertThat(clampedLow.normalizedLevel).isEqualTo(0.0f)
    }

    @Test
    fun `voice payload serialization and deserialization roundtrip`() {
        val sampleAudio = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val payload = VoicePayload(
            mode = VoiceMode.PUSH_TO_TALK,
            sequence = 12,
            isFinal = true,
            transcript = "Roger that",
            audioData = sampleAudio
        )

        val bytes = payload.serialize()
        assertThat(VoicePayload.isVoicePayload(bytes)).isTrue()

        val restored = VoicePayload.deserialize(bytes)
        assertThat(restored.mode).isEqualTo(VoiceMode.PUSH_TO_TALK)
        assertThat(restored.sequence).isEqualTo(12)
        assertThat(restored.isFinal).isTrue()
        assertThat(restored.transcript).isEqualTo("Roger that")
        assertThat(restored.audioData).isEqualTo(sampleAudio)
    }
}
