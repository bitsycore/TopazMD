package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator

// Window body below the title bar: a menu bar, the document tab strip, the editor/preview
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

// ==================
// MARK: Menu bar
// ==================

// Compact File / Edit / View menu bar on the left, and a small-icon view-mode switch on the
// right. Replaces the old button toolbar to save space and provide standard menus.
@Composable
internal fun AppMenus(inState: AppState, inModifier: Modifier = Modifier) {
	// Which top-level menu is open (by label), shared so hovering switches between them.
	var vOpenMenu by remember { mutableStateOf<String?>(null) }
	Row(
		modifier = inModifier,
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		MenuButton("File", vOpenMenu, { vOpenMenu = it }) { vClose ->
			menuItem("New") { vClose(); inState.newDocument() }
			menuItem("Open File…") { vClose(); onOpen(inState) }
			menuItem("Open Folder…") { vClose(); chooseFolder()?.let { inState.projectRoot = it; inState.showProjectPanel = true } }
			separator()
			menuItem("Save") { vClose(); onSave(inState) }
			menuItem("Save As…") { vClose(); onSaveAs(inState) }
			separator()
			menuItem("Close Tab") { vClose(); inState.closeDocument(inState.activeIndex) }
		}
		MenuButton("Edit", vOpenMenu, { vOpenMenu = it }) { vClose ->
			menuItem("Bold") { vClose(); editWrap(inState, "**", "**", "bold") }
			menuItem("Italic") { vClose(); editWrap(inState, "_", "_", "italic") }
			menuItem("Strikethrough") { vClose(); editWrap(inState, "~~", "~~", "strikethrough") }
			menuItem("Inline code") { vClose(); editWrap(inState, "`", "`", "code") }
			menuItem("Code block") { vClose(); editWrap(inState, "```\n", "\n```", "code") }
			separator()
			menuItem("Heading") { vClose(); editPrefix(inState, "# ") }
			menuItem("Bullet list") { vClose(); editPrefix(inState, "- ") }
			menuItem("Quote") { vClose(); editPrefix(inState, "> ") }
			menuItem("Link") { vClose(); editWrap(inState, "[", "](https://)", "text") }
		}
		MenuButton("View", vOpenMenu, { vOpenMenu = it }) { vClose ->
			menuItem("Editor", inState.viewMode == ViewMode.Editor) { vClose(); inState.viewMode = ViewMode.Editor }
			menuItem("Split", inState.viewMode == ViewMode.Split) { vClose(); inState.viewMode = ViewMode.Split }
			menuItem("Preview", inState.viewMode == ViewMode.Preview) { vClose(); inState.viewMode = ViewMode.Preview }
			separator()
			menuItem("Project Files", inState.showProjectPanel) { vClose(); inState.showProjectPanel = !inState.showProjectPanel }
			menuItem("Status Bar", inState.settings.showStatusBar) { vClose(); inState.settings.showStatusBar = !inState.settings.showStatusBar }
			separator()
			menuItem("Settings…") { vClose(); inState.showSettings = true }
		}
	}
}

// A flat top-level menu button that opens a popup menu. The popup is icon-free, avoiding the
// IntelliJ SVG icons not bundled in the standalone distribution. Open state is shared across
// the menu bar so that, while one menu is open, hovering another switches to it; buttons also
// show a hover/open highlight overlay.
@Composable
private fun MenuButton(
	inLabel: String,
	inOpenMenu: String?,
	inSetOpenMenu: (String?) -> Unit,
	inContent: MenuScope.(close: () -> Unit) -> Unit,
) {
	val vIsOpen = inOpenMenu == inLabel
	val vInteraction = remember { MutableInteractionSource() }
	val vHovered by vInteraction.collectIsHoveredAsState()

	// While any menu is open, moving the mouse over a different menu opens that one.
	LaunchedEffect(vHovered, inOpenMenu) {
		if (vHovered && inOpenMenu != null && inOpenMenu != inLabel) inSetOpenMenu(inLabel)
	}

	val vHighlight =
		when {
			vIsOpen -> JewelTheme.globalColors.text.info.copy(alpha = 0.18f)
			vHovered -> JewelTheme.globalColors.text.info.copy(alpha = 0.10f)
			else -> Color.Transparent
		}
	Box {
		Box(
			modifier =
				Modifier
					.clip(RoundedCornerShape(6.dp))
					.background(vHighlight)
					.hoverable(vInteraction)
					.clickable { inSetOpenMenu(if (vIsOpen) null else inLabel) }
					.padding(horizontal = 10.dp, vertical = 5.dp),
		) {
			Text(inLabel, fontSize = 13.sp)
		}
		if (vIsOpen) {
			PopupMenu(
				onDismissRequest = {
					if (inOpenMenu == inLabel) inSetOpenMenu(null)
					true
				},
				horizontalAlignment = Alignment.Start,
			) {
				inContent { inSetOpenMenu(null) }
			}
		}
	}
}

