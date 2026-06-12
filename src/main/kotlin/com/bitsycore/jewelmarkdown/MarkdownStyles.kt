package com.bitsycore.jewelmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.BlockQuote
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Indented

// Builds a Markdown styling that fixes two readability problems with the stock styling:
// block-quote text rendered too dark in dark mode (it overrides the readable default), and
// code blocks that blend into a light background. Quotes keep the readable muted default with
// a visible accent bar; code blocks get a contrasting background plus a 1px border.
fun buildMarkdownStyling(
	inIsDark: Boolean,
	inBaseTextStyle: TextStyle,
	inEditorTextStyle: TextStyle,
	inBorderColor: Color,
): MarkdownStyling {
	val vCodeBackground = if (inIsDark) Color(0xFF1E1F22) else Color(0xFFEFF1F5)
	val vQuoteLine = if (inIsDark) Color(0xFF4C7DFF) else Color(0xFF3B6EDB)
	return if (inIsDark) {
		MarkdownStyling.dark(
			baseTextStyle = inBaseTextStyle,
			editorTextStyle = inEditorTextStyle,
			blockQuote = BlockQuote.dark(lineWidth = 4.dp, lineColor = vQuoteLine),
			code =
				Code.dark(
					indented = Indented.dark(background = vCodeBackground, borderWidth = 1.dp, borderColor = inBorderColor),
					fenced = Fenced.dark(background = vCodeBackground, borderWidth = 1.dp, borderColor = inBorderColor),
				),
		)
	} else {
		MarkdownStyling.light(
			baseTextStyle = inBaseTextStyle,
			editorTextStyle = inEditorTextStyle,
			blockQuote = BlockQuote.light(lineWidth = 4.dp, lineColor = vQuoteLine),
			code =
				Code.light(
					indented = Indented.light(background = vCodeBackground, borderWidth = 1.dp, borderColor = inBorderColor),
					fenced = Fenced.light(background = vCodeBackground, borderWidth = 1.dp, borderColor = inBorderColor),
				),
		)
	}
}
