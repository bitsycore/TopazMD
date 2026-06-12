package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

// The three layout modes offered by the toolbar's view switch.
enum class ViewMode { Editor, Split, Preview }

// Holds the mutable UI state of the editor window: the document buffer (text + caret),
// its backing file, the active theme and the chosen layout. Reading these inside
// composition drives recomposition (live preview, dirty indicator, theme switch).
// A TextFieldValue (not TextFieldState) is used so the editor can apply a
// syntax-highlighting VisualTransformation.
@Stable
class AppState(inInitialText: String, inIsDark: Boolean) {
	// Editor buffer with selection/caret; bound to the editor's TextArea.
	var fieldValue by mutableStateOf(TextFieldValue(inInitialText, TextRange(inInitialText.length)))

	// Backing file of the current document, or null for an unsaved buffer.
	var currentFile by mutableStateOf<File?>(null)

	// Dark theme is the default; toggled from the title bar.
	var isDark by mutableStateOf(inIsDark)

	// Editor-only, side-by-side or preview-only layout.
	var viewMode by mutableStateOf(ViewMode.Split)

	// Whether Ctrl is currently held (tracked from the window key handler); used so preview
	// links open only on Ctrl+Click.
	var isCtrlDown by mutableStateOf(false)

	// Text as last opened/saved; compared against the buffer to detect edits.
	var savedText by mutableStateOf(inInitialText)
		private set

	// Current buffer contents as a plain String.
	val text: String get() = fieldValue.text

	// True when the buffer differs from the last opened/saved content.
	val isDirty: Boolean get() = text != savedText

	// Replaces the whole buffer (New/Open), places the caret at the end and resets baseline.
	fun loadText(inNewText: String, inFile: File?) {
		fieldValue = TextFieldValue(inNewText, TextRange(inNewText.length))
		currentFile = inFile
		savedText = inNewText
	}

	// Records the current text as the saved baseline after a successful write.
	fun markSaved(inFile: File) {
		currentFile = inFile
		savedText = text
	}

	// Window/title-bar caption: a dot marks unsaved changes.
	fun windowTitle(): String {
		val vName = currentFile?.name ?: "Untitled"
		val vDirtyMark = if (isDirty) "● " else ""
		return "$vDirtyMark$vName  —  Jewel Markdown"
	}
}

// Creates and remembers an AppState seeded with the sample document, dark theme on.
@Composable
fun rememberAppState(): AppState = remember { AppState(kSampleMarkdown, inIsDark = true) }
