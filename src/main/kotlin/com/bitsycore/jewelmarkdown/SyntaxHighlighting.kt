// MimeType is deprecated in Jewel 0.34 but is still required to implement the
// CodeHighlighter interface's (also deprecated) MimeType-based overload.
@file:Suppress("DEPRECATION")

package com.bitsycore.jewelmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter

// ==================
// MARK: Editor — Markdown source highlighting
// ==================

// Theme-aware colors for the raw-Markdown editor highlighter.
private class MarkdownPalette(
	val heading: Color,
	val emphasis: Color,
	val code: Color,
	val link: Color,
	val quote: Color,
	val listMarker: Color,
	val meta: Color,
)

// Returns the editor palette for the active theme (Darcula-like dark / IntelliJ-like light).
private fun markdownPalette(inIsDark: Boolean): MarkdownPalette =
	if (inIsDark) {
		MarkdownPalette(
			heading = Color(0xFFFFC66D),
			emphasis = Color(0xFF9876AA),
			code = Color(0xFF6A8759),
			link = Color(0xFF589DF6),
			quote = Color(0xFF808080),
			listMarker = Color(0xFFCC7832),
			meta = Color(0xFF6A6A6A),
		)
	} else {
		MarkdownPalette(
			heading = Color(0xFF7A3E9D),
			emphasis = Color(0xFF9B2393),
			code = Color(0xFF067D17),
			link = Color(0xFF1750EB),
			quote = Color(0xFF767676),
			listMarker = Color(0xFF7A3E9D),
			meta = Color(0xFF9AA0A6),
		)
	}

// A VisualTransformation that colorizes Markdown syntax in the editor without changing the
// text (offsets map 1:1). Applied by the value-based TextArea on every edit.
class MarkdownSyntaxTransformation(private val inIsDark: Boolean) : VisualTransformation {
	override fun filter(text: AnnotatedString): TransformedText {
		val vRaw = text.text
		val vPalette = markdownPalette(inIsDark)

		val vStyled =
			buildAnnotatedString {
				append(vRaw)

				// Applies a span style to every match of a pattern in the raw text.
				fun mark(inRegex: Regex, inStyle: SpanStyle) {
					for (vMatch in inRegex.findAll(vRaw)) {
						addStyle(inStyle, vMatch.range.first, vMatch.range.last + 1)
					}
				}

				// Broad blocks first, finer inline spans after (overlapping spans merge).
				mark(Regex("```[\\s\\S]*?```"), SpanStyle(color = vPalette.code))
				mark(Regex("`[^`\\n]+`"), SpanStyle(color = vPalette.code))
				mark(Regex("(?m)^#{1,6}\\s.*$"), SpanStyle(color = vPalette.heading, fontWeight = FontWeight.Bold))
				mark(Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1"), SpanStyle(fontWeight = FontWeight.Bold))
				mark(Regex("(?<![*_\\w])[*_](?=\\S)([^*_\\n]+?)(?<=\\S)[*_](?![*_\\w])"), SpanStyle(fontStyle = FontStyle.Italic))
				mark(
					Regex("~~(?=\\S)(.+?)(?<=\\S)~~"),
					SpanStyle(color = vPalette.emphasis, textDecoration = TextDecoration.LineThrough),
				)
				mark(
					Regex("!?\\[[^\\]\\n]*]\\([^)\\n]*\\)"),
					SpanStyle(color = vPalette.link, textDecoration = TextDecoration.Underline),
				)
				mark(
					Regex("(?<!\\()https?://[^\\s)]+"),
					SpanStyle(color = vPalette.link, textDecoration = TextDecoration.Underline),
				)
				mark(Regex("(?m)^\\s*>.*$"), SpanStyle(color = vPalette.quote, fontStyle = FontStyle.Italic))
				mark(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s"), SpanStyle(color = vPalette.listMarker, fontWeight = FontWeight.Bold))
				mark(Regex("(?m)^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$"), SpanStyle(color = vPalette.meta))
			}

		return TransformedText(vStyled, OffsetMapping.Identity)
	}
}

// ==================
// MARK: Preview — fenced code block highlighting
// ==================

// A Jewel CodeHighlighter backed by the `highlights` engine; colors fenced code blocks in
// the rendered preview according to the block's language and the active theme.
class JewelHighlightsCodeHighlighter(private val inIsDark: Boolean) : CodeHighlighter {
	// Deprecated MimeType-based entry point; delegates to the language-string overload.
	@Suppress("OVERRIDE_DEPRECATION")
	override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> =
		highlight(code, mimeType?.toString().orEmpty())

	// Highlights `code` for the given language, off the UI thread.
	override fun highlight(code: String, language: String): Flow<AnnotatedString> =
		flow { emit(highlightCode(code, language, inIsDark)) }.flowOn(Dispatchers.Default)
}

// Maps a fence info string or MIME type (e.g. "kotlin", "text/kotlin", "kt") to a language.
private fun syntaxLanguageFor(inLanguage: String): SyntaxLanguage {
	val vRaw = inLanguage.substringAfterLast('/').removePrefix("x-").trim().lowercase()
	val vName =
		when (vRaw) {
			"kt", "kts", "kotlin" -> "kotlin"
			"js", "mjs", "cjs", "javascript", "node" -> "javascript"
			"ts", "typescript" -> "typescript"
			"py", "python" -> "python"
			"rb", "ruby" -> "ruby"
			"rs", "rust" -> "rust"
			"sh", "bash", "zsh", "shell", "console" -> "shell"
			"c++", "cxx", "cc", "hpp", "cpp" -> "cpp"
			"cs", "csharp", "c#" -> "csharp"
			"go", "golang" -> "go"
			"php" -> "php"
			"pl", "perl" -> "perl"
			"swift" -> "swift"
			"dart" -> "dart"
			"java" -> "java"
			"c", "h" -> "c"
			else -> vRaw
		}
	return SyntaxLanguage.getByName(vName) ?: SyntaxLanguage.DEFAULT
}

// Builds a highlighted AnnotatedString for a code block; falls back to plain text for
// unknown languages or empty input.
private fun highlightCode(inCode: String, inLanguage: String, inIsDark: Boolean): AnnotatedString {
	val vLanguage = syntaxLanguageFor(inLanguage)
	if (inCode.isEmpty() || vLanguage == SyntaxLanguage.DEFAULT) return AnnotatedString(inCode)

	val vHighlights =
		Highlights.Builder()
			.code(inCode)
			.language(vLanguage)
			.theme(SyntaxThemes.darcula(inIsDark))
			.build()
			.getHighlights()

	return buildAnnotatedString {
		append(inCode)
		val vLength = inCode.length
		for (vHighlight in vHighlights) {
			when (vHighlight) {
				is ColorHighlight -> {
					val vStart = vHighlight.location.start.coerceIn(0, vLength)
					val vEnd = vHighlight.location.end.coerceIn(vStart, vLength)
					if (vStart < vEnd) {
						val vArgb = (0xFF shl 24) or (vHighlight.rgb and 0xFFFFFF)
						addStyle(SpanStyle(color = Color(vArgb)), vStart, vEnd)
					}
				}

				is BoldHighlight -> {
					val vStart = vHighlight.location.start.coerceIn(0, vLength)
					val vEnd = vHighlight.location.end.coerceIn(vStart, vLength)
					if (vStart < vEnd) addStyle(SpanStyle(fontWeight = FontWeight.Bold), vStart, vEnd)
				}
			}
		}
	}
}
