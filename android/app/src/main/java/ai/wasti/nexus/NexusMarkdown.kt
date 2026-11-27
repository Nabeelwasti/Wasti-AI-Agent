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
    val tick = 0x60.toChar()
    var index = 0
    while (index < text.length) {
        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", index + 2)
            if (end > index + 2) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(text.substring(index + 2, end))
                builder.pop()
                index = end + 2
                continue
            }
        }

        if (text[index] == tick) {
            val end = text.indexOf(tick, index + 1)
            if (end > index + 1) {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x1F808080)
                    )
                )
                builder.append(text.substring(index + 1, end))
                builder.pop()
                index = end + 1
                continue
            }
        }

        if (text[index] == '*' && (index == 0 || text[index - 1] != '*')) {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(text.substring(index + 1, end))
                builder.pop()
                index = end + 1
                continue
            }
        }

        builder.append(text[index])
        index += 1
    }
}
