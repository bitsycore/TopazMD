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
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.markdown.LazyMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.intui.markdown.standalone.dark as markdownRendererDark
import org.jetbrains.jewel.intui.markdown.standalone.light as markdownRendererLight
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark as markdownStylingDark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.dark as alertStylingDark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.light as alertStylingLight
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.dark as tableStylingDark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables.light as tableStylingLight
import org.jetbrains.jewel.intui.markdown.standalone.styling.light as markdownStylingLight

// Live Markdown preview. Parses the text off the UI thread and renders it with
// Jewel's renderer, wired for GitHub-flavored Markdown (tables, alerts,
// strikethrough, autolinks). Styling follows the current dark/light theme.
@Composable
fun MarkdownPreview(
	inText: String,
	inIsDark: Boolean,
	inModifier: Modifier = Modifier,
	inOnUrlClick: (String) -> Unit = { openUrl(it) },
) {
	// Base Markdown styling for the active theme.
	val vStyling = remember(inIsDark) {
		if (inIsDark) MarkdownStyling.markdownStylingDark() else MarkdownStyling.markdownStylingLight()
	}

	// Block renderer including the GFM table/alert/strikethrough rendering extensions.
	// The inline renderer (needed for strikethrough) is derived from this list.
	val vRenderer = remember(vStyling, inIsDark) {
		val vRendererExtensions =
			listOf(
				GitHubTableRendererExtension(
					if (inIsDark) GfmTableStyling.tableStylingDark() else GfmTableStyling.tableStylingLight(),
					vStyling,
				),
				GitHubAlertRendererExtension(
					if (inIsDark) AlertStyling.alertStylingDark() else AlertStyling.alertStylingLight(),
					vStyling,
				),
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

	// Code-block syntax highlighter for the active theme.
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
