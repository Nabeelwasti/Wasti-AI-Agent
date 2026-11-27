package ai.wasti.nexus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Small, safe Markdown presentation layer for assistant output.
 * It renders common chat formatting without interpreting HTML or executing links.
 */
internal fun parseNexusMarkdown(source: String): AnnotatedString {
    val result = AnnotatedString.Builder()
    val lines = source.split("\n")
    var inCodeBlock = false
    val fence = 0x60.toChar().toString().repeat(3)

    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith(fence)) {
            inCodeBlock = !inCodeBlock
            if (index < lines.lastIndex) result.append("\n")
            return@forEachIndexed
        }

        if (inCodeBlock) {
            result.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x1F808080)
                )
            )
            result.append(rawLine)
            result.pop()
            if (index < lines.lastIndex) result.append("\n")
            return@forEachIndexed
        }

        when {
            trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
                val heading = trimmed.dropWhile { it == '#' }.trimStart()
                result.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineMarkdown(result, heading)
                result.pop()
            }
            trimmed == "---" || trimmed == "***" -> {
                result.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                result.append("────────────────────────")
                result.pop()
            }
            trimmed.startsWith("> ") -> {
                result.pushStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color(0xFF8A9BB5)))
                appendInlineMarkdown(result, trimmed.removePrefix("> "))
                result.pop()
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                result.append("• ")
                appendInlineMarkdown(result, trimmed.drop(2))
            }
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                val marker = Regex("^\\d+\\. ").find(trimmed)?.value.orEmpty()
                result.append(marker)
                appendInlineMarkdown(result, trimmed.removePrefix(marker))
            }
            else -> appendInlineMarkdown(result, rawLine)
        }
        if (index < lines.lastIndex) result.append("\n")
    }

    return result.toAnnotatedString()
}

private fun appendInlineMarkdown(builder: AnnotatedString.Builder, text: String) {
    val tick = 0x60.toChar().toString()
    val escapedTick = Regex.escape(tick)
    val token = Regex("(\\*\\*.+?\\*\\*|${escapedTick}[^${tick}]+${escapedTick}|(?<!\\*)\\*[^*]+\\*(?!\\*))")
    var cursor = 0
    token.findAll(text).forEach { match ->
        if (match.range.first > cursor) builder.append(text.substring(cursor, match.range.first))
        val value = match.value
        when {
            value.startsWith("**") && value.endsWith("**") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(value.substring(2, value.length - 2))
                builder.pop()
            }
            value.startsWith(tick) && value.endsWith(tick) -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x1F808080)
                    )
                )
                builder.append(value.substring(1, value.length - 1))
                builder.pop()
            }
            value.startsWith("*") && value.endsWith("*") -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(value.substring(1, value.length - 1))
                builder.pop()
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) builder.append(text.substring(cursor))
}
