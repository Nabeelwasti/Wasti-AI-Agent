package ai.wasti.nexus

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusMarkdownTest {
    @Test
    fun rendersCommonInlineFormattingWithoutChangingReadableText() {
        val tick = 0x60.toChar()
        val rendered = parseNexusMarkdown("**Bold** and *italic* with ${tick}code${tick}.")
        assertEquals("Bold and italic with inline code.", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle != null })
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun rendersHeadingsListsQuotesAndFencedCode() {
        val fence = 0x60.toChar().toString().repeat(3)
        val rendered = parseNexusMarkdown(
            "# Heading\n- First item\n> A quoted note\n\n${fence}kotlin\nval answer = 42\n${fence}"
        )
        assertTrue(rendered.text.contains("Heading"))
        assertTrue(rendered.text.contains("• First item"))
        assertTrue(rendered.text.contains("A quoted note"))
        assertTrue(rendered.text.contains("val answer = 42"))
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun leavesUnformattedTextUnchanged() {
        val source = "Plain text\nSecond line."
        assertEquals(source, parseNexusMarkdown(source).text)
    }
}
