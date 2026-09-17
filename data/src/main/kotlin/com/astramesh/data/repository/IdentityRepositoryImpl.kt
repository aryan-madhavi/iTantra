package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraKeyPair
import com.astramesh.crypto.EphemeralIdDeriver
import com.astramesh.domain.repository.IdentityRepository

import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.crypto.AstraPrivateKey
import com.astramesh.crypto.AstraPublicKey

class IdentityRepositoryImpl(
    private val context: Context? = null,
    private var localKeyPair: AstraKeyPair = AstraKeyPair.generate(),
    private var displayName: String = "AstraNode"
) : IdentityRepository {

    private val prefs by lazy {
        context?.getSharedPreferences("astra_identity_prefs", Context.MODE_PRIVATE)
    }

    private val cachedNodeId: NodeId by lazy {
        val savedSeed = prefs?.getString("identity_seed", null)
        val keyPair = if (savedSeed != null) {
            val seedBytes = com.astramesh.common.ByteUtils.hexToByteArray(savedSeed)
            val priv = AstraPrivateKey(seedBytes)
            val pub = AstraPublicKey(seedBytes) // Deterministic pair
            AstraKeyPair(pub, priv)
        } else {
            val gen = AstraKeyPair.generate()
            prefs?.edit()?.putString("identity_seed", com.astramesh.common.ByteUtils.toHexString(gen.publicKey.rawBytes))?.apply()
            gen
        }
        localKeyPair = keyPair
        val permanentId = NodeId.fromPublicKey(keyPair.publicKey)
        AstraLog.d("IdentityRepository", "IDENTITY_MAP permanentId=$permanentId")
        permanentId
    }

    override suspend fun getLocalIdentity(): AstraKeyPair = localKeyPair

    override suspend fun getDisplayName(): String = displayName

    override suspend fun setDisplayName(name: String): AstraResult<Unit> = AstraResult.of {
        displayName = name
    }

    override suspend fun getRotatingNodeId(): NodeId {
        return cachedNodeId
    }

    override suspend fun rotateIdentityEpoch(): NodeId {
        AstraLog.d("IdentityRepository", "IDENTITY_ROTATE returning stable permanentId=$cachedNodeId")
        return cachedNodeId
    }

    override suspend fun wipeAllCryptographicKeys(): AstraResult<Unit> = AstraResult.of {
        localKeyPair.privateKey.destroy()
        prefs?.edit()?.clear()?.apply()
        localKeyPair = AstraKeyPair.generate()
        AstraLog.d("IdentityRepository", "IDENTITY_ROTATE wiped keys and re-generated permanent identity")
    }

    override suspend fun getPreloadLanguage(): com.astramesh.core.Language {
        val code = prefs?.getString("preload_tts_language", null)
        return if (code != null) {
            com.astramesh.core.Language.fromCode(code)
        } else {
            com.astramesh.core.Language.HINDI
        }
    }

    override suspend fun setPreloadLanguage(language: com.astramesh.core.Language): AstraResult<Unit> = AstraResult.of {
        prefs?.edit()?.putString("preload_tts_language", language.code)?.apply()
        AstraLog.d("IdentityRepository", "Saved default preload TTS language: ${language.name} (${language.code})")
    }
}
