package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

// Shared spacing/shape constants for the IntelliJ-like layout.
private val kPaneCorner = 8.dp
private val kContentGap = 12.dp

// Subtle, dark Windows-11-Mica-style ambient gradient for the app background. Panes sit on
// top as solid (lighter) cards, so the gradient only shows through the gutters/bars.
private fun ambientBackground(inIsDark: Boolean): Brush =
	if (inIsDark) {
		Brush.linearGradient(
			0.0f to Color(0xFF2A2C34),
			0.45f to Color(0xFF202126),
			1.0f to Color(0xFF24202B),
		)
	} else {
		Brush.linearGradient(
			0.0f to Color(0xFFEDF1F8),
			0.45f to Color(0xFFF5F6F9),
			1.0f to Color(0xFFF0ECF7),
		)
	}

// Window body below the title bar: a toolbar, the editor/preview split, and a status bar,
// all on the tool-window background. Each pane is a separated, bordered card.
@Composable
fun AppBody(inState: AppState) {
	val vBorder = JewelTheme.globalColors.borders.normal
	Column(
		Modifier
			.fillMaxSize()
			.background(ambientBackground(JewelTheme.isDark))
	) {
		Toolbar(inState)
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
		EditorAndPreview(inState, Modifier.weight(1f).fillMaxWidth())
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
		StatusBar(inState)
	}
}

// Document actions (New/Open/Save/Save As) on the left, view-mode switch on the right.
@Composable
private fun Toolbar(inState: AppState) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		OutlinedButton(onClick = { inState.loadText("", null) }) { Text("New") }
		OutlinedButton(onClick = { onOpen(inState) }) { Text("Open") }
		OutlinedButton(onClick = { onSave(inState) }) { Text("Save") }
		OutlinedButton(onClick = { onSaveAs(inState) }) { Text("Save As") }
		Spacer(Modifier.weight(1f))
		ViewModeSwitch(inState)
	}
}

// Three-way switch between Editor, Split and Preview layouts.
@Composable
private fun ViewModeSwitch(inState: AppState) {
	Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
		ModeButton("Editor", inState.viewMode == ViewMode.Editor) { inState.viewMode = ViewMode.Editor }
		ModeButton("Split", inState.viewMode == ViewMode.Split) { inState.viewMode = ViewMode.Split }
		ModeButton("Preview", inState.viewMode == ViewMode.Preview) { inState.viewMode = ViewMode.Preview }
	}
}

// A single view-mode button; the active mode is shown as a filled (default) button.
@Composable
private fun ModeButton(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
	if (inSelected) {
		DefaultButton(onClick = inOnClick) { Text(inLabel) }
	} else {
		OutlinedButton(onClick = inOnClick) { Text(inLabel) }
	}
}

// Editor and preview as separated cards, honoring the current view mode. Panes are spaced
// apart (not just a hairline divider) so the two regions read as distinct surfaces.
@Composable
private fun EditorAndPreview(inState: AppState, inModifier: Modifier) {
	Row(
		modifier = inModifier.padding(kContentGap),
		horizontalArrangement = Arrangement.spacedBy(kContentGap),
	) {
		if (inState.viewMode != ViewMode.Preview) {
			Pane("Editor", Modifier.weight(1f).fillMaxHeight()) {
				EditorPane(inState, Modifier.fillMaxSize())
			}
		}
		if (inState.viewMode != ViewMode.Editor) {
			Pane("Preview", Modifier.weight(1f).fillMaxHeight()) {
				MarkdownPreview(
					inText = inState.text,
					inIsDark = inState.isDark,
					inModifier = Modifier.fillMaxSize(),
					// IntelliJ-style: links open only on Ctrl+Click; plain clicks select text.
					inOnUrlClick = { vUrl -> if (inState.isCtrlDown) openUrl(vUrl) },
				)
			}
		}
	}
}

// A bordered, rounded panel with a small header label — the IntelliJ tool-window look.
@Composable
private fun Pane(inTitle: String, inModifier: Modifier, inContent: @Composable BoxScope.() -> Unit) {
	val vBorder = JewelTheme.globalColors.borders.normal
	val vShape = RoundedCornerShape(kPaneCorner)
	Column(
		inModifier
			.clip(vShape)
			.background(JewelTheme.globalColors.panelBackground)
			.border(1.dp, vBorder, vShape)
	) {
		PaneHeader(inTitle)
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
		Box(Modifier.weight(1f).fillMaxWidth(), content = inContent)
	}
}

// Compact, muted header label at the top of a pane.
@Composable
private fun PaneHeader(inTitle: String) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			inTitle.uppercase(),
			color = JewelTheme.globalColors.text.info,
			fontWeight = FontWeight.Medium,
			fontSize = 11.sp,
			letterSpacing = 0.5.sp,
		)
	}
}

// Raw Markdown editor, monospace, with live Markdown syntax highlighting. Undecorated so it
// blends into its pane card rather than drawing a second border.
@Composable
private fun EditorPane(inState: AppState, inModifier: Modifier) {
	val vEditorStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace)
	val vTransformation = remember(inState.isDark) { MarkdownSyntaxTransformation(inState.isDark) }
	TextArea(
		value = inState.fieldValue,
		onValueChange = { inState.fieldValue = it },
		modifier = inModifier.padding(10.dp),
		textStyle = vEditorStyle,
		visualTransformation = vTransformation,
		undecorated = true,
		placeholder = { Text("Write some Markdown…") },
	)
}

// Bottom status bar: file path, dirty state and document metrics, in muted text.
@Composable
private fun StatusBar(inState: AppState) {
	val vText = inState.text
	val vLineCount = if (vText.isEmpty()) 0 else vText.count { it == '\n' } + 1
	val vWordCount = if (vText.isBlank()) 0 else vText.trim().split(Regex("\\s+")).size
	val vMuted = JewelTheme.globalColors.text.info
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(inState.currentFile?.absolutePath ?: "Unsaved document", color = vMuted, fontSize = 12.sp)
		Spacer(Modifier.weight(1f))
		Text(if (inState.isDirty) "Modified" else "Saved", color = vMuted, fontSize = 12.sp)
		Text("$vLineCount lines", color = vMuted, fontSize = 12.sp)
		Text("$vWordCount words", color = vMuted, fontSize = 12.sp)
		Text("${vText.length} chars", color = vMuted, fontSize = 12.sp)
	}
}

// ==================
// MARK: File actions
// ==================

// Opens a Markdown file into the editor; ignores read failures.
private fun onOpen(inState: AppState) {
	val vFile = chooseOpenFile() ?: return
	runCatching { vFile.readText() }.onSuccess { inState.loadText(it, vFile) }
}

// Saves to the current file, falling back to "Save As" when there is none.
private fun onSave(inState: AppState) {
	val vFile = inState.currentFile
	if (vFile == null) {
		onSaveAs(inState)
		return
	}
	runCatching { vFile.writeText(inState.text) }.onSuccess { inState.markSaved(vFile) }
}

// Prompts for a destination and writes the document there.
private fun onSaveAs(inState: AppState) {
	val vSuggested = inState.currentFile?.name ?: "untitled.md"
	val vFile = chooseSaveFile(vSuggested) ?: return
	runCatching { vFile.writeText(inState.text) }.onSuccess { inState.markSaved(vFile) }
}
