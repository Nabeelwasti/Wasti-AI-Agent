package ai.wasti.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NexusFileSearchTest {
    @Test fun detectsNaturalLanguageFileSearchRequests() {
        assertTrue(NexusFileSearch.isSearchRequest("Find my passport photo"))
        assertTrue(NexusFileSearch.isSearchRequest("Search for the salary spreadsheet"))
        assertTrue(NexusFileSearch.isSearchRequest("Where is my downloaded PDF?"))
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

    @Test fun emptyResultsAreExplicit() {
        val context = NexusFileSearch.formatForModel("Find missing file", emptyList())
        assertTrue(context.contains("No matching supported files"))
    }
}
