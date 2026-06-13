# Jewel Markdown

A **Compose for Desktop** Markdown editor and live visualizer, built with the JetBrains
**Jewel** UI toolkit (the IntelliJ look-and-feel) and a custom **decorated window**.
Dark theme by default.

## Features

- **Decorated window** with a custom Jewel title bar that hosts the **File / Edit / View
  menus** (hover to switch between open menus), a centered document name, the view-mode
  icons, settings and a light/dark toggle.
- **Multiple documents as tabs**: open many files, reorder by dragging, right-click for
  Close / Close Others / Close All; **drag files onto the window** to open them.
- **Project panel**: an activity-bar icon toggles a file tree of an opened folder; click a
  Markdown/text file to open it in a tab.
- **Split view** with a **draggable divider**; switch Editor / Split / Preview.
- **Live preview** via Jewel's Markdown renderer (matches the IntelliJ theme) with
  GitHub-flavored Markdown — **tables, alerts, ~~strikethrough~~, autolinks** — plus
  headings/lists/code/quotes/links. **Syntax-highlighted code blocks**; Ctrl+Click opens links.
- **Editor** with live Markdown **syntax highlighting** and a formatting toolbar (bold,
  italic, code, headings, lists, quotes, links) with explanatory tooltips.
- **Keyboard shortcuts** with a Visual-Studio-style default layout, **rebindable** in
  Settings (shown next to each menu item).
- **Settings** dialog (categories: Appearance / Editor / Shortcuts / About): theme,
  **background gradient presets**, panel corners/spacing, **editor font** & size, status
  bar, and **Reset to defaults**. Preferences and the keymap **persist across runs**.
- **Dark theme by default** with a subtle Windows-11-Mica-style ambient gradient.
- Status bar with caret position, dirty state and document metrics.

## Requirements

This app uses Jewel's `DecoratedWindow`, which **only works on the JetBrains Runtime
(JBR)** — on a stock JDK it throws at startup. You don't need to install JBR yourself:
the build declares a `vendor = JETBRAINS` Java toolchain and the **foojay** resolver
downloads JBR 21 automatically on first build. Both compilation and `gradlew run` use it
(`run` is pinned to the JBR via `compose.desktop.application.javaHome`).

## Run

```bash
./gradlew run
```

## Package a native installer

```bash
./gradlew packageDistributionForCurrentOS   # .msi on Windows, .dmg on macOS, .deb on Linux
```

## Tech stack

| Component        | Version                  |
|------------------|--------------------------|
| Kotlin           | 2.2.0                    |
| Compose Desktop  | 1.10.3                   |
| Jewel            | 0.34.0-253.32098.101     |
| Gradle           | 9.4.1                    |
| Runtime          | JetBrains Runtime 21     |

## Project structure

```
src/main/kotlin/com/bitsycore/jewelmarkdown/
  Main.kt             application(), Jewel theme, DecoratedWindow + custom TitleBar
  AppContent.kt       toolbar, view-mode switch, editor/preview split, status bar
  MarkdownPreview.kt  ProvideMarkdownStyling + LazyMarkdown with GFM, off-thread parsing
  AppState.kt         document state (TextFieldState), dirty tracking, theme + view mode
  FileOps.kt          native open/save dialogs, open-URL helper
  SampleContent.kt    default showcase document
```

See `PROGRESS.md` for the design decisions and how the exact Jewel API/coordinates were
verified.
