package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDocumentExtractorTest {
    @Test fun extractsWordAndSpreadsheetTextWithoutExternalEntities() {
        val xml = """<?xml version="1.0"?><root><w:p xmlns:w="urn:w"><w:r><w:t>Hello world</w:t></w:r></w:p><c><v>42</v></c></root>"""
        val text = NexusDocumentExtractor.extractOfficeXmlText(xml.toByteArray())
        assertTrue(text.contains("Hello world"))
        assertTrue(text.contains("42"))
    }

    @Test fun outputIsBounded() {
        val xml = "<root><t>" + "x".repeat(250_000) + "</t></root>"
        assertEquals(NexusDocumentExtractor.MAX_OUTPUT_CHARS, NexusDocumentExtractor.extractOfficeXmlText(xml.toByteArray()).length)
    }
}
