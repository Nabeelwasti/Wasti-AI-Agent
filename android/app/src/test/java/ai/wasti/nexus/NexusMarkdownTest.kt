package ai.wasti.nexus

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusMarkdownTest {
    @Test
    fun rendersCommonInlineFormattingWithoutChangingReadableText() {
        val rendered = parseNexusMarkdown("**Bold** and *italic* with inline code.")
        assertEquals("Bold and italic with inline code.", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle != null })
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun rendersHeadingsListsQuotesAndFencedCode() {
        val rendered = parseNexusMarkdown(
            "# Heading\n- First item\n> A quoted note\n\n${0x60.toChar()} ${0x60.toChar()} ${0x60.toChar()}kotlin\nval answer = 42\n${0x60.toChar()} ${0x60.toChar()} ${0x60.toChar()}"
                .replace("${0x60.toChar()} ", "${0x60.toChar()}")
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
