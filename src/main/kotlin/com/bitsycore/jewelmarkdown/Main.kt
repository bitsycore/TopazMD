package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.text.font.FontWeight
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
			onCloseRequest = { exitApplication() },
			state = vWindowState,
			title = vState.windowTitle(),
			// Track Ctrl so the preview can open links only on Ctrl+Click (never consumes the event).
			onPreviewKeyEvent = { vEvent ->
				if (vEvent.isCtrlPressed != vState.isCtrlDown) vState.isCtrlDown = vEvent.isCtrlPressed
				false
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
	// Subtle ambient accent tinting the title bar (matches the app's dark gradient).
	val vTitleAccent = if (inState.isDark) Color(0xFF34304A) else Color(0xFFDDE3F2)
	TitleBar(modifier = Modifier.newFullscreenControls(), gradientStartColor = vTitleAccent) {
		Text(
			"Jewel Markdown",
			modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
			fontWeight = FontWeight.SemiBold,
		)

		Text(inState.windowTitle())

		Row(
			modifier = Modifier.align(Alignment.End).padding(end = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			OutlinedButton(onClick = { inState.isDark = !inState.isDark }) {
				Text(if (inState.isDark) "Light mode" else "Dark mode")
			}
		}
	}
}
