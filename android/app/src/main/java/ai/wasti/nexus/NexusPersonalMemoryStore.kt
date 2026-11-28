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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class NexusPersonalMemoryNote(
    val id: String,
    val createdAt: Long,
    val text: String
)

/**
 * Explicit user-managed memory. Notes are encrypted at rest and never synced automatically.
 * This is retrieval memory, not model-weight training or autonomous learning.
 */
internal object NexusPersonalMemoryStore {
    private const val KEY_ALIAS = "wasti.nexus.personal.memory.v1"
    private const val AAD = "WASTI-NEXUS-PERSONAL-MEMORY-V1"
    private const val MAX_NOTES = 100
    private const val MAX_NOTE_CHARS = 2_000

    private fun file(context: Context) = AtomicFile(
        File(context.filesDir, "memory/personal-notes.v1.enc").apply {
            parentFile?.let { check(it.exists() || it.mkdirs()) { "Could not create the personal-memory directory." } }
        }
    )

    @Synchronized
    fun list(context: Context): List<NexusPersonalMemoryNote> = readAll(context).sortedByDescending { it.createdAt }

    @Synchronized
    fun add(context: Context, text: String): NexusPersonalMemoryNote {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "A memory note cannot be empty." }
        require(normalized.length <= MAX_NOTE_CHARS) { "A memory note must be $MAX_NOTE_CHARS characters or fewer." }
        val note = NexusPersonalMemoryNote(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), normalized)
        writeAll(context, (readAll(context) + note).takeLast(MAX_NOTES))
        return note
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        require(id.matches(Regex("^[A-Za-z0-9-]{1,64}$"))) { "Invalid memory note ID." }
        writeAll(context, readAll(context).filterNot { it.id == id })
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    fun relevant(context: Context, prompt: String, limit: Int = 4): List<String> {
        val notes = runCatching { readAll(context) }.getOrDefault(emptyList())
        if (notes.isEmpty()) return emptyList()
        val stopWords = setOf("the", "and", "for", "with", "that", "this", "from", "what", "why", "how", "can", "you", "your", "please", "about", "have", "want")
        val terms = Regex("[\\p{L}\\p{N}_]{3,}")
            .findAll(prompt.lowercase())
            .map { it.value }
            .filterNot { it in stopWords }
            .toSet()
        if (terms.isEmpty()) return emptyList()
        return notes.map { note ->
            val body = note.text.lowercase()
            val score = terms.sumOf { term -> if (body.contains(term)) 1 else 0 }
            note to score
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<NexusPersonalMemoryNote, Int>> { it.second }.thenByDescending { it.first.createdAt })
            .take(limit.coerceIn(1, 8))
            .map { it.first.text }
    }

    private fun readAll(context: Context): List<NexusPersonalMemoryNote> {
        val target = file(context)
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return emptyList()
        val envelope = JSONObject(target.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(envelope.optInt("version") == 1) { "Unsupported personal-memory format. Existing data was left untouched." }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == 12 && encrypted.isNotEmpty()) { "Personal-memory file is damaged. Existing data was left untouched." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val array = JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        require(array.length() <= MAX_NOTES) { "Personal-memory file exceeds its safe record limit." }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            NexusPersonalMemoryNote(
                id = item.getString("id"),
                createdAt = item.getLong("createdAt"),
                text = item.getString("text").take(MAX_NOTE_CHARS)
            )
        }
    }

    private fun writeAll(context: Context, notes: List<NexusPersonalMemoryNote>) {
        require(notes.size <= MAX_NOTES)
        val array = JSONArray()
        notes.forEach { note ->
            array.put(JSONObject()
                .put("id", note.id)
                .put("createdAt", note.createdAt)
                .put("text", note.text.take(MAX_NOTE_CHARS)))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        val target = file(context)
        val output = target.startWrite()
        try {
            output.write(envelope.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
