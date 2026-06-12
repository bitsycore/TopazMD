package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.Tooltip

// Window body below the title bar: a toolbar, the document tab strip, the editor/preview
// split and an optional status bar, over the configured ambient gradient, with the settings
// overlay on top.
@Composable
fun AppBody(inState: AppState) {
	val vSettings = inState.settings
	val vBorder = JewelTheme.globalColors.borders.normal
	Box(
		Modifier
			.fillMaxSize()
			.background(vSettings.gradient.brush(JewelTheme.isDark))
	) {
		Row(Modifier.fillMaxSize()) {
			ActivityBar(inState)
			Divider(Orientation.Vertical, Modifier.fillMaxHeight(), color = vBorder)
			if (inState.showProjectPanel) {
				ProjectPanel(inState, Modifier.width(250.dp).fillMaxHeight())
				Divider(Orientation.Vertical, Modifier.fillMaxHeight(), color = vBorder)
			}
			Column(Modifier.weight(1f).fillMaxHeight()) {
				Toolbar(inState)
				Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
				TabStrip(inState)
				EditorAndPreview(inState, Modifier.weight(1f).fillMaxWidth())
				if (vSettings.showStatusBar) {
					Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
					StatusBar(inState)
				}
			}
		}
		if (inState.showSettings) {
			SettingsOverlay(inState)
		}
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
		OutlinedButton(onClick = { inState.newDocument() }) { Text("New") }
		OutlinedButton(onClick = { onOpen(inState) }) { Text("Open") }
		OutlinedButton(onClick = { chooseFolder()?.let { inState.projectRoot = it; inState.showProjectPanel = true } }) { Text("Open Folder") }
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

// ==================
// MARK: Tabs
// ==================

// Horizontal strip of open-document tabs, IntelliJ-style.
@Composable
private fun TabStrip(inState: AppState) {
	Row(
		modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		inState.documents.forEachIndexed { vIndex, vDoc ->
			TabItem(
				inDoc = vDoc,
				inActive = vIndex == inState.activeIndex,
				inOnSelect = { inState.activeIndex = vIndex },
				inOnClose = { inState.closeDocument(vIndex) },
			)
		}
	}
}

// A single tab: title, dirty dot and a close affordance.
@Composable
private fun TabItem(inDoc: Document, inActive: Boolean, inOnSelect: () -> Unit, inOnClose: () -> Unit) {
	val vShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
	Row(
		modifier =
			Modifier
				.clip(vShape)
				.background(if (inActive) JewelTheme.globalColors.panelBackground else Color.Transparent)
				.clickable(onClick = inOnSelect)
				.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			(if (inDoc.isDirty) "● " else "") + inDoc.title,
			fontSize = 13.sp,
			color = if (inActive) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info,
		)
		Box(
			modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = inOnClose).padding(horizontal = 4.dp),
		) {
			Text("×", fontSize = 14.sp, color = JewelTheme.globalColors.text.info)
		}
	}
}

// ==================
// MARK: Editor / preview split
// ==================

