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
import java.net.HttpURLConnection
import java.net.URL
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
    val sourceRevision: String,
    val minimumAvailableRamGb: Double,
    val note: String
) {
    val downloadable: Boolean
        get() = status == "ready" &&
            (sizeBytes ?: 0L) > 0L &&
            sha256?.matches(Regex("^[a-fA-F0-9]{64}$")) == true &&
            sourceRevision.matches(Regex("^[a-fA-F0-9]{40}$")) &&
            source.startsWith("https://huggingface.co/")
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
                sourceRevision = item.optString("source_revision"),
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
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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

    suspend fun archiveAllModelsToDrive(
        context: Context,
        treeUri: Uri,
        models: List<WorkspaceModel>,
        onProgress: (Long, Long) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val ready = models.filter { it.downloadable }
        require(ready.size == 12) { "All twelve model manifests must be ready before archiving." }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Google Drive workspace permission has expired. Connect it again.")
        require(root.isDirectory && root.canRead()) { "Google Drive workspace is not readable." }
        require(root.canWrite()) {
            "The selected Drive provider did not grant write access. Reconnect the folder or upload the model files using the Google Drive app."
        }

        val totalBytes = ready.sumOf { requireNotNull(it.sizeBytes) }
        val maxFileBytes = ready.maxOf { requireNotNull(it.sizeBytes) }
        val availableCache = StatFs(context.cacheDir.absolutePath).availableBytes
        require(availableCache >= maxFileBytes + MIN_FREE_RESERVE_BYTES) {
            "Not enough temporary phone storage for the largest model. Free at least ${(maxFileBytes + MIN_FREE_RESERVE_BYTES) / 1_073_741_824.0} GB and retry."
        }

        var completedBytes = 0L
        var archivedCount = 0
        for (model in ready) {
            currentCoroutineContext().ensureActive()
            try {
                archiveOneModelToDrive(context, root, model) { received, _ ->
                    onProgress(completedBytes + received, totalBytes)
                }
                archivedCount += 1
                completedBytes += requireNotNull(model.sizeBytes)
                onProgress(completedBytes, totalBytes)
            } catch (error: Exception) {
                throw IllegalStateException("Could not archive ${model.displayName}: ${error.message}", error)
            }
        }
        archivedCount
    }

    private suspend fun archiveOneModelToDrive(
        context: Context,
        root: DocumentFile,
        model: WorkspaceModel,
        onProgress: (Long, Long) -> Unit
    ) {
        val expectedSize = requireNotNull(model.sizeBytes)
        val existingMatches = findFiles(root, model.fileName, maxVisited = 600)
        require(existingMatches.size <= 1) {
            "More than one file named ${model.fileName} exists in the selected Drive folder. Remove duplicates first."
        }
        val existing = existingMatches.singleOrNull()
        if (existing != null && existing.isFile && existing.length() == expectedSize) {
            // Installation independently checks the complete SHA-256 before
            // the model is used. Avoid re-uploading a same-sized archive.
            onProgress(expectedSize, expectedSize)
            return
        }
        if (existing != null) {
            require(existing.delete()) { "Could not replace the incorrect existing Drive file ${model.fileName}." }
        }

        val temporaryName = ".wasti-${model.id}.part"
        root.findFile(temporaryName)?.delete()
        val temporary = root.createFile("application/octet-stream", temporaryName)
            ?: throw IllegalStateException("Google Drive could not create a temporary model file.")

        val sourceUri = Uri.parse(model.source)
        val segments = sourceUri.pathSegments
        require(sourceUri.scheme == "https" && sourceUri.host == "huggingface.co" && segments.size == 2) {
            "The pinned model source URL is invalid."
        }
        val downloadUri = Uri.Builder()
            .scheme("https")
            .authority("huggingface.co")
            .appendPath(segments[0])
            .appendPath(segments[1])
            .appendPath("resolve")
            .appendPath(model.sourceRevision)
            .appendPath(model.fileName)
            .appendQueryParameter("download", "true")
            .build()
        val connection = URL(downloadUri.toString()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("User-Agent", "WASTI-NEXUS-Android/0.4.0")
        connection.setRequestProperty("Accept-Encoding", "identity")

        try {
            connection.connect()
            val responseCode = connection.responseCode
            require(responseCode in 200..299) {
                "Hugging Face download returned HTTP $responseCode for ${model.fileName}."
            }
            val responseLength = connection.contentLengthLong
            if (responseLength > 0L) {
                require(responseLength == expectedSize) {
                    "Upstream file length changed. Expected $expectedSize bytes, received $responseLength."
                }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val input = connection.inputStream
            val output = context.contentResolver.openOutputStream(temporary.uri, "w")
                ?: throw IllegalStateException("Google Drive did not provide a writable file stream.")
            input.use { stream ->
                output.buffered(256 * 1024).use { sink ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= expectedSize) {
                            "Upstream model file is larger than the pinned catalogue size."
                        }
                        digest.update(buffer, 0, count)
                        sink.write(buffer, 0, count)
                        onProgress(copied, expectedSize)
                    }
                    sink.flush()
                }
            }

            require(copied == expectedSize) {
                "Incomplete model archive. Expected $expectedSize bytes, received $copied."
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash.equals(model.sha256, ignoreCase = true)) {
                "Upstream SHA-256 verification failed. The model was rejected before it could be archived."
            }
            val storedLength = temporary.length()
            if (storedLength > 0L) {
                require(storedLength == expectedSize) {
                    "Google Drive stored an incomplete model file. Expected $expectedSize bytes, found $storedLength."
                }
            }

            if (!temporary.renameTo(model.fileName)) {
                val finalDocument = root.createFile("application/octet-stream", model.fileName)
                    ?: throw IllegalStateException("Google Drive could not create the final model file.")
                try {
                    val copyDigest = MessageDigest.getInstance("SHA-256")
                    var copiedToFinal = 0L
                    val archiveInput = context.contentResolver.openInputStream(temporary.uri)
                        ?: throw IllegalStateException("Could not reopen the verified temporary Drive file.")
                    val finalOutput = context.contentResolver.openOutputStream(finalDocument.uri, "w")
                        ?: throw IllegalStateException("Google Drive did not provide the final file stream.")
                    archiveInput.use { stream ->
                        finalOutput.buffered(256 * 1024).use { sink ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                copiedToFinal += count
                                copyDigest.update(buffer, 0, count)
                                sink.write(buffer, 0, count)
                            }
                            sink.flush()
                        }
                    }
                    val finalHash = copyDigest.digest().joinToString("") { "%02x".format(it) }
                    require(copiedToFinal == expectedSize && finalHash.equals(model.sha256, ignoreCase = true)) {
                        "Final Google Drive file verification failed."
                    }
                } catch (error: Exception) {
                    finalDocument.delete()
                    throw error
                } finally {
                    temporary.delete()
                }
            }

            val finalFile = root.findFile(model.fileName)
                ?: throw IllegalStateException("Google Drive did not retain the archived model file.")
            val finalLength = finalFile.length()
            if (finalLength > 0L) {
                require(finalLength == expectedSize) { "Archived model length does not match the pinned catalogue." }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
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