// Adds a plain (icon-free) selectable menu item.
private fun MenuScope.menuItem(inLabel: String, inSelected: Boolean = false, inOnClick: () -> Unit) {
	selectableItem(selected = inSelected, onClick = inOnClick) { Text(inLabel) }
}

// Small-icon switch between Editor, Split and Preview layouts.
@Composable
internal fun ViewModeIcons(inState: AppState) {
	Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
		ViewModeIconButton(ViewMode.Editor, inState)
		ViewModeIconButton(ViewMode.Split, inState)
		ViewModeIconButton(ViewMode.Preview, inState)
	}
}

// A highlight-on-select icon button for one view mode.
@Composable
private fun ViewModeIconButton(inMode: ViewMode, inState: AppState) {
	val vSelected = inState.viewMode == inMode
	val vTint = if (vSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info
	Tooltip(tooltip = { Text(inMode.name) }) {
		Box(
			modifier =
				Modifier
					.size(28.dp)
					.clip(RoundedCornerShape(6.dp))
					.background(if (vSelected) JewelTheme.globalColors.text.info.copy(alpha = 0.15f) else Color.Transparent)
					.clickable { inState.viewMode = inMode },
			contentAlignment = Alignment.Center,
		) {
			ViewModeGlyph(inMode, vTint, Modifier.size(16.dp))
		}
	}
}

// Draws a tiny glyph representing a view mode: one pane, split panes, or rendered lines.
@Composable
private fun ViewModeGlyph(inMode: ViewMode, inTint: Color, inModifier: Modifier) {
	Canvas(inModifier) {
		val vW = size.width
		val vH = size.height
		val vStroke = size.minDimension * 0.09f
		drawRoundRect(color = inTint, cornerRadius = CornerRadius(size.minDimension * 0.14f), style = Stroke(width = vStroke))
		when (inMode) {
			ViewMode.Editor -> {} // single pane — just the outline
			ViewMode.Split -> drawLine(inTint, Offset(vW / 2f, 0f), Offset(vW / 2f, vH), strokeWidth = vStroke)
			ViewMode.Preview ->
				for (vI in 1..3) {
					val vY = vH * (0.25f + vI * 0.18f)
					drawLine(inTint, Offset(vW * 0.25f, vY), Offset(vW * 0.75f, vY), strokeWidth = vStroke * 0.8f)
				}
		}
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
				inOnCloseOthers = { inState.closeOthers(vIndex) },
				inOnCloseAll = { inState.closeAll() },
			)
		}
	}
}

