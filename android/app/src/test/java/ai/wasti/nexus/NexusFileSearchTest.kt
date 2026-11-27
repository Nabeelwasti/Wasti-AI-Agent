package ai.wasti.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NexusFileSearchTest {
    @Test fun detectsNaturalLanguageFileSearchRequests() {
        assertTrue(NexusFileSearch.isSearchRequest("Find my passport photo"))
        assertTrue(NexusFileSearch.isSearchRequest("Search for the salary spreadsheet"))
        assertTrue(NexusFileSearch.isSearchRequest("Where is my downloaded PDF?"))
    }

    @Test fun remoteFileContextRequiresExplicitOptInEvenForAutoFallback() {
        assertFalse(NexusFileSearch.maySendFileContextRemotely(true, false))
        assertTrue(NexusFileSearch.maySendFileContextRemotely(true, true))
        assertTrue(NexusFileSearch.maySendFileContextRemotely(false, false))
    }

    @Test fun doesNotMisclassifyOrdinaryConversationAsFileSearch() {
        assertFalse(NexusFileSearch.isSearchRequest("Find a better way to organize my day"))
        assertFalse(NexusFileSearch.isSearchRequest("Explain how photosynthesis works"))
        assertFalse(NexusFileSearch.isSearchRequest("Search the internet for AI news"))
    }

    @Test fun fileContextLabelsUntrustedContentAndDoesNotClaimFullExtraction() {
        val hit = NexusFileHit(
            name = "salary.csv",
            path = "/storage/emulated/0/Download/salary.csv",
            mime = "text/csv",
            sizeBytes = 1024,
            modifiedAt = 1_700_000_000_000,
            excerpt = "Department,Salary\\nPrinting,50000"
        )
        val context = NexusFileSearch.formatForModel("Find salary spreadsheet", listOf(hit))
        assertTrue(context.contains("untrusted data"))
        assertTrue(context.contains("salary.csv"))
        assertTrue(context.contains("not the complete file"))
        assertTrue(context.contains("Printing,50000"))
    }

    @Test fun canonicalPathGuardRejectsFilesOutsideSharedStorageRoot() {
        val parent = Files.createTempDirectory("nexus-file-search").toFile()
        val root = File(parent, "shared").apply { mkdirs() }
        val inside = File(root, "notes.txt").apply { writeText("private note") }
        val outside = File(parent, "outside.txt").apply { writeText("must not be read") }
        try {
            assertTrue(NexusFileSearch.isWithinRoot(root, inside))
            assertFalse(NexusFileSearch.isWithinRoot(root, outside))
            val link = File(root, "outside-link.txt")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
                assertFalse(NexusFileSearch.isWithinRoot(root, link))
            } catch (_: UnsupportedOperationException) {
                // Symlink creation is unavailable on some CI hosts.
            } catch (_: java.io.IOException) {
                // Filesystem permissions may disable symlink creation.
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test fun boundedTextReaderRejectsFilesThatExceedTheReadLimit() {
        val directory = Files.createTempDirectory("nexus-bounded-read").toFile()
        val valid = File(directory, "valid.txt").apply { writeText("safe text") }
        val oversized = File(directory, "oversized.txt").apply {
            writeBytes(ByteArray(128 * 1024 + 1) { 'x'.code.toByte() })
        }
        try {
            assertEquals("safe text", NexusFileSearch.readTextBounded(valid)?.first)
            assertEquals(9, NexusFileSearch.readTextBounded(valid)?.second)
            assertEquals(null, NexusFileSearch.readTextBounded(oversized))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun emptyResultsAreExplicit() {
        val context = NexusFileSearch.formatForModel("Find missing file", emptyList())
        assertTrue(context.contains("No matching supported files"))
    }
}
