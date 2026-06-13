package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// A named background-gradient preset. Each supplies the ambient gradient stops and a matching
// title-bar accent for both the dark and light themes.
enum class GradientPreset(
	val displayName: String,
	private val darkStops: List<Color>,
	private val lightStops: List<Color>,
	private val darkAccent: Color,
	private val lightAccent: Color,
) {
	Mica(
		"Mica",
		listOf(Color(0xFF2A2C34), Color(0xFF202126), Color(0xFF24202B)),
		listOf(Color(0xFFEDF1F8), Color(0xFFF5F6F9), Color(0xFFF0ECF7)),
		Color(0xFF34304A),
		Color(0xFFDDE3F2),
	),
	Indigo(
		"Indigo",
		listOf(Color(0xFF252A40), Color(0xFF1E2030), Color(0xFF2A2440)),
		listOf(Color(0xFFE7ECFB), Color(0xFFF2F4FC), Color(0xFFEDE9FB)),
		Color(0xFF3B3D6B),
		Color(0xFFD3DAF6),
	),
	Slate(
		"Slate",
		listOf(Color(0xFF2B2F36), Color(0xFF23262C), Color(0xFF272B31)),
		listOf(Color(0xFFEDEFF2), Color(0xFFF4F5F7), Color(0xFFEFF1F4)),
		Color(0xFF3A4048),
		Color(0xFFDCE0E6),
	),
	Aurora(
		"Aurora",
		listOf(Color(0xFF1E2A2E), Color(0xFF1D2430), Color(0xFF26203A)),
		listOf(Color(0xFFE6F3F0), Color(0xFFEFF3F8), Color(0xFFF1ECF8)),
		Color(0xFF2C4A4A),
		Color(0xFFD2EAE4),
	),
	Graphite(
		"Graphite",
		listOf(Color(0xFF2A2A2A), Color(0xFF1E1E1E), Color(0xFF242424)),
		listOf(Color(0xFFF0F0F0), Color(0xFFF6F6F6), Color(0xFFF2F2F2)),
		Color(0xFF3A3A3A),
		Color(0xFFDFDFDF),
	),
	;

	// Diagonal linear gradient for the current theme.
	fun brush(inIsDark: Boolean): Brush {
		val vStops = if (inIsDark) darkStops else lightStops
		return Brush.linearGradient(0.0f to vStops[0], 0.45f to vStops[1], 1.0f to vStops[2])
	}

	// Title-bar accent color for the current theme.
	fun titleAccent(inIsDark: Boolean): Color = if (inIsDark) darkAccent else lightAccent
}

// Editor font family choices.
enum class EditorFont(val displayName: String, val family: FontFamily) {
	Monospace("Monospace", FontFamily.Monospace),
	SansSerif("Sans-serif", FontFamily.SansSerif),
	Serif("Serif", FontFamily.Serif),
}

// All user-configurable UI settings. Properties are observable so edits apply live.
@Stable
class Settings {
	// Background gradient preset.
	var gradient by mutableStateOf(GradientPreset.Mica)

	// Corner radius of the editor/preview panels, in dp.
	var paneCornerDp by mutableStateOf(8f)

	// Spacing around and between the panels, in dp.
	var contentGapDp by mutableStateOf(12f)

	// Editor font family and size.
	var editorFont by mutableStateOf(EditorFont.Monospace)
	var editorFontSizeSp by mutableStateOf(13f)

	// Whether the bottom status bar is shown.
	var showStatusBar by mutableStateOf(true)

	// Restores every setting to its default value.
	fun reset() {
		gradient = GradientPreset.Mica
		paneCornerDp = 8f
		contentGapDp = 12f
		editorFont = EditorFont.Monospace
		editorFontSizeSp = 13f
		showStatusBar = true
	}
}
