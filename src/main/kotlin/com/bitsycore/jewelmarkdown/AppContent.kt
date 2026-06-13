package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.PopupProperties
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
import java.awt.datatransfer.DataFlavor
import java.io.File
import org.jetbrains.jewel.ui.component.separator

// Window body below the title bar: a menu bar, the document tab strip, the editor/preview
// split and an optional status bar, over the configured ambient gradient, with the settings
// overlay on top.
@Composable
fun AppBody(inState: AppState) {
	val vSettings = inState.settings
	val vBorder = JewelTheme.globalColors.borders.normal

	// Accept files dropped onto the window and open each in a tab.
	val vDropTarget =
		remember(inState) {
			object : DragAndDropTarget {
				override fun onDrop(event: DragAndDropEvent): Boolean {
					val vFiles = droppedFiles(event).filter { it.isFile }
					vFiles.forEach { inState.openFile(it) }
					return vFiles.isNotEmpty()
				}
			}
		}

	Box(
		Modifier
			.fillMaxSize()
			.background(vSettings.gradient.brush(JewelTheme.isDark))
			.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = vDropTarget)
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
		// Closes an open title-bar menu when clicking anywhere in the content area.
		if (inState.menuOpenName != null) {
			Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { inState.menuOpenName = null } })
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
	val vOpenMenu = inState.menuOpenName
	val vSetOpenMenu: (String?) -> Unit = { inState.menuOpenName = it }
	Row(
		modifier = inModifier,
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		MenuButton("File", vOpenMenu, vSetOpenMenu) { vClose ->
			actionItem(inState, ShortcutAction.NewFile, vClose)
			actionItem(inState, ShortcutAction.OpenFile, vClose)
			actionItem(inState, ShortcutAction.OpenFolder, vClose)
			separator()
			actionItem(inState, ShortcutAction.Save, vClose)
			actionItem(inState, ShortcutAction.SaveAs, vClose)
			separator()
			actionItem(inState, ShortcutAction.CloseTab, vClose)
		}
		MenuButton("Edit", vOpenMenu, vSetOpenMenu) { vClose ->
			actionItem(inState, ShortcutAction.Bold, vClose)
			actionItem(inState, ShortcutAction.Italic, vClose)
			menuItem("Strikethrough") { vClose(); editWrap(inState, "~~", "~~", "strikethrough") }
			actionItem(inState, ShortcutAction.InlineCode, vClose)
			menuItem("Code block") { vClose(); editWrap(inState, "```\n", "\n```", "code") }
			separator()
			actionItem(inState, ShortcutAction.Heading, vClose)
			actionItem(inState, ShortcutAction.BulletList, vClose)
			actionItem(inState, ShortcutAction.Quote, vClose)
			actionItem(inState, ShortcutAction.Link, vClose)
		}
		MenuButton("View", vOpenMenu, vSetOpenMenu) { vClose ->
			actionItem(inState, ShortcutAction.ViewEditor, vClose, inState.viewMode == ViewMode.Editor)
			actionItem(inState, ShortcutAction.ViewSplit, vClose, inState.viewMode == ViewMode.Split)
			actionItem(inState, ShortcutAction.ViewPreview, vClose, inState.viewMode == ViewMode.Preview)
			separator()
			actionItem(inState, ShortcutAction.ToggleProjectPanel, vClose, inState.showProjectPanel)
			menuItem("Status Bar", inState.settings.showStatusBar) { vClose(); inState.settings.showStatusBar = !inState.settings.showStatusBar }
			separator()
			actionItem(inState, ShortcutAction.OpenSettings, vClose)
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
				// Non-focusable so the menu bar keeps receiving hover events (enabling
				// hover-to-switch); a dismiss layer in AppBody closes it on an outside click.
				popupProperties = PopupProperties(focusable = false),
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

// Adds a menu item bound to a shortcut action: shows the key hint, closes the menu and runs it.
private fun MenuScope.actionItem(inState: AppState, inAction: ShortcutAction, inClose: () -> Unit, inSelected: Boolean = false) {
	val vBinding = inState.keymap[inAction]?.let { setOf(it.label()) }
	selectableItem(
		selected = inSelected,
		keybinding = vBinding,
		onClick = {
			inClose()
			runShortcutAction(inState, inAction)
		},
	) {
		Text(inAction.displayName)
	}
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

// A highlight-on-select/hover icon button for one view mode.
@Composable
private fun ViewModeIconButton(inMode: ViewMode, inState: AppState) {
	IconButtonBox(inState.viewMode == inMode, inMode.name, { inState.viewMode = inMode }) { vTint ->
		ViewModeGlyph(inMode, vTint, Modifier.size(16.dp))
	}
}

// A 28dp icon button with select + hover highlight and a tooltip.
@Composable
private fun IconButtonBox(inSelected: Boolean, inTooltip: String, inOnClick: () -> Unit, inIcon: @Composable (Color) -> Unit) {
	val vInteraction = remember { MutableInteractionSource() }
	val vHovered by vInteraction.collectIsHoveredAsState()
	val vTint = if (inSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info
	val vBg =
		when {
			inSelected -> JewelTheme.globalColors.text.info.copy(alpha = 0.18f)
			vHovered -> JewelTheme.globalColors.text.info.copy(alpha = 0.10f)
			else -> Color.Transparent
		}
	Tooltip(tooltip = { Text(inTooltip) }) {
		Box(
			modifier =
				Modifier
					.size(28.dp)
					.clip(RoundedCornerShape(6.dp))
					.background(vBg)
					.hoverable(vInteraction)
					.clickable(onClick = inOnClick),
			contentAlignment = Alignment.Center,
		) {
			inIcon(vTint)
		}
	}
}

// Title-bar settings icon button (a sliders glyph).
@Composable
internal fun SettingsIconButton(inState: AppState) {
	IconButtonBox(inState.showSettings, "Settings", { inState.showSettings = true }) { vTint ->
		SettingsGlyph(vTint, Modifier.size(16.dp))
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

// Draws a "sliders" settings glyph: three lines, each with a knob.
@Composable
private fun SettingsGlyph(inTint: Color, inModifier: Modifier) {
	Canvas(inModifier) {
		val vW = size.width
		val vH = size.height
		val vStroke = size.minDimension * 0.09f
		val vR = size.minDimension * 0.11f
		for (vI in 0..2) {
			val vY = vH * (0.25f + vI * 0.25f)
			drawLine(inTint, Offset(vW * 0.12f, vY), Offset(vW * 0.88f, vY), strokeWidth = vStroke)
			val vCx = vW * (if (vI % 2 == 0) 0.68f else 0.34f)
			drawCircle(inTint, radius = vR, center = Offset(vCx, vY))
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
		modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(3.dp),
		verticalAlignment = Alignment.Bottom,
	) {
		inState.documents.forEachIndexed { vIndex, vDoc ->
			TabItem(
				inState = inState,
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

// A single tab: title, dirty dot, a close affordance, a right-click context menu, and
// drag-to-reorder (drag a tab left/right to move it).
@Composable
private fun TabItem(
	inState: AppState,
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
		val vAccent = JewelTheme.globalColors.borders.focused
		Row(
			modifier =
				Modifier
					.clip(vShape)
					.background(
						if (inActive) {
							JewelTheme.globalColors.panelBackground
						} else {
							JewelTheme.globalColors.panelBackground.copy(alpha = 0.4f)
						}
					)
					.drawBehind {
						if (inActive) {
							val vY = 1.dp.toPx()
							drawLine(vAccent, Offset(0f, vY), Offset(size.width, vY), strokeWidth = 2.dp.toPx())
						}
					}
					.clickable(onClick = inOnSelect)
					.pointerInput(inDoc) {
						var vAccum = 0f
						detectDragGestures(
							onDragEnd = { vAccum = 0f },
							onDrag = { change, dragAmount ->
								change.consume()
								vAccum += dragAmount.x
								val vIdx = inState.documents.indexOf(inDoc)
								when {
									vIdx < 0 -> {}
									vAccum > 70f && vIdx < inState.documents.lastIndex -> {
										inState.moveDocument(vIdx, vIdx + 1)
										vAccum = 0f
									}
									vAccum < -70f && vIdx > 0 -> {
										inState.moveDocument(vIdx, vIdx - 1)
										vAccum = 0f
									}
								}
							},
						)
					}
					.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
// spacing/corner settings. In Split mode a draggable divider sets the editor/preview ratio.
@Composable
private fun EditorAndPreview(inState: AppState, inModifier: Modifier) {
	val vGap = inState.settings.contentGapDp.dp
	val vCorner = inState.settings.paneCornerDp.dp

	@Composable
	fun EditorCard(inPaneModifier: Modifier) =
		Pane(inPaneModifier, vCorner) { EditorPane(inState, Modifier.fillMaxSize()) }

	@Composable
	fun PreviewCard(inPaneModifier: Modifier) =
		Pane(inPaneModifier, vCorner) {
			MarkdownPreview(
				inText = inState.active.text,
				inIsDark = inState.isDark,
				inModifier = Modifier.fillMaxSize(),
				// IntelliJ-style: links open only on Ctrl+Click; plain clicks select text.
				inOnUrlClick = { vUrl -> if (inState.isCtrlDown) openUrl(vUrl) },
			)
		}

	when (inState.viewMode) {
		ViewMode.Editor -> Row(inModifier.padding(start = vGap, end = vGap, bottom = vGap)) { EditorCard(Modifier.weight(1f).fillMaxHeight()) }
		ViewMode.Preview -> Row(inModifier.padding(start = vGap, end = vGap, bottom = vGap)) { PreviewCard(Modifier.weight(1f).fillMaxHeight()) }
		ViewMode.Split -> {
			var vWidth by remember { mutableStateOf(1f) }
			Row(modifier = inModifier.padding(start = vGap, end = vGap, bottom = vGap).onSizeChanged { vWidth = it.width.toFloat().coerceAtLeast(1f) }) {
				EditorCard(Modifier.weight(inState.splitRatio).fillMaxHeight())
				SplitHandle(vGap) { vDelta -> inState.splitRatio = (inState.splitRatio + vDelta / vWidth).coerceIn(0.15f, 0.85f) }
				PreviewCard(Modifier.weight(1f - inState.splitRatio).fillMaxHeight())
			}
		}
	}
}

// Draggable divider between the editor and preview panes; reports horizontal drag in pixels.
@Composable
private fun SplitHandle(inWidth: Dp, inOnDrag: (Float) -> Unit) {
	Box(
		modifier =
			Modifier
				.width(inWidth)
				.fillMaxHeight()
				.pointerInput(Unit) { detectDragGestures { change, dragAmount -> change.consume(); inOnDrag(dragAmount.x) } },
		contentAlignment = Alignment.Center,
	) {
		Box(Modifier.width(2.dp).fillMaxHeight().background(JewelTheme.globalColors.borders.normal))
	}
}

// A bordered, rounded, shadowed panel (the IntelliJ tool-window card look).
@Composable
private fun Pane(inModifier: Modifier, inCornerDp: Dp, inContent: @Composable BoxScope.() -> Unit) {
	val vShape = RoundedCornerShape(inCornerDp)
	Box(
		modifier =
			inModifier
				.shadow(2.dp, vShape)
				.clip(vShape)
				.background(JewelTheme.globalColors.panelBackground)
				.border(1.dp, JewelTheme.globalColors.borders.normal, vShape),
		content = inContent,
	)
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

	// Caret position (1-based line/column) from the current selection.
	val vCaret = vDoc.fieldValue.selection.start.coerceIn(0, vText.length)
	val vBeforeCaret = vText.substring(0, vCaret)
	val vCaretLine = vBeforeCaret.count { it == '\n' } + 1
	val vCaretCol = vCaret - (vBeforeCaret.lastIndexOf('\n') + 1) + 1

	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(vDoc.file?.absolutePath ?: "Unsaved document", color = vMuted, fontSize = 12.sp)
		Spacer(Modifier.weight(1f))
		Text("Ln $vCaretLine, Col $vCaretCol", color = vMuted, fontSize = 12.sp)
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

// Extracts dropped files from a drag-and-drop event via the AWT transferable.
@OptIn(ExperimentalComposeUiApi::class)
private fun droppedFiles(inEvent: DragAndDropEvent): List<File> {
	val vTransferable = inEvent.awtTransferable
	if (!vTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
	@Suppress("UNCHECKED_CAST")
	return (vTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>).orEmpty()
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
