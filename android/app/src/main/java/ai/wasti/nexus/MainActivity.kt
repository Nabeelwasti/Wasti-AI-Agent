package ai.wasti.nexus

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadataReader
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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val alias = "wasti.nexus.gateway.key"
    private val memoryAlias = "wasti.nexus.memory.key"
    private val prefs by lazy { getSharedPreferences("nexus-config", MODE_PRIVATE) }
    private val localEngineDelegate = lazy { AiChat.getInferenceEngine(this) }
    private val localEngine: InferenceEngine by localEngineDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NexusScreen(
                    initialUrl = prefs.getString("url", "") ?: "",
                    initialKey = decryptSecret(),
                    initialModelPath = prefs.getString("local_model_path", "") ?: "",
                    initialModels = installedModels(),
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
                    onLoadLocal = { path -> loadLocalModel(path) },
                    onUnloadLocal = { unloadLocalModel() },
                    onSendLocal = { prompt -> sendLocal(prompt) },
                    onSaveMemory = { turns -> saveMemorySnapshot(turns) },
                    onRestoreMemory = { restoreLatestLocalMemorySnapshot() },
                    onRestoreRemoteMemory = { url, key -> restoreLatestRemoteMemorySnapshot(url, key) },
                    onFetchModels = { url, key -> fetchRemoteModels(url, key) },
                    onSyncMemory = { url, key, turns -> syncMemorySnapshot(url, key, turns) },
                    onSendRemote = { url, key, prompt, hybrid, agentId ->
                        sendRemote(url, key, prompt, hybrid, agentId)
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

    private fun memoryEncryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(memoryAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                memoryAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun createEncryptedMemorySnapshot(turns: List<Pair<String, String>>): JSONObject {
        require(turns.isNotEmpty()) { "There is no conversation to save yet." }
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

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, memoryEncryptionKey())
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
            .put("key_id", "android-keystore-memory-v1")
            .put("nonce", nonce)
            .put("ciphertext", ciphertext)
            .put("content_type", "conversation-summary")
            .put("tags", JSONArray().put("wasti-nexus").put("android"))
    }

    private suspend fun saveMemorySnapshot(turns: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val record = createEncryptedMemorySnapshot(turns)
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


    private fun decryptMemoryEnvelope(record: JSONObject): List<Pair<String, String>> {
        val nonce = Base64.decode(record.getString("nonce"), Base64.URL_SAFE or Base64.NO_WRAP)
        val ciphertext = Base64.decode(record.getString("ciphertext"), Base64.URL_SAFE or Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, memoryEncryptionKey(), GCMParameterSpec(128, nonce))
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

    private suspend fun restoreLatestLocalMemorySnapshot(): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val directory = File(filesDir, "memory/snapshots")
            val latest = directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("No local encrypted memory snapshots were found.")
            decryptMemoryEnvelope(JSONObject(latest.readText(Charsets.UTF_8)))
        }

    private suspend fun restoreLatestRemoteMemorySnapshot(
        url: String,
        key: String
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
        require(key.length >= 32) { "API key must be at least 32 characters." }
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
                .put("nonce", cipher.getString("nonce"))
                .put("ciphertext", cipher.getString("ciphertext"))
                .put("content_type", stored.optString("content_type", "conversation-summary"))
                .put("tags", stored.optJSONArray("tags") ?: JSONArray())
        }
        persistMemoryEnvelope(envelope)
        decryptMemoryEnvelope(envelope)
    }

    private suspend fun syncMemorySnapshot(
        url: String,
        key: String,
        turns: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val record = createEncryptedMemorySnapshot(turns)
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
        val estimatedNeed = (model.length() * 1.6).toLong() + 700L * 1024L * 1024L
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
                "You are WASTI NEXUS, a helpful, accurate and practical AI assistant. " +
                    "Be clear about uncertainty and never invent sources or capabilities."
            )
        } catch (error: OutOfMemoryError) {
            localEngine.cleanUp()
            throw IllegalStateException("Android ran out of memory while loading this model. Try a smaller GGUF quantization.", error)
        } catch (error: Exception) {
            localEngine.cleanUp()
            throw error
        }
    }

    private fun unloadLocalModel() {
        localEngine.cleanUp()
    }

    private suspend fun sendLocal(prompt: String): String = withContext(Dispatchers.Default) {
        val output = StringBuilder()
        localEngine.sendUserPrompt(prompt).collect { token -> output.append(token) }
        output.toString().ifBlank { "The local model returned an empty response." }
    }


    private suspend fun fetchRemoteModels(url: String, key: String): List<String> =
        withContext(Dispatchers.IO) {
            val root = url.trim().trimEnd('/')
            require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
            require(key.length >= 32) { "API key must be at least 32 characters." }
            val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
            val request = Request.Builder()
                .url("$apiRoot/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
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
        prompt: String,
        hybrid: Boolean,
        agentId: String
    ): String = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
        require(key.length >= 32) { "API key must be at least 32 characters." }
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject()
            .put("model", if (hybrid) "nexus-hybrid" else agentId)
            .put("messages", messages)
            .put("stream", false)
        if (hybrid) {
            payload.put("nexus", JSONObject().put("mode", "hybrid").put("review_rounds", 1))
        }
        val apiRoot = if (root.endsWith("/v1")) root else "$root/v1"
        val request = Request.Builder()
            .url("$apiRoot/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Gateway HTTP ${response.code}: $text")
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
    initialKey: String,
    initialModelPath: String,
    initialModels: List<String>,
    initialFreeStorageBytes: Long,
    onListModels: () -> List<String>,
    onSelectModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onFreeStorage: () -> Long,
    onSave: (String, String) -> Unit,
    onImportModel: suspend (Uri) -> String,
    onLoadLocal: suspend (String) -> Unit,
    onUnloadLocal: () -> Unit,
    onSendLocal: suspend (String) -> String,
    onSaveMemory: suspend (List<Pair<String, String>>) -> String,
    onRestoreMemory: suspend () -> List<Pair<String, String>>,
    onRestoreRemoteMemory: suspend (String, String) -> List<Pair<String, String>>,
    onFetchModels: suspend (String, String) -> List<String>,
    onSyncMemory: suspend (String, String, List<Pair<String, String>>) -> String,
    onSendRemote: suspend (String, String, String, Boolean, String) -> String
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var localModelPath by remember { mutableStateOf(initialModelPath) }
    var installedModels by remember { mutableStateOf(initialModels) }
    var freeStorageBytes by remember { mutableLongStateOf(initialFreeStorageBytes) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var confirmDeleteModel by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Choose Online or Offline mode to begin.") }
    var conversation by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var busy by remember { mutableStateOf(false) }
    var hybrid by remember { mutableStateOf(true) }
    var offlineMode by remember { mutableStateOf(false) }
    var localReady by remember { mutableStateOf(false) }
    var agentId by remember { mutableStateOf("qwen") }
    var remoteModels by remember { mutableStateOf(emptyList<String>()) }
    var remoteMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    if (localReady) onUnloadLocal()
                    localReady = false
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

    Scaffold(topBar = { TopAppBar(title = { Text("WASTI NEXUS") }) }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("One intelligence. Many brains.", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !offlineMode,
                    onClick = { offlineMode = false },
                    label = { Text("Online") }
                )
                FilterChip(
                    selected = offlineMode,
                    onClick = { offlineMode = true },
                    label = { Text("Offline — on device") }
                )
            }

            if (offlineMode) {
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
                                if (localReady) onUnloadLocal()
                                localReady = false
                                localModelPath = modelPath
                                onSelectModel(modelPath)
                                modelMenuExpanded = false
                                answer = "Selected model. Load it to start offline inference."
                            }
                        )
                    }
                }
                Text(
                    "Installed: ${installedModels.size} model(s) · Free storage: ${"%.1f".format(freeStorageBytes / (1024.0 * 1024.0 * 1024.0))} GB",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = { modelPicker.launch(arrayOf("*/*")) }
                    ) { Text("Import GGUF") }
                    Button(
                        enabled = !busy && localModelPath.isNotBlank() && !localReady,
                        onClick = {
                            busy = true
                            scope.launch {
                                answer = try {
                                    onLoadLocal(localModelPath)
                                    localReady = true
                                    "Local model loaded. You can chat without internet."
                                } catch (error: Exception) {
                                    localReady = false
                                    "Could not load local model: ${error.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Load model") }
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
                        text = { Text("This deletes the selected GGUF file from this app's private storage. The original file is not affected.") },
                        confirmButton = {
                            TextButton(onClick = {
                                if (localReady) onUnloadLocal()
                                localReady = false
                                try {
                                    onDeleteModel(localModelPath)
                                    installedModels = onListModels()
                                    localModelPath = installedModels.firstOrNull().orEmpty()
                                    onSelectModel(localModelPath)
                                    freeStorageBytes = onFreeStorage()
                                    answer = "Model removed from this device."
                                } catch (error: Exception) {
                                    answer = "Could not remove model: ${error.message}"
                                }
                                confirmDeleteModel = false
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
                            onUnloadLocal()
                            localReady = false
                            answer = "Local model unloaded and native memory released."
                        }
                    ) { Text("Unload model") }
                }
                Text(
                    "Only GGUF is supported by the current local runtime. Model loading checks available RAM, but real fit and speed still depend on your phone and the selected quantization.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("ONLINE GATEWAY", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(),
                    label = { Text("Gateway HTTPS URL") }, singleLine = true
                )
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(),
                    label = { Text("Gateway API key") },
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
                    FilterChip(selected = hybrid, onClick = { hybrid = true }, label = { Text("Hybrid — all brains") })
                    FilterChip(selected = !hybrid, onClick = { hybrid = false }, label = { Text("Single brain") })
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
                                "Loaded ${remoteModels.size} configured online brain(s)."
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
                prompt, { prompt = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("Your prompt") }
            )
            Button(
                enabled = !busy && prompt.isNotBlank() && (!offlineMode || localReady),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = try {
                            if (offlineMode) {
                                onSendLocal(prompt)
                            } else {
                                onSave(url, key)
                                onSendRemote(url, key, prompt, hybrid, agentId)
                            }
                        } catch (error: Exception) {
                            "Request failed: ${error.message}"
                        }
                        answer = result
                        if (!result.startsWith("Request failed:")) {
                            conversation = conversation + (prompt to result)
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        busy -> "Working…"
                        offlineMode -> "✦ RUN LOCALLY — NO INTERNET"
                        hybrid -> "✦ HYBRID — ALL BRAINS"
                        else -> "Send to online brain"
                    }
                )
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Answer", style = MaterialTheme.typography.titleMedium)
            Text(answer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                val restored = onRestoreMemory()
                                conversation = restored
                                "Restored ${restored.size} encrypted conversation turn(s) from this device."
                            } catch (error: Exception) {
                                "Could not restore local memory: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Restore latest local memory") }
                OutlinedButton(
                    enabled = !busy && !offlineMode,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                val restored = onRestoreRemoteMemory(url, key)
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
                    enabled = !busy && conversation.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                "Encrypted memory snapshot saved locally: ${onSaveMemory(conversation)}"
                            } catch (error: Exception) {
                                "Could not save encrypted memory: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Save encrypted memory") }
                OutlinedButton(
                    enabled = !busy && conversation.isNotEmpty() && !offlineMode,
                    onClick = {
                        busy = true
                        scope.launch {
                            answer = try {
                                onSyncMemory(url, key, conversation)
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
                "Memory is encrypted on this device before saving or syncing. Its Android Keystore key is device-bound; a recovery-key feature is required before moving memory to another phone.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
