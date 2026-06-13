package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

// Application entry point. Builds the IntelliJ (Jewel) theme — dark by default —
// and shows a decorated window hosting the Markdown editor and live preview.
// Must run on the JetBrains Runtime (see gradle.properties), or DecoratedWindow fails.
fun main() = application {
	val vState = rememberAppState()
	val vWindowState = rememberWindowState(width = 1280.dp, height = 860.dp)

	val vThemeDefinition =
		if (vState.isDark) {
			JewelTheme.darkThemeDefinition()
		} else {
			JewelTheme.lightThemeDefinition()
		}

	// Title-bar styling is derived from the theme (dark/light) automatically.
	val vStyling = ComponentStyling.default().decoratedWindow()

	IntUiTheme(vThemeDefinition, vStyling, false) {
		DecoratedWindow(
			onCloseRequest = {
				Persistence.save(vState)
				exitApplication()
			},
			state = vWindowState,
			title = vState.windowTitle(),
			// Track Ctrl (for Ctrl+Click links), record a shortcut being rebound, or dispatch a
			// bound shortcut action. Returns true only when a key event is consumed.
			onPreviewKeyEvent = { vEvent ->
				if (vEvent.isCtrlPressed != vState.isCtrlDown) vState.isCtrlDown = vEvent.isCtrlPressed
				val vRecording = vState.recordingAction
				when {
					vRecording != null && vEvent.type == KeyEventType.KeyDown && !isModifierKey(vEvent.key) -> {
						vState.keymap[vRecording] = shortcutFromEvent(vEvent)
						vState.recordingAction = null
						true
					}
					vEvent.type == KeyEventType.KeyDown -> {
						val vAction = vState.keymap.entries.firstOrNull { it.value.matches(vEvent) }?.key
						if (vAction != null) {
							runShortcutAction(vState, vAction)
							true
						} else {
							false
						}
					}
					else -> false
				}
			},
		) {
			AppTitleBar(vState)
			AppBody(vState)
		}
	}
}

// Custom window title bar: app name on the left, document title centered,
// and a dark/light theme toggle on the right.
@Composable
private fun DecoratedWindowScope.AppTitleBar(inState: AppState) {
	// Subtle ambient accent tinting the title bar (matches the configured app gradient).
	val vTitleAccent = inState.settings.gradient.titleAccent(inState.isDark)
	TitleBar(modifier = Modifier.newFullscreenControls(), gradientStartColor = vTitleAccent) {
		// Left: the File / Edit / View menus live in the title bar itself.
		AppMenus(inState, Modifier.align(Alignment.Start).padding(start = 8.dp))

		// Center: the active document name (dirty dot), without the app name.
		val vDoc = inState.active
		Text((if (vDoc.isDirty) "● " else "") + vDoc.title)

		// Right: view-mode icons, settings and theme toggle.
		Row(
			modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ViewModeIcons(inState)
			OutlinedButton(onClick = { inState.showSettings = true }) { Text("Settings") }
			OutlinedButton(onClick = { inState.isDark = !inState.isDark }) {
				Text(if (inState.isDark) "Light" else "Dark")
			}
		}
	}
}