// Editor and preview as separated cards, honoring the current view mode and the configured
// spacing/corner settings.
@Composable
private fun EditorAndPreview(inState: AppState, inModifier: Modifier) {
	val vGap = inState.settings.contentGapDp.dp
	val vCorner = inState.settings.paneCornerDp.dp
	Row(
		modifier = inModifier.padding(vGap),
		horizontalArrangement = Arrangement.spacedBy(vGap),
	) {
		if (inState.viewMode != ViewMode.Preview) {
			Pane("Editor", Modifier.weight(1f).fillMaxHeight(), vCorner) {
				EditorPane(inState, Modifier.fillMaxSize())
			}
		}
		if (inState.viewMode != ViewMode.Editor) {
			Pane("Preview", Modifier.weight(1f).fillMaxHeight(), vCorner) {
				MarkdownPreview(
					inText = inState.active.text,
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
private fun Pane(inTitle: String, inModifier: Modifier, inCornerDp: Dp, inContent: @Composable BoxScope.() -> Unit) {
	val vBorder = JewelTheme.globalColors.borders.normal
	val vShape = RoundedCornerShape(inCornerDp)
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

// Editor pane: a Markdown formatting toolbar above the raw text area.
@Composable
private fun EditorPane(inState: AppState, inModifier: Modifier) {
	Column(inModifier) {
		MarkdownToolbar(inState)
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = JewelTheme.globalColors.borders.normal)
		EditorTextArea(inState, Modifier.weight(1f).fillMaxWidth())
	}
}

// Raw Markdown editor, monospace, with live Markdown syntax highlighting. Undecorated so it
// blends into its pane card rather than drawing a second border.
@Composable
private fun EditorTextArea(inState: AppState, inModifier: Modifier) {
	val vDoc = inState.active
	val vEditorStyle =
		JewelTheme.defaultTextStyle.copy(
			fontFamily = FontFamily.Monospace,
			fontSize = inState.settings.editorFontSizeSp.sp,
		)
	val vTransformation = remember(inState.isDark) { MarkdownSyntaxTransformation(inState.isDark) }
	TextArea(
		value = vDoc.fieldValue,
		onValueChange = { vDoc.fieldValue = it },
		modifier = inModifier.padding(10.dp),
		textStyle = vEditorStyle,
		visualTransformation = vTransformation,
		undecorated = true,
		placeholder = { Text("Write some Markdown…") },
	)
}

// Writerside-style Markdown helpers. Each button applies a formatting action to the active
// document's selection and shows a tooltip describing the Markdown syntax it inserts.
@Composable
private fun MarkdownToolbar(inState: AppState) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		fun wrap(inPrefix: String, inSuffix: String, inPlaceholder: String) {
			val vDoc = inState.active
			vDoc.fieldValue = MarkdownActions.wrap(vDoc.fieldValue, inPrefix, inSuffix, inPlaceholder)
		}
		fun prefix(inPrefix: String) {
			val vDoc = inState.active
			vDoc.fieldValue = MarkdownActions.prefixLine(vDoc.fieldValue, inPrefix)
		}

		HelperButton("B", "Bold", "Wraps the selection in **double asterisks**.") { wrap("**", "**", "bold") }
		HelperButton("I", "Italic", "Wraps the selection in _underscores_.") { wrap("_", "_", "italic") }
		HelperButton("S", "Strikethrough", "Wraps the selection in ~~tildes~~.") { wrap("~~", "~~", "strikethrough") }
		HelperButton("</>", "Inline code", "Wraps the selection in `backticks`.") { wrap("`", "`", "code") }
		HelperButton("```", "Code block", "Wraps the selection in a fenced ``` code block.") { wrap("```\n", "\n```", "code") }
		HelperButton("#", "Heading", "Prefixes the line with '# ' to make a heading.") { prefix("# ") }
		HelperButton("•", "Bullet list", "Prefixes the line with '- ' to make a list item.") { prefix("- ") }
		HelperButton(">", "Quote", "Prefixes the line with '> ' to make a block quote.") { prefix("> ") }
		HelperButton("link", "Link", "Inserts a [text](url) link.") { wrap("[", "](https://)", "text") }
	}
}

// A compact, tooltip-backed formatting button.
@Composable
private fun HelperButton(inLabel: String, inName: String, inHelp: String, inOnClick: () -> Unit) {
	Tooltip(tooltip = {
		Column {
			Text(inName, fontWeight = FontWeight.SemiBold)
			Text(inHelp, color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
		}
	}) {
		Box(
			modifier =
				Modifier
					.clip(RoundedCornerShape(6.dp))
					.clickable(onClick = inOnClick)
					.padding(horizontal = 8.dp, vertical = 4.dp),
		) {
			Text(inLabel, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
		}
	}
}

// Bottom status bar: file path, dirty state and document metrics, in muted text.
@Composable
private fun StatusBar(inState: AppState) {
	val vDoc = inState.active
	val vText = vDoc.text
	val vLineCount = if (vText.isEmpty()) 0 else vText.count { it == '\n' } + 1
	val vWordCount = if (vText.isBlank()) 0 else vText.trim().split(Regex("\\s+")).size
	val vMuted = JewelTheme.globalColors.text.info
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(vDoc.file?.absolutePath ?: "Unsaved document", color = vMuted, fontSize = 12.sp)
		Spacer(Modifier.weight(1f))
		Text(if (vDoc.isDirty) "Modified" else "Saved", color = vMuted, fontSize = 12.sp)
		Text("$vLineCount lines", color = vMuted, fontSize = 12.sp)
		Text("$vWordCount words", color = vMuted, fontSize = 12.sp)
		Text("${vText.length} chars", color = vMuted, fontSize = 12.sp)
	}
}

// ==================
// MARK: Settings overlay
// ==================

// Modal settings overlay: a dimmed scrim (click to dismiss) with a centered card of controls
// that edit the live Settings model.
@Composable
private fun SettingsOverlay(inState: AppState) {
	val vSettings = inState.settings
	val vBorder = JewelTheme.globalColors.borders.normal
	val vMuted = JewelTheme.globalColors.text.info
	Box(
		modifier =
			Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.45f))
				.pointerInput(Unit) { detectTapGestures { inState.showSettings = false } },
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier =
				Modifier
					.width(440.dp)
					.clip(RoundedCornerShape(12.dp))
					.background(JewelTheme.globalColors.panelBackground)
					.border(1.dp, vBorder, RoundedCornerShape(12.dp))
					// Swallow taps so clicks inside the card do not dismiss it.
					.pointerInput(Unit) { detectTapGestures { } }
					.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)

			GroupHeader("Appearance")
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Text("Theme", modifier = Modifier.width(120.dp))
				Chip("Dark", inState.isDark) { inState.isDark = true }
				Chip("Light", !inState.isDark) { inState.isDark = false }
			}
			Text("Background gradient", color = vMuted, fontSize = 12.sp)
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				for (vPreset in GradientPreset.entries) {
					Chip(vPreset.displayName, vSettings.gradient == vPreset) { vSettings.gradient = vPreset }
				}
			}

			GroupHeader("Layout")
			SliderRow("Panel corners", vSettings.paneCornerDp, 0f..20f) { vSettings.paneCornerDp = it }
			SliderRow("Panel spacing", vSettings.contentGapDp, 0f..28f) { vSettings.contentGapDp = it }

			GroupHeader("Editor")
			SliderRow("Font size", vSettings.editorFontSizeSp, 10f..24f) { vSettings.editorFontSizeSp = it }

			GroupHeader("View")
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Checkbox(checked = vSettings.showStatusBar, onCheckedChange = { vSettings.showStatusBar = it })
				Text("Show status bar")
			}

			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				DefaultButton(onClick = { inState.showSettings = false }) { Text("Done") }
			}
		}
	}
}

