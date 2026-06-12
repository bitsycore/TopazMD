package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.dark as markdownRendererDark
import org.jetbrains.jewel.intui.markdown.standalone.light as markdownRendererLight
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.dark as tableStylingDark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.light as tableStylingLight
import org.jetbrains.jewel.markdown.LazyMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

// Live Markdown preview. Parses the text off the UI thread and renders it with Jewel's
// renderer, wired for GitHub-flavored Markdown (tables, alerts, strikethrough, autolinks) and
// code-block syntax highlighting. Styling follows the current dark/light theme and uses the
// corrected block-quote/code/alert styles for readability.
@Composable
fun MarkdownPreview(
	inText: String,
	inIsDark: Boolean,
	inModifier: Modifier = Modifier,
	inOnUrlClick: (String) -> Unit = { openUrl(it) },
) {
	val vBaseStyle = JewelTheme.defaultTextStyle
	val vEditorStyle = JewelTheme.editorTextStyle
	val vBorder = JewelTheme.globalColors.borders.normal

	// Base Markdown styling for the active theme (readable quotes + bordered code blocks).
	val vStyling = remember(inIsDark, vBaseStyle, vEditorStyle, vBorder) {
		buildMarkdownStyling(inIsDark, vBaseStyle, vEditorStyle, vBorder)
	}

	// Block renderer including the GFM table/alert/strikethrough rendering extensions.
	val vRenderer = remember(vStyling, inIsDark) {
		val vAlertStyling = buildAlertStyling(inIsDark, vBaseStyle.color)
		val vRendererExtensions =
			listOf(
				GitHubTableRendererExtension(
					if (inIsDark) GfmTableStyling.tableStylingDark() else GfmTableStyling.tableStylingLight(),
					vStyling,
				),
				GitHubAlertRendererExtension(vAlertStyling, vStyling),
				GitHubStrikethroughRendererExtension,
			)
		if (inIsDark) {
			MarkdownBlockRenderer.markdownRendererDark(vStyling, vRendererExtensions)
		} else {
			MarkdownBlockRenderer.markdownRendererLight(vStyling, vRendererExtensions)
		}
	}

	// Parser carrying the matching GFM parsing extensions; reused across edits.
	val vProcessor = remember {
		MarkdownProcessor(
			listOf(
				GitHubTableProcessorExtension,
				GitHubAlertProcessorExtension,
				GitHubStrikethroughProcessorExtension(),
				AutolinkProcessorExtension,
			),
			MarkdownMode.Standalone,
		)
	}

	// Re-parse off the UI thread whenever the document text changes.
	val vBlocks by produceState(emptyList<MarkdownBlock>(), inText, vProcessor) {
		value = withContext(Dispatchers.Default) { vProcessor.processMarkdownDocument(inText) }
	}

	// Per-theme code-block syntax highlighter.
	val vHighlighter = remember(inIsDark) { JewelHighlightsCodeHighlighter(inIsDark) }

	// Install styling/renderer/processor/highlighter into composition locals, then render lazily.
	ProvideMarkdownStyling(vStyling, vRenderer, vHighlighter, markdownProcessor = vProcessor) {
		val vListState = rememberLazyListState()
		VerticallyScrollableContainer(vListState as ScrollableState, modifier = inModifier) {
			LazyMarkdown(
				blocks = vBlocks,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(16.dp),
				state = vListState,
				selectable = true,
				onUrlClick = inOnUrlClick,
			)
		}
	}
}
