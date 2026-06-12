package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import java.io.File

// File extensions shown in the project tree (text/Markdown documents).
private val kTextExtensions = setOf("md", "markdown", "mdx", "txt")

// ==================
// MARK: Activity bar
// ==================

// Thin far-left strip of tool-window toggles (IntelliJ-style). Currently a single button to
// show/hide the project files panel.
@Composable
internal fun ActivityBar(inState: AppState) {
	Column(
		modifier = Modifier.fillMaxHeight().width(44.dp).padding(vertical = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		ActivityButton(
			inSelected = inState.showProjectPanel,
			inTooltip = "Project files",
			inOnClick = { inState.showProjectPanel = !inState.showProjectPanel },
		) { vTint -> FolderGlyph(vTint, Modifier.size(20.dp)) }
	}
}

// A square, highlight-on-select activity-bar button hosting a drawn icon.
@Composable
private fun ActivityButton(
	inSelected: Boolean,
	inTooltip: String,
	inOnClick: () -> Unit,
	inIcon: @Composable (Color) -> Unit,
) {
	val vTint = if (inSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info
	Tooltip(tooltip = { Text(inTooltip) }) {
		Box(
			modifier =
				Modifier
					.size(32.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(if (inSelected) JewelTheme.globalColors.text.info.copy(alpha = 0.15f) else Color.Transparent)
					.clickable(onClick = inOnClick),
			contentAlignment = Alignment.Center,
		) {
			inIcon(vTint)
		}
	}
}

// A minimal folder icon drawn as a stroked path (avoids needing icon resources).
@Composable
private fun FolderGlyph(inTint: Color, inModifier: Modifier) {
	Canvas(inModifier) {
		val vW = size.width
		val vH = size.height
		val vPath =
			Path().apply {
				moveTo(vW * 0.10f, vH * 0.30f)
				lineTo(vW * 0.42f, vH * 0.30f)
				lineTo(vW * 0.50f, vH * 0.42f)
				lineTo(vW * 0.90f, vH * 0.42f)
				lineTo(vW * 0.90f, vH * 0.78f)
				lineTo(vW * 0.10f, vH * 0.78f)
				close()
			}
		drawPath(vPath, color = inTint, style = Stroke(width = size.minDimension * 0.09f))
	}
}

// ==================
// MARK: Project panel
// ==================

// Collapsible left panel showing the opened folder's Markdown/text files as a tree.
@Composable
internal fun ProjectPanel(inState: AppState, inModifier: Modifier) {
	val vRoot = inState.projectRoot
	val vBorder = JewelTheme.globalColors.borders.normal
	Column(inModifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.35f))) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				(vRoot?.name ?: "No folder").uppercase(),
				color = JewelTheme.globalColors.text.info,
				fontSize = 11.sp,
				letterSpacing = 0.5.sp,
			)
			Spacer(Modifier.weight(1f))
			OutlinedButton(onClick = { chooseFolder()?.let { inState.projectRoot = it } }) { Text("Open…") }
		}
		Divider(Orientation.Horizontal, Modifier.fillMaxWidth(), color = vBorder)

		if (vRoot == null) {
			Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
				OutlinedButton(onClick = { chooseFolder()?.let { inState.projectRoot = it } }) { Text("Open Folder") }
			}
		} else {
			val vExpanded = remember(vRoot.absolutePath) { mutableStateMapOf<String, Boolean>() }
			VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
				Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
					for (vChild in childrenOf(vRoot)) {
						FileTreeNode(vChild, 0, vExpanded, inState)
					}
				}
			}
		}
	}
}

// One tree node: a directory (expandable) or a document file (opens in a tab when clicked).
@Composable
private fun FileTreeNode(inFile: File, inDepth: Int, inExpanded: SnapshotStateMap<String, Boolean>, inState: AppState) {
	if (inFile.isDirectory) {
		val vKey = inFile.absolutePath
		val vIsOpen = inExpanded[vKey] == true
		TreeRow(inDepth, inFile.name, inIsDir = true, inIsOpen = vIsOpen, inIsActive = false) {
			inExpanded[vKey] = !vIsOpen
		}
		if (vIsOpen) {
			for (vChild in childrenOf(inFile)) {
				FileTreeNode(vChild, inDepth + 1, inExpanded, inState)
			}
		}
	} else {
		val vIsActive = inState.active.file?.absolutePath == inFile.absolutePath
		TreeRow(inDepth, inFile.name, inIsDir = false, inIsOpen = false, inIsActive = vIsActive) {
			inState.openFile(inFile)
		}
	}
}

// A single indented, clickable tree row with a disclosure triangle for directories.
@Composable
private fun TreeRow(
	inDepth: Int,
	inLabel: String,
	inIsDir: Boolean,
	inIsOpen: Boolean,
	inIsActive: Boolean,
	inOnClick: () -> Unit,
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable(onClick = inOnClick)
				.padding(start = (8 + inDepth * 14).dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Text(
			if (inIsDir) (if (inIsOpen) "▾" else "▸") else "  ",
			color = JewelTheme.globalColors.text.info,
			fontSize = 11.sp,
		)
		Text(
			inLabel,
			fontSize = 13.sp,
			color = if (inIsDir || inIsActive) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info,
		)
	}
}

// Directory children to show: sub-directories and Markdown/text files, dirs first, sorted by
// name, hidden entries excluded.
private fun childrenOf(inDir: File): List<File> =
	(inDir.listFiles()?.asList().orEmpty())
		.filter { !it.isHidden && (it.isDirectory || it.extension.lowercase() in kTextExtensions) }
		.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
