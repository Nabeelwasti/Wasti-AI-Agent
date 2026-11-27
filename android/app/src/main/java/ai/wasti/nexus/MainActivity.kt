package ai.wasti.nexus

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

internal data class NexusAttachment(val name: String, val mimeType: String, val textContent: String? = null, val imageDataUrl: String? = null)

private fun readNexusAttachment(context: Context, uri: Uri): NexusAttachment {
    val resolver = context.contentResolver
    val mime = (resolver.getType(uri) ?: "").lowercase().substringBefore(';')
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf { it.isNotBlank() } ?: "attachment"
    val extension = name.substringAfterLast('.', "").lowercase()
    val textTypes = setOf("text/plain", "text/markdown", "text/csv", "application/json", "application/xml", "text/xml", "text/html")
    val textExtensions = setOf("txt", "md", "csv", "json", "xml", "html", "kt", "py", "js", "log")
    val imageTypes = setOf("image/jpeg", "image/png", "image/webp")
    val isText = mime in textTypes || extension in textExtensions
    val normalizedMime = when {
        isText -> mime.takeIf { it in textTypes } ?: when (extension) {
            "json" -> "application/json"; "xml" -> "application/xml"; "csv" -> "text/csv"
            "html" -> "text/html"; "md" -> "text/markdown"; else -> "text/plain"
        }
        else -> mime
    }
    require(isText || normalizedMime in imageTypes) { "Unsupported type. Use TXT, MD, CSV, JSON, XML, HTML, KT, PY, JS, LOG, JPEG, PNG or WebP." }
    val limit = if (isText) 128 * 1024 else 1_000_000
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { if (isText) "Text attachments must be 128 KB or smaller." else "Images must be 1 MB or smaller." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IllegalStateException("Could not open the selected attachment.")
    require(bytes.isNotEmpty()) { "The selected attachment is empty." }
    return if (isText) {
        val content = String(bytes, Charsets.UTF_8)
        require('\u0000' !in content) { "The selected file is not valid UTF-8 text." }
        NexusAttachment(name, normalizedMime, textContent = content)
    } else {
        NexusAttachment(name, normalizedMime, imageDataUrl = "data:" + normalizedMime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}
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
    private suspend fun executeCancellable(request: Request): okhttp3.Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }
    private val prefs by lazy { getSharedPreferences("nexus-config", MODE_PRIVATE) }
    private val chatStore by lazy { EncryptedNexusChatStore(applicationContext) }
    private val localEngineDelegate = lazy { AiChat.getInferenceEngine(applicationContext) }
    private val localEngine: InferenceEngine by localEngineDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var chatHistoryResult by remember { mutableStateOf<Result<List<NexusChatSession>>?>(null) }
            LaunchedEffect(Unit) {
                chatHistoryResult = withContext(Dispatchers.IO) { runCatching { chatStore.load() } }
            }
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this@MainActivity)
                else dynamicLightColorScheme(this@MainActivity)
            ) {
                if (chatHistoryResult == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val historyResult = requireNotNull(chatHistoryResult)
                    NexusScreen(
                    initialUrl = prefs.getString("url", null)?.takeIf { it.isNotBlank() } ?: "http://localhost:8088",
                    initialPrompt = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(12000),
                    initialKey = decryptSecret(),
                    initialModelPath = prefs.getString("local_model_path", "") ?: "",
                    initialModels = installedModels(),
                    initialWorkspaceModels = DriveModelWorkspace.loadCatalog(this),
                    initialRegistryModels = NexusModelRegistry.load(this),
                    initialModelNotices = NexusModelRegistry.loadNotices(this),
                    initialLicenseTexts = NexusModelRegistry.loadLicenseTexts(this),
                    initialDriveWorkspaceUri = prefs.getString("drive_workspace_uri", "") ?: "",
                    initialFreeStorageBytes = StatFs(filesDir.absolutePath).availableBytes,
                    initialChatSessions = historyResult.getOrDefault(emptyList()),
                    initialChatError = historyResult.exceptionOrNull()?.message.orEmpty(),
                    onPersistChatSessions = { sessions -> withContext(Dispatchers.IO) { chatStore.save(sessions) } },
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
                    onSendRemote = { url, key, history, prompt, hybrid, agentId, attachments, onCollaboration, onToken ->
                        sendRemote(url, key, history, prompt, hybrid, agentId, attachments, onCollaboration, onToken)
                    }
                    )
                }
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
        val apiRoot = NexusRuntimePolicy.normalizeApiRoot(root)
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
        val apiRoot = NexusRuntimePolicy.normalizeApiRoot(root)
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
        val estimatedNeed = NexusRuntimePolicy.estimatedLocalWorkingSet(model.absolutePath)
        require(!memory.lowMemory && memory.availMem >= estimatedNeed) {
            val modelMb = model.length() / (1024L * 1024L)
            val availableMb = memory.availMem / (1024L * 1024L)
            val estimatedMb = estimatedNeed / (1024L * 1024L)
            "Android reports ${availableMb} MB available for a ${modelMb} MB GGUF. " +
                "Estimated native working set is ${estimatedMb} MB (includes runtime/context headroom). " +
                "Android low-memory state: ${memory.lowMemory}. The app no longer rejects models using the old 2.5× + 1.2 GB estimate."
        }
        try {
            val engineState = kotlinx.coroutines.withTimeout(45_000L) {
                localEngine.state.first {
                    it is InferenceEngine.State.Initialized ||
                        it is InferenceEngine.State.ModelReady ||
                        it is InferenceEngine.State.Error
                }
            }
            when (engineState) {
                is InferenceEngine.State.Error,
                is InferenceEngine.State.ModelReady -> localEngine.cleanUp()
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
            runCatching { localEngine.cleanUp() }
            throw IllegalStateException(
                "Native inference ran out of memory while loading ${model.length() / (1024L * 1024L)} MB GGUF. " +
                    "Android's process limit or the model's 8K context/batch working set was reached. " +
                    "Try closing memory-heavy apps or use a smaller quantization; the preflight estimate was not a guarantee.",
                error
            )
        } catch (error: Exception) {
            runCatching { localEngine.cleanUp() }
            throw error
        }
    }

    private suspend fun unloadLocalModel() = withContext(Dispatchers.Default) {
        localEngine.cleanUp()
    }

    private suspend fun sendLocal(prompt: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            val output = StringBuilder()
            try {
                localEngine.sendUserPrompt(prompt).collect { token ->
                    output.append(token)
                    withContext(Dispatchers.Main.immediate) { onToken(token) }
                }
                check(localEngine.state.value is InferenceEngine.State.ModelReady) {
                    "The inference engine did not return to its ready state. Reload the model before sending another message."
                }
                output.toString().ifBlank { "The local model returned an empty response." }
            } catch (error: Exception) {
                if (localEngine.state.value is InferenceEngine.State.Error) {
                    runCatching { localEngine.cleanUp() }
                }
                throw error
            }
        }


    private suspend fun fetchRemoteModels(url: String, key: String): List<String> =
        withContext(Dispatchers.IO) {
            val root = url.trim().trimEnd('/')
            val local = NexusRuntimePolicy.isLoopbackHttp(root)
            require(root.startsWith("https://") || local) { "Use HTTPS for remote servers; HTTP is permitted only for loopback Termux." }
            require(key.length >= 32) { "Enter the Termux pairing token or a strong remote server bearer key." }
            val apiRoot = NexusRuntimePolicy.normalizeApiRoot(root)
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
        agentId: String,
        attachments: List<NexusAttachment>,
        onCollaboration: (String) -> Unit,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        val local = NexusRuntimePolicy.isLoopbackHttp(root)
        require(root.startsWith("https://") || local) { "Use HTTPS for remote servers; HTTP is permitted only for loopback Termux." }
        require(key.length >= 32) { "Enter the Termux pairing token or a strong remote server bearer key." }

        // Bound the remote context so a long chat cannot exceed gateway request limits.
        // Only the online conversation is supplied; local-only turns never leave the device.
        // Preserve the complete remote conversation. The model/provider's actual context
        // window is the only remaining bound; NEXUS no longer silently drops older turns.
        val selectedHistory = history.toList()
        val messages = JSONArray()
        selectedHistory.asReversed().forEach { (user, assistant) ->
            messages.put(JSONObject().put("role", "user").put("content", user))
            messages.put(JSONObject().put("role", "assistant").put("content", assistant))
        }
        val textAttachments = attachments.filter { it.textContent != null }
        val promptWithFiles = buildString {
            append(prompt)
            textAttachments.forEach { attachment ->
                append("\n\n[Attached file: ").append(attachment.name).append("]\n")
                append(attachment.textContent.orEmpty())
            }
        }
        val images = attachments.mapNotNull { it.imageDataUrl }
        val currentContent: Any = if (images.isEmpty()) promptWithFiles else JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", promptWithFiles))
            images.forEach { dataUrl -> put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl))) }
        }
        messages.put(JSONObject().put("role", "user").put("content", currentContent))
        val automatic = agentId == "nexus-auto"
        val stream = !hybrid && !automatic
        val payload = JSONObject()
            .put("model", when { hybrid -> "nexus-hybrid"; automatic -> "nexus-auto"; else -> agentId })
            .put("messages", messages)
            .put("stream", stream)
        if (automatic) payload.put("nexus", JSONObject().put("mode", "auto"))
        if (hybrid) {
            payload.put("nexus", JSONObject().put("mode", "hybrid").put("review_rounds", 1))
        }
        val apiRoot = NexusRuntimePolicy.normalizeApiRoot(root)
        val requestBuilder = Request.Builder()
            .url("$apiRoot/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (key.isNotBlank()) requestBuilder.header("Authorization", "Bearer $key")
        val request = requestBuilder.build()
        executeCancellable(request).use { response ->
            val responseBody = response.body ?: throw IllegalStateException("Gateway returned an empty response body.")
            if (!response.isSuccessful) {
                val errorText = responseBody.string()
                throw GatewayHttpException(response.code, "Gateway HTTP ${response.code}: $errorText")
            }
            if (stream) {
                val output = StringBuilder()
                responseBody.charStream().buffered().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isBlank()) continue
                        val delta = runCatching {
                            JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("delta")?.optString("content").orEmpty()
                        }.getOrDefault("")
                        if (delta.isNotEmpty()) {
                            output.append(delta)
                            withContext(Dispatchers.Main.immediate) { onToken(delta) }
                        }
                    }
                }
                val streamedAnswer = output.toString()
                if (streamedAnswer.isBlank()) throw IllegalStateException("The streaming provider returned no text tokens.")
                val provider = response.header("x-nexus-provider").orEmpty()
                val model = response.header("x-nexus-model").orEmpty()
                withContext(Dispatchers.Main.immediate) {
                    onCollaboration("Mode: single-model stream\nSelected brain: ${model.ifBlank { agentId }}\nProvider: ${provider.ifBlank { "configured inference host" }}")
                }
                streamedAnswer
            } else {
                val responseJson = JSONObject(responseBody.string())
                val answer = responseJson.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gateway response did not contain an answer.")
                responseJson.optJSONObject("nexus")?.let { metadata ->
                    val trace = formatCollaborationTrace(metadata)
                    withContext(Dispatchers.Main.immediate) { onCollaboration(trace) }
                }
                answer
            }
        }
    }

    private fun formatCollaborationTrace(metadata: JSONObject): String {
        val lines = mutableListOf<String>()
        listOf("mode" to "Mode", "task" to "Task", "trace_id" to "Trace", "selected_agent" to "Selected brain", "synthesis_agent" to "Synthesis brain").forEach { (key, label) ->
            metadata.optString(key).takeIf { it.isNotBlank() }?.let { lines += "$label: $it" }
        }
        if (metadata.has("duration_ms")) lines += "Duration: ${metadata.optLong("duration_ms")} ms"
        val participants = metadata.optJSONArray("participants") ?: metadata.optJSONArray("participating_agents")
        if (participants != null && participants.length() > 0) {
            lines += "Participating brains:"
            for (i in 0 until participants.length()) {
                val item = participants.opt(i)
                val id = if (item is JSONObject) item.optString("id", item.optString("agent")) else item?.toString().orEmpty()
                if (id.isNotBlank()) lines += "• $id"
            }
        }
        fun reports(title: String, items: JSONArray?, key: String = "content") {
            if (items == null || items.length() == 0) return
            lines += ""; lines += "$title:"
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id", item.optString("agent", "Brain ${i + 1}"))
                lines += "[$id${if (item.has("ok") && !item.optBoolean("ok")) " · failed" else ""}]"
                item.optString(key).takeIf { it.isNotBlank() }?.let { lines += it.take(6000) }
                item.optString("error").takeIf { it.isNotBlank() }?.let { lines += "Error: $it" }
            }
        }
        reports("Independent model responses", metadata.optJSONArray("independent_answers") ?: metadata.optJSONArray("specialist_results"))
        reports("Peer review / discussion", metadata.optJSONArray("peer_reviews") ?: metadata.optJSONArray("peer_review_reports"))
        reports("Failed agents", metadata.optJSONArray("failed_agents"), "error")
        return lines.joinToString("\n").ifBlank { "The connected server returned no detailed collaboration trace." }
    }
    override fun onDestroy() {
        // AiChat.getInferenceEngine() returns a process-wide singleton. An Activity can be
        // destroyed for configuration changes while the process remains alive; destroying the
        // singleton here would leave the next Activity with a cancelled, unusable engine.
        // Native resources are reclaimed when the Android process exits. Users can explicitly
        // unload a model from Settings to release its working memory while the process is alive.
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
    initialModelNotices: String,
    initialLicenseTexts: String,
    initialDriveWorkspaceUri: String,
    initialFreeStorageBytes: Long,
    initialChatSessions: List<NexusChatSession>,
    initialChatError: String,
    onPersistChatSessions: suspend (List<NexusChatSession>) -> Unit,
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
    onSendRemote: suspend (String, String, List<Pair<String, String>>, String, Boolean, String, List<NexusAttachment>, (String) -> Unit, (String) -> Unit) -> String
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var localModelPath by remember {
        mutableStateOf(initialModelPath.takeIf { it in initialModels } ?: initialModels.minByOrNull { File(it).length() }.orEmpty())
    }
    var installedModels by remember { mutableStateOf(initialModels) }
    var workspaceModels by remember { mutableStateOf(initialWorkspaceModels) }
    var registryDialog by remember { mutableStateOf(false) }
    var noticesDialog by remember { mutableStateOf(false) }
    var licenseTextsDialog by remember { mutableStateOf(false) }
    var driveWorkspaceUri by remember { mutableStateOf(initialDriveWorkspaceUri) }
    var driveWorkspaceName by remember { mutableStateOf(if (initialDriveWorkspaceUri.isBlank()) "Not connected" else "Google Drive workspace") }
    var freeStorageBytes by remember { mutableLongStateOf(initialFreeStorageBytes) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var confirmDeleteModel by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var attachments by remember { mutableStateOf(emptyList<NexusAttachment>()) }
    var chatSessions by remember { mutableStateOf(initialChatSessions) }
    var activeSessionId by remember { mutableStateOf(initialChatSessions.firstOrNull()?.id ?: UUID.randomUUID().toString()) }
    var historyDialog by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var deleteSessionId by remember { mutableStateOf<String?>(null) }
    var exportMarkdown by remember { mutableStateOf("") }
    var historyNotice by remember { mutableStateOf(initialChatError) }
    var answer by remember { mutableStateOf("Welcome to WASTI NEXUS. Install a local model to begin private offline chat.") }
    var conversation by remember { mutableStateOf(initialChatSessions.firstOrNull()?.turns ?: emptyList()) }
    var streamingResponse by remember { mutableStateOf("") }
    var remoteConversation by remember { mutableStateOf(initialChatSessions.firstOrNull()?.remoteTurns ?: emptyList()) }
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
    var autoLoadAttemptedPath by remember { mutableStateOf("") }
    var modelLoadError by remember { mutableStateOf("") }
    var activityStatus by remember { mutableStateOf("Ready") }
    var collaborationTrace by remember { mutableStateOf("") }
    var collaborationExpanded by remember { mutableStateOf(false) }
    var activeGenerationJob by remember { mutableStateOf<Job?>(null) }
    var agentId by remember { mutableStateOf("nexus-gemma-4-26b-a4b") }
    var remoteModels by remember { mutableStateOf(emptyList<String>()) }
    var remoteMenuExpanded by remember { mutableStateOf(false) }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    var drawerDestination by remember { mutableStateOf("Chat") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val configuredRemoteModelCount = NexusRuntimePolicy.configuredRemoteBrainCount(remoteModels)
    val scope = rememberCoroutineScope()
    val chatScrollState = rememberScrollState()
    // Native token callbacks can arrive hundreds of times per second. Buffer them away from
    // Compose state and publish one snapshot every 40 ms instead of recomposing the full screen
    // for every token.
    val streamTokens = remember { Channel<String>(Channel.UNLIMITED) }
    val streamBuffer = remember { StringBuilder() }
    LaunchedEffect(streamTokens) {
        while (isActive) {
            val first = streamTokens.receive()
            streamBuffer.append(first)
            while (true) {
                val next = streamTokens.tryReceive().getOrNull() ?: break
                streamBuffer.append(next)
            }
            streamingResponse = streamBuffer.toString()
            delay(40)
        }
    }
    fun queueStreamToken(token: String) { streamTokens.trySend(token) }
    suspend fun persistConversation(turns: List<Pair<String, String>>, remoteTurns: List<Pair<String, String>>) {
        if (turns.isEmpty() || initialChatError.isNotBlank()) return
        val existing = chatSessions.firstOrNull { it.id == activeSessionId }
        val updatedSession = NexusChatSession.createOrUpdate(existing, turns, remoteTurns)
        val updatedSessions = (chatSessions.filterNot { it.id == activeSessionId } + updatedSession)
            .sortedByDescending { it.updatedAt }
        withContext(Dispatchers.IO) { onPersistChatSessions(updatedSessions) }
        chatSessions = updatedSessions
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(exportMarkdown) }
                    ?: error("Could not open the selected export destination.")
            }.onSuccess { historyNotice = "Conversation exported successfully." }
                .onFailure { historyNotice = "Export failed: ${it.message}" }
        }
    }
    LaunchedEffect(conversation.size, busy, settingsExpanded) {
        if (!settingsExpanded) chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }
    // Automatically load the selected model after launch, import or model selection.
    LaunchedEffect(localModelPath, installedModels) {
        androidx.compose.runtime.snapshotFlow { busy }.first { isBusy -> !isBusy }
        if (localModelPath.isBlank() || localModelPath !in installedModels ||
            loadedModelPath == localModelPath || autoLoadAttemptedPath == localModelPath) return@LaunchedEffect
        autoLoadAttemptedPath = localModelPath
        modelLoadError = ""
        busy = true
        try {
            if (localReady && loadedModelPath.isNotBlank()) onUnloadLocal()
            localReady = false
            loadedModelPath = ""
            onLoadLocal(localModelPath)
            localReady = true
            loadedModelPath = localModelPath
            answer = "${File(localModelPath).name} loaded automatically. Offline chat is ready."
        } catch (error: Exception) {
            localReady = false
            loadedModelPath = ""
            modelLoadError = error.message ?: error.javaClass.simpleName
            answer = "Automatic model load failed: $modelLoadError"
        } finally {
            busy = false
        }
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
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) prompt = listOf(prompt.trim(), spoken).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                require(attachments.size < 4) { "You can attach up to four files per message." }
                require(attachments.count { it.imageDataUrl != null } < 2) { "You can attach up to two images per message." }
                attachments = attachments + readNexusAttachment(context, uri)
            } catch (error: Exception) { answer = "Photo attachment failed: ${error.message}" }
        }
    }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                require(attachments.size < 4) { "You can attach up to four files per message." }
                val attachment = readNexusAttachment(context, uri)
                if (attachment.imageDataUrl != null) {
                    require(attachments.count { it.imageDataUrl != null } < 2) { "You can attach up to two images per message." }
                }
                attachments = attachments + attachment
                answer = "Attached " + attachment.name + "."
            } catch (error: Exception) {
                answer = "Attachment rejected: " + (error.message ?: "unsupported file")
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
                    modelLoadError = ""
                    autoLoadAttemptedPath = ""
                    answer = "Model imported and GGUF metadata validated. Loading it automatically..."
                } catch (error: Exception) {
                    answer = "Model import failed: ${error.message}"
                } finally {
                    busy = false
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !busy,
        drawerContent = {
            ModalDrawerSheet {
                Text("WASTI NEXUS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp), style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(label = { Text("New chat") }, selected = false, icon = { Icon(Icons.Default.Add, contentDescription = null) }, onClick = {
                    drawerDestination = "Chat"
                    val resetPath = localModelPath
                    conversation = emptyList(); remoteConversation = emptyList(); streamBuffer.clear(); streamingResponse = ""
                    collaborationTrace = ""; activeSessionId = UUID.randomUUID().toString(); answer = "New conversation started."
                    scope.launch { drawerState.close() }
                    if (localReady && resetPath.isNotBlank()) scope.launch {
                        busy = true
                        try { onUnloadLocal(); localReady = false; loadedModelPath = ""; onLoadLocal(resetPath); localReady = true; loadedModelPath = resetPath }
                        catch (error: Exception) { localReady = false; loadedModelPath = ""; answer = "New chat is ready, but the model needs reloading: ${error.message}" }
                        finally { busy = false }
                    }
                })
                NavigationDrawerItem(label = { Text("Chat history") }, selected = drawerDestination == "History", icon = { Icon(Icons.Default.History, contentDescription = null) }, onClick = {
                    drawerDestination = "History"; historyDialog = true; scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text("Models & settings") }, selected = drawerDestination == "Settings", icon = { Icon(Icons.Default.Tune, contentDescription = null) }, onClick = {
                    drawerDestination = "Settings"; settingsExpanded = true; scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text(if (hybrid) "Hybrid Team · On" else "Hybrid Team") }, selected = hybrid, icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }, enabled = !offlineMode && configuredRemoteModelCount >= 2, onClick = {
                    hybrid = !hybrid; scope.launch { drawerState.close() }
                })
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Private on-device inference and your configured model workspace.", modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) {
    SelectionContainer {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Open navigation") } },
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
                    IconButton(enabled = !busy, onClick = {
                        // Reset the visible session immediately; native context reset happens in
                        // the background so the + action never waits for a model reload.
                        val resetModelPath = localModelPath
                        conversation = emptyList()
                        streamBuffer.clear()
                        streamingResponse = ""
                        remoteConversation = emptyList()
                        collaborationTrace = ""
                        activeSessionId = UUID.randomUUID().toString()
                        answer = "New conversation started."
                        if (localReady && resetModelPath.isNotBlank()) {
                            busy = true
                            activityStatus = "Resetting local model context in background…"
                            scope.launch {
                                try {
                                    onUnloadLocal()
                                    localReady = false
                                    loadedModelPath = ""
                                    onLoadLocal(resetModelPath)
                                    localReady = true
                                    loadedModelPath = resetModelPath
                                    answer = "New conversation ready."
                                    activityStatus = "Ready · local model loaded"
                                } catch (error: Exception) {
                                    localReady = false
                                    loadedModelPath = ""
                                    answer = "New chat is ready, but the local model needs reloading: ${error.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = "New chat") }
                }
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.imePadding(), tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth()) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (attachments.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            attachments.forEachIndexed { index, attachment ->
                                AssistChip(
                                    onClick = { if (!busy) attachments = attachments.filterIndexed { itemIndex, _ -> itemIndex != index } },
                                    label = { Text(attachment.name + "  ×", maxLines = 1) },
                                    enabled = !busy
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(enabled = !busy && attachments.size < 4, onClick = { attachmentMenuExpanded = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add photos or files")
                            }
                            DropdownMenu(expanded = attachmentMenuExpanded, onDismissRequest = { attachmentMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Choose a photo") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }, onClick = {
                                    attachmentMenuExpanded = false; photoPicker.launch("image/*")
                                })
                                DropdownMenuItem(text = { Text("Browse files on device") }, leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) }, onClick = {
                                    attachmentMenuExpanded = false; attachmentPicker.launch(arrayOf("*/*"))
                                })
                            }
                        }
                        Text(
                            if (attachments.isEmpty()) "Add photos or files" else "${attachments.size} attachment(s) · tap a chip to remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (!offlineMode) {
                            FilterChip(
                                selected = hybrid,
                                onClick = { hybrid = !hybrid },
                                enabled = !busy && configuredRemoteModelCount >= 2,
                                label = { Text("Hybrid team") }
                            )
                        }
                    }
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
                        IconButton(enabled = !busy, onClick = {
                            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to WASTI NEXUS")
                            }
                            runCatching { speechLauncher.launch(speechIntent) }
                                .onFailure { answer = "Speech recognition is not available on this device." }
                        }) { Icon(Icons.Default.Mic, contentDescription = "Voice input") }
                        if (activeGenerationJob != null) {
                            IconButton(onClick = { activeGenerationJob?.cancel() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                            }
                        } else Button(
                            enabled = !busy && (prompt.isNotBlank() || attachments.isNotEmpty()) && (!offlineMode || attachments.any { it.imageDataUrl != null } || localReady || (url.startsWith("http://localhost") && remoteModels.isNotEmpty())),
                            onClick = {
                                val submittedPrompt = prompt.trim()
                                val submittedAttachments = attachments.toList()
                                val imageAttachments = submittedAttachments.any { it.imageDataUrl != null }
                                collaborationTrace = ""
                                collaborationExpanded = false
                                streamBuffer.clear()
                                streamingResponse = ""
                                busy = true
                                activeGenerationJob = scope.launch {
                                    var usedRemote = false
                                    var attemptedLocalInference = false
                                    try {
                                        val mode = when {
                                            offlineMode -> NexusMode.OFFLINE
                                            autoMode -> NexusMode.AUTO
                                            else -> NexusMode.ONLINE
                                        }
                                        activityStatus = "Understanding your request and classifying the task..."
                                        if (imageAttachments) {
                                            require(!offlineMode) { "Image understanding requires Online or Auto mode and a configured vision-capable self-hosted model." }
                                            val root = url.trim().trimEnd('/')
                                            val loopback = NexusRuntimePolicy.isLoopbackHttp(root)
                                            require(key.length >= 32 && (loopback || root.startsWith("https://")) &&
                                                remoteModels.contains(if (autoMode) "nexus-auto" else agentId)) {
                                                "Configure and refresh a remote gateway with a vision-capable model before sending images."
                                            }
                                            val targetAgent = if (autoMode) "nexus-auto" else agentId
                                            val appAwarePrompt = NexusAppKnowledge.enrich(
                                            context, submittedPrompt.ifBlank { "Analyze attached image(s)." }
                                        )
                                        val result = onSendRemote(url, key, remoteConversation, appAwarePrompt, false, targetAgent, submittedAttachments, { collaborationTrace = it }, { token -> queueStreamToken(token) })
                                            val displayPrompt = (submittedPrompt.ifBlank { "Analyze attached image(s)." }) +
                                                submittedAttachments.joinToString(prefix = "\n\n[Attached: ", postfix = "]") { it.name }
                                            val updatedConversation = conversation + (displayPrompt to result)
                                            val updatedRemoteConversation = remoteConversation + (displayPrompt to result)
                                            conversation = updatedConversation
                                            remoteConversation = updatedRemoteConversation
                                            runCatching { persistConversation(updatedConversation, updatedRemoteConversation) }
                                                .onFailure { historyNotice = "Reply completed, but local history could not be saved: ${it.message}" }
                                            streamingResponse = ""
                                            prompt = ""
                                            attachments = emptyList()
                                            activityStatus = "Completed · online vision inference"
                                            answer = "Completed via configured self-hosted vision model."
                                            return@launch
                                        }
                                        val textContextPrompt = buildString {
                                            append(submittedPrompt)
                                            submittedAttachments.filter { it.textContent != null }.forEach { attachment ->
                                                append("\n\n[Attached file: ").append(attachment.name).append("]\n")
                                                append(attachment.textContent.orEmpty())
                                            }
                                        }
                                        val appAwarePrompt = NexusAppKnowledge.enrich(context, textContextPrompt)
                                        val task = NexusRuntimePolicy.classify(textContextPrompt)
                                        activityStatus = "Task: ${task.name.lowercase()} · selecting the best available route..."
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
                                        val localServerAvailable = loopback &&
                                            NexusRuntimePolicy.hasCompatibleRemoteModel(remoteModels, task)
                                        val decision = NexusRuntimePolicy.decide(
                                            mode = mode,
                                            snapshot = snapshot,
                                            remoteConfigured = remoteConfigured && !loopback,
                                            localServerAvailable = localServerAvailable,
                                            prompt = textContextPrompt
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
                                                    attemptedLocalInference = true
                                                    onSendLocal(appAwarePrompt) { token -> queueStreamToken(token) }
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (localError: Exception) {
                                                    localReady = false
                                                    loadedModelPath = ""
                                                    if (mode != NexusMode.AUTO) throw localError
                                                    when {
                                                        localServerAvailable -> {
                                                            usedRemote = true
                                                            onSendRemote(url, key, remoteConversation, appAwarePrompt, false, remoteModels.first(), emptyList(), { collaborationTrace = it }, { token -> queueStreamToken(token) })
                                                        }
                                                        snapshot.internetValidated && remoteConfigured -> {
                                                            onSave(url, key)
                                                            usedRemote = true
                                                            onSendRemote(url, key, remoteConversation, appAwarePrompt, false, "nexus-auto", emptyList(), { collaborationTrace = it }, { token -> queueStreamToken(token) })
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
                                                onSendRemote(url, key, remoteConversation, appAwarePrompt, false, remoteModels.first(), emptyList(), { collaborationTrace = it }, { token -> queueStreamToken(token) })
                                            }
                                            NexusRoute.REMOTE -> {
                                                if (localReady) onUnloadLocal()
                                                localReady = false
                                                loadedModelPath = ""
                                                onSave(url, key)
                                                usedRemote = true
                                                val remoteAgent = if (mode == NexusMode.AUTO) "nexus-auto" else agentId
                                                onSendRemote(url, key, remoteConversation, appAwarePrompt, hybrid && mode != NexusMode.AUTO, remoteAgent, emptyList(), { collaborationTrace = it }, { token -> queueStreamToken(token) })
                                            }
                                            NexusRoute.BLOCKED -> throw IllegalStateException(decision.reason)
                                        }
                                        val displayPrompt = submittedPrompt +
                                            submittedAttachments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\n\n[Attached: ", postfix = "]") { it.name }.orEmpty()
                                        val updatedConversation = conversation + (displayPrompt to result)
                                        val updatedRemoteConversation = if (usedRemote) remoteConversation + (submittedPrompt to result) else remoteConversation
                                        conversation = updatedConversation
                                        remoteConversation = updatedRemoteConversation
                                        runCatching { persistConversation(updatedConversation, updatedRemoteConversation) }
                                            .onFailure { historyNotice = "Reply completed, but local history could not be saved: ${it.message}" }
                                        streamingResponse = ""
                                        prompt = ""
                                        attachments = emptyList()
                                        activityStatus = "Completed · ${if (usedRemote) "online" else "on-device"} inference"
                                        answer = "Completed via ${if (usedRemote) "self-hosted inference server" else "on-device inference"}. ${decision.reason}"
                                    } catch (cancelled: CancellationException) {
                                        streamingResponse = ""
                                        activityStatus = "Stopped by user"
                                        answer = "Generation stopped by user."
                                        throw cancelled
                                    } catch (error: Exception) {
                                        streamingResponse = ""
                                        if (attemptedLocalInference) {
                                            localReady = false
                                            loadedModelPath = ""
                                        }
                                        activityStatus = "Failed · ${error.message ?: "unknown error"}"
                                        answer = "Request failed: ${error.message}"
                                    } finally {
                                        activeGenerationJob = null
                                        busy = false
                                    }
                                }
                            }
                        ) { Icon(Icons.Default.Send, contentDescription = "Send message") }
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
                OutlinedButton(
                    onClick = { noticesDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Source Licences & Upstream Notices") }
                OutlinedButton(
                    onClick = { licenseTextsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Read full Apache 2.0 & MIT licence texts") }
                if (licenseTextsDialog) {
                    AlertDialog(
                        onDismissRequest = { licenseTextsDialog = false },
                        title = { Text("Apache 2.0 & MIT Licence Texts") },
                        text = {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                            ) {
                                SelectionContainer { Text(initialLicenseTexts, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { licenseTextsDialog = false }) { Text("Close") }
                        }
                    )
                }
                if (noticesDialog) {
                    AlertDialog(
                        onDismissRequest = { noticesDialog = false },
                        title = { Text("Open Source Licences & Notices") },
                        text = {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                            ) {
                                SelectionContainer { Text(initialModelNotices, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { noticesDialog = false }) { Text("Close") }
                        }
                    )
                }
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
                                        Text(
                                            "Open upstream source",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }
                                            }
                                        )
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
                                        modelLoadError = ""
                                        autoLoadAttemptedPath = ""
                                        answer = "Selected model. Loading automatically..."
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

                } else {
                    Text("Choose the WASTI-NEXUS-MODEL-WORKSPACE folder from Google Drive. NEXUS receives access only to the folder you select.", style = MaterialTheme.typography.bodySmall)
                }
                Text("LOCAL MODEL LIBRARY", style = MaterialTheme.typography.titleMedium)
                Text("Each model's install, verification and live device status stays on its own action card.", style = MaterialTheme.typography.bodySmall)
                    workspaceModels.forEach { model ->
                        val onDevicePath = installedModels.firstOrNull { path ->
                            val name = File(path).name
                            name.equals(model.fileName, ignoreCase = true) || name.endsWith("-${model.fileName}", ignoreCase = true)
                        }
                        val partialOnDevice = File(context.filesDir, "models/${model.id}-${model.fileName}.part").exists()
                        val deviceStatus = when {
                            onDevicePath != null && localReady && loadedModelPath == onDevicePath -> "READY · loaded in inference engine"
                            onDevicePath != null -> "INSTALLED · file present, not currently loaded"
                            partialOnDevice -> "INCOMPLETE · partial transfer; resume/reinstall required"
                            else -> "NOT INSTALLED in app storage"
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("${model.family} · ${model.quantization} · ${model.license}", style = MaterialTheme.typography.bodySmall)
                                Text("ON THIS DEVICE: $deviceStatus", style = MaterialTheme.typography.labelSmall, color = if (onDevicePath != null && localReady && loadedModelPath == onDevicePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("File: ${model.fileName}", style = MaterialTheme.typography.bodySmall)
                                if (model.downloadable) {
                                    Text("Pinned source · ${model.sizeBytes?.div(1_000_000)} MB · device runtime test pending", style = MaterialTheme.typography.bodySmall)
                                    if (driveWorkspaceUri.isBlank()) {
                                        OutlinedButton(
                                            enabled = !busy,
                                            onClick = { driveWorkspacePicker.launch(null) }
                                        ) { Text("Connect Drive to download") }
                                    } else {
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
                                    }
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
                if (confirmArchiveAll) {
                    AlertDialog(
                        onDismissRequest = { if (!busy) confirmArchiveAll = false },
                        title = { Text("Archive all twelve models?") },
                        text = {
                            SelectionContainer {
                                Text("This transfers 9.59 GB of public model files into the selected private Google Drive folder. Use Wi-Fi and keep the app open. Files are checked against pinned sizes and SHA-256 values. If Drive write access is unavailable, the app will explain the manual upload fallback.")
                            }
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
                if (modelLoadError.isNotBlank()) {
                    Text("MODEL LOAD ERROR: $modelLoadError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !busy, onClick = {
                        autoLoadAttemptedPath = ""
                        modelLoadError = ""
                        scope.launch {
                            busy = true
                            try {
                                onLoadLocal(localModelPath)
                                localReady = true
                                loadedModelPath = localModelPath
                                answer = "Model loaded successfully."
                            } catch (error: Exception) {
                                localReady = false
                                loadedModelPath = ""
                                modelLoadError = error.message ?: error.javaClass.simpleName
                                answer = "Model load failed: $modelLoadError"
                            } finally { busy = false }
                        }
                    }) { Text("Retry model load") }
                } else {
                    Text(if (localReady && loadedModelPath == localModelPath) "Selected model is loaded and ready." else "Selected model will load automatically. No manual Load Model step is required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        text = { SelectionContainer { Text("This deletes the selected GGUF file from this app's private storage. The original file is not affected.") } },
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
                        enabled = configuredRemoteModelCount >= 2,
                        label = { Text("Hybrid orchestration") }
                    )
                    FilterChip(selected = !hybrid, onClick = { hybrid = false }, label = { Text("Single brain") })
                }
                Text(
                    "Hybrid team splits a request across configured specialist models, compares their independent outputs, then synthesizes one answer. It needs at least two distinct online models.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (configuredRemoteModelCount < 2) {
                    Text(
                        "Add at least two distinct online models to enable Hybrid team.",
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
                                val fetchedModels = onFetchModels(url, key)
                                remoteModels = fetchedModels
                                if (agentId !in fetchedModels) agentId = fetchedModels.firstOrNull().orEmpty()
                                val fetchedBrainCount = NexusRuntimePolicy.configuredRemoteBrainCount(fetchedModels)
                                if (fetchedBrainCount < 2) hybrid = false
                                if (fetchedBrainCount < 2) {
                                    "Loaded $fetchedBrainCount configured online brain(s). Hybrid stays disabled until two distinct models are configured."
                                } else {
                                    "Loaded $fetchedBrainCount configured online brain(s). Hybrid orchestration is available."
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
                conversation.forEachIndexed { conversationIndex, (user, assistant) ->
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.88f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(user, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(user)) }) { Text("Copy") }
                                    TextButton(onClick = {
                                        prompt = user
                                        val editedTurns = conversation.take(conversationIndex)
                                        conversation = editedTurns
                                        remoteConversation = emptyList()
                                        streamingResponse = ""
                                        scope.launch {
                                            runCatching { persistConversation(editedTurns, emptyList()) }
                                                .onFailure { historyNotice = "Edited conversation could not be saved: ${it.message}" }
                                        }
                                    }) { Text("Edit") }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("WASTI NEXUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(parseNexusMarkdown(assistant), Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp))
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(assistant)) }) { Text("Copy response") }
                    }
                }
                if (collaborationTrace.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("NEXUS · MODEL COLLABORATION", style = MaterialTheme.typography.labelLarge)
                                TextButton(onClick = { collaborationExpanded = !collaborationExpanded }) { Text(if (collaborationExpanded) "Hide" else "View discussion") }
                            }
                            if (collaborationExpanded) {
                                Text(collaborationTrace, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { clipboardManager.setText(AnnotatedString(collaborationTrace)) }) { Text("Copy collaboration log") }
                            } else Text("The connected server returned actual model participation and review metadata.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (streamingResponse.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("WASTI NEXUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(parseNexusMarkdown(streamingResponse), Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) }
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(streamingResponse)) }) { Text("Copy response") }
                    }
                }
                if (answer.startsWith("Request failed:")) {
                    Text(answer, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) Text(activityStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                else Text(activityStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (historyNotice.isNotBlank()) {
                    Text(historyNotice, color = if (initialChatError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (historyDialog) {
                    AlertDialog(
                        onDismissRequest = { historyDialog = false },
                        title = { Text("Conversation history") },
                        text = {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = historyQuery,
                                    onValueChange = { historyQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Search conversations") }
                                )
                                if (initialChatError.isNotBlank()) {
                                    Text("Encrypted history could not be opened. Existing data has been preserved; no new history will overwrite it. Details: $initialChatError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                val matchingSessions = NexusChatHistory.search(chatSessions, historyQuery)
                                if (matchingSessions.isEmpty()) {
                                    Text("No saved conversations found.", style = MaterialTheme.typography.bodySmall)
                                }
                                matchingSessions.forEach { session ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(session.title, style = MaterialTheme.typography.titleSmall)
                                            Text("${session.turns.size} turns · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(session.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = {
                                                    val updated = chatSessions.map { if (it.id == session.id) it.copy(pinned = !it.pinned, updatedAt = System.currentTimeMillis()) else it }
                                                    scope.launch {
                                                        try { withContext(Dispatchers.IO) { onPersistChatSessions(updated) }; chatSessions = updated.sortedWith(compareByDescending<NexusChatSession> { it.pinned }.thenByDescending { it.updatedAt }) }
                                                        catch (error: Exception) { historyNotice = "Could not update pin: ${error.message}" }
                                                    }
                                                }) { Text(if (session.pinned) "Unpin" else "Pin") }
                                                TextButton(onClick = {
                                                    conversation = session.turns
                                                    remoteConversation = session.remoteTurns
                                                    activeSessionId = session.id
                                                    collaborationTrace = ""
                                                    streamingResponse = ""
                                                    historyDialog = false
                                                    answer = "Opened ${session.title}."
                                                }) { Text("Open") }
                                                TextButton(onClick = {
                                                    renameSessionId = session.id
                                                    renameSessionTitle = session.title
                                                }) { Text("Rename") }
                                                TextButton(onClick = {
                                                    exportMarkdown = NexusChatHistory.exportMarkdown(session)
                                                    val safeName = session.title.replace(Regex("[^A-Za-z0-9_-]"), "-").trim('-').take(48).ifBlank { "nexus-chat" }
                                                    exportLauncher.launch("$safeName.md")
                                                }) { Text("Export") }
                                                TextButton(onClick = { deleteSessionId = session.id }) { Text("Delete") }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { historyDialog = false }) { Text("Close") } },
                        dismissButton = { TextButton(onClick = { historyQuery = ""; historyDialog = false }) { Text("Clear search") } }
                    )
                }
                if (renameSessionId != null) {
                    AlertDialog(
                        onDismissRequest = { renameSessionId = null },
                        title = { Text("Rename conversation") },
                        text = {
                            OutlinedTextField(
                                value = renameSessionTitle,
                                onValueChange = { renameSessionTitle = it.take(100) },
                                singleLine = true,
                                label = { Text("Conversation name") }
                            )
                        },
                        confirmButton = {
                            TextButton(enabled = renameSessionTitle.isNotBlank() && !busy, onClick = {
                                val targetId = renameSessionId ?: return@TextButton
                                val updated = chatSessions.map { if (it.id == targetId) it.copy(title = renameSessionTitle.trim(), updatedAt = System.currentTimeMillis()) else it }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { onPersistChatSessions(updated) }
                                        chatSessions = updated.sortedByDescending { it.updatedAt }
                                        historyNotice = "Conversation renamed."
                                    } catch (error: Exception) {
                                        historyNotice = "Could not rename conversation: ${error.message}"
                                    } finally { renameSessionId = null }
                                }
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { renameSessionId = null }) { Text("Cancel") } }
                    )
                }
                if (deleteSessionId != null) {
                    AlertDialog(
                        onDismissRequest = { deleteSessionId = null },
                        title = { Text("Delete conversation?") },
                        text = { SelectionContainer { Text("This permanently removes the selected encrypted conversation from this device. Export it first if you need a copy.") } },
                        confirmButton = {
                            TextButton(onClick = {
                                val targetId = deleteSessionId ?: return@TextButton
                                val updated = chatSessions.filterNot { it.id == targetId }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { onPersistChatSessions(updated) }
                                        chatSessions = updated
                                        if (activeSessionId == targetId) {
                                            conversation = emptyList()
                                            remoteConversation = emptyList()
                                            activeSessionId = UUID.randomUUID().toString()
                                        }
                                        historyNotice = "Conversation deleted from this device."
                                    } catch (error: Exception) {
                                        historyNotice = "Could not delete conversation: ${error.message}"
                                    } finally { deleteSessionId = null }
                                }
                            }) { Text("Delete") }
                        },
                        dismissButton = { TextButton(onClick = { deleteSessionId = null }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
    }
    }
}
