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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.jetbrains.jewel.window.defaultTitleBarStyle
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarStyle

// True when the JVM exposes the JetBrains Runtime API used by Jewel's DecoratedWindow.
// Detected via the com.jetbrains.JBR sentinel class; on any other JDK the app falls back to a
// standard OS-decorated Compose Window rather than crashing on window creation.
private val kIsJbr: Boolean = runCatching { Class.forName("com.jetbrains.JBR") }.isSuccess

// CLI flag: open the bundled demo document immediately at startup. Convenient for testing
// changes without having to pick a file from disk.
private const val kFlagDemo = "--demo"

// Attaches a Filter to every handler on the root j.u.l. logger that drops records whose
// loggerName starts with any of the given prefixes. Used to mute JavaFX's "unsupported
// JavaFX configuration" WARNING and Jewel's "missing IntelliJ SVG icon" SEVERE errors
// without affecting application logging.
private fun silenceNoisyLoggers(vararg inPrefixes: String) {
	val vRoot = java.util.logging.LogManager.getLogManager().getLogger("") ?: return
	val vMatchers = inPrefixes.toList()
	for (vHandler in vRoot.handlers) {
		val vExisting = vHandler.filter
		vHandler.filter = java.util.logging.Filter { vRecord ->
			val vName = vRecord.loggerName ?: ""
			val vIsNoisy = vMatchers.any { vName.startsWith(it) }
			if (vIsNoisy) false else (vExisting?.isLoggable(vRecord) ?: true)
		}
	}
}

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
	// app, but noisy in the run log. Jewel's standalone distribution likewise emits SEVERE
	// errors for missing IntelliJ context-menu SVG icons. Logger-level filtering wasn't
	// enough: PlatformImpl pins its own logger level, and Jewel's logger writes through
	// `getLogger("")` so the level on a parent is ignored. Filter at the handler level
	// instead — that's the last thing every record passes through before it hits stderr.
	silenceNoisyLoggers("javafx", "com.sun.javafx", "org.jetbrains.jewel")

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
		// Override Jewel's IntelliJ-themed context menu (which loads expui/general/*.svg
		// icons that aren't bundled in the standalone distribution — that's where the pink
		// placeholder icons + GRAVE log noise came from) with Compose's built-in plain
		// text-only context menu. The light/dark variant follows the current theme.
		val vContextMenu =
			if (vState.isDark) {
				androidx.compose.foundation.DarkDefaultContextMenuRepresentation
			} else {
				androidx.compose.foundation.LightDefaultContextMenuRepresentation
			}
		androidx.compose.runtime.CompositionLocalProvider(
			androidx.compose.foundation.LocalContextMenuRepresentation provides vContextMenu,
		) {
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
				// Paint the body gradient on the whole window content so the FallbackHeader
				// "melts into" the same background as the editor area — the Islands look.
				Box(
					Modifier
						.fillMaxSize()
						.background(vState.settings.gradient.brush(vState.isDark))
				) {
					Column(Modifier.fillMaxSize()) {
						FallbackHeader(vState)
						Box(Modifier.weight(1f).fillMaxWidth()) { AppBody(vState) }
					}
				}
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
	// Repaint the Jewel TitleBar so it disappears into the body gradient: solid background
	// matching the body's top stop, and a transparent 1dp underline. With these two changes
	// the title bar becomes visually indistinguishable from the body — Islands.
	val vTopStop = inState.settings.gradient.topStop(inState.isDark)
	val vBaseStyle = JewelTheme.defaultTitleBarStyle
	val vStyle = remember(vTopStop, vBaseStyle) { meltedTitleBarStyle(vBaseStyle, vTopStop) }
	TitleBar(modifier = Modifier.newFullscreenControls(), style = vStyle) {
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

// Builds a TitleBarStyle that takes the same metrics/icons/sub-styles as the active theme but
// replaces the background/border colors so the JBR-decorated title bar visually melts into the
// body gradient (matching Islands UI). Jewel's @GenerateDataFunctions doesn't synthesize a
// copy() helper, so we have to spell out every TitleBarColors field explicitly.
private fun meltedTitleBarStyle(inBase: TitleBarStyle, inTopStop: Color): TitleBarStyle {
	val vBaseColors = inBase.colors
	val vMelted =
		TitleBarColors(
			background = inTopStop,
			inactiveBackground = inTopStop,
			content = vBaseColors.content,
			// Jewel paints a 1dp Spacer below the TitleBar in this color. Color.Transparent
			// would reveal the window's default white surface (a 1-pixel seam); painting the
			// border in the same color as the title-bar background hides the seam entirely.
			border = inTopStop,
			fullscreenControlButtonsBackground = vBaseColors.fullscreenControlButtonsBackground,
			titlePaneButtonHoveredBackground = vBaseColors.titlePaneButtonHoveredBackground,
			titlePaneButtonPressedBackground = vBaseColors.titlePaneButtonPressedBackground,
			titlePaneCloseButtonHoveredBackground = vBaseColors.titlePaneCloseButtonHoveredBackground,
			titlePaneCloseButtonPressedBackground = vBaseColors.titlePaneCloseButtonPressedBackground,
			iconButtonHoveredBackground = vBaseColors.iconButtonHoveredBackground,
			iconButtonPressedBackground = vBaseColors.iconButtonPressedBackground,
			dropdownPressedBackground = vBaseColors.dropdownPressedBackground,
			dropdownHoveredBackground = vBaseColors.dropdownHoveredBackground,
		)
	return TitleBarStyle(
		colors = vMelted,
		metrics = inBase.metrics,
		icons = inBase.icons,
		dropdownStyle = inBase.dropdownStyle,
		iconButtonStyle = inBase.iconButtonStyle,
		paneButtonStyle = inBase.paneButtonStyle,
		paneCloseButtonStyle = inBase.paneCloseButtonStyle,
	)
}

// In-body header used when the JBR window-decoration API is unavailable, or when the user
// asked for a plain OS-decorated window. The OS title bar shows the window title; this strip
// reproduces the menus and icons that normally live in TitleBar so the app stays fully usable.
@Composable
private fun FallbackHeader(inState: AppState) {
	val vDoc = inState.active
	// Transparent — the OS-decorated Window paints the body gradient behind us, and the
	// chrome (menus/title/icons) sits flat on it like in the IntelliJ Islands look.
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
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
