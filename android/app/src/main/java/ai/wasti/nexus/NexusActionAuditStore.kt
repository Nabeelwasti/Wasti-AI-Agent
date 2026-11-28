package ai.wasti.nexus

import android.content.Context
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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

internal data class NexusActionAuditRecord(
    val id: String,
    val atMillis: Long,
    val action: String,
    val outcome: String,
    val summary: String
)

/**
 * Encrypted, bounded local audit trail. It stores action labels and outcomes only;
 * screen text, notification bodies, contact details, file excerpts and secrets are never logged.
 */
internal object NexusActionAuditStore {
    private const val KEY_ALIAS = "wasti.nexus.action.audit.v1"
    private const val MAX_RECORDS = 200
    private const val AAD = "WASTI-NEXUS-ACTION-AUDIT-V1"

    private fun file(context: Context) = AtomicFile(
        File(context.filesDir, "security/action-audit.v1.enc").apply {
            parentFile?.let { check(it.exists() || it.mkdirs()) { "Could not create the security audit directory." } }
        }
    )

    @Synchronized
    fun read(context: Context): List<NexusActionAuditRecord> {
        val target = file(context)
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return emptyList()
        val envelope = JSONObject(target.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(envelope.optInt("version") == 1) { "Unsupported security audit format. Existing audit data was left untouched." }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == 12 && ciphertext.isNotEmpty()) { "Security audit file is damaged. Existing data was left untouched." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val records = JSONArray(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        return (0 until records.length()).map { index ->
            val item = records.getJSONObject(index)
            NexusActionAuditRecord(
                id = item.getString("id"),
                atMillis = item.getLong("at"),
                action = item.getString("action").take(160),
                outcome = item.getString("outcome").take(32),
                summary = item.getString("summary").take(240)
            )
        }
    }

    @Synchronized
    fun append(context: Context, action: String, outcome: String, summary: String) {
        require(action.isNotBlank() && action.length <= 160)
        require(outcome in setOf("APPROVED", "DENIED", "COMPLETED", "FAILED"))
        val records = (read(context) + NexusActionAuditRecord(
            id = java.util.UUID.randomUUID().toString(),
            atMillis = System.currentTimeMillis(),
            action = action.take(160),
            outcome = outcome,
            summary = summary.replace(Regex("\\s+"), " ").trim().take(240)
        )).takeLast(MAX_RECORDS)
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject()
                .put("id", record.id)
                .put("at", record.atMillis)
                .put("action", record.action)
                .put("outcome", record.outcome)
                .put("summary", record.summary))
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

    @Synchronized
    fun clear(context: Context) {
        val target = file(context)
        target.delete()
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
