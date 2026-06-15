package com.bitsycore.jewelmarkdown

import java.io.File
import java.util.Properties

// Loads and saves user preferences (theme, UI settings, keymap) and the last session
// (open tabs and the active tab) to a properties file in the user's home directory, so
// they persist across runs.
object Persistence {
	private val kFile = File(System.getProperty("user.home"), ".jewelmarkdown/settings.properties")

	// Applies any saved preferences onto the given state. Missing/invalid values are ignored.
	// Returns the loaded session payload so callers can decide when to apply it (e.g. after
	// inspecting --demo or restoreSession).
	fun load(inState: AppState): SessionSnapshot {
		if (!kFile.exists()) return SessionSnapshot(emptyList(), 0)
		val vProps = Properties()
		runCatching { kFile.inputStream().use { vProps.load(it) } }.onFailure { return SessionSnapshot(emptyList(), 0) }

		val vSettings = inState.settings
		vProps.getProperty("isDark")?.toBooleanStrictOrNull()?.let { inState.isDark = it }
		vProps.getProperty("gradient")?.let { vName -> GradientPreset.entries.firstOrNull { it.name == vName }?.let { vSettings.gradient = it } }
		vProps.getProperty("editorFont")?.let { vName -> EditorFont.entries.firstOrNull { it.name == vName }?.let { vSettings.editorFont = it } }
		vProps.getProperty("paneCorner")?.toFloatOrNull()?.let { vSettings.paneCornerDp = it }
		vProps.getProperty("contentGap")?.toFloatOrNull()?.let { vSettings.contentGapDp = it }
		vProps.getProperty("editorFontSize")?.toFloatOrNull()?.let { vSettings.editorFontSizeSp = it }
		vProps.getProperty("editorWordWrap")?.toBooleanStrictOrNull()?.let { vSettings.editorWordWrap = it }
		vProps.getProperty("showStatusBar")?.toBooleanStrictOrNull()?.let { vSettings.showStatusBar = it }
		vProps.getProperty("showMemoryUsage")?.toBooleanStrictOrNull()?.let { vSettings.showMemoryUsage = it }
		vProps.getProperty("restoreSession")?.toBooleanStrictOrNull()?.let { vSettings.restoreSession = it }
		vProps.getProperty("exitOnLastTabClose")?.toBooleanStrictOrNull()?.let { vSettings.exitOnLastTabClose = it }
		vProps.getProperty("useNonDecoratedWindow")?.toBooleanStrictOrNull()?.let { vSettings.useNonDecoratedWindow = it }
		vProps.getProperty("splitRatio")?.toFloatOrNull()?.let { inState.splitRatio = it.coerceIn(0.15f, 0.85f) }

		for (vAction in ShortcutAction.entries) {
			vProps.getProperty("key.${vAction.name}")?.let { vEnc -> decodeShortcut(vEnc)?.let { inState.keymap[vAction] = it } }
		}

		val vTabPaths =
			(vProps.getProperty("openTabs") ?: "")
				.split('\n')
				.map { it.trim() }
				.filter { it.isNotEmpty() }
		val vActive = vProps.getProperty("activeTab")?.toIntOrNull() ?: 0
		return SessionSnapshot(vTabPaths, vActive)
	}

	// Writes the current preferences and the open-tab session to disk, swallowing any IO failure.
	fun save(inState: AppState) {
		val vProps = Properties()
		val vSettings = inState.settings
		vProps.setProperty("isDark", inState.isDark.toString())
		vProps.setProperty("gradient", vSettings.gradient.name)
		vProps.setProperty("editorFont", vSettings.editorFont.name)
		vProps.setProperty("paneCorner", vSettings.paneCornerDp.toString())
		vProps.setProperty("contentGap", vSettings.contentGapDp.toString())
		vProps.setProperty("editorFontSize", vSettings.editorFontSizeSp.toString())
		vProps.setProperty("editorWordWrap", vSettings.editorWordWrap.toString())
		vProps.setProperty("showStatusBar", vSettings.showStatusBar.toString())
		vProps.setProperty("showMemoryUsage", vSettings.showMemoryUsage.toString())
		vProps.setProperty("restoreSession", vSettings.restoreSession.toString())
		vProps.setProperty("exitOnLastTabClose", vSettings.exitOnLastTabClose.toString())
		vProps.setProperty("useNonDecoratedWindow", vSettings.useNonDecoratedWindow.toString())
		vProps.setProperty("splitRatio", inState.splitRatio.toString())
		for ((vAction, vShortcut) in inState.keymap) {
			vProps.setProperty("key.${vAction.name}", vShortcut.encode())
		}

		// Only file-backed tabs are saved: the unsaved scratch buffers have no stable identity
		// to restore from, so we drop them rather than guess.
		val vTabPaths = inState.documents.mapNotNull { it.file?.absolutePath }
		vProps.setProperty("openTabs", vTabPaths.joinToString("\n"))
		val vActive = inState.documents.getOrNull(inState.activeIndex)?.file?.absolutePath
		val vActiveIdx = if (vActive == null) 0 else vTabPaths.indexOf(vActive).coerceAtLeast(0)
		vProps.setProperty("activeTab", vActiveIdx.toString())

		runCatching {
			kFile.parentFile?.mkdirs()
			kFile.outputStream().use { vProps.store(it, "JewelMarkdown preferences") }
		}
	}
}

// Snapshot of the last session: file paths in tab order and the active tab index.
data class SessionSnapshot(val paths: List<String>, val activeIndex: Int)