// A compact selectable chip used for theme/preset choices.
@Composable
private fun Chip(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
	val vBorder = JewelTheme.globalColors.borders.normal
	val vAccent = JewelTheme.globalColors.text.info
	val vShape = RoundedCornerShape(6.dp)
	Box(
		modifier =
			Modifier
				.clip(vShape)
				.background(if (inSelected) vAccent.copy(alpha = 0.18f) else Color.Transparent)
				.border(1.dp, if (inSelected) vAccent else vBorder, vShape)
				.clickable(onClick = inOnClick)
				.padding(horizontal = 10.dp, vertical = 5.dp),
	) {
		Text(inLabel, fontSize = 12.sp)
	}
}

// A labeled slider row showing the current integer value.
@Composable
private fun SliderRow(
	inLabel: String,
	inValue: Float,
	inRange: ClosedFloatingPointRange<Float>,
	inOnChange: (Float) -> Unit,
) {
	Column(Modifier.fillMaxWidth()) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(inLabel)
			Text(inValue.toInt().toString(), color = JewelTheme.globalColors.text.info)
		}
		Slider(value = inValue, onValueChange = inOnChange, modifier = Modifier.fillMaxWidth(), valueRange = inRange)
	}
}

// ==================
// MARK: File actions
// ==================

// Opens a Markdown file into a new tab.
private fun onOpen(inState: AppState) {
	val vFile = chooseOpenFile() ?: return
	inState.openFile(vFile)
}

// Saves the active document to its file, falling back to "Save As" when there is none.
private fun onSave(inState: AppState) {
	val vDoc = inState.active
	val vFile = vDoc.file
	if (vFile == null) {
		onSaveAs(inState)
		return
	}
	runCatching { vFile.writeText(vDoc.text) }.onSuccess { vDoc.markSaved(vFile) }
}

// Prompts for a destination and writes the active document there.
private fun onSaveAs(inState: AppState) {
	val vDoc = inState.active
	val vFile = chooseSaveFile(vDoc.file?.name ?: "untitled.md") ?: return
	runCatching { vFile.writeText(vDoc.text) }.onSuccess { vDoc.markSaved(vFile) }
}
