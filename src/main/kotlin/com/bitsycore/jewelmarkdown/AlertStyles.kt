package com.bitsycore.jewelmarkdown

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.alerts.light
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.CautionAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.ImportantAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.NoteAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.TipAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.WarningAlertStyling

// Builds GitHub-alert styling with readable body text and without icon keys (the IntelliJ
// alert icons are not bundled in the standalone distribution). Each alert keeps its colored
// accent bar and bold colored title.
fun buildAlertStyling(inIsDark: Boolean, inTextColor: Color): AlertStyling =
	if (inIsDark) {
		AlertStyling.dark(
			note = NoteAlertStyling.dark(titleIconKey = null, textColor = inTextColor),
			tip = TipAlertStyling.dark(titleIconKey = null, textColor = inTextColor),
			important = ImportantAlertStyling.dark(titleIconKey = null, textColor = inTextColor),
			warning = WarningAlertStyling.dark(titleIconKey = null, textColor = inTextColor),
			caution = CautionAlertStyling.dark(titleIconKey = null, textColor = inTextColor),
		)
	} else {
		AlertStyling.light(
			note = NoteAlertStyling.light(titleIconKey = null, textColor = inTextColor),
			tip = TipAlertStyling.light(titleIconKey = null, textColor = inTextColor),
			important = ImportantAlertStyling.light(titleIconKey = null, textColor = inTextColor),
			warning = WarningAlertStyling.light(titleIconKey = null, textColor = inTextColor),
			caution = CautionAlertStyling.light(titleIconKey = null, textColor = inTextColor),
		)
	}
