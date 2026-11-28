package ai.wasti.nexus

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Bounded, on-device extraction for PDF and modern Office/OpenDocument formats.
 * Extracted content is untrusted input and must never be interpreted as executable instructions.
 */
internal object NexusDocumentExtractor {
    const val MAX_INPUT_BYTES = 20 * 1024 * 1024
    const val MAX_OUTPUT_CHARS = 200_000
    private const val MAX_ARCHIVE_ENTRIES = 500
    private const val MAX_EXPANDED_BYTES = 32 * 1024 * 1024
    private val officeExtensions = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")

    suspend fun extract(context: Context, uri: Uri, name: String, mimeType: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension == "pdf" || mimeType.equals("application/pdf", true) -> extractPdf(context, readBounded(context, uri))
            extension in officeExtensions -> extractOffice(readBounded(context, uri), extension)
            extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") || mimeType.lowercase() in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif") -> {
                readBounded(context, uri) // Enforce the same bounded input policy before handing the URI to ML Kit.
                analyzeImage(context, uri)
            }
            else -> throw IllegalArgumentException("Supported rich documents: PDF, DOCX, XLSX, PPTX, ODT, ODS and ODP. Images: JPEG, PNG, WebP, HEIC.")
        }
    }

    internal fun extractFileForSearch(context: Context, file: java.io.File): String? {
        if (!file.isFile || file.length() !in 1..MAX_INPUT_BYTES.toLong()) return null
        val extension = file.extension.lowercase()
        return runCatching {
            val bytes = readFileBounded(file)
            when (extension) {
                "pdf" -> extractPdf(context, bytes)
                "docx", "xlsx", "pptx", "odt", "ods", "odp" -> extractOffice(bytes, extension)
                "jpg", "jpeg", "png", "webp" -> analyzeImageBlocking(context, Uri.fromFile(file))
                else -> null
            }
        }.getOrNull()
    }

    private fun readFileBounded(file: java.io.File): ByteArray {
        val output = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "This file exceeds the 20 MB extraction limit." }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun analyzeImageBlocking(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val sections = mutableListOf<String>()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(image)).text.trim()
            if (text.isNotBlank()) sections += "Text detected on-device:\\n$text"
        } finally {
            recognizer.close()
        }
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val labels = Tasks.await(labeler.process(image)).sortedByDescending { it.confidence }.take(12)
                .map { "${it.text} (${"%.2f".format(java.util.Locale.ROOT, it.confidence)})" }
            if (labels.isNotEmpty()) sections += "On-device visual labels:\\n" + labels.joinToString(", ")
        } finally {
            labeler.close()
        }
        return sections.joinToString("\n\n").take(MAX_OUTPUT_CHARS)
    }

    private fun readBounded(context: Context, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "This file exceeds the 20 MB on-device extraction limit." }
                output.write(buffer, 0, count)
            }
        } ?: throw IllegalStateException("Android could not open the selected file.")
        require(output.size() > 0) { "The selected file is empty." }
        return output.toByteArray()
    }

    private fun extractPdf(context: Context, bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
            require(!document.isEncrypted) { "This PDF is password-protected. Unlock it locally before importing." }
            require(document.numberOfPages <= 500) { "This PDF has more than 500 pages; split it into smaller files." }
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = minOf(document.numberOfPages, 100)
            }
            val text = stripper.getText(document).trim()
            require(text.isNotBlank()) {
                "No selectable text was found in this PDF. It may be a scanned document; use the image/OCR flow on individual pages."
            }
            return text.take(MAX_OUTPUT_CHARS)
        }
    }

    private fun extractOffice(bytes: ByteArray, extension: String): String {
        val output = StringBuilder()
        var expanded = 0
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ARCHIVE_ENTRIES) { "The Office archive contains too many entries." }
                val path = entry.name
                val wanted = path.endsWith(".xml", true) && (
                    path.startsWith("word/", true) || path.startsWith("xl/", true) ||
                    path.startsWith("ppt/", true) || path.equals("content.xml", true)
                )
                if (!entry.isDirectory) {
                    val data = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        expanded += count
                        require(expanded <= MAX_EXPANDED_BYTES) { "The Office archive expands beyond the 32 MB safety limit." }
                        if (wanted) data.write(buffer, 0, count)
                    }
                    if (wanted) {
                        val xmlText = extractOfficeXmlText(data.toByteArray())
                        if (xmlText.isNotBlank()) {
                            output.append(xmlText).append('\n')
                            require(output.length <= MAX_OUTPUT_CHARS) { "Extracted document text exceeds the 200,000 character limit." }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val result = output.toString().trim()
        require(result.isNotBlank()) { "No readable text was found in this $extension document." }
        return result
    }

    internal fun extractOfficeXmlText(xml: ByteArray): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val all = document.getElementsByTagName("*")
        val parts = ArrayList<String>()
        for (i in 0 until all.length) {
            val node = all.item(i)
            val tag = (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
            if (tag == "t" || tag == "v" || (tag == "p" && node.namespaceURI?.contains("opendocument") == true)) {
                val value = node.textContent?.trim().orEmpty()
                if (value.isNotBlank()) parts += value
            }
        }
        return parts.joinToString("\n").take(MAX_OUTPUT_CHARS)
    }

    private suspend fun analyzeImage(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val sections = mutableListOf<String>()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val recognized = recognizer.process(image).await().text.trim()
            if (recognized.isNotBlank()) sections += "Text detected on-device:\n$recognized"
        } finally {
            recognizer.close()
        }
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val labels = labeler.process(image).await()
                .sortedByDescending { it.confidence }
                .take(12)
                .map { "${it.text} (${"%.2f".format(java.util.Locale.ROOT, it.confidence)})" }
            if (labels.isNotEmpty()) sections += "On-device visual labels (not a full caption):\n" + labels.joinToString(", ")
        } finally {
            labeler.close()
        }
        require(sections.isNotEmpty()) { "No text or visual labels could be recognized in this image." }
        return sections.joinToString("\n\n").take(MAX_OUTPUT_CHARS)
    }
}
