package com.bitsycore.jewelmarkdown

import java.io.File
import java.util.Properties

// Loads and saves user preferences (theme, UI settings and keymap) to a properties file in
// the user's home directory, so they persist across runs.
object Persistence {
	private val kFile = File(System.getProperty("user.home"), ".jewelmarkdown/settings.properties")

	// Applies any saved preferences onto the given state. Missing/invalid values are ignored.
	fun load(inState: AppState) {
		if (!kFile.exists()) return
		val vProps = Properties()
		runCatching { kFile.inputStream().use { vProps.load(it) } }.onFailure { return }

		val vSettings = inState.settings
		vProps.getProperty("isDark")?.toBooleanStrictOrNull()?.let { inState.isDark = it }
		vProps.getProperty("gradient")?.let { vName -> GradientPreset.entries.firstOrNull { it.name == vName }?.let { vSettings.gradient = it } }
		vProps.getProperty("editorFont")?.let { vName -> EditorFont.entries.firstOrNull { it.name == vName }?.let { vSettings.editorFont = it } }
		vProps.getProperty("paneCorner")?.toFloatOrNull()?.let { vSettings.paneCornerDp = it }
		vProps.getProperty("contentGap")?.toFloatOrNull()?.let { vSettings.contentGapDp = it }
		vProps.getProperty("editorFontSize")?.toFloatOrNull()?.let { vSettings.editorFontSizeSp = it }
		vProps.getProperty("showStatusBar")?.toBooleanStrictOrNull()?.let { vSettings.showStatusBar = it }
		vProps.getProperty("splitRatio")?.toFloatOrNull()?.let { inState.splitRatio = it.coerceIn(0.15f, 0.85f) }

		for (vAction in ShortcutAction.entries) {
			vProps.getProperty("key.${vAction.name}")?.let { vEnc -> decodeShortcut(vEnc)?.let { inState.keymap[vAction] = it } }
		}
	}

	// Writes the current preferences to disk, swallowing any IO failure.
	fun save(inState: AppState) {
		val vProps = Properties()
		val vSettings = inState.settings
		vProps.setProperty("isDark", inState.isDark.toString())
		vProps.setProperty("gradient", vSettings.gradient.name)
		vProps.setProperty("editorFont", vSettings.editorFont.name)
		vProps.setProperty("paneCorner", vSettings.paneCornerDp.toString())
		vProps.setProperty("contentGap", vSettings.contentGapDp.toString())
		vProps.setProperty("editorFontSize", vSettings.editorFontSizeSp.toString())
		vProps.setProperty("showStatusBar", vSettings.showStatusBar.toString())
		vProps.setProperty("splitRatio", inState.splitRatio.toString())
		for ((vAction, vShortcut) in inState.keymap) {
			vProps.setProperty("key.${vAction.name}", vShortcut.encode())
		}
		runCatching {
			kFile.parentFile?.mkdirs()
			kFile.outputStream().use { vProps.store(it, "JewelMarkdown preferences") }
		}
	}
}
