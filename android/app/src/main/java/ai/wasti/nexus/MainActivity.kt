package ai.wasti.nexus

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class GatewayHttpException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)

internal data class ModelContentRange(val first: Long, val last: Long, val total: Long)

internal fun parseModelContentRange(value: String?): ModelContentRange? {
    val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$", RegexOption.IGNORE_CASE)
        .matchEntire(value?.trim().orEmpty()) ?: return null
    val first = match.groupValues[1].toLongOrNull() ?: return null
    val last = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: return null
    if (first > last || last >= total) return null
    return ModelContentRange(first, last, total)
}

internal fun shouldFallbackToLocal(error: Exception): Boolean {
    if (error is java.io.IOException) return true
    if (error is IllegalArgumentException) {
        val message = error.message.orEmpty()
        return message.contains("Gateway URL", ignoreCase = true) ||
            message.contains("API key", ignoreCase = true)
    }
    val status = (error as? GatewayHttpException)?.statusCode ?: return false
    return status in setOf(408, 429, 500, 502, 503, 504)
}

class MainActivity : ComponentActivity() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val alias = "wasti.nexus.gateway.key"
    private val prefs by lazy { getSharedPreferences("nexus-config", MODE_PRIVATE) }
    private val localEngineDelegate = lazy { AiChat.getInferenceEngine(this) }
    private val localEngine: InferenceEngine by localEngineDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NexusScreen(
                    initialUrl = prefs.getString("url", null)?.takeIf { it.isNotBlank() } ?: "http://localhost:8088",
                    initialPrompt = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(12000),
                    initialKey = decryptSecret(),
                    initialModelPath = prefs.getString("local_model_path", "") ?: "",
                    initialModels = installedModels(),
                    initialWorkspaceModels = DriveModelWorkspace.loadCatalog(this),
                    initialRegistryModels = NexusModelRegistry.load(this),
                    initialDriveWorkspaceUri = prefs.getString("drive_workspace_uri", "") ?: "",
                    initialFreeStorageBytes = StatFs(filesDir.absolutePath).availableBytes,
                    onListModels = { installedModels() },
                    onSelectModel = { path -> prefs.edit().putString("local_model_path", path).apply() },
                    onDeleteModel = { path -> deleteModel(path) },
                    onFreeStorage = { StatFs(filesDir.absolutePath).availableBytes },
                    onSave = { url, key ->
                        prefs.edit().putString("url", url).apply()
                        encryptSecret(key)
                    },
                    onImportModel = { uri -> importModel(uri) },
                    onConnectDriveWorkspace = { uri ->
                        DriveModelWorkspace.persistWorkspaceAccess(this, uri)
                        val name = DriveModelWorkspace.validateWorkspace(this, uri)
                        prefs.edit().putString("drive_workspace_uri", uri.toString()).apply()
                        name
                    },
                    onInstallDriveModel = { uri, model, progress ->
                        DriveModelWorkspace.installModel(this, uri, model, progress)
                    },
                    onArchiveAllModelsToDrive = { uri, models, progress ->
                        DriveModelWorkspace.archiveAllModelsToDrive(this, uri, models, progress)
                    },
                    onLoadLocal = { path -> loadLocalModel(path) },
                    onUnloadLocal = { unloadLocalModel() },
                    onSendLocal = { prompt, onToken -> sendLocal(prompt, onToken) },
                    onSaveMemory = { turns, passphrase -> saveMemorySnapshot(turns, passphrase) },
                    onRestoreMemory = { passphrase -> restoreLatestLocalMemorySnapshot(passphrase) },
                    onRestoreRemoteMemory = { url, key, passphrase -> restoreLatestRemoteMemorySnapshot(url, key, passphrase) },
                    onFetchModels = { url, key -> fetchRemoteModels(url, key) },
                    onSyncMemory = { url, key, turns, passphrase -> syncMemorySnapshot(url, key, turns, passphrase) },
                    onSendRemote = { url, key, history, prompt, hybrid, agentId ->
                        sendRemote(url, key, history, prompt, hybrid, agentId)
                    }
                )
            }
        }
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptSecret(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove("key_iv").remove("key_ciphertext").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        prefs.edit()
            .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("key_ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .apply()
    }

    private fun decryptSecret(): String = try {
        val iv = Base64.decode(prefs.getString("key_iv", null), Base64.NO_WRAP)
        val ciphertext = Base64.decode(prefs.getString("key_ciphertext", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext))
    } catch (_: Exception) {
        ""
    }

    private fun createEncryptedMemorySnapshot(
        turns: List<Pair<String, String>>,
        passphrase: String
    ): JSONObject {
        require(turns.isNotEmpty()) { "There is no conversation to save yet." }
        require(passphrase.length >= 12) { "Choose a memory recovery passphrase with at least 12 characters." }
        val messages = JSONArray()
        turns.takeLast(30).forEach { (user, assistant) ->
            messages.put(JSONObject().put("role", "user").put("content", user))
            messages.put(JSONObject().put("role", "assistant").put("content", assistant))
        }
        val plaintext = JSONObject()
            .put("schema_version", "1.0")
            .put("messages", messages)
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(plaintext.size <= 700 * 1024) { "Memory snapshot is too large. Save a shorter conversation." }

        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val key = deriveMemoryKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = Base64.encodeToString(
            cipher.iv,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(plaintext),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return JSONObject()
            .put("snapshot_id", UUID.randomUUID().toString())
            .put("created_at", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString())
            .put("key_id", "pbkdf2-sha256-600k-v1")
            .put("kdf", "PBKDF2-HMAC-SHA256")
            .put("iterations", 600000)
            .put("salt", Base64.encodeToString(salt, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            .put("nonce", nonce)
            .put("ciphertext", ciphertext)
            .put("content_type", "conversation-summary")
            .put("tags", JSONArray().put("wasti-nexus").put("android"))
    }

    private fun deriveMemoryKey(passphrase: String, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, 600000, 256)
        return try {
            val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            javax.crypto.spec.SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private suspend fun saveMemorySnapshot(
        turns: List<Pair<String, String>>,
        passphrase: String
    ): String = withContext(Dispatchers.IO) {
            val record = createEncryptedMemorySnapshot(turns, passphrase)
            val directory = File(filesDir, "memory/snapshots").apply { mkdirs() }
            val target = File(directory, record.getString("snapshot_id") + ".json")
            val temporary = File(directory, target.name + ".tmp")
            try {
                temporary.writeText(record.toString(), Charsets.UTF_8)
                require(temporary.renameTo(target)) { "Could not atomically save the encrypted memory snapshot." }
            } catch (error: Exception) {
                temporary.delete()
                throw error
            }
            record.getString("snapshot_id")
        }


    private fun decryptMemoryEnvelope(record: JSONObject, passphrase: String): List<Pair<String, String>> {
        require(passphrase.length >= 12) { "Enter the same memory recovery passphrase used when saving this snapshot." }
        require(record.optString("kdf") == "PBKDF2-HMAC-SHA256" && record.optInt("iterations") == 600000) {
            "Unsupported memory key-derivation settings."
        }
        val salt = Base64.decode(record.getString("salt"), Base64.URL_SAFE or Base64.NO_WRAP)
        require(salt.size == 16) { "Memory recovery salt is invalid." }
        val nonce = Base64.decode(record.getString("nonce"), Base64.URL_SAFE or Base64.NO_WRAP)
        val ciphertext = Base64.decode(record.getString("ciphertext"), Base64.URL_SAFE or Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveMemoryKey(passphrase, salt), GCMParameterSpec(128, nonce))
        val payload = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        val messages = payload.getJSONArray("messages")
        val restored = mutableListOf<Pair<String, String>>()
        var pendingUser: String? = null
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            when (message.optString("role")) {
                "user" -> pendingUser = message.optString("content")
                "assistant" -> {
                    val user = pendingUser
                    if (user != null) {
                        restored += user to message.optString("content")
                        pendingUser = null
                    }
                }
            }
        }
        require(restored.isNotEmpty()) { "The encrypted snapshot contains no complete conversation turns." }
        return restored
    }

    private fun persistMemoryEnvelope(record: JSONObject) {
        val directory = File(filesDir, "memory/snapshots").apply { mkdirs() }
        val target = File(directory, record.getString("snapshot_id") + ".json")
        val temporary = File(directory, target.name + ".tmp")
        try {
            temporary.writeText(record.toString(), Charsets.UTF_8)
            require(temporary.renameTo(target)) { "Could not atomically save the encrypted memory snapshot." }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun restoreLatestLocalMemorySnapshot(passphrase: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val directory = File(filesDir, "memory/snapshots")
            val latest = directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("No local encrypted memory snapshots were found.")
            decryptMemoryEnvelope(JSONObject(latest.readText(Charsets.UTF_8)), passphrase)
        }

    private suspend fun restoreLatestRemoteMemorySnapshot(
        url: String,
        key: String,
        passphrase: String
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
        require(key.length >= 32) { "Enter the Termux pairing token or a strong remote server bearer key." }
        val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
        val listRequest = Request.Builder()
            .url("$apiRoot/memory/snapshots?limit=1")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        val snapshotId = http.newCall(listRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("GitHub memory list returned HTTP ${response.code}: $body")
            val items = JSONObject(body).optJSONArray("data")
            require(items != null && items.length() > 0) { "No encrypted snapshots are available in GitHub yet." }
            items.getJSONObject(0).getString("snapshot_id")
        }
        val readRequest = Request.Builder()
            .url("$apiRoot/memory/snapshots/$snapshotId")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        val envelope = http.newCall(readRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("GitHub memory read returned HTTP ${response.code}: $body")
            val stored = JSONObject(body).getJSONObject("data")
            val cipher = stored.getJSONObject("cipher")
            JSONObject()
                .put("snapshot_id", stored.getString("record_id"))
                .put("created_at", stored.getString("created_at"))
                .put("key_id", cipher.getString("key_id"))
                .put("kdf", cipher.getString("kdf"))
                .put("iterations", cipher.getInt("iterations"))
                .put("salt", cipher.getString("salt"))
                .put("nonce", cipher.getString("nonce"))
                .put("ciphertext", cipher.getString("ciphertext"))
                .put("content_type", stored.optString("content_type", "conversation-summary"))
                .put("tags", stored.optJSONArray("tags") ?: JSONArray())
        }
        persistMemoryEnvelope(envelope)
        decryptMemoryEnvelope(envelope, passphrase)
    }

    private suspend fun syncMemorySnapshot(
        url: String,
        key: String,
        turns: List<Pair<String, String>>,
        passphrase: String
    ): String = withContext(Dispatchers.IO) {
        val record = createEncryptedMemorySnapshot(turns, passphrase)
        val directory = File(filesDir, "memory/snapshots").apply { mkdirs() }
        val target = File(directory, record.getString("snapshot_id") + ".json")
        val temporary = File(directory, target.name + ".tmp")
        temporary.writeText(record.toString(), Charsets.UTF_8)
        require(temporary.renameTo(target)) { "Encrypted snapshot was saved locally but could not be finalized." }

        val root = url.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
        require(key.length >= 32) { "API key must be at least 32 characters." }
        val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
        val request = Request.Builder()
            .url("$apiRoot/memory/snapshots")
            .header("Authorization", "Bearer $key")
            .post(record.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Snapshot saved locally. GitHub sync returned HTTP ${response.code}: $body"
                )
            }
            "Encrypted snapshot ${record.getString("snapshot_id")} saved locally and synced to GitHub."
        }
    }

    /** Resumable, checksum-pinned Qwen download. Partial bytes are retained after network interruption. */
    private suspend fun downloadFirstLocalModel(
        onProgress: suspend (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val revision = "12145bd1d629190a4d44254073650877954d02c9"
        val fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        val expectedSha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        val url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/$revision/$fileName?download=true"
        val downloadClient = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val modelDirectory = File(filesDir, "models").apply { mkdirs() }
        val target = File(modelDirectory, fileName)
        val temporary = File(modelDirectory, "$fileName.part")
        val maxBytes = 800_000_000L
        val reserveBytes = 1024L * 1024L * 1024L
        try {
            var offset = temporary.length()
            if (offset !in 0..maxBytes) {
                temporary.delete()
                offset = 0L
            }
            val requestBuilder = Request.Builder().url(url).get()
            if (offset > 0L) requestBuilder.header("Range", "bytes=$offset-")
            downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body ?: throw IllegalStateException("The model host returned an empty response.")
                val append = offset > 0L && response.code == 206
                val totalLength: Long
                if (append) {
                    val range = parseModelContentRange(response.header("Content-Range"))
                        ?: throw IllegalStateException("The model host returned an invalid resume range.")
                    require(range.first == offset && range.last >= range.first && range.total in 1..maxBytes) {
                        "The model host returned a mismatched resume range. The partial file must be checked before retrying."
                    }
                    totalLength = range.total
                } else {
                    if (offset > 0L) temporary.delete()
                    require(response.code == 200) { "Model download failed with HTTP ${response.code}." }
                    totalLength = body.contentLength()
                    require(totalLength in 1..maxBytes) { "Unexpected model size. Download stopped for safety." }
                }
                val remaining = totalLength - if (append) offset else 0L
                require(remaining > 0L && remaining <= maxBytes) { "Invalid remaining model download size." }
                val freeBytes = StatFs(filesDir.absolutePath).availableBytes
                require(freeBytes >= remaining + reserveBytes) {
                    "Not enough free storage. Keep at least 1 GB free after installation."
                }
                var written = if (append) offset else 0L
                withContext(Dispatchers.Main) { onProgress(written, totalLength) }
                body.byteStream().use { input ->
                    java.io.RandomAccessFile(temporary, "rw").use { output ->
                        output.seek(written)
                        val buffer = ByteArray(1024 * 1024)
                        var lastReported = written
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            written += count
                            require(written <= totalLength && written <= maxBytes) {
                                "Downloaded file exceeded the verified size limit."
                            }
                            output.write(buffer, 0, count)
                            if (written - lastReported >= 4L * 1024L * 1024L) {
                                withContext(Dispatchers.Main) { onProgress(written, totalLength) }
                                lastReported = written
                            }
                        }
                    }
                }
                require(written == totalLength) {
                    "Model download is incomplete. The partial file is saved; retry to resume."
                }
                withContext(Dispatchers.Main) { onProgress(written, totalLength) }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualSha256 != expectedSha256) {
                temporary.delete()
                throw IllegalStateException("Model checksum verification failed. The partial file was deleted.")
            }
            try {
                temporary.inputStream().use { GgufMetadataReader.create().readStructuredMetadata(it) }
            } catch (metadataError: Exception) {
                temporary.delete()
                throw IllegalStateException("Downloaded file failed GGUF metadata validation and was rejected.", metadataError)
            }
            // Preserve an existing installation until the verified replacement is finalized.
            val backup = File(modelDirectory, "$fileName.previous-${UUID.randomUUID()}")
            var previousMoved = false
            try {
                if (target.exists()) {
                    require(target.renameTo(backup)) { "Could not safely preserve the previous model installation." }
                    previousMoved = true
                }
                require(temporary.renameTo(target)) { "Could not finalize the verified model download." }
                prefs.edit().putString("local_model_path", target.absolutePath).apply()
                if (previousMoved) backup.delete()
                target.absolutePath
            } catch (installError: Exception) {
                if (previousMoved && !target.exists()) backup.renameTo(target)
                throw installError
            }
        } catch (error: Exception) {
            // Keep a bounded .part file on transient IO failures so the next tap can resume.
            if (error.message?.contains("checksum verification failed", ignoreCase = true) == true) temporary.delete()
            throw error
        }
    }

    private fun installedModels(): List<String> =
        File(filesDir, "models").listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) && it.length() > 1024L * 1024L }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.absolutePath }
            ?: emptyList()

    private fun deleteModel(path: String) {
        val directory = File(filesDir, "models").canonicalFile
        val target = File(path).canonicalFile
        require(target.parentFile == directory && target.extension.equals("gguf", ignoreCase = true)) {
            "Refusing to delete a file outside NEXUS model storage."
        }
        require(target.delete()) { "Could not delete the selected model file." }
    }

    private suspend fun importModel(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                displayName to size
            }
        } ?: (null to -1L)

        val displayName = name.first ?: throw IllegalArgumentException("Could not read the selected file name.")
        require(displayName.endsWith(".gguf", ignoreCase = true)) {
            "Choose a GGUF model file. Other model formats are not supported by this runtime yet."
        }
        val declaredSize = name.second
        val maxModelBytes = 8L * 1024L * 1024L * 1024L
        require(declaredSize <= maxModelBytes || declaredSize < 0) {
            "This model file is larger than the current 8 GB import limit."
        }
        val freeBytes = StatFs(filesDir.absolutePath).availableBytes
        require(declaredSize < 0 || freeBytes > declaredSize + 1024L * 1024L * 1024L) {
            "Not enough free storage. Keep at least 1 GB free after importing a model."
        }

        val modelDirectory = File(filesDir, "models").apply { mkdirs() }
        val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val target = File(modelDirectory, "${UUID.randomUUID()}-$safeName")
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Android could not open the selected model.")
            input.use { source ->
                target.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxModelBytes) { "Model exceeds the 8 GB import limit." }
                        destination.write(buffer, 0, count)
                    }
                }
            }
            require(target.length() > 1024L * 1024L) { "The selected file is too small to be a valid GGUF model." }
            target.inputStream().use { stream ->
                GgufMetadataReader.create().readStructuredMetadata(stream)
            }
            prefs.edit().putString("local_model_path", target.absolutePath).apply()
            target.absolutePath
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private suspend fun loadLocalModel(path: String) = withContext(Dispatchers.Default) {
        val model = File(path)
        require(model.isFile && model.extension.equals("gguf", ignoreCase = true)) {
            "Select a valid installed GGUF model first."
        }
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val estimatedNeed = (model.length() * 2.5).toLong() + 1229L * 1024L * 1024L
        require(!memory.lowMemory && memory.availMem >= estimatedNeed) {
            "Available RAM is too low for this model right now. Close other apps or choose a smaller quantized model."
        }
        try {
            when (val state = localEngine.state.first {
                it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
            }) {
                is InferenceEngine.State.Error -> throw state.exception
                else -> Unit
            }
            localEngine.loadModel(model.absolutePath)
            localEngine.setSystemPrompt(
                "You are WASTI NEXUS, a capable, friendly and practical assistant. " +
                    "Answer the user directly in their language, including Urdu or Roman Urdu. " +
                    "Use natural, human wording and useful examples. Do not refuse ordinary requests; " +
                    "if something is uncertain, say so and still give the safest useful help. " +
                    "Never invent facts, sources, or actions."
            )
        } catch (error: OutOfMemoryError) {
            localEngine.cleanUp()
            throw IllegalStateException("Android ran out of memory while loading this model. Try a smaller GGUF quantization.", error)
        } catch (error: Exception) {
            localEngine.cleanUp()
            throw error
        }
    }

    private suspend fun unloadLocalModel() = withContext(Dispatchers.Default) {
        localEngine.cleanUp()
    }

    private suspend fun sendLocal(prompt: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            val output = StringBuilder()
            localEngine.sendUserPrompt(prompt).collect { token ->
                output.append(token)
                withContext(Dispatchers.Main.immediate) { onToken(token) }
            }
            output.toString().ifBlank { "The local model returned an empty response." }
        }


    private suspend fun fetchRemoteModels(url: String, key: String): List<String> =
        withContext(Dispatchers.IO) {
            val root = url.trim().trimEnd('/')
            val local = NexusRuntimePolicy.isLoopbackHttp(root)
            require(root.startsWith("https://") || local) { "Use HTTPS for remote servers; HTTP is permitted only for loopback Termux." }
            require(key.length >= 32) { "Enter the Termux pairing token or a strong remote server bearer key." }
            val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
            val requestBuilder = Request.Builder().url("$apiRoot/models").get()
            if (key.isNotBlank()) requestBuilder.header("Authorization", "Bearer $key")
            val request = requestBuilder.build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("Model catalogue returned HTTP ${response.code}: $body")
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                (0 until data.length())
                    .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                    .distinct()
                    .sorted()
            }
        }

    private suspend fun sendRemote(
        url: String,
        key: String,
        history: List<Pair<String, String>>,
        prompt: String,
        hybrid: Boolean,
        agentId: String
    ): String = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        val local = isLoopbackHttp(root)
        require(root.startsWith("https://") || local) { "Use HTTPS for remote servers; HTTP is permitted only for loopback Termux." }
        require(key.length >= 32) { "Enter the Termux pairing token or a strong remote server bearer key." }

        // Bound the remote context so a long chat cannot exceed gateway request limits.
        // Only the online conversation is supplied; local-only turns never leave the device.
        val selectedHistory = mutableListOf<Pair<String, String>>()
        var contextChars = prompt.length
        for (turn in history.asReversed()) {
            val turnChars = turn.first.length + turn.second.length
            if (selectedHistory.size >= 10 || contextChars + turnChars > 18000) break
            selectedHistory.add(turn)
            contextChars += turnChars
        }
        val messages = JSONArray()
        selectedHistory.asReversed().forEach { (user, assistant) ->
            messages.put(JSONObject().put("role", "user").put("content", user))
            messages.put(JSONObject().put("role", "assistant").put("content", assistant))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val automatic = agentId == "nexus-auto"
        val payload = JSONObject()
            .put("model", when { hybrid -> "nexus-hybrid"; automatic -> "nexus-auto"; else -> agentId })
            .put("messages", messages)
            .put("stream", false)
        if (automatic) payload.put("nexus", JSONObject().put("mode", "auto"))
        if (hybrid) {
            payload.put("nexus", JSONObject().put("mode", "hybrid").put("review_rounds", 1))
        }
        val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
        val requestBuilder = Request.Builder()
            .url("$apiRoot/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (key.isNotBlank()) requestBuilder.header("Authorization", "Bearer $key")
        val request = requestBuilder.build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GatewayHttpException(response.code, "Gateway HTTP ${response.code}: $text")
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")
                ?: throw IllegalStateException("Gateway response did not contain an answer.")
        }
    }

    override fun onDestroy() {
        if (localEngineDelegate.isInitialized()) runCatching { localEngine.destroy() }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexusScreen(
    initialUrl: String,
    initialPrompt: String,
    initialKey: String,
    initialModelPath: String,
    initialModels: List<String>,
    initialWorkspaceModels: List<WorkspaceModel>,
    initialRegistryModels: List<NexusCapabilityModel>,
    initialDriveWorkspaceUri: String,
    initialFreeStorageBytes: Long,
    onListModels: () -> List<String>,
    onSelectModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onFreeStorage: () -> Long,
    onSave: (String, String) -> Unit,
    onImportModel: suspend (Uri) -> String,
    onConnectDriveWorkspace: (Uri) -> String,
    onInstallDriveModel: suspend (Uri, WorkspaceModel, (Long, Long) -> Unit) -> String,
    onArchiveAllModelsToDrive: suspend (Uri, List<WorkspaceModel>, (Long, Long) -> Unit) -> Int,
    onLoadLocal: suspend (String) -> Unit,
    onUnloadLocal: suspend () -> Unit,
    onSendLocal: suspend (String, (String) -> Unit) -> String,
    onSaveMemory: suspend (List<Pair<String, String>>, String) -> String,
    onRestoreMemory: suspend (String) -> List<Pair<String, String>>,
    onRestoreRemoteMemory: suspend (String, String, String) -> List<Pair<String, String>>,
    onFetchModels: suspend (String, String) -> List<String>,
    onSyncMemory: suspend (String, String, List<Pair<String, String>>, String) -> String,
    onSendRemote: suspend (String, String, List<Pair<String, String>>, String, Boolean, String) -> String
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var localModelPath by remember { mutableStateOf(initialModelPath) }
    var installedModels by remember { mutableStateOf(initialModels) }
    var workspaceModels by remember { mutableStateOf(initialWorkspaceModels) }
    var registryDialog by remember { mutableStateOf(false) }
    var driveWorkspaceUri by remember { mutableStateOf(initialDriveWorkspaceUri) }
    var driveWorkspaceName by remember { mutableStateOf(if (initialDriveWorkspaceUri.isBlank()) "Not connected" else "Google Drive workspace") }
    var freeStorageBytes by remember { mutableLongStateOf(initialFreeStorageBytes) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var confirmDeleteModel by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var answer by remember { mutableStateOf("Welcome to WASTI NEXUS. Install a local model to begin private offline chat.") }
    var conversation by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var streamingResponse by remember { mutableStateOf("") }
    var remoteConversation by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var memoryPassphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadingModel by remember { mutableStateOf(false) }
    var archivingModels by remember { mutableStateOf(false) }
    var archiveProgress by remember { mutableFloatStateOf(0f) }
    var confirmArchiveAll by remember { mutableStateOf(false) }
    var hybrid by remember { mutableStateOf(false) }
    var autoMode by remember { mutableStateOf(false) }
    var offlineMode by remember { mutableStateOf(true) }
    var localReady by remember { mutableStateOf(false) }
    var loadedModelPath by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf("nexus-gemma-4-26b-a4b") }
    var remoteModels by remember { mutableStateOf(emptyList<String>()) }
    var remoteMenuExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val chatScrollState = rememberScrollState()
    LaunchedEffect(conversation.size, busy, settingsExpanded) {
        if (!settingsExpanded) chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }
    val driveWorkspacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                driveWorkspaceName = onConnectDriveWorkspace(uri)
                driveWorkspaceUri = uri.toString()
                answer = "Google Drive model workspace connected. Local model downloads need no AI API key."
            } catch (error: Exception) {
                answer = "Could not connect Google Drive folder: ${error.message}"
            }
        }
    }
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    if (localReady) onUnloadLocal()
                    localReady = false
                    loadedModelPath = ""
                    localModelPath = onImportModel(uri)
                    installedModels = onListModels()
                    freeStorageBytes = onFreeStorage()
                    answer = "Model imported and GGUF metadata validated. Load it before starting offline chat."
                } catch (error: Exception) {
                    answer = "Model import failed: ${error.message}"
                } finally {
                    busy = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WASTI NEXUS")
                        Text(
                            when {
                                offlineMode -> "OFFLINE · PRIVATE ON-DEVICE"
                                autoMode -> "AUTO · LOCAL FIRST WITH ONLINE FALLBACK"
                                else -> "ONLINE · HOSTED AI"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(enabled = !busy, onClick = {
                        conversation = emptyList()
                        streamingResponse = ""
                        remoteConversation = emptyList()
                        answer = "New conversation started."
                    }) { Text("New chat") }
                    TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                        Text(if (settingsExpanded) "Done" else "Settings")
                    }
                }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.imePadding(), tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth()) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message WASTI NEXUS") },
                            maxLines = 5,
                            enabled = !busy
                        )
                        Button(
                            enabled = !busy && prompt.isNotBlank() && (!offlineMode || localReady || (url.startsWith("http://localhost") && remoteModels.isNotEmpty())),
                            onClick = {
                                val submittedPrompt = prompt.trim()
                                busy = true
                                scope.launch {
                                    var usedRemote = false
                                    try {
                                        val mode = when {
                                            offlineMode -> NexusMode.OFFLINE
                                            autoMode -> NexusMode.AUTO
                                            else -> NexusMode.ONLINE
                                        }
                                        val task = NexusRuntimePolicy.classify(submittedPrompt)
                                        val loopback = NexusRuntimePolicy.isLoopbackHttp(url.trim().trimEnd('/'))
                                        val remoteConfigured = remoteModels.isNotEmpty() &&
                                            (loopback || (key.length >= 32 && url.startsWith("https://"))) &&
                                            (!autoMode || loopback || remoteModels.contains("nexus-auto"))
                                        val beforeSelection = NexusRuntimeProbe.read(context, localModelPath, localReady && loadedModelPath == localModelPath)
                                        val reclaimableBytes = if (localReady && loadedModelPath.isNotBlank()) {
                                            (NexusRuntimePolicy.estimatedLocalWorkingSet(loadedModelPath).coerceAtMost(beforeSelection.totalRamBytes) * 3L) / 4L
                                        } else 0L
                                        val preferredLocalPath = if (mode == NexusMode.AUTO) {
                                            NexusRuntimePolicy.chooseLocalModelPath(
                                                task, installedModels, beforeSelection.availableRamBytes + reclaimableBytes, localModelPath
                                            )
                                        } else localModelPath
                                        if (mode == NexusMode.AUTO && preferredLocalPath.isNotBlank() && preferredLocalPath != loadedModelPath) {
                                            if (localReady) onUnloadLocal()
                                            localReady = false
                                            loadedModelPath = ""
                                            localModelPath = preferredLocalPath
                                            onSelectModel(preferredLocalPath)
                                        }
                                        val effectiveLocalPath = if (mode == NexusMode.AUTO) preferredLocalPath else localModelPath
                                        val snapshot = NexusRuntimeProbe.read(
                                            context, effectiveLocalPath, localReady && loadedModelPath == effectiveLocalPath
                                        )
                                        val localServerAvailable = loopback && remoteModels.isNotEmpty() &&
                                            NexusRuntimePolicy.localServerCanHandle(task, remoteModels.first())
                                        val decision = NexusRuntimePolicy.decide(
                                            mode = mode,
                                            snapshot = snapshot,
                                            remoteConfigured = remoteConfigured && !loopback,
                                            localServerAvailable = localServerAvailable,
                                            prompt = submittedPrompt
                                        )
                                        val result = when (decision.route) {
                                            NexusRoute.LOCAL -> {
                                                try {
                                                    if (!localReady || loadedModelPath != effectiveLocalPath) {
                                                        if (localReady) onUnloadLocal()
                                                        onLoadLocal(effectiveLocalPath)
                                                        localReady = true
                                                        loadedModelPath = effectiveLocalPath
                                                    }
                                                    streamingResponse = ""
                                                    onSendLocal(submittedPrompt) { token -> streamingResponse += token }
                                                } catch (localError: Exception) {
                                                    localReady = false
                                                    loadedModelPath = ""
                                                    if (mode != NexusMode.AUTO) throw localError
                                                    when {
                                                        localServerAvailable -> {
                                                            usedRemote = true
                                                            onSendRemote(url, key, remoteConversation, submittedPrompt, false, remoteModels.first())
                                                        }
                                                        snapshot.internetValidated && remoteConfigured -> {
                                                            onSave(url, key)
                                                            usedRemote = true
                                                            onSendRemote(url, key, remoteConversation, submittedPrompt, false, "nexus-auto")
                                                        }
                                                        else -> throw localError
                                                    }
                                                }
                                            }
                                            NexusRoute.TERMUX -> {
                                                if (localReady) onUnloadLocal()
                                                localReady = false
                                                loadedModelPath = ""
                                                usedRemote = true
                                                onSendRemote(url, key, remoteConversation, submittedPrompt, false, remoteModels.first())
                                            }
                                            NexusRoute.REMOTE -> {
                                                if (localReady) onUnloadLocal()
                                                localReady = false
                                                loadedModelPath = ""
                                                onSave(url, key)
                                                usedRemote = true
                                                val remoteAgent = if (mode == NexusMode.AUTO) "nexus-auto" else agentId
                                                onSendRemote(url, key, remoteConversation, submittedPrompt, hybrid && mode != NexusMode.AUTO, remoteAgent)
                                            }
                                            NexusRoute.BLOCKED -> throw IllegalStateException(decision.reason)
                                        }
                                        conversation = conversation + (submittedPrompt to result)
                                        streamingResponse = ""
                                        if (usedRemote) {
                                            remoteConversation = remoteConversation + (submittedPrompt to result)
                                        }
                                        prompt = ""
                                        answer = "Completed via ${if (usedRemote) "self-hosted remote inference" else "on-device inference"}. ${decision.reason}"
                                    } catch (error: Exception) {
                                        streamingResponse = ""
                                        answer = "Request failed: ${error.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) { Text("Send") }
                    }
                }
            }
        }
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp).verticalScroll(chatScrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (settingsExpanded) {
                Text("One intelligence. Many brains.", style = MaterialTheme.typography.headlineSmall)
                Text("MODEL GOVERNANCE", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${initialRegistryModels.size} model families and tools are catalogued with their licence, capability, hosting and hardware status. Catalogue entries are not automatically installable.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { registryDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Browse extended model and licence registry") }
                if (registryDialog) {
                    AlertDialog(
                        onDismissRequest = { registryDialog = false },
                        title = { Text("WASTI NEXUS · Model Governance") },
                        text = {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                initialRegistryModels.forEach { item ->
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${item.creator} · ${item.license}", style = MaterialTheme.typography.bodySmall)
                                        Text("${item.placement.replace('_', ' ')} · ${item.licenseStatus} · ${item.status}", style = MaterialTheme.typography.labelSmall)
                                        Text(item.notes, style = MaterialTheme.typography.bodySmall)
                                        Text(item.commercialUse, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { registryDialog = false }) { Text("Close") }
                        }
                    )
                }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = autoMode,
                    onClick = { autoMode = true; offlineMode = false },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = !autoMode && !offlineMode,
                    onClick = { autoMode = false; offlineMode = false },
                    label = { Text("Online") }
                )
                FilterChip(
                    selected = offlineMode,
                    onClick = { autoMode = false; offlineMode = true },
                    label = { Text("Offline") }
                )
            }

            if (offlineMode || autoMode) {
                Text("LOCAL MODEL", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { modelMenuExpanded = true },
                    enabled = installedModels.isNotEmpty() && !busy
                ) {
                    Text(localModelPath.substringAfterLast('/').ifBlank { "Choose an installed GGUF model" })
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    installedModels.forEach { modelPath ->
                        DropdownMenuItem(
                            text = { Text(modelPath.substringAfterLast('/')) },
                            onClick = {
                                modelMenuExpanded = false
                                busy = true
                                scope.launch {
                                    try {
                                        if (localReady) onUnloadLocal()
                                        localReady = false
                                        loadedModelPath = ""
                                        localModelPath = modelPath
                                        onSelectModel(modelPath)
                                        answer = "Selected model. Load it to start offline inference."
                                    } catch (error: Exception) {
                                        answer = "Could not switch model: ${error.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        )
                    }
                }
                Text(
                    "Installed: ${installedModels.size} model(s) · Free storage: ${"%.1f".format(freeStorageBytes / (1024.0 * 1024.0 * 1024.0))} GB",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "All twelve open-weight model files have pinned public source revisions, exact sizes and SHA-256 values. NEXUS verifies the checksum before installation. Public-source downloads and offline inference require no AI API key.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("GOOGLE DRIVE MODEL WORKSPACE", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    enabled = !busy,
                    onClick = { driveWorkspacePicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (driveWorkspaceUri.isBlank()) "Connect Google Drive folder" else "Change Google Drive folder") }
                if (driveWorkspaceUri.isNotBlank()) {
                    Text("Connected folder: ${driveWorkspaceName.ifBlank { "Google Drive workspace" }}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = !busy && workspaceModels.size == 12 && workspaceModels.all { it.downloadable },
                        onClick = { confirmArchiveAll = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Archive all 12 models to Drive · 9.59 GB") }
                    Text("Copies the pinned public GGUF files into your private Drive folder. Wi-Fi is recommended. Requires Drive write permission; no AI API key is needed.", style = MaterialTheme.typography.bodySmall)
                    if (archivingModels) {
                        LinearProgressIndicator(progress = { archiveProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Archiving all models: ${(archiveProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    workspaceModels.forEach { model ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("${model.family} · ${model.quantization} · ${model.license}", style = MaterialTheme.typography.bodySmall)
                                if (model.downloadable) {
                                    Text("Pinned source · ${model.sizeBytes?.div(1_000_000)} MB · device runtime test pending", style = MaterialTheme.typography.bodySmall)
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            downloadingModel = true
                                            downloadProgress = 0f
                                            busy = true
                                            scope.launch {
                                                try {
                                                    val downloadedPath = onInstallDriveModel(Uri.parse(driveWorkspaceUri), model) { received, total ->
                                                        downloadProgress = if (total > 0L) received.toFloat() / total.toFloat() else 0f
                                                    }
                                                    installedModels = onListModels()
                                                    localModelPath = downloadedPath
                                                    onSelectModel(downloadedPath)
                                                    onLoadLocal(downloadedPath)
                                                    localReady = true
                                                    loadedModelPath = downloadedPath
                                                    offlineMode = true
                                                    autoMode = false
                                                    freeStorageBytes = onFreeStorage()
                                                    answer = "${model.displayName} downloaded from Google Drive, SHA-256 verified, installed and loaded. Offline chat is ready."
                                                } catch (error: Exception) {
                                                    localReady = false
                                                    answer = "Drive model installation failed: ${error.message}"
                                                } finally {
                                                    downloadingModel = false
                                                    busy = false
                                                }
                                            }
                                        }
                                    ) { Text("Download from Drive") }
                                    if (downloadingModel && downloadProgress > 0f) {
                                        LinearProgressIndicator(progress = { downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                        Text("Transfer and verification: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    Text("Not yet downloadable · ${model.note}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    Text("Choose the WASTI-NEXUS-MODEL-WORKSPACE folder from Google Drive. NEXUS receives access only to the folder you select.", style = MaterialTheme.typography.bodySmall)
                }
                if (confirmArchiveAll) {
                    AlertDialog(
                        onDismissRequest = { if (!busy) confirmArchiveAll = false },
                        title = { Text("Archive all twelve models?") },
                        text = {
                            Text("This transfers 9.59 GB of public model files into the selected private Google Drive folder. Use Wi-Fi and keep the app open. Files are checked against pinned sizes and SHA-256 values. If Drive write access is unavailable, the app will explain the manual upload fallback.")
                        },
                        confirmButton = {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    confirmArchiveAll = false
                                    busy = true
                                    archivingModels = true
                                    archiveProgress = 0f
                                    scope.launch {
                                        try {
                                            val count = onArchiveAllModelsToDrive(
                                                Uri.parse(driveWorkspaceUri),
                                                workspaceModels
                                            ) { completed, total ->
                                                archiveProgress = if (total > 0L) completed.toFloat() / total.toFloat() else 0f
                                            }
                                            answer = "Archived $count model files to your private Google Drive folder. Each source file passed its pinned size and SHA-256 checks. Select Download from Drive on any model to install it locally."
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            answer = "Google Drive model archive failed: ${error.message}"
                                        } finally {
                                            archivingModels = false
                                            busy = false
                                        }
                                    }
                                }
                            ) { Text("Start archive") }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmArchiveAll = false }
                            ) { Text("Cancel") }
                        }
                    )
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import GGUF from device") }
                    Button(
                        enabled = !busy && localModelPath.isNotBlank() && !localReady,
                        onClick = {
                            busy = true
                            scope.launch {
                                answer = try {
                                    onLoadLocal(localModelPath)
                                    localReady = true
                                    loadedModelPath = localModelPath
                                    "Local model loaded. You can chat without internet."
                                } catch (error: Exception) {
                                    localReady = false
                                    loadedModelPath = ""
                                    "Could not load local model: ${error.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Load model") }
                if (localModelPath.isNotBlank()) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { confirmDeleteModel = true }
                    ) { Text("Remove selected model") }
                }
                if (confirmDeleteModel) {
                    AlertDialog(
                        onDismissRequest = { confirmDeleteModel = false },
                        title = { Text("Remove downloaded model?") },
                        text = { Text("This deletes the selected GGUF file from this app's private storage. The original file is not affected.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDeleteModel = false
                                busy = true
                                scope.launch {
                                    try {
                                        if (localReady) onUnloadLocal()
                                        localReady = false
                                        loadedModelPath = ""
                                        onDeleteModel(localModelPath)
                                        installedModels = onListModels()
                                        localModelPath = installedModels.firstOrNull().orEmpty()
                                        onSelectModel(localModelPath)
                                        freeStorageBytes = onFreeStorage()
                                        answer = "Model removed from this device."
                                    } catch (error: Exception) {
                                        answer = "Could not remove model: ${error.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDeleteModel = false }) { Text("Cancel") }
                        }
                    )
                }
                if (localReady) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    onUnloadLocal()
                                    localReady = false
                                    loadedModelPath = ""
                                    answer = "Local model unloaded and native memory released."
                                } catch (error: Exception) {
                                    answer = "Could not unload local model: ${error.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Unload model") }
                }
                Text(
                    "Only GGUF is supported by the current local runtime. Model loading checks available RAM, but real fit and speed still depend on your phone and the selected quantization.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!offlineMode) {
                Text("ONLINE GATEWAY", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(),
                    label = { Text("Self-hosted endpoint (Termux / LAN / server)") }, singleLine = true
                )
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(),
                    label = { Text("Termux pairing token / remote server bearer key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        onSave(url, key)
                        answer = "Connection settings saved. API key encrypted with Android Keystore."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save connection") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hybrid,
                        onClick = { hybrid = true },
                        enabled = remoteModels.size >= 2,
                        label = { Text("Hybrid orchestration") }
                    )
                    FilterChip(selected = !hybrid, onClick = { hybrid = false }, label = { Text("Single brain") })
                }
                if (remoteModels.size < 2) {
                    Text(
                        "Hybrid requires at least two distinct configured online models. Single-brain mode is active.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                onSave(url, key)
                                remoteModels = onFetchModels(url, key)
                                if (agentId !in remoteModels) agentId = remoteModels.firstOrNull().orEmpty()
                                if (remoteModels.size < 2) hybrid = false
                                if (remoteModels.size < 2) {
                                    "Loaded ${remoteModels.size} online brain(s). Hybrid stays disabled until two distinct models are configured."
                                } else {
                                    "Loaded ${remoteModels.size} online brain(s). Hybrid orchestration is available."
                                }
                            } catch (error: Exception) {
                                "Could not load online brains: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Refresh online brains") }
                if (!hybrid) {
                    if (remoteModels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { remoteMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(agentId.ifBlank { "Choose online brain" }) }
                        DropdownMenu(
                            expanded = remoteMenuExpanded,
                            onDismissRequest = { remoteMenuExpanded = false }
                        ) {
                            remoteModels.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId) },
                                    onClick = {
                                        agentId = modelId
                                        remoteMenuExpanded = false
                                    }
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            agentId, { agentId = it }, Modifier.fillMaxWidth(),
                            label = { Text("Configured agent ID (for example: qwen)") }, singleLine = true
                        )
                    }
                }
            }


                            OutlinedTextField(
                value = memoryPassphrase,
                onValueChange = { memoryPassphrase = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Memory recovery passphrase (12+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && memoryPassphrase.length >= 12,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                val restored = onRestoreMemory(memoryPassphrase)
                                conversation = restored
                                "Restored ${restored.size} encrypted conversation turn(s) from local storage."
                            } catch (error: Exception) {
                                "Could not restore local memory: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Restore latest local memory") }
                OutlinedButton(
                    enabled = !busy && !offlineMode && memoryPassphrase.length >= 12,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                val restored = onRestoreRemoteMemory(url, key, memoryPassphrase)
                                conversation = restored
                                "Restored ${restored.size} encrypted conversation turn(s) from GitHub."
                            } catch (error: Exception) {
                                "Could not restore GitHub memory: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Restore from GitHub") }
                OutlinedButton(
                    enabled = !busy && conversation.isNotEmpty() && memoryPassphrase.length >= 12,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                "Encrypted memory snapshot saved locally: ${onSaveMemory(conversation, memoryPassphrase)}"
                            } catch (error: Exception) {
                                "Could not save encrypted memory: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Save encrypted memory") }
                OutlinedButton(
                    enabled = !busy && conversation.isNotEmpty() && !offlineMode && memoryPassphrase.length >= 12,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                onSyncMemory(url, key, conversation, memoryPassphrase)
                            } catch (error: Exception) {
                                error.message ?: "Memory sync failed."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Sync to GitHub") }
            }
            Text(
                "Memory uses PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM. Use the same 12+ character recovery passphrase on every device. The passphrase is not uploaded or stored by NEXUS; if you forget it, encrypted snapshots cannot be recovered.",
                style = MaterialTheme.typography.bodySmall
            )

                Text(answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (conversation.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Hello, I'm WASTI NEXUS.", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Your personal AI workspace. Ask a question, explore an idea, or start creating.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (offlineMode) "Private offline mode is selected." else "Your selected online brain is ready when connected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                conversation.takeLast(40).forEach { (user, assistant) ->
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.88f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(user, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("WASTI NEXUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(assistant, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp))
                    }
                }
                if (streamingResponse.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("WASTI NEXUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(streamingResponse, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp))
                    }
                }
                if (answer.startsWith("Request failed:")) {
                    Text(answer, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) Text("WASTI NEXUS is thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