// A single tab: title, dirty dot, a close affordance and a right-click context menu.
@Composable
private fun TabItem(
	inDoc: Document,
	inActive: Boolean,
	inOnSelect: () -> Unit,
	inOnClose: () -> Unit,
	inOnCloseOthers: () -> Unit,
	inOnCloseAll: () -> Unit,
) {
	ContextMenuArea(items = {
		listOf(
			ContextMenuItem("Close", inOnClose),
			ContextMenuItem("Close Others", inOnCloseOthers),
			ContextMenuItem("Close All", inOnCloseAll),
		)
	}) {
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
			fontFamily = inState.settings.editorFont.family,
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
		HelperButton("B", "Bold", "Wraps the selection in **double asterisks**.") { editWrap(inState, "**", "**", "bold") }
		HelperButton("I", "Italic", "Wraps the selection in _underscores_.") { editWrap(inState, "_", "_", "italic") }
		HelperButton("S", "Strikethrough", "Wraps the selection in ~~tildes~~.") { editWrap(inState, "~~", "~~", "strikethrough") }
		HelperButton("</>", "Inline code", "Wraps the selection in `backticks`.") { editWrap(inState, "`", "`", "code") }
		HelperButton("```", "Code block", "Wraps the selection in a fenced ``` code block.") { editWrap(inState, "```\n", "\n```", "code") }
		HelperButton("#", "Heading", "Prefixes the line with '# ' to make a heading.") { editPrefix(inState, "# ") }
		HelperButton("•", "Bullet list", "Prefixes the line with '- ' to make a list item.") { editPrefix(inState, "- ") }
		HelperButton(">", "Quote", "Prefixes the line with '> ' to make a block quote.") { editPrefix(inState, "> ") }
		HelperButton("link", "Link", "Inserts a [text](url) link.") { editWrap(inState, "[", "](https://)", "text") }
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

// Modal settings dialog with a category sidebar (IntelliJ-style). The scrim dismisses on an
// outside click; the sidebar switches the right-hand content.
@Composable
private fun SettingsOverlay(inState: AppState) {
	val vSettings = inState.settings
	val vBorder = JewelTheme.globalColors.borders.normal
	var vCategory by remember { mutableStateOf("Appearance") }
	val vCategories = listOf("Appearance", "Editor", "Shortcuts", "About")
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
					.width(640.dp)
					.height(480.dp)
					.clip(RoundedCornerShape(12.dp))
					.background(JewelTheme.globalColors.panelBackground)
					.border(1.dp, vBorder, RoundedCornerShape(12.dp))
					.pointerInput(Unit) { detectTapGestures { } },
		) {
			Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
			Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
			Row(Modifier.weight(1f).fillMaxWidth()) {
				Column(
					Modifier.width(150.dp).fillMaxHeight().padding(8.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					for (vCat in vCategories) {
						CategoryItem(vCat, vCat == vCategory) { vCategory = vCat }
					}
				}
				Divider(Orientation.Vertical, Modifier.fillMaxHeight(), color = vBorder)
				Column(
					Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					when (vCategory) {
						"Appearance" -> AppearanceSettings(inState)
						"Editor" -> EditorSettings(inState)
						"Shortcuts" -> ShortcutSettings(inState)
						else -> AboutSettings()
					}
				}
			}
			Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)
			Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
				Chip("Reset to defaults", false) {
					vSettings.reset()
					inState.resetKeymap()
				}
				Spacer(Modifier.weight(1f))
				DefaultButton(onClick = { inState.showSettings = false }) { Text("Done") }
			}
		}
	}
}

// A settings sidebar category entry.
@Composable
private fun CategoryItem(inLabel: String, inSelected: Boolean, inOnClick: () -> Unit) {
	Box(
		modifier =
			Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(6.dp))
				.background(if (inSelected) JewelTheme.globalColors.text.info.copy(alpha = 0.15f) else Color.Transparent)
				.clickable(onClick = inOnClick)
				.padding(horizontal = 10.dp, vertical = 6.dp),
	) {
		Text(inLabel, color = if (inSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info)
	}
}

// Appearance category: theme, background gradient and layout.
@Composable
private fun AppearanceSettings(inState: AppState) {
	val vSettings = inState.settings
	GroupHeader("Theme")
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
		Chip("Dark", inState.isDark) { inState.isDark = true }
		Chip("Light", !inState.isDark) { inState.isDark = false }
	}
	GroupHeader("Background gradient")
	Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		for (vPreset in GradientPreset.entries) {
			Chip(vPreset.displayName, vSettings.gradient == vPreset) { vSettings.gradient = vPreset }
		}
	}
	GroupHeader("Layout")
	SliderRow("Panel corners", vSettings.paneCornerDp, 0f..20f) { vSettings.paneCornerDp = it }
	SliderRow("Panel spacing", vSettings.contentGapDp, 0f..28f) { vSettings.contentGapDp = it }
}

