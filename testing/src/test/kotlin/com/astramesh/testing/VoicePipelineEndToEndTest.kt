package com.astramesh.testing

import com.astramesh.core.EmergencyClassifier
import com.astramesh.core.IthantraMessage
import com.astramesh.core.Language
import com.astramesh.core.MessageType
import com.astramesh.core.NodeId
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.repository.SpeechSynthesizer
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class VoicePipelineEndToEndTest {

    private lateinit var medium: VirtualBleMedium
    private val nodes = mutableListOf<VirtualMeshNode>()

    @Before
    fun setUp() {
        medium = VirtualBleMedium()
    }

    @After
    fun tearDown() {
        nodes.forEach { it.stop() }
        nodes.clear()
    }

    private fun createNode(idLong: Long, language: Language = Language.ENGLISH): VirtualMeshNode {
        val node = VirtualMeshNode(NodeId(idLong), medium)
        node.preferredLanguage = language
        node.start()
        nodes.add(node)
        return node
    }

    @Test
    fun testVoicePipeline_DirectTransmission_HindiToTamil_WithTranslationAndTts() {
        val nodeA = createNode(1L, Language.HINDI)
        val nodeB = createNode(2L, Language.TAMIL)

        val mockSynthesizer = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthesizer

        // Topology: A <-> B
        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        // Sender speaks Hindi distress phrase: "कृपया मेरी मदद कीजिए।" (Please help me)
        val hindiSpeech = "कृपया मेरी मदद कीजिए।"
        val isEmergency = EmergencyClassifier.isEmergency(hindiSpeech, Language.HINDI)
        assertThat(isEmergency).isTrue()

        val ithantraMsg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = if (isEmergency) MessageType.ALERT else MessageType.NORMAL,
            language = Language.HINDI,
            sequenceNumber = 101L,
            timestamp = System.currentTimeMillis(),
            text = hindiSpeech
        )

        val packetId = nodeA.sendPacket(
            destination = nodeB.nodeId,
            payload = ithantraMsg.toBinary(),
            priority = MessagePriority.EMERGENCY
        )

        Thread.sleep(150)

        // Verify Node B received the packet
        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)

        val synthesized = nodeB.synthesizedSpeechHistory.first()
        // Tamil translation of "Please help me."
        assertThat(synthesized.text).isEqualTo("எனக்கு உதவுங்கள்.")
        assertThat(synthesized.language).isEqualTo(Language.TAMIL)
        assertThat(synthesized.isEmergency).isTrue()

        coVerify(atLeast = 1) {
            mockSynthesizer.synthesizeAndPlay("எனக்கு உதவுங்கள்.", Language.TAMIL, true, any())
        }
    }

    @Test
    fun testVoicePipeline_MultiHopRelay_IntermediaryNodesDoNotTriggerTts() {
        // Topology: A (English) <-> B (Relay 1) <-> C (Relay 2) <-> D (Hindi Receiver)
        val nodeA = createNode(10L, Language.ENGLISH)
        val nodeB = createNode(20L, Language.MARATHI)
        val nodeC = createNode(30L, Language.GUJARATI)
        val nodeD = createNode(40L, Language.HINDI)

        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        val mockSynthC = mockk<SpeechSynthesizer>(relaxed = true)
        val mockSynthD = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB
        nodeC.speechSynthesizer = mockSynthC
        nodeD.speechSynthesizer = mockSynthD

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeD.nodeId, latencyMs = 0L)

        nodeA.routingTable.updateRoute(nodeD.nodeId, nodeB.nodeId, cost = 3.0f, hopCount = 3, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeD.nodeId, nodeC.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        nodeC.routingTable.updateRoute(nodeD.nodeId, nodeD.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val englishPhrase = "There is a fire here."
        val ithantraMsg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.ALERT,
            language = Language.ENGLISH,
            sequenceNumber = 202L,
            timestamp = System.currentTimeMillis(),
            text = englishPhrase
        )

        nodeA.sendPacket(nodeD.nodeId, ithantraMsg.toBinary(), MessagePriority.EMERGENCY)

        Thread.sleep(250)

        // Intermediate relays must forward WITHOUT synthesizing speech locally
        assertThat(nodeB.packetsRelayed.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).isEmpty()
        coVerify(exactly = 0) { mockSynthB.synthesizeAndPlay(any(), any(), any(), any()) }

        assertThat(nodeC.packetsRelayed.get()).isEqualTo(1L)
        assertThat(nodeC.synthesizedSpeechHistory).isEmpty()
        coVerify(exactly = 0) { mockSynthC.synthesizeAndPlay(any(), any(), any(), any()) }

        // Final destination receives and synthesizes speech translated to Hindi
        assertThat(nodeD.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeD.synthesizedSpeechHistory).hasSize(1)

        val speechD = nodeD.synthesizedSpeechHistory.first()
        assertThat(speechD.text).isEqualTo("यहाँ आग लगी है।") // Hindi for "There is a fire here."
        assertThat(speechD.language).isEqualTo(Language.HINDI)
        assertThat(speechD.isEmergency).isTrue()

        coVerify(atLeast = 1) {
            mockSynthD.synthesizeAndPlay("यहाँ आग लगी है।", Language.HINDI, true, any())
        }
    }

    @Test
    fun testVoicePipeline_KeywordActivation_DistressElevatesToAlert_BenignWordsStayNormal() {
        // Test benign text with distress substrings: must NOT elevate
        val benign1 = "I am having an espresso with my breakfast"
        val benign2 = "The student completed the lessons"
        val benign3 = "We walked along the sea shell beach"

        assertThat(EmergencyClassifier.isEmergency(benign1, Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency(benign2, Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.isEmergency(benign3, Language.ENGLISH)).isFalse()
        assertThat(EmergencyClassifier.classify(benign1, Language.ENGLISH)).isEqualTo(MessageType.NORMAL)

        // Test genuine emergency terms: must elevate to ALERT
        val distress1 = "Emergency! Send medical help immediately."
        val distress2 = "बचाओ! हम मलबे में फंसे हैं"
        val distress3 = "Danger, fire on the second floor!"

        assertThat(EmergencyClassifier.isEmergency(distress1, Language.ENGLISH)).isTrue()
        assertThat(EmergencyClassifier.isEmergency(distress2, Language.HINDI)).isTrue()
        assertThat(EmergencyClassifier.isEmergency(distress3, Language.ENGLISH)).isTrue()
        assertThat(EmergencyClassifier.classify(distress1, Language.ENGLISH)).isEqualTo(MessageType.ALERT)
        assertThat(EmergencyClassifier.classify(distress2, Language.HINDI)).isEqualTo(MessageType.ALERT)
    }

    @Test
    fun testVoicePipeline_StoreAndForward_DelaysTtsUntilContactEstablished() {
        val nodeA = createNode(501L, Language.ENGLISH)
        val nodeB = createNode(502L, Language.HINDI)

        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB

        // Nodes disconnected initially
        val text = "We need clean water and food."
        val ithantraMsg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.ENGLISH,
            sequenceNumber = 303L,
            timestamp = System.currentTimeMillis(),
            text = text
        )

        nodeA.sendPacket(nodeB.nodeId, ithantraMsg.toBinary(), MessagePriority.DIRECT_MESSAGE)

        // Verify buffered in StoreAndForwardQueue and NOT yet delivered or synthesized
        assertThat(nodeA.storeAndForwardQueue.size()).isEqualTo(1)
        assertThat(nodeB.packetsReceived.get()).isEqualTo(0L)
        assertThat(nodeB.synthesizedSpeechHistory).isEmpty()

        // Now link is formed
        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeA.onPeerConnected(nodeB.nodeId)

        Thread.sleep(150)

        // Now packet is delivered and translated into Hindi
        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        val speech = nodeB.synthesizedSpeechHistory.first()
        assertThat(speech.text).isEqualTo("हमें पीने का पानी और भोजन चाहिए।") // Hindi for water and food
        assertThat(speech.language).isEqualTo(Language.HINDI)
    }

    @Test
    fun testVoicePipeline_BroadcastEmergencyVoiceAlert_AllNodesSynthesizeInPreferredLanguage() {
        val nodeA = createNode(600L, Language.HINDI)
        val nodeB = createNode(601L, Language.ENGLISH)
        val nodeC = createNode(602L, Language.MARATHI)

        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        val mockSynthC = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB
        nodeC.speechSynthesizer = mockSynthC

        // Triangle broadcast topology
        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeA.nodeId, latencyMs = 0L)

        // Node A broadcasts emergency SOS in Hindi
        val alertText = "आपातकालीन चेतावनी! तत्काल सहायता चाहिए!"
        val alertMsg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.ALERT,
            language = Language.HINDI,
            sequenceNumber = 404L,
            timestamp = System.currentTimeMillis(),
            text = alertText
        )

        nodeA.sendPacket(NodeId.BROADCAST, alertMsg.toBinary(), MessagePriority.EMERGENCY)

        Thread.sleep(300)

        // Node B (English listener) synthesizes English alert
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        val speechB = nodeB.synthesizedSpeechHistory.first()
        assertThat(speechB.text).isEqualTo("Emergency Alert! Immediate assistance required!")
        assertThat(speechB.language).isEqualTo(Language.ENGLISH)
        assertThat(speechB.isEmergency).isTrue()

        // Node C (Marathi listener) synthesizes Marathi alert
        assertThat(nodeC.synthesizedSpeechHistory).hasSize(1)
        val speechC = nodeC.synthesizedSpeechHistory.first()
        assertThat(speechC.text).isEqualTo("आणीबाणी इशारा! त्वरित मदतीची गरज आहे!")
        assertThat(speechC.language).isEqualTo(Language.MARATHI)
        assertThat(speechC.isEmergency).isTrue()

        // Node A should NOT synthesize its own outgoing broadcast
        assertThat(nodeA.synthesizedSpeechHistory).isEmpty()
    }

    @Test
    fun testVoicePipeline_VoicePayloadTransmission_EnglishToMarathi_WithSourceLanguagePreserved() {
        val nodeA = createNode(701L, Language.ENGLISH)
        val nodeB = createNode(702L, Language.MARATHI)

        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val voicePayload = com.astramesh.core.VoicePayload(
            mode = com.astramesh.core.VoiceMode.PUSH_TO_TALK,
            sequence = 1,
            isFinal = true,
            transcript = "Send medical help immediately.",
            sourceLanguage = Language.ENGLISH
        )

        nodeA.sendPacket(nodeB.nodeId, voicePayload.serialize(), MessagePriority.EMERGENCY)

        Thread.sleep(150)

        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)

        val speechB = nodeB.synthesizedSpeechHistory.first()
        assertThat(speechB.text).isEqualTo("त्वरित वैद्यकीय मदत पाठवा.") // Marathi translation
        assertThat(speechB.language).isEqualTo(Language.MARATHI)
        assertThat(speechB.isEmergency).isTrue()
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL BIDIRECTIONAL AND EDGE CASE TESTS
    // -----------------------------------------------------------------------

    @Test
    fun testVoicePipeline_AToB_TextMessage_EnglishToHindi() {
        val nodeA = createNode(800L, Language.ENGLISH)
        val nodeB = createNode(801L, Language.HINDI)
        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val msg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.ENGLISH,
            sequenceNumber = 901L,
            timestamp = System.currentTimeMillis(),
            text = "Water and food are needed."
        )
        nodeA.sendPacket(nodeB.nodeId, msg.toBinary(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        val speech = nodeB.synthesizedSpeechHistory.first()
        assertThat(speech.language).isEqualTo(Language.HINDI)
        assertThat(speech.isEmergency).isFalse()
        // Verify translation was produced (non-empty Hindi text)
        assertThat(speech.text).isNotEmpty()
    }

    @Test
    fun testVoicePipeline_BToA_TextMessage_HindiToEnglish() {
        val nodeA = createNode(802L, Language.ENGLISH)
        val nodeB = createNode(803L, Language.HINDI)
        val mockSynthA = mockk<SpeechSynthesizer>(relaxed = true)
        nodeA.speechSynthesizer = mockSynthA

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val msg = IthantraMessage(
            senderId = nodeB.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.HINDI,
            sequenceNumber = 902L,
            timestamp = System.currentTimeMillis(),
            text = "पानी और भोजन की आवश्यकता है।"
        )
        nodeB.sendPacket(nodeA.nodeId, msg.toBinary(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeA.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeA.synthesizedSpeechHistory).hasSize(1)
        val speech = nodeA.synthesizedSpeechHistory.first()
        assertThat(speech.language).isEqualTo(Language.ENGLISH)
        assertThat(speech.isEmergency).isFalse()
        assertThat(speech.text).isNotEmpty()
    }

    @Test
    fun testVoicePipeline_HindiToMarathi_BidirectionalTranslation() {
        val nodeA = createNode(810L, Language.HINDI)
        val nodeB = createNode(811L, Language.MARATHI)
        val mockSynthA = mockk<SpeechSynthesizer>(relaxed = true)
        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeA.speechSynthesizer = mockSynthA
        nodeB.speechSynthesizer = mockSynthB

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        // A (Hindi) → B (Marathi)
        val hindiMsg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.HINDI,
            sequenceNumber = 903L,
            timestamp = System.currentTimeMillis(),
            text = "मदद भेजें।"
        )
        nodeA.sendPacket(nodeB.nodeId, hindiMsg.toBinary(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        assertThat(nodeB.synthesizedSpeechHistory.first().language).isEqualTo(Language.MARATHI)
        assertThat(nodeB.synthesizedSpeechHistory.first().text).isNotEmpty()

        // B (Marathi) → A (Hindi)
        val marathiMsg = IthantraMessage(
            senderId = nodeB.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.MARATHI,
            sequenceNumber = 904L,
            timestamp = System.currentTimeMillis(),
            text = "मदत पाठवा."
        )
        nodeB.sendPacket(nodeA.nodeId, marathiMsg.toBinary(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeA.synthesizedSpeechHistory).hasSize(1)
        assertThat(nodeA.synthesizedSpeechHistory.first().language).isEqualTo(Language.HINDI)
        assertThat(nodeA.synthesizedSpeechHistory.first().text).isNotEmpty()
    }

    @Test
    fun testVoicePipeline_SameLanguage_NoTranslationRequired() {
        val nodeA = createNode(820L, Language.ENGLISH)
        val nodeB = createNode(821L, Language.ENGLISH)
        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val text = "Proceed to the rally point immediately."
        val msg = IthantraMessage(
            senderId = nodeA.nodeId,
            messageType = MessageType.NORMAL,
            language = Language.ENGLISH,
            sequenceNumber = 905L,
            timestamp = System.currentTimeMillis(),
            text = text
        )
        nodeA.sendPacket(nodeB.nodeId, msg.toBinary(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        val speech = nodeB.synthesizedSpeechHistory.first()
        // Same language: text must be delivered unchanged
        assertThat(speech.text).isEqualTo(text)
        assertThat(speech.language).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun testVoicePipeline_VadThresholdSensitivityChange_AffectsEffectiveRms() {
        // Verify SttVadConfig maps correctly from normalized threshold to RMS integer
        val lowSensitivity = com.astramesh.core.SttVadConfig(vadThreshold = 0.10f)
        val midSensitivity = com.astramesh.core.SttVadConfig(vadThreshold = 0.30f)
        val highSensitivity = com.astramesh.core.SttVadConfig(vadThreshold = 0.90f)

        // Lower threshold → lower RMS → more sensitive (detects softer speech)
        assertThat(lowSensitivity.effectiveRmsThreshold).isLessThan(midSensitivity.effectiveRmsThreshold)
        assertThat(midSensitivity.effectiveRmsThreshold).isLessThan(highSensitivity.effectiveRmsThreshold)

        // Values must be in allowed range
        assertThat(lowSensitivity.effectiveRmsThreshold).isAtLeast(150)
        assertThat(highSensitivity.effectiveRmsThreshold).isAtMost(2500)

        // VAD frame counts scale correctly with timing
        val config = com.astramesh.core.SttVadConfig(
            vadMinSpeechMs = 200L,
            vadMinSilenceMs = 400L,
            vadSpeechPadMs = 80L
        )
        assertThat(config.minSpeechFrames(40)).isEqualTo(5)   // 200ms / 40ms
        assertThat(config.minSilenceFrames(40)).isEqualTo(10) // 400ms / 40ms
        assertThat(config.speechPadFrames(40)).isEqualTo(2)   // 80ms / 40ms

        // Wake-word sensitivity clamped to 0.01..0.50
        val tooLow = com.astramesh.core.SttVadConfig(wakeWordSensitivity = 0.001f)
        val tooHigh = com.astramesh.core.SttVadConfig(wakeWordSensitivity = 0.99f)
        assertThat(tooLow.effectiveWakeWordThreshold).isAtLeast(0.01f)
        assertThat(tooHigh.effectiveWakeWordThreshold).isAtMost(0.50f)
    }

    @Test
    fun testVoicePipeline_AToB_VoicePayload_EnglishToHindi() {
        val nodeA = createNode(830L, Language.ENGLISH)
        val nodeB = createNode(831L, Language.HINDI)
        val mockSynthB = mockk<SpeechSynthesizer>(relaxed = true)
        nodeB.speechSynthesizer = mockSynthB

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        // Use a well-covered phrasebook phrase to ensure MT produces Hindi output
        val voicePayload = com.astramesh.core.VoicePayload(
            mode = com.astramesh.core.VoiceMode.PUSH_TO_TALK,
            sequence = 2,
            isFinal = true,
            transcript = "Send help now.",
            sourceLanguage = Language.ENGLISH
        )
        nodeA.sendPacket(nodeB.nodeId, voicePayload.serialize(), MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(150)

        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        assertThat(nodeB.synthesizedSpeechHistory).hasSize(1)
        val speech = nodeB.synthesizedSpeechHistory.first()
        // Target language must be the receiver's configured language, not the sender's
        assertThat(speech.language).isEqualTo(Language.HINDI)
        // A non-empty string must have been produced by MT → TTS pipeline
        assertThat(speech.text).isNotEmpty()
        // The translated text must differ from the source (MT ran, not passthrough)
        assertThat(speech.text).isNotEqualTo("Send help now.")
    }
}
