package com.bitsycore.jewelmarkdown

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

// Native "open file" dialog. Returns the selected file, or null if cancelled.
fun chooseOpenFile(): File? {
	val vDialog = FileDialog(null as Frame?, "Open Markdown", FileDialog.LOAD)
	vDialog.isVisible = true
	val vDir = vDialog.directory ?: return null
	val vName = vDialog.file ?: return null
	return File(vDir, vName)
}

// Native "save file" dialog seeded with a suggested name. Returns the target, or null.
fun chooseSaveFile(inSuggestedName: String): File? {
	val vDialog = FileDialog(null as Frame?, "Save Markdown", FileDialog.SAVE)
	vDialog.file = inSuggestedName
	vDialog.isVisible = true
	val vDir = vDialog.directory ?: return null
	val vName = vDialog.file ?: return null
	return File(vDir, vName)
}

// Native "choose folder" dialog (Swing JFileChooser, since AWT FileDialog cannot pick
// directories on Windows). Returns the chosen folder, or null if cancelled.
fun chooseFolder(): File? {
	val vChooser = JFileChooser()
	vChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
	vChooser.dialogTitle = "Open Folder"
	return if (vChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile else null
}

// Opens a URL in the user's default browser, swallowing any failure.
fun openUrl(inUrl: String) {
	runCatching {
		if (Desktop.isDesktopSupported()) {
			Desktop.getDesktop().browse(URI(inUrl))
		}
	}
}