// Editor category: font family/size and status bar.
@Composable
private fun EditorSettings(inState: AppState) {
	val vSettings = inState.settings
	GroupHeader("Font")
	Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		for (vFont in EditorFont.entries) {
			Chip(vFont.displayName, vSettings.editorFont == vFont) { vSettings.editorFont = vFont }
		}
	}
	SliderRow("Font size", vSettings.editorFontSizeSp, 10f..24f) { vSettings.editorFontSizeSp = it }
	GroupHeader("View")
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("Status bar", modifier = Modifier.width(120.dp))
		Chip("On", vSettings.showStatusBar) { vSettings.showStatusBar = true }
		Chip("Off", !vSettings.showStatusBar) { vSettings.showStatusBar = false }
	}
}

// Shortcuts category: each action with its current binding; click to rebind.
@Composable
private fun ShortcutSettings(inState: AppState) {
	Text(
		"Click a shortcut, then press the new key combination.",
		color = JewelTheme.globalColors.text.info,
		fontSize = 12.sp,
	)
	for (vAction in ShortcutAction.entries) {
		val vRecording = inState.recordingAction == vAction
		val vLabel = if (vRecording) "Press keys…" else (inState.keymap[vAction]?.label() ?: "Unbound")
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(vAction.displayName, modifier = Modifier.weight(1f))
			Chip(vLabel, vRecording) { inState.recordingAction = if (vRecording) null else vAction }
		}
	}
}

// About category: brief app info.
@Composable
private fun AboutSettings() {
	val vMuted = JewelTheme.globalColors.text.info
	Text("Jewel Markdown", fontWeight = FontWeight.Bold, fontSize = 15.sp)
	Text("A Compose for Desktop Markdown editor built with JetBrains Jewel.", color = vMuted, fontSize = 13.sp)
	Text("Kotlin 2.2 · Compose 1.10 · Jewel 0.34 · JetBrains Runtime 21", color = vMuted, fontSize = 12.sp)
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
// MARK: Actions
// ==================

// Wraps the active document's selection with the given prefix/suffix.
private fun editWrap(inState: AppState, inPrefix: String, inSuffix: String, inPlaceholder: String) {
	val vDoc = inState.active
	vDoc.fieldValue = MarkdownActions.wrap(vDoc.fieldValue, inPrefix, inSuffix, inPlaceholder)
}

// Prefixes the active document's current line with the given marker.
private fun editPrefix(inState: AppState, inPrefix: String) {
	val vDoc = inState.active
	vDoc.fieldValue = MarkdownActions.prefixLine(vDoc.fieldValue, inPrefix)
}

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

// Executes a bound keyboard-shortcut action against the current state.
internal fun runShortcutAction(inState: AppState, inAction: ShortcutAction) {
	when (inAction) {
		ShortcutAction.NewFile -> inState.newDocument()
		ShortcutAction.OpenFile -> onOpen(inState)
		ShortcutAction.OpenFolder -> chooseFolder()?.let { inState.projectRoot = it; inState.showProjectPanel = true }
		ShortcutAction.Save -> onSave(inState)
		ShortcutAction.SaveAs -> onSaveAs(inState)
		ShortcutAction.CloseTab -> inState.closeDocument(inState.activeIndex)
		ShortcutAction.Bold -> editWrap(inState, "**", "**", "bold")
		ShortcutAction.Italic -> editWrap(inState, "_", "_", "italic")
		ShortcutAction.InlineCode -> editWrap(inState, "`", "`", "code")
		ShortcutAction.Heading -> editPrefix(inState, "# ")
		ShortcutAction.Quote -> editPrefix(inState, "> ")
		ShortcutAction.BulletList -> editPrefix(inState, "- ")
		ShortcutAction.Link -> editWrap(inState, "[", "](https://)", "text")
		ShortcutAction.ToggleProjectPanel -> inState.showProjectPanel = !inState.showProjectPanel
		ShortcutAction.ViewEditor -> inState.viewMode = ViewMode.Editor
		ShortcutAction.ViewSplit -> inState.viewMode = ViewMode.Split
		ShortcutAction.ViewPreview -> inState.viewMode = ViewMode.Preview
		ShortcutAction.OpenSettings -> inState.showSettings = true
	}
}
