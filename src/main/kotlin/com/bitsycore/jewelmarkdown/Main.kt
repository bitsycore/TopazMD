package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

// True when the JVM exposes the JetBrains Runtime API used by Jewel's DecoratedWindow.
// Detected via the com.jetbrains.JBR sentinel class; on any other JDK the app falls back to a
// standard OS-decorated Compose Window rather than crashing on window creation.
private val kIsJbr: Boolean = runCatching { Class.forName("com.jetbrains.JBR") }.isSuccess

// CLI flag: open the bundled demo document immediately at startup. Convenient for testing
// changes without having to pick a file from disk.
private const val kFlagDemo = "--demo"

// Application entry point. Builds the IntelliJ (Jewel) theme — dark by default — and opens a
// window hosting the Markdown editor and live preview. On the JetBrains Runtime the window is
// decorated by Jewel (custom TitleBar with the app menus). On any other JDK that API is absent
// — or the user opted into a plain window via Settings — so we use a standard Compose Window
// and reproduce the menus in an in-body header instead.
fun main(inArgs: Array<String>) {
	// Enable Compose's Swing interop blending so the JavaFX WebView used by the Mermaid
	// renderer composes correctly with Compose overlays (the Settings dialog stays on top)
	// and stops flashing white during scroll. Must be set before any Compose window is created.
	System.setProperty("compose.interop.blending", "true")

	// JavaFX prints an "Unsupported JavaFX configuration" warning when it's loaded from the
	// classpath instead of the JPMS module path — harmless in a non-modular Compose Desktop
	// app, but noisy in the run log. Silence its informational loggers.
	java.util.logging.Logger.getLogger("javafx").level = java.util.logging.Level.SEVERE
	java.util.logging.Logger.getLogger("com.sun.javafx").level = java.util.logging.Level.SEVERE

	// macOS only: route the app's Swing JMenuBar to the system menu bar at the top of the
	// screen instead of inside the window. Both properties must be set before AWT initializes.
	if (kIsMac) {
		System.setProperty("apple.laf.useScreenMenuBar", "true")
		System.setProperty("apple.awt.application.name", "JMD")
	}

	runApp(inArgs)
}

// The actual Compose entry point — split out so the interop property gets set first.
private fun runApp(inArgs: Array<String>): Unit = application {
	val vOpenDemo = inArgs.contains(kFlagDemo)
	val vState = rememberAppState(inOpenDemo = vOpenDemo)
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
		val vOnClose: () -> Unit = {
			Persistence.save(vState)
			exitApplication()
		}

		// Warm up the Mermaid renderer in the background so diagrams render as soon as the
		// user opens a document that contains one.
		RememberMermaidInit()

		// Suppress the heavyweight WebView whenever a modal Compose overlay (currently the
		// Settings dialog) is open; otherwise the JFXPanel eats the outside-tap dismiss.
		MermaidRuntime.suppressed = vState.showSettings

		// Honors "exit on last tab close": when a close action latches pendingExit, persist and quit.
		LaunchedEffect(vState.pendingExit) {
			if (vState.pendingExit) vOnClose()
		}

		// Track Ctrl (for Ctrl+Click links), record a shortcut being rebound, or dispatch a
		// bound shortcut action. Returns true only when a key event is consumed. Shared between
		// the JBR-decorated and the fallback windows.
		val vKeyHandler: (KeyEvent) -> Boolean = { vEvent ->
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
		}

		// Window-decoration choice is locked at startup: if JBR is present and the user did not
		// opt out, use the Jewel-decorated window; otherwise fall back to a plain OS-decorated
		// Window. Changing the toggle in Settings only takes effect on the next launch.
		val vUseDecorated = kIsJbr && !vState.settings.useNonDecoratedWindow
		if (vUseDecorated) {
			DecoratedWindow(
				onCloseRequest = { vOnClose() },
				state = vWindowState,
				title = vState.windowTitle(),
				onPreviewKeyEvent = vKeyHandler,
			) {
				InstallMacOsMenuBar(vState)
				AppTitleBar(vState)
				AppBody(vState)
			}
		} else {
			Window(
				onCloseRequest = vOnClose,
				state = vWindowState,
				title = vState.windowTitle(),
				onPreviewKeyEvent = vKeyHandler,
			) {
				InstallMacOsMenuBar(vState)
				Column(Modifier.fillMaxSize()) {
					FallbackHeader(vState)
					Box(Modifier.weight(1f).fillMaxWidth()) { AppBody(vState) }
				}
			}
		}
	}
}

// JBR-decorated title bar: app menus on the left, document title centered, view-mode and
// settings icons on the right. The TitleBar's gradientStartColor tints it with the ambient
// app gradient. The center stays empty when no tab is open.
@Composable
private fun DecoratedWindowScope.AppTitleBar(inState: AppState) {
	val vTitleAccent = inState.settings.gradient.titleAccent(inState.isDark)
	TitleBar(modifier = Modifier.newFullscreenControls(), gradientStartColor = vTitleAccent) {
		// Left: the File / Edit / View / Help menus live in the title bar itself. On macOS the
		// same menus appear in the system menu bar instead, so we skip them here.
		if (!kIsMac) AppMenus(inState, Modifier.align(Alignment.Start).padding(start = 8.dp))

		// Center: the active document name (dirty dot), without the app name.
		val vDoc = inState.active
		if (vDoc != null) Text((if (vDoc.isDirty) "● " else "") + vDoc.title)

		// Right: view-mode icons and a settings icon (theme is set in Settings).
		Row(
			modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ViewModeIcons(inState)
			SettingsIconButton(inState)
		}
	}
}

// In-body header used when the JBR window-decoration API is unavailable, or when the user
// asked for a plain OS-decorated window. The OS title bar shows the window title; this strip
// reproduces the menus and icons that normally live in TitleBar so the app stays fully usable.
@Composable
private fun FallbackHeader(inState: AppState) {
	val vTitleAccent = inState.settings.gradient.titleAccent(inState.isDark)
	val vDoc = inState.active
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.background(vTitleAccent)
				.height(36.dp)
				.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		// On macOS the same menus are routed to the system menu bar.
		if (!kIsMac) AppMenus(inState)
		Spacer(Modifier.weight(1f))
		if (vDoc != null) Text((if (vDoc.isDirty) "● " else "") + vDoc.title)
		Spacer(Modifier.weight(1f))
		Row(
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ViewModeIcons(inState)
			SettingsIconButton(inState)
		}
	}
}
