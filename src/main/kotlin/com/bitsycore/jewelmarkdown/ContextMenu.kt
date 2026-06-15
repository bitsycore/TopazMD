package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.ContextMenuDivider
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text

// Context-menu representation that matches the rest of the app's look: rounded panel using the
// editor card's panelBackground, the same alpha-0.1 outline as all the other surfaces, hover
// tint on the active row, real Jewel Divider between logical item groups (instead of the
// default Compose menu's flat horizontal line). Used in place of Compose's
// LightDefault/DarkDefault representations so the right-click menu on tabs and inside the
// editor reads as part of the app.
object ThemedContextMenuRepresentation : ContextMenuRepresentation {
	@OptIn(ExperimentalComposeUiApi::class)
	@Composable
	override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
		val vStatus = state.status
		if (vStatus !is ContextMenuState.Status.Open) return
		val vItems = items()
		if (vItems.isEmpty()) return

		val vPositionProvider = rememberPopupPositionProviderAtPosition(vStatus.rect.center)
		Popup(
			popupPositionProvider = vPositionProvider,
			onDismissRequest = { state.status = ContextMenuState.Status.Closed },
			properties = PopupProperties(focusable = true),
		) {
			ThemedContextMenuPanel(vItems) { vItem ->
				state.status = ContextMenuState.Status.Closed
				vItem.onClick()
			}
		}
	}
}

// The popup body. A rounded panel with the same card surface + outline as the editor pane; a
// Jewel Divider is emitted between "destructive" actions (anything whose label starts with
// "Cut" or "Close") and the actions that follow, so the menu has the visual grouping native
// editors get without us having to invent a new item type.
@Composable
private fun ThemedContextMenuPanel(inItems: List<ContextMenuItem>, inOnClick: (ContextMenuItem) -> Unit) {
	val vShape = RoundedCornerShape(6.dp)
	val vOutline = JewelTheme.globalColors.text.normal.copy(alpha = 0.1f)
	// Sized via widthIn (min/max) only — combining widthIn with IntrinsicSize.Max produced
	// pathological measurements with fillMaxWidth rows, which made the menu fill the window
	// earlier. Rows fill the Column; the Column itself caps at ~220dp so the popup feels like
	// a native compact context menu.
	Column(
		modifier =
			Modifier
				.widthIn(min = 140.dp, max = 220.dp)
				.clip(vShape)
				.background(JewelTheme.globalColors.panelBackground)
				.border(1.dp, vOutline, vShape)
				.padding(vertical = 4.dp),
	) {
		// Divider is the same Jewel component used by the editor card; uses a more saturated
		// 18% wash than the outer outline so the separator actually reads inside the panel.
		val vDividerColor = JewelTheme.globalColors.text.normal.copy(alpha = 0.18f)
		// When the items list contains explicit ContextMenuDivider markers (Jewel emits these
		// to flag where a separator should appear), respect them and skip the implicit
		// "separator before the last item" heuristic so we don't double up.
		val vHasExplicit = inItems.any { it is ContextMenuDivider }
		for ((vIdx, vItem) in inItems.withIndex()) {
			if (vItem is ContextMenuDivider) {
				Divider(
					Orientation.Horizontal,
					Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp),
					color = vDividerColor,
				)
				continue
			}
			if (!vHasExplicit && separatorBefore(inItems, vIdx)) {
				Divider(
					Orientation.Horizontal,
					Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp),
					color = vDividerColor,
				)
			}
			ContextMenuRow(vItem.label) { inOnClick(vItem) }
		}
	}
}

// A single context-menu item row. Hovering tints the background using the same text-info wash
// the menu bar uses for hover/highlight; the label is rendered in the theme's normal text.
@Composable
private fun ContextMenuRow(inLabel: String, inOnClick: () -> Unit) {
	val vInteraction = remember { MutableInteractionSource() }
	val vHovered by vInteraction.collectIsHoveredAsState()
	val vHover = JewelTheme.globalColors.text.info.copy(alpha = 0.10f)
	Box(
		modifier =
			Modifier
				.fillMaxWidth()
				.background(if (vHovered) vHover else Color.Transparent)
				.hoverable(vInteraction)
				.clickable(onClick = inOnClick)
				.padding(horizontal = 12.dp, vertical = 6.dp),
	) {
		Text(inLabel, fontSize = 13.sp, color = JewelTheme.globalColors.text.normal)
	}
}

// Locale-independent heuristic for splitting the menu: when there are three or more entries,
// insert a divider before the last one. Compose's BasicTextField filters Cut/Copy/Paste out
// when their action is null (empty clipboard, no selection, etc.), so a "real" text menu can
// surface as Cut+Copy+SelectAll (3 entries) or Paste+SelectAll (2). The "≥ 3" rule groups the
// destructive/edit ops away from the trailing "select all" in either count, and also reads
// nicely on the tab menu (Close / Close Others / Close All) where the last item is the
// most-destructive option.
private fun separatorBefore(inItems: List<ContextMenuItem>, inIdx: Int): Boolean =
	inItems.size >= 3 && inIdx == inItems.lastIndex
