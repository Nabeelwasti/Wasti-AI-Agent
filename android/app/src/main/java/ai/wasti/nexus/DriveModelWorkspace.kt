package ai.wasti.nexus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal data class WorkspaceModel(
    val id: String,
    val family: String,
    val displayName: String,
    val fileName: String,
    val quantization: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val status: String,
    val license: String,
    val source: String,
    val minimumAvailableRamGb: Double,
    val note: String
) {
    val downloadable: Boolean
        get() = status == "ready" &&
            (sizeBytes ?: 0L) > 0L &&
            sha256?.matches(Regex("^[a-fA-F0-9]{64}$")) == true
}

internal object DriveModelWorkspace {
    private const val CATALOG_ASSET = "nexus-model-catalog.json"
    private const val MIN_FREE_RESERVE_BYTES = 512L * 1024L * 1024L

    fun loadCatalog(context: Context): List<WorkspaceModel> {
        val json = context.assets.open(CATALOG_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCatalog(json)
    }

    internal fun parseCatalog(json: String): List<WorkspaceModel> {
        val root = JSONObject(json)
        require(root.optInt("schema_version") == 1) { "Unsupported model catalogue schema." }
        val array = root.optJSONArray("models") ?: error("The model catalogue has no models array.")
        val result = ArrayList<WorkspaceModel>(array.length())
        val seenIds = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            val fileName = item.getString("file_name")
            require(id.matches(Regex("^[a-z0-9][a-z0-9._-]{1,63}$"))) { "Invalid model ID in catalogue." }
            require(seenIds.add(id)) { "Duplicate model ID: $id" }
            require(fileName.matches(Regex("^[A-Za-z0-9._-]+\\.gguf$", RegexOption.IGNORE_CASE))) {
                "Unsafe model filename in catalogue."
            }
            val size = if (item.isNull("size_bytes")) null else item.optLong("size_bytes").takeIf { it > 0L }
            val sha = item.optString("sha256").takeIf { it.matches(Regex("^[a-fA-F0-9]{64}$")) }
            val status = item.optString("status", "pending_verification")
            require(status in setOf("ready", "pending_verification", "restricted", "incompatible")) {
                "Unknown model status: $status"
            }
            result += WorkspaceModel(
                id = id,
                family = item.optString("family"),
                displayName = item.optString("display_name", id),
                fileName = fileName,
                quantization = item.optString("quantization"),
                sizeBytes = size,
                sha256 = sha,
                status = status,
                license = item.optString("license"),
                source = item.optString("source"),
                minimumAvailableRamGb = item.optDouble("minimum_available_ram_gb", 0.0),
                note = item.optString("note")
            )
        }
        require(result.size == 12) { "The NEXUS catalogue must contain exactly twelve model families." }
        require(result.any { it.id == "qwen-0.5b" && it.downloadable }) {
            "The pinned Qwen starter model must remain available in the catalogue."
        }
        return result
    }

    fun persistWorkspaceAccess(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    fun validateWorkspace(context: Context, uri: Uri): String {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalStateException("The selected Google Drive folder is not accessible.")
        require(root.isDirectory && root.canRead()) { "Choose a readable Google Drive folder." }
        return root.name?.takeIf { it.isNotBlank() } ?: "Google Drive workspace"
    }

    suspend fun installModel(
        context: Context,
        treeUri: Uri,
        model: WorkspaceModel,
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        require(model.downloadable) { "This model is not ready for download: ${model.note}" }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Google Drive workspace permission has expired. Connect it again.")
        require(root.isDirectory && root.canRead()) { "Google Drive workspace is not readable." }

        val matches = findFiles(root, model.fileName, maxVisited = 600)
        require(matches.size == 1) {
            when (matches.size) {
                0 -> "Upload ${model.fileName} into your WASTI-NEXUS-MODEL-WORKSPACE folder first."
                else -> "More than one file named ${model.fileName} exists. Keep exactly one verified copy."
            }
        }
        val source = matches.single()
        require(source.isFile && source.canRead()) { "The selected Drive model file cannot be read." }
        val providerLength = source.length()
        if (providerLength > 0L && providerLength != model.sizeBytes) {
            throw IllegalStateException("Drive file size does not match the catalogue. Expected ${model.sizeBytes} bytes, found $providerLength.")
        }

        val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
        val freeBytes = StatFs(modelDirectory.absolutePath).availableBytes
        val requiredBytes = requireNotNull(model.sizeBytes) + MIN_FREE_RESERVE_BYTES
        require(freeBytes >= requiredBytes) {
            "Not enough phone storage. Need ${(requiredBytes / 1_073_741_824.0).formatOneDecimal()} GB free including safety reserve."
        }

        val target = File(modelDirectory, "${model.id}-${model.fileName}")
        val temporary = File(modelDirectory, "${model.id}-${model.fileName}.part")
        if (target.exists() && target.length() == model.sizeBytes && sha256(target) == model.sha256) {
            return@withContext target.absolutePath
        }
        temporary.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IllegalStateException("Google Drive did not provide a readable file stream.")
            input.use { stream ->
                temporary.outputStream().buffered(256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= requireNotNull(model.sizeBytes)) {
                            "Drive file is larger than the approved catalogue size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(copied, requireNotNull(model.sizeBytes))
                    }
                    output.flush()
                }
            }
            require(copied == model.sizeBytes) {
                "Incomplete Drive transfer. Expected ${model.sizeBytes} bytes, received $copied."
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(model.sha256, ignoreCase = true)) {
                "SHA-256 verification failed. The downloaded model was rejected."
            }
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            target.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun findFiles(root: DocumentFile, name: String, maxVisited: Int): List<DocumentFile> {
        val matches = ArrayList<DocumentFile>()
        val pending = ArrayDeque<Pair<DocumentFile, Int>>()
        val visited = HashSet<String>()
        pending.add(root to 0)
        while (pending.isNotEmpty()) {
            val (directory, depth) = pending.removeFirst()
            if (!visited.add(directory.uri.toString())) continue
            require(visited.size <= maxVisited) { "Google Drive workspace contains too many entries to scan safely." }
            for (child in directory.listFiles()) {
                if (child.isFile && child.name == name) matches += child
                if (child.isDirectory && depth < 4) pending.add(child to depth + 1)
            }
        }
        return matches
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
}
