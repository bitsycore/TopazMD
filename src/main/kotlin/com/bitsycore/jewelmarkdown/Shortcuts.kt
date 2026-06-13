package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

// Editor actions that can be bound to keyboard shortcuts.
enum class ShortcutAction(val displayName: String) {
	NewFile("New file"),
	OpenFile("Open file"),
	OpenFolder("Open folder"),
	Save("Save"),
	SaveAs("Save as"),
	CloseTab("Close tab"),
	Bold("Bold"),
	Italic("Italic"),
	InlineCode("Inline code"),
	Heading("Heading"),
	Quote("Quote"),
	BulletList("Bullet list"),
	Link("Link"),
	ToggleProjectPanel("Toggle project files"),
	ViewEditor("Editor view"),
	ViewSplit("Split view"),
	ViewPreview("Preview view"),
	OpenSettings("Open settings"),
}

// A keyboard shortcut: a key plus modifier flags.
data class Shortcut(val key: Key, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
	// True when the given key event matches this shortcut exactly.
	fun matches(inEvent: KeyEvent): Boolean =
		inEvent.key == key &&
			inEvent.isCtrlPressed == ctrl &&
			inEvent.isShiftPressed == shift &&
			inEvent.isAltPressed == alt

	// Human-readable label, e.g. "Ctrl+Shift+S".
	fun label(): String {
		val vParts = buildList {
			if (ctrl) add("Ctrl")
			if (shift) add("Shift")
			if (alt) add("Alt")
			add(keyDisplayName(key))
		}
		return vParts.joinToString("+")
	}
}

// A short display name for a key (strips the "Key: " prefix Compose's toString adds).
private fun keyDisplayName(inKey: Key): String =
	when (inKey) {
		Key.Grave -> "`"
		Key.One -> "1"
		Key.Two -> "2"
		Key.Three -> "3"
		else -> inKey.toString().removePrefix("Key: ")
	}

// True for the bare modifier keys, which should not become a binding on their own.
fun isModifierKey(inKey: Key): Boolean =
	inKey == Key.CtrlLeft ||
		inKey == Key.CtrlRight ||
		inKey == Key.ShiftLeft ||
		inKey == Key.ShiftRight ||
		inKey == Key.AltLeft ||
		inKey == Key.AltRight ||
		inKey == Key.MetaLeft ||
		inKey == Key.MetaRight

// Builds a shortcut from a key event's key and currently-held modifiers.
fun shortcutFromEvent(inEvent: KeyEvent): Shortcut =
	Shortcut(inEvent.key, ctrl = inEvent.isCtrlPressed, shift = inEvent.isShiftPressed, alt = inEvent.isAltPressed)

// Encodes a shortcut to a string for persistence ("ctrl,shift,alt,keyCode").
fun Shortcut.encode(): String = "$ctrl,$shift,$alt,${key.keyCode}"

// Decodes a shortcut from its persisted string, or null if malformed.
fun decodeShortcut(inText: String): Shortcut? {
	val vParts = inText.split(",")
	if (vParts.size != 4) return null
	val vCtrl = vParts[0].toBooleanStrictOrNull() ?: return null
	val vShift = vParts[1].toBooleanStrictOrNull() ?: return null
	val vAlt = vParts[2].toBooleanStrictOrNull() ?: return null
	val vCode = vParts[3].toLongOrNull() ?: return null
	return Shortcut(Key(vCode), ctrl = vCtrl, shift = vShift, alt = vAlt)
}

// The default Visual-Studio-style keymap.
fun defaultKeymap(): SnapshotStateMap<ShortcutAction, Shortcut> {
	val vMap = mutableStateMapOf<ShortcutAction, Shortcut>()
	vMap[ShortcutAction.NewFile] = Shortcut(Key.N, ctrl = true)
	vMap[ShortcutAction.OpenFile] = Shortcut(Key.O, ctrl = true)
	vMap[ShortcutAction.OpenFolder] = Shortcut(Key.O, ctrl = true, shift = true)
	vMap[ShortcutAction.Save] = Shortcut(Key.S, ctrl = true)
	vMap[ShortcutAction.SaveAs] = Shortcut(Key.S, ctrl = true, shift = true)
	vMap[ShortcutAction.CloseTab] = Shortcut(Key.W, ctrl = true)
	vMap[ShortcutAction.Bold] = Shortcut(Key.B, ctrl = true)
	vMap[ShortcutAction.Italic] = Shortcut(Key.I, ctrl = true)
	vMap[ShortcutAction.InlineCode] = Shortcut(Key.Grave, ctrl = true)
	vMap[ShortcutAction.Heading] = Shortcut(Key.H, ctrl = true, shift = true)
	vMap[ShortcutAction.Quote] = Shortcut(Key.Q, ctrl = true, shift = true)
	vMap[ShortcutAction.BulletList] = Shortcut(Key.L, ctrl = true, shift = true)
	vMap[ShortcutAction.Link] = Shortcut(Key.K, ctrl = true)
	vMap[ShortcutAction.ToggleProjectPanel] = Shortcut(Key.E, ctrl = true, shift = true)
	vMap[ShortcutAction.ViewEditor] = Shortcut(Key.One, ctrl = true)
	vMap[ShortcutAction.ViewSplit] = Shortcut(Key.Two, ctrl = true)
	vMap[ShortcutAction.ViewPreview] = Shortcut(Key.Three, ctrl = true)
	vMap[ShortcutAction.OpenSettings] = Shortcut(Key.S, ctrl = true, alt = true)
	return vMap
}
