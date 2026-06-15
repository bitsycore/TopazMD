package com.bitsycore.jewelmarkdown

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
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
	DuplicateLine("Duplicate line"),
	DeleteLine("Delete line"),
	MoveLineUp("Move line up"),
	MoveLineDown("Move line down"),
	SelectLine("Select line"),
	ToggleProjectPanel("Toggle project files"),
	ViewEditor("Editor view"),
	ViewSplit("Split view"),
	ViewPreview("Preview view"),
	OpenSettings("Open settings"),
}

// A keyboard shortcut: a key plus modifier flags. `meta` is the Cmd key on macOS and the
// Windows key on Windows; on macOS it's our "primary" modifier (Cmd+S, Cmd+D…) so it can be
// captured and matched independently of Ctrl.
data class Shortcut(
	val key: Key,
	val ctrl: Boolean = false,
	val shift: Boolean = false,
	val alt: Boolean = false,
	val meta: Boolean = false,
) {
	// True when the given key event matches this shortcut exactly.
	fun matches(inEvent: KeyEvent): Boolean =
		inEvent.key == key &&
			inEvent.isCtrlPressed == ctrl &&
			inEvent.isShiftPressed == shift &&
			inEvent.isAltPressed == alt &&
			inEvent.isMetaPressed == meta

	// Human-readable label. On macOS we use the native modifier symbols with no "+" separator
	// (e.g. "⇧⌘D"); on other platforms we keep the Windows/Linux convention of "Ctrl+Shift+D".
	fun label(): String {
		if (kIsMac) {
			val vBuilder = StringBuilder()
			if (ctrl) vBuilder.append('⌃')
			if (alt) vBuilder.append('⌥')
			if (shift) vBuilder.append('⇧')
			if (meta) vBuilder.append('⌘')
			vBuilder.append(keyDisplayName(key))
			return vBuilder.toString()
		}
		val vParts = buildList {
			if (ctrl) add("Ctrl")
			if (shift) add("Shift")
			if (alt) add("Alt")
			if (meta) add("Win")
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
		Key.DirectionUp -> "↑"
		Key.DirectionDown -> "↓"
		Key.Comma -> ","
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
	Shortcut(
		inEvent.key,
		ctrl = inEvent.isCtrlPressed,
		shift = inEvent.isShiftPressed,
		alt = inEvent.isAltPressed,
		meta = inEvent.isMetaPressed,
	)

// Encodes a shortcut to a string for persistence ("ctrl,shift,alt,meta,keyCode"). The 5-part
// format is the current schema; decodeShortcut also accepts the legacy 4-part format from
// before macOS Cmd support was added.
fun Shortcut.encode(): String = "$ctrl,$shift,$alt,$meta,${key.keyCode}"

fun decodeShortcut(inText: String): Shortcut? {
	val vParts = inText.split(",")
	return when (vParts.size) {
		5 -> {
			val vCtrl = vParts[0].toBooleanStrictOrNull() ?: return null
			val vShift = vParts[1].toBooleanStrictOrNull() ?: return null
			val vAlt = vParts[2].toBooleanStrictOrNull() ?: return null
			val vMeta = vParts[3].toBooleanStrictOrNull() ?: return null
			val vCode = vParts[4].toLongOrNull() ?: return null
			Shortcut(Key(vCode), ctrl = vCtrl, shift = vShift, alt = vAlt, meta = vMeta)
		}
		4 -> {
			// Legacy schema before the meta field existed; default meta = false.
			val vCtrl = vParts[0].toBooleanStrictOrNull() ?: return null
			val vShift = vParts[1].toBooleanStrictOrNull() ?: return null
			val vAlt = vParts[2].toBooleanStrictOrNull() ?: return null
			val vCode = vParts[3].toLongOrNull() ?: return null
			Shortcut(Key(vCode), ctrl = vCtrl, shift = vShift, alt = vAlt)
		}
		else -> null
	}
}

// Builds a "primary modifier" shortcut: Cmd on macOS, Ctrl elsewhere. Use this for shortcuts
// that should follow the native platform convention (file ops, edit ops, view switches, etc.).
private fun primary(inKey: Key, inShift: Boolean = false, inAlt: Boolean = false): Shortcut =
	if (kIsMac) {
		Shortcut(inKey, meta = true, shift = inShift, alt = inAlt)
	} else {
		Shortcut(inKey, ctrl = true, shift = inShift, alt = inAlt)
	}

// The default keymap. Editor shortcuts use the platform's primary modifier (Cmd on macOS,
// Ctrl elsewhere). Move-line stays on Alt+Shift+arrows on both platforms — that's the IntelliJ
// + VSCode convention everywhere.
fun defaultKeymap(): SnapshotStateMap<ShortcutAction, Shortcut> {
	val vMap = mutableStateMapOf<ShortcutAction, Shortcut>()
	vMap[ShortcutAction.NewFile] = primary(Key.N)
	vMap[ShortcutAction.OpenFile] = primary(Key.O)
	vMap[ShortcutAction.OpenFolder] = primary(Key.O, inShift = true)
	vMap[ShortcutAction.Save] = primary(Key.S)
	vMap[ShortcutAction.SaveAs] = primary(Key.S, inShift = true)
	vMap[ShortcutAction.CloseTab] = primary(Key.W)
	vMap[ShortcutAction.Bold] = primary(Key.B)
	vMap[ShortcutAction.Italic] = primary(Key.I)
	vMap[ShortcutAction.InlineCode] = primary(Key.Grave)
	vMap[ShortcutAction.Heading] = primary(Key.H, inShift = true)
	vMap[ShortcutAction.Quote] = primary(Key.Q, inShift = true)
	vMap[ShortcutAction.BulletList] = primary(Key.L, inShift = true)
	vMap[ShortcutAction.Link] = primary(Key.K)
	vMap[ShortcutAction.DuplicateLine] = primary(Key.D)
	vMap[ShortcutAction.DeleteLine] = primary(Key.K, inShift = true)
	vMap[ShortcutAction.MoveLineUp] = Shortcut(Key.DirectionUp, alt = true, shift = true)
	vMap[ShortcutAction.MoveLineDown] = Shortcut(Key.DirectionDown, alt = true, shift = true)
	vMap[ShortcutAction.SelectLine] = primary(Key.L)
	vMap[ShortcutAction.ToggleProjectPanel] = primary(Key.E, inShift = true)
	vMap[ShortcutAction.ViewEditor] = primary(Key.One)
	vMap[ShortcutAction.ViewSplit] = primary(Key.Two)
	vMap[ShortcutAction.ViewPreview] = primary(Key.Three)
	vMap[ShortcutAction.OpenSettings] =
		if (kIsMac) Shortcut(Key.Comma, meta = true) else Shortcut(Key.S, ctrl = true, alt = true)
	return vMap
}
