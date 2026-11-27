package ai.wasti.nexus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class NexusChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val turns: List<Pair<String, String>>,
    val remoteTurns: List<Pair<String, String>> = emptyList(),
    val pinned: Boolean = false
) {
    companion object {
        fun createOrUpdate(
            existing: NexusChatSession?,
            turns: List<Pair<String, String>>,
            remoteTurns: List<Pair<String, String>>
        ): NexusChatSession {
            val firstPrompt = turns.firstOrNull()?.first.orEmpty()
            val title = existing?.title?.takeIf { it.isNotBlank() }
                ?: firstPrompt.replace(Regex("\\s+"), " ").trim().take(64).ifBlank { "New conversation" }
            return NexusChatSession(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = title,
                updatedAt = System.currentTimeMillis(),
                turns = turns.toList(),
                remoteTurns = remoteTurns.toList(),
                pinned = existing?.pinned ?: false
            )
        }
    }
}

internal object NexusChatHistory {
    fun search(sessions: List<NexusChatSession>, query: String): List<NexusChatSession> {
        val needle = query.trim()
        if (needle.isEmpty()) return sessions.sortedWith(compareByDescending<NexusChatSession> { it.pinned }.thenByDescending { it.updatedAt })
        return sessions.filter { session ->
            session.title.contains(needle, ignoreCase = true) ||
                (session.turns + session.remoteTurns).any { (user, assistant) ->
                    user.contains(needle, ignoreCase = true) || assistant.contains(needle, ignoreCase = true)
                }
        }.sortedWith(compareByDescending<NexusChatSession> { it.pinned }.thenByDescending { it.updatedAt })
    }

    fun exportMarkdown(session: NexusChatSession): String = buildString {
        append("# ").append(session.title).append("\n\n")
        session.turns.forEachIndexed { index, (user, assistant) ->
            append("## You\n\n").append(user).append("\n\n")
            append("## WASTI NEXUS\n\n").append(assistant).append("\n\n")
            if (index < session.turns.lastIndex) append("---\n\n")
        }
    }
}

/**
 * Encrypted, app-private conversation store.
 *
 * AES-256-GCM keys are generated and retained by Android Keystore. The plaintext
 * conversation database never leaves this device. Android backup is disabled by
 * the manifest, so cross-device transfer remains an explicit encrypted export flow.
 */
internal class EncryptedNexusChatStore(context: Context) {
    private val alias = "wasti.nexus.chat.sessions.v1"
    private val chatDirectory = File(context.filesDir, "chat").apply { check(exists() || mkdirs()) { "Could not create the private chat-history directory." } }
    private val atomicFile = AtomicFile(File(chatDirectory, "sessions.v1.enc"))
    private val aad = "WASTI-NEXUS-CHAT-STORE-V1".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun load(): List<NexusChatSession> {
        val source = atomicFile.baseFile
        // AtomicFile may have a recoverable .bak even when the base file is absent.
        // openRead() performs that recovery before we inspect the restored file.
        if (!source.exists() && !File(source.path + ".bak").exists()) return emptyList()
        val input = atomicFile.openRead()
        val envelope = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(envelope.optInt("version") == 1) { "This chat-history format is not supported by this app version." }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == 12 && ciphertext.isNotEmpty()) { "Encrypted chat history is damaged." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val payload = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        val array = payload.getJSONArray("sessions")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val turns = decodeTurns(item.optJSONArray("turns") ?: JSONArray())
            val remoteTurns = decodeTurns(item.optJSONArray("remote_turns") ?: JSONArray())
            NexusChatSession(
                id = item.getString("id").also { require(runCatching { UUID.fromString(it) }.isSuccess) },
                title = item.optString("title").take(100).ifBlank { "Conversation" },
                updatedAt = item.optLong("updated_at"),
                turns = turns,
                remoteTurns = remoteTurns,
                pinned = item.optBoolean("pinned", false)
            )
        }.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun save(sessions: List<NexusChatSession>) {
        val payload = JSONObject().put("schema_version", 1).put("sessions", JSONArray().apply {
            sessions.sortedWith(compareByDescending<NexusChatSession> { it.pinned }.thenByDescending { it.updatedAt }).forEach { session ->
                put(JSONObject()
                    .put("id", session.id)
                    .put("title", session.title.take(100))
                    .put("updated_at", session.updatedAt)
                    .put("turns", encodeTurns(session.turns))
                    .put("remote_turns", encodeTurns(session.remoteTurns))
                    .put("pinned", session.pinned))
            }
        }).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(aad)
        val envelope = JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP))
            .toString()
            .toByteArray(Charsets.UTF_8)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(envelope)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encodeTurns(turns: List<Pair<String, String>>) = JSONArray().apply {
        turns.forEach { (user, assistant) ->
            put(JSONObject().put("user", user).put("assistant", assistant))
        }
    }

    private fun decodeTurns(array: JSONArray): List<Pair<String, String>> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            item.getString("user") to item.getString("assistant")
        }
}
