package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

// The three layout modes offered by the toolbar's view switch.
enum class ViewMode { Editor, Split, Preview }

// A single open document: its editor buffer (text + caret), backing file and saved baseline.
@Stable
class Document(inText: String, inFile: File?) {
	// Editor buffer with selection/caret; bound to the editor's TextArea.
	var fieldValue by mutableStateOf(TextFieldValue(inText, TextRange(inText.length)))

	// Backing file of the document, or null for an unsaved scratch buffer.
	var file by mutableStateOf(inFile)
		private set

	// Text as last opened/saved; compared against the buffer to detect edits.
	var savedText by mutableStateOf(inText)
		private set

	val text: String get() = fieldValue.text
	val isDirty: Boolean get() = text != savedText
	val title: String get() = file?.name ?: "Untitled"

	// Records the current text as saved and binds the document to a file.
	fun markSaved(inFile: File) {
		file = inFile
		savedText = text
	}
}

// Top-level UI state: the set of open documents, the active one, theme, layout, settings and
// the project (folder) panel state.
@Stable
class AppState(inIsDark: Boolean) {
	// Open documents shown as tabs; kept non-empty.
	val documents = mutableStateListOf<Document>()
	var activeIndex by mutableStateOf(0)

	var isDark by mutableStateOf(inIsDark)
	var viewMode by mutableStateOf(ViewMode.Split)

	// Ctrl held (tracked from the window key handler); preview links open only on Ctrl+Click.
	var isCtrlDown by mutableStateOf(false)

	// User-configurable UI settings.
	val settings = Settings()
	var showSettings by mutableStateOf(false)

	// Keyboard shortcuts (action -> key combo) and the action currently being rebound.
	val keymap = defaultKeymap()
	var recordingAction by mutableStateOf<ShortcutAction?>(null)

	// Project (folder) panel.
	var projectRoot by mutableStateOf<File?>(null)
	var showProjectPanel by mutableStateOf(false)

	// The currently focused document (documents is never empty, so this is non-null).
	val active: Document get() = documents[activeIndex.coerceIn(0, documents.lastIndex)]

	// Opens a new empty scratch document and focuses it.
	fun newDocument() {
		documents.add(Document("", null))
		activeIndex = documents.lastIndex
	}

	// Opens a file in a tab, focusing an existing tab if the file is already open.
	fun openFile(inFile: File) {
		val vExisting = documents.indexOfFirst { it.file?.absolutePath == inFile.absolutePath }
		if (vExisting >= 0) {
			activeIndex = vExisting
			return
		}
		val vContent = runCatching { inFile.readText() }.getOrNull() ?: return
		documents.add(Document(vContent, inFile))
		activeIndex = documents.lastIndex
	}

	// Closes a tab, keeping at least one document open.
	fun closeDocument(inIndex: Int) {
		if (inIndex !in documents.indices) return
		documents.removeAt(inIndex)
		if (documents.isEmpty()) documents.add(Document(kSampleMarkdown, null))
		activeIndex = activeIndex.coerceIn(0, documents.lastIndex)
	}

	// Closes every tab except the one at inIndex.
	fun closeOthers(inIndex: Int) {
		val vKeep = documents.getOrNull(inIndex) ?: return
		documents.clear()
		documents.add(vKeep)
		activeIndex = 0
	}

	// Closes all tabs, leaving a single empty scratch document.
	fun closeAll() {
		documents.clear()
		documents.add(Document("", null))
		activeIndex = 0
	}

	// Moves the tab at inFrom to inTo, used for drag-to-reorder.
	fun moveDocument(inFrom: Int, inTo: Int) {
		if (inFrom !in documents.indices || inTo !in documents.indices || inFrom == inTo) return
		val vActive = active
		documents.add(inTo, documents.removeAt(inFrom))
		activeIndex = documents.indexOf(vActive).coerceAtLeast(0)
	}

	// Restores the keyboard shortcuts to the default Visual-Studio-style layout.
	fun resetKeymap() {
		keymap.clear()
		keymap.putAll(defaultKeymap())
	}

	// Window/title-bar caption for the active document; a dot marks unsaved changes.
	fun windowTitle(): String {
		val vDoc = active
		val vDirtyMark = if (vDoc.isDirty) "● " else ""
		return "$vDirtyMark${vDoc.title}  —  Jewel Markdown"
	}
}

// Creates and remembers the app state seeded with one sample document, dark theme on.
@Composable
fun rememberAppState(): AppState =
	remember {
		AppState(inIsDark = true).apply { documents.add(Document(kSampleMarkdown, null)) }
	}
