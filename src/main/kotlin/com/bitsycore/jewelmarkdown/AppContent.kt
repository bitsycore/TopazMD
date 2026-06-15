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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
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
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.io.File
import org.jetbrains.jewel.ui.component.separator

// Window body below the title bar: a menu bar, the document tab strip, the editor/preview
// split and an optional status bar, over the configured ambient gradient, with the settings
// overlay on top.
@Composable
fun AppBody(inState: AppState) {
	val vSettings = inState.settings

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
		// Islands-style layout: activity bar and status bar sit flat on the gradient; the only
		// rounded "islands" are the project panel and the editor area, separated by the same
		// content-gap value the editor card uses internally.
		val vGap = vSettings.contentGapDp.dp
		val vCorner = vSettings.paneCornerDp.dp
		Row(Modifier.fillMaxSize()) {
			ActivityBar(inState)
			Column(Modifier.weight(1f).fillMaxHeight()) {
				Row(
					modifier =
						Modifier
							.weight(1f)
							.fillMaxWidth()
							.padding(
								// Left side stays tight (2dp) because the activity bar already
								// provides visual margin from the window edge. The right side
								// matches the top (8dp) so the editor island has the same
								// breathing room from the window edge as from the title bar.
								start = 2.dp,
								end = 8.dp,
								// Aligns the top of the islands with the activity bar's first
								// icon (which sits at vertical = 8.dp inside the bar).
								top = 8.dp,
								// When the status bar is on it provides its own 6dp vertical
								// padding; when off we still want a small margin so the islands
								// don't touch the window edge — 2dp minimum.
								bottom = if (vSettings.showStatusBar) 0.dp else 2.dp,
							),
					// Tight 2dp between the project-pane island and the editor island — they
					// read as adjacent surfaces, not as two separate sections of the window.
					horizontalArrangement = Arrangement.spacedBy(2.dp),
				) {
					if (inState.showProjectPanel) {
						Pane(Modifier.width(250.dp).fillMaxHeight(), vCorner) {
							ProjectPanel(inState, Modifier.fillMaxSize())
						}
					}
					Box(Modifier.weight(1f).fillMaxHeight()) {
						val vDoc = inState.active
						if (vDoc != null) {
							EditorAndPreview(inState, vDoc, Modifier.fillMaxSize())
						} else {
							WelcomePanel(inState, Modifier.fillMaxSize())
						}
					}
				}
				if (vSettings.showStatusBar) {
					// Always render the status bar so the islands always have a visible bottom
					// margin. With no active doc the bar just shows a "no document" label.
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
			menuItem("Close all tabs") { vClose(); inState.closeAll() }
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
		MenuButton("Help", vOpenMenu, vSetOpenMenu) { vClose ->
			menuItem("Open demo file") { vClose(); inState.openDemo() }
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

// Horizontal strip of open-document tab pills, docked at the top of the TabbedPane card.
@Composable
private fun TabStrip(inState: AppState) {
	Row(
		modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalAlignment = Alignment.CenterVertically,
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

// A single tab pill: fully-rounded rectangle hosting the title, dirty dot, and close glyph,
// with a right-click context menu and drag-to-reorder.
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
		val vShape = RoundedCornerShape(6.dp)
		// Active tab tints with the theme's text color (white on dark, near-black on light)
		// at a meaningful alpha so it clearly reads as the focused pill; inactive pills keep
		// a very faint background so each still reads as its own box but recedes.
		val vBg =
			when {
				inActive -> JewelTheme.globalColors.text.normal.copy(alpha = 0.15f)
				else -> JewelTheme.globalColors.text.info.copy(alpha = 0.05f)
			}
		Row(
			modifier =
				Modifier
					.clip(vShape)
					.background(vBg)
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
					.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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

// Editor and preview live inside a single rounded card (matching IntelliJ's editor pane). The
// card carries the document tab strip at the top; below the 1dp separator the body shows either
// the editor, the preview, or — in split mode — the editor and preview side by side separated
// only by a slim vertical divider.
@Composable
private fun EditorAndPreview(inState: AppState, inDoc: Document, inModifier: Modifier) {
	val vCorner = inState.settings.paneCornerDp.dp

	TabbedPane(inState, vCorner, inModifier) {
		when (inState.viewMode) {
			ViewMode.Editor -> EditorPane(inState, inDoc, Modifier.fillMaxSize())
			ViewMode.Preview -> PreviewBody(inState, inDoc, Modifier.fillMaxSize())
			ViewMode.Split -> {
				var vWidth by remember { mutableStateOf(1f) }
				Row(
					modifier =
						Modifier
							.fillMaxSize()
							.onSizeChanged { vWidth = it.width.toFloat().coerceAtLeast(1f) },
				) {
					EditorPane(inState, inDoc, Modifier.weight(inState.splitRatio).fillMaxHeight())
					SplitHandle(8.dp) { vDelta ->
						inState.splitRatio = (inState.splitRatio + vDelta / vWidth).coerceIn(0.15f, 0.85f)
					}
					PreviewBody(inState, inDoc, Modifier.weight(1f - inState.splitRatio).fillMaxHeight())
				}
			}
		}
	}
}

// Markdown preview hooked up to the active document. Extracted so both split mode and the
// preview-only view share the same wiring without duplicating the click/URL plumbing.
@Composable
private fun PreviewBody(inState: AppState, inDoc: Document, inModifier: Modifier) {
	MarkdownPreview(
		inText = inDoc.text,
		inIsDark = inState.isDark,
		inModifier = inModifier,
		// IntelliJ-style: links open only on Ctrl+Click; plain clicks select text.
		inOnUrlClick = { vUrl -> if (inState.isCtrlDown) openUrl(vUrl) },
	)
}

// A Pane (rounded shadowed card) with the document tab strip docked at the top, separated from
// the body by a single 1dp divider. The strip's tab pills are individually rounded with a small
// gap between them, matching IntelliJ's tab bar.
@Composable
private fun TabbedPane(inState: AppState, inCorner: Dp, inModifier: Modifier, inContent: @Composable BoxScope.() -> Unit) {
	// Same faint wash the Pane border uses, so the divider between tab strip and body matches
	// the card outline instead of standing out as a hard dark line.
	val vSubtleLine = JewelTheme.globalColors.text.normal.copy(alpha = 0.1f)
	Pane(inModifier, inCorner) {
		Column(Modifier.fillMaxSize()) {
			TabStrip(inState)
			Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vSubtleLine)
			Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = inContent)
		}
	}
}

// Draggable divider between the editor and preview panes; reports horizontal drag in pixels.
// The hit area is wider than the visible line so the user has something easy to grab. The line
// itself stays 1dp wide at all times (no layout shift on hover); discoverability comes from
// the resize cursor and a thin outline that appears around the line while the pointer is over
// the hit area.
@Composable
private fun SplitHandle(inWidth: Dp, inOnDrag: (Float) -> Unit) {
	val vInteraction = remember { MutableInteractionSource() }
	val vHovered by vInteraction.collectIsHoveredAsState()
	val vNormal = JewelTheme.globalColors.borders.normal
	val vHighlight = JewelTheme.globalColors.text.normal
	Box(
		modifier =
			Modifier
				.width(inWidth)
				.fillMaxHeight()
				.hoverable(vInteraction)
				.pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
				.pointerInput(Unit) {
					detectDragGestures { change, dragAmount ->
						change.consume()
						inOnDrag(dragAmount.x)
					}
				},
		contentAlignment = Alignment.Center,
	) {
		// The visible line stays 1dp wide so it never reflows the adjacent panes. On hover we
		// draw a high-contrast outline using Canvas (which paints inside the hit area, outside
		// the 1dp line) so the indicator appears without changing the line's bounding box.
		if (vHovered) {
			Canvas(modifier = Modifier.fillMaxSize()) {
				val vStroke = 1.dp.toPx()
				val vLineHalf = 0.5.dp.toPx()
				val vCx = size.width / 2f
				drawRect(
					color = vHighlight,
					topLeft = Offset(vCx - vLineHalf - vStroke, 0f),
					size = Size(vStroke, size.height),
				)
				drawRect(
					color = vHighlight,
					topLeft = Offset(vCx + vLineHalf, 0f),
					size = Size(vStroke, size.height),
				)
			}
		}
		Box(Modifier.width(1.dp).fillMaxHeight().background(vNormal))
	}
}

// A rounded panel (the IntelliJ tool-window card look). No drop-shadow; outlines and inner
// dividers use a very faint white/black wash (alpha = 0.1 of the theme's text color) so the
// cards have shape without harsh edges. In dark mode the surface is tinted slightly lighter
// than the chrome so the editor reads as an inset "paper" against the ambient gradient.
@Composable
private fun Pane(inModifier: Modifier, inCornerDp: Dp, inContent: @Composable BoxScope.() -> Unit) {
	val vShape = RoundedCornerShape(inCornerDp)
	val vCardBg =
		if (JewelTheme.isDark) Color(0xFF26282C)
		else JewelTheme.globalColors.panelBackground
	val vSubtleLine = JewelTheme.globalColors.text.normal.copy(alpha = 0.1f)
	Box(
		modifier =
			inModifier
				.clip(vShape)
				.background(vCardBg)
				.border(1.dp, vSubtleLine, vShape),
		content = inContent,
	)
}

// Editor pane: a Markdown formatting toolbar above the raw text area, with a line-number
// gutter and a thin change-gutter on the left. Text area, change gutter and line numbers all
// share the same vertical scroll, so they stay aligned even on documents taller than the
// viewport. When word wrap is off the whole row also gains a horizontal scroll.
@Composable
private fun EditorPane(inState: AppState, inDoc: Document, inModifier: Modifier) {
	var vLayout by remember(inDoc) { mutableStateOf<TextLayoutResult?>(null) }
	// Drive the gutters off the *layout's* text rather than the live document text. The layout
	// always lags by one frame after a keystroke (Compose re-layouts after the state mutation),
	// and computing the changed-line set from the live text while still drawing at the old
	// layout's Y positions makes the stripes flash on the wrong line for that single frame.
	// Keying the set and the line counts to the layout's input text keeps them strictly in
	// sync with what the BasicTextField is actually showing.
	val vLayoutText = vLayout?.layoutInput?.text?.text ?: ""
	val vChanged = remember(vLayoutText, inDoc.savedText) { computeChangedLines(inDoc.savedText, vLayoutText) }
	val vEditorStyle =
		JewelTheme.defaultTextStyle.copy(
			fontFamily = inState.settings.editorFont.family,
			fontSize = inState.settings.editorFontSizeSp.sp,
			color = JewelTheme.globalColors.text.normal,
		)
	val vWrap = inState.settings.editorWordWrap
	val vScrollState = rememberScrollState()
	val vHScrollState = rememberScrollState()

	Column(inModifier) {
		MarkdownToolbar(inState)
		Divider(
			Orientation.Horizontal,
			Modifier.fillMaxWidth(),
			color = JewelTheme.globalColors.text.normal.copy(alpha = 0.1f),
		)
		Box(
			modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(vScrollState),
		) {
			val vRowModifier =
				if (vWrap) Modifier.fillMaxWidth().padding(10.dp)
				else Modifier.horizontalScroll(vHScrollState).padding(10.dp)
			Row(modifier = vRowModifier, verticalAlignment = Alignment.Top) {
				LineNumbersGutter(vLayout, vLayoutText, vEditorStyle)
				Spacer(Modifier.width(4.dp))
				ChangeGutter(vLayout, vLayoutText, vChanged)
				Spacer(Modifier.width(6.dp))
				EditorTextArea(
					inDoc = inDoc,
					inIsDark = inState.isDark,
					inStyle = vEditorStyle,
					inWrap = vWrap,
					inModifier = if (vWrap) Modifier.weight(1f) else Modifier,
					inOnTextLayout = { vLayout = it },
				)
			}
		}
	}
}

// Left-margin gutter that prints a line number for every logical line of the document. When a
// logical line wraps, only its first visual sub-line gets a number — wrapped continuations stay
// blank so the column still reads as "one number per source line". Width auto-fits the largest
// number to keep things tight.
@Composable
private fun LineNumbersGutter(inLayout: TextLayoutResult?, inText: String, inStyle: TextStyle) {
	val vDensity = LocalDensity.current
	val vTextMeasurer = rememberTextMeasurer()
	val vMuted = JewelTheme.globalColors.text.info
	val vNumberStyle = remember(inStyle, vMuted) { inStyle.copy(color = vMuted) }
	val vLogicalCount = remember(inText) { inText.count { it == '\n' } + 1 }
	val vWidestMeasure =
		remember(vLogicalCount, vNumberStyle) { vTextMeasurer.measure(vLogicalCount.toString(), vNumberStyle) }
	val vWidthDp = with(vDensity) { vWidestMeasure.size.width.toDp() }
	val vHeightDp = with(vDensity) { (inLayout?.size?.height ?: 0).toDp() }

	Canvas(modifier = Modifier.width(vWidthDp).height(vHeightDp)) {
		val vLayout2 = inLayout ?: return@Canvas
		var vLogicalIdx = 1
		for (vVisualIdx in 0 until vLayout2.lineCount) {
			val vStart = vLayout2.getLineStart(vVisualIdx)
			val vIsLogicalStart = vVisualIdx == 0 || (vStart > 0 && inText[vStart - 1] == '\n')
			if (!vIsLogicalStart) continue
			val vMeasure = vTextMeasurer.measure(vLogicalIdx.toString(), vNumberStyle)
			val vTop = vLayout2.getLineTop(vVisualIdx)
			drawText(vMeasure, topLeft = Offset(size.width - vMeasure.size.width, vTop))
			vLogicalIdx++
		}
	}
}

// Vertical strip painted with a stripe per *logical* line that differs from the last-saved
// content. The stripe spans every visual sub-line of a wrapped logical line so the indicator
// still reads as "this source line is modified" when wrap is on.
@Composable
private fun ChangeGutter(inLayout: TextLayoutResult?, inText: String, inChanged: Set<Int>) {
	val vColor = Color(0xFFE2A03F)
	val vDensity = LocalDensity.current
	val vHeightDp = with(vDensity) { (inLayout?.size?.height ?: 0).toDp() }
	Canvas(modifier = Modifier.width(3.dp).height(vHeightDp)) {
		val vLayout2 = inLayout ?: return@Canvas
		var vLogicalIdx = 0
		for (vVisualIdx in 0 until vLayout2.lineCount) {
			val vStart = vLayout2.getLineStart(vVisualIdx)
			val vIsLogicalStart = vVisualIdx == 0 || (vStart > 0 && inText[vStart - 1] == '\n')
			if (vIsLogicalStart && vVisualIdx > 0) vLogicalIdx++
			if (vLogicalIdx in inChanged) {
				val vTop = vLayout2.getLineTop(vVisualIdx)
				val vBottom = vLayout2.getLineBottom(vVisualIdx)
				drawRect(
					color = vColor,
					topLeft = Offset(0f, vTop),
					size = Size(size.width, vBottom - vTop),
				)
			}
		}
	}
}

// Per-line diff against the last-saved text: a line is "changed" if it doesn't have an
// identical counterpart at the same index in the saved content. A whole-document Myers diff
// would be more accurate (it could detect inserts and shift line numbers), but a simple
// index-based comparison is fast, allocation-light, and matches the common edit-and-save flow.
private fun computeChangedLines(inSaved: String, inCurrent: String): Set<Int> {
	if (inSaved == inCurrent) return emptySet()
	val vSavedLines = inSaved.split('\n')
	val vCurrentLines = inCurrent.split('\n')
	val vChanged = HashSet<Int>(vCurrentLines.size)
	for (vIdx in vCurrentLines.indices) {
		if (vIdx >= vSavedLines.size || vSavedLines[vIdx] != vCurrentLines[vIdx]) {
			vChanged.add(vIdx)
		}
	}
	return vChanged
}

// Raw Markdown editor, monospace, with live Markdown syntax highlighting. We use the lower-
// level BasicTextField so we own scrolling (shared with the change gutter via a parent
// verticalScroll) and so we can keep the VisualTransformation-based syntax highlighting —
// the newer TextFieldState API doesn't support inline styled output. The wrap flag controls
// whether the field fills its row width (wrap = on) or asks for its intrinsic, possibly very
// wide, content width (wrap = off, the row above provides horizontal scrolling).
@Composable
private fun EditorTextArea(
	inDoc: Document,
	inIsDark: Boolean,
	inStyle: TextStyle,
	inWrap: Boolean,
	inModifier: Modifier,
	inOnTextLayout: (TextLayoutResult) -> Unit = {},
) {
	val vTransformation = remember(inIsDark) { MarkdownSyntaxTransformation(inIsDark) }
	Box(modifier = inModifier) {
		if (inDoc.fieldValue.text.isEmpty()) {
			Text(
				"Write some Markdown…",
				style = inStyle.copy(color = JewelTheme.globalColors.text.info),
			)
		}
		BasicTextField(
			value = inDoc.fieldValue,
			onValueChange = { inDoc.fieldValue = it },
			textStyle = inStyle,
			visualTransformation = vTransformation,
			onTextLayout = inOnTextLayout,
			cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
			modifier = if (inWrap) Modifier.fillMaxWidth() else Modifier,
		)
	}
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

// Bottom status bar: file path, dirty state, document metrics and optional JVM heap usage,
// all in muted text. With no active document we still render the bar (so the layout keeps a
// constant bottom margin) and show a single "No document" label in place of the metrics.
@Composable
private fun StatusBar(inState: AppState) {
	val vDoc = inState.active
	val vMuted = JewelTheme.globalColors.text.info
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (vDoc == null) {
			Text("No document", color = vMuted, fontSize = 12.sp)
			Spacer(Modifier.weight(1f))
			if (inState.settings.showMemoryUsage) MemoryUsage(vMuted)
			return@Row
		}
		val vText = vDoc.text
		val vLineCount = if (vText.isEmpty()) 0 else vText.count { it == '\n' } + 1
		val vWordCount = if (vText.isBlank()) 0 else vText.trim().split(Regex("\\s+")).size

		// Caret position (1-based line/column) from the current selection.
		val vCaret = vDoc.fieldValue.selection.start.coerceIn(0, vText.length)
		val vBeforeCaret = vText.substring(0, vCaret)
		val vCaretLine = vBeforeCaret.count { it == '\n' } + 1
		val vCaretCol = vCaret - (vBeforeCaret.lastIndexOf('\n') + 1) + 1

		Text(vDoc.file?.absolutePath ?: "Unsaved document", color = vMuted, fontSize = 12.sp)
		Spacer(Modifier.weight(1f))
		Text("Ln $vCaretLine, Col $vCaretCol", color = vMuted, fontSize = 12.sp)
		Text(if (vDoc.isDirty) "Modified" else "Saved", color = vMuted, fontSize = 12.sp)
		Text("$vLineCount lines", color = vMuted, fontSize = 12.sp)
		Text("$vWordCount words", color = vMuted, fontSize = 12.sp)
		Text("${vText.length} chars", color = vMuted, fontSize = 12.sp)
		if (inState.settings.showMemoryUsage) MemoryUsage(vMuted)
	}
}

// JVM heap-usage indicator: "used / total MB". Polled every 2 seconds in a coroutine so the
// number stays roughly current without spinning the CPU. Clicking the label triggers
// System.gc() the same way IntelliJ's heap indicator does.
@Composable
private fun MemoryUsage(inColor: Color) {
	val vUsage by produceState(0L to 0L) {
		while (true) {
			val vRt = Runtime.getRuntime()
			val vUsed = (vRt.totalMemory() - vRt.freeMemory()) / (1024L * 1024L)
			val vTotal = vRt.totalMemory() / (1024L * 1024L)
			value = vUsed to vTotal
			kotlinx.coroutines.delay(2000)
		}
	}
	Text(
		"${vUsage.first} / ${vUsage.second} MB",
		color = inColor,
		fontSize = 12.sp,
		modifier = Modifier.clickable { System.gc() },
	)
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
	val vCategories = listOf("Appearance", "Editor", "Behavior", "Shortcuts", "About")
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
						"Behavior" -> BehaviorSettings(inState)
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
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("Word wrap", modifier = Modifier.width(120.dp))
		Chip("On", vSettings.editorWordWrap) { vSettings.editorWordWrap = true }
		Chip("Off", !vSettings.editorWordWrap) { vSettings.editorWordWrap = false }
	}
	GroupHeader("View")
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("Status bar", modifier = Modifier.width(120.dp))
		Chip("On", vSettings.showStatusBar) { vSettings.showStatusBar = true }
		Chip("Off", !vSettings.showStatusBar) { vSettings.showStatusBar = false }
	}
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("Memory usage", modifier = Modifier.width(120.dp))
		Chip("On", vSettings.showMemoryUsage) { vSettings.showMemoryUsage = true }
		Chip("Off", !vSettings.showMemoryUsage) { vSettings.showMemoryUsage = false }
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

// Behavior category: app-lifecycle and window-decoration toggles.
@Composable
private fun BehaviorSettings(inState: AppState) {
	val vSettings = inState.settings
	GroupHeader("Session")
	OnOffRow(
		inLabel = "Restore tabs at startup",
		inDescription = "Re-open the files that were open when you last quit the app.",
		inValue = vSettings.restoreSession,
		inOnChange = { vSettings.restoreSession = it },
	)
	OnOffRow(
		inLabel = "Quit when last tab closes",
		inDescription = "Closing the final tab exits the app instead of showing the welcome panel.",
		inValue = vSettings.exitOnLastTabClose,
		inOnChange = { vSettings.exitOnLastTabClose = it },
	)
	GroupHeader("Window")
	OnOffRow(
		inLabel = "Use OS title bar",
		inDescription = "Force the standard OS-decorated window even on the JetBrains Runtime. Takes effect at next launch.",
		inValue = vSettings.useNonDecoratedWindow,
		inOnChange = { vSettings.useNonDecoratedWindow = it },
	)
}

// A labeled On/Off chip row with a one-line description, used by the Behavior settings.
@Composable
private fun OnOffRow(inLabel: String, inDescription: String, inValue: Boolean, inOnChange: (Boolean) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(inLabel, modifier = Modifier.weight(1f))
			Chip("On", inValue) { inOnChange(true) }
			Chip("Off", !inValue) { inOnChange(false) }
		}
		Text(inDescription, color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
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

// Empty-state panel shown when no tabs are open: large title, brief hint, and shortcuts to
// create or open a document.
@Composable
private fun WelcomePanel(inState: AppState, inModifier: Modifier) {
	Box(modifier = inModifier, contentAlignment = Alignment.Center) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text("Jewel Markdown", fontWeight = FontWeight.Bold, fontSize = 22.sp)
			Text(
				"No tabs open. Create a new document or open an existing file to get started.",
				color = JewelTheme.globalColors.text.info,
				fontSize = 13.sp,
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				DefaultButton(onClick = { inState.newDocument() }) { Text("New file") }
				DefaultButton(onClick = { chooseOpenFile()?.let { inState.openFile(it) } }) { Text("Open file…") }
				DefaultButton(onClick = { chooseFolder()?.let { inState.projectRoot = it; inState.showProjectPanel = true } }) { Text("Open folder…") }
				DefaultButton(onClick = { inState.openDemo() }) { Text("Open demo") }
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
// MARK: Actions
// ==================

// Wraps the active document's selection with the given prefix/suffix. No-op when no tab is open.
private fun editWrap(inState: AppState, inPrefix: String, inSuffix: String, inPlaceholder: String) {
	val vDoc = inState.active ?: return
	vDoc.fieldValue = MarkdownActions.wrap(vDoc.fieldValue, inPrefix, inSuffix, inPlaceholder)
}

// Prefixes the active document's current line with the given marker. No-op when no tab is open.
private fun editPrefix(inState: AppState, inPrefix: String) {
	val vDoc = inState.active ?: return
	vDoc.fieldValue = MarkdownActions.prefixLine(vDoc.fieldValue, inPrefix)
}

// Opens a Markdown file into a new tab.
private fun onOpen(inState: AppState) {
	val vFile = chooseOpenFile() ?: return
	inState.openFile(vFile)
}

// Saves the active document to its file, falling back to "Save As" when there is none.
private fun onSave(inState: AppState) {
	val vDoc = inState.active ?: return
	val vFile = vDoc.file
	if (vFile == null) {
		onSaveAs(inState)
		return
	}
	runCatching { vFile.writeText(vDoc.text) }.onSuccess { vDoc.markSaved(vFile) }
}

// Prompts for a destination and writes the active document there.
private fun onSaveAs(inState: AppState) {
	val vDoc = inState.active ?: return
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
