# JewelMarkdown — Progress & Decisions

A Compose for Desktop Markdown **editor + live visualizer**, built with the JetBrains
**Jewel** UI toolkit (IntelliJ look-and-feel) and a custom **decorated window**. Dark theme
by default, modern style.

This file is the running log of decisions and progress. It is updated as the build proceeds.

## Goal

- Compose Desktop (JVM-only) application.
- JetBrains Jewel standalone UI (not inside an IntelliJ plugin).
- `DecoratedWindow` + custom `TitleBar`.
- Split view: raw Markdown editor on the left, rendered preview on the right.
- Rendering done with Jewel's own Markdown module (consistent IntelliJ styling).
- **Dark theme as the default**, modern styling, with a light/dark toggle.

## Verified environment

| Thing | Value | Source |
|-------|-------|--------|
| JDK (default) | OpenJDK 25.0.2, `JAVA_HOME` set | `java -version` |
| JDK (alt) | Temurin 21.0.10 (scoop) | `~/scoop/apps/openjdk21` |
| JBR | `jbrsdk_jcef-21` present | `~/.gradle/jdks` |
| Gradle dists cached | 9.4.0, 9.4.1 | `~/.gradle/wrapper/dists` |
| Compose MP plugin cached | **1.10.3** | `~/.gradle/caches/.../compose-gradle-plugin` |
| Maven Central | reachable | ping repo1.maven.org |

## Decisions (resolved)

1. **Compose Multiplatform 1.10.3** — already cached locally; resolves reliably.
2. **Kotlin 2.2.0** + Compose compiler plugin `org.jetbrains.kotlin.plugin.compose:2.2.0`.
   Verified from Jewel 0.34.0 bytecode (`@Metadata mv=[2,2,0]`, class major 65 → JDK 21).
3. **Gradle 9.4.1** via wrapper — distribution already cached.
4. **Jewel `0.34.0-253.32098.101`** — pinned across *all* `org.jetbrains.jewel:*` modules.
   - Verified against Maven Central `maven-metadata.xml` (the version exists verbatim).
   - This build targets **stable Compose 1.10.0** (per its `jewel-foundation` POM), which
     is binary-compatible with our Compose plugin 1.10.3 (same 1.10.x minor).
   - **Artifact-naming correction:** there is *no* `-253` suffix in the artifact *name*;
     the IJP build is encoded in the *version* (`<jewel>-<ijpBuild>`). `jewel-int-ui-standalone-253`
     etc. return 404. Coordinates used are the plain names at the pinned version.
   - Modules: `jewel-int-ui-standalone`, `jewel-int-ui-decorated-window`,
     `jewel-markdown-core`, `jewel-markdown-int-ui-standalone-styling`, and
     `jewel-markdown-extensions-{gfm-tables,gfm-alerts,gfm-strikethrough,autolink}`.
5. **JBR is mandatory (not best-effort).** `DecoratedWindow` calls
   `error("DecoratedWindow can only be used on JetBrainsRuntime(JBR) platform")` when
   `!JBR.isAvailable()` — it does **not** degrade. So the app must run on JBR.
   - **Provisioned portably via a Java toolchain** with `vendor = JvmVendorSpec.JETBRAINS`
     + `languageVersion = 21`, resolved by the **foojay-resolver-convention** plugin
     (`1.0.0`) in `settings.gradle.kts`. foojay downloads JBR 21 automatically — no
     machine-specific path. (An earlier approach hardcoded `org.gradle.java.home` to a
     manually-extracted JBR; the foojay toolchain replaced it.)
   - **Gotcha:** the toolchain alone is *not* enough — Compose Desktop's `run` task uses
     the Gradle daemon JVM, not the Java toolchain, so it launched on JDK 25 and crashed
     with the JBR error. Fix: also set `compose.desktop.application.javaHome` to the
     toolchain's JBR (`javaToolchains.launcherFor { JETBRAINS, 21 }.get().metadata.installationPath`).
     Compilation uses the `java { toolchain }`; `run` uses that `javaHome`. Both are the
     same foojay-provisioned JBR.
6. **Markdown rendering via Jewel's `jewel-markdown-*` modules** with **GFM** wired on both
   the processor and renderer (tables, alerts, strikethrough, autolinks). Rendered through
   `LazyMarkdown` (its default `blockRenderer` reads the provided GFM renderer from the
   composition local; the `Markdown(String)` overload would *not*).
7. **Dark theme default** with a light/dark toggle in the title bar.
8. **Coding conventions** (org rules, Kotlin standard syntax): tabs; constants `kName`;
   locals `vName`; params `inName`; concise comments on each class/function (no Javadoc).

### API verification method

The README compatibility table on the public Jewel repo is stale, so versions/signatures
were taken from **ground truth**: the published `maven-metadata.xml`/POMs, and the actual
`0.34.0` JARs + **`-sources.jar`** (introspected with `javap` and by reading the Kotlin
declarations) for every API used — `IntUiTheme`, `darkThemeDefinition`,
`ComponentStyling.default().decoratedWindow()`, `DecoratedWindow`, `TitleBar`,
`newFullscreenControls`, `MarkdownProcessor`, `MarkdownStyling/BlockRenderer.dark/light`,
`ProvideMarkdownStyling`, `LazyMarkdown`, `VerticallyScrollableContainer`, `TextArea`.

## Project layout

```
settings.gradle.kts · build.gradle.kts · gradle.properties · gradlew(.bat)
src/main/kotlin/com/bitsycore/jewelmarkdown/
  Main.kt            application(), theme, DecoratedWindow + custom TitleBar (theme toggle)
  AppContent.kt      toolbar (New/Open/Save/Save As), view-mode switch, editor/preview split, status bar
  MarkdownPreview.kt ProvideMarkdownStyling + LazyMarkdown with GFM extensions, off-thread parsing
  AppState.kt        TextFieldState-backed document state, dirty tracking, theme + view mode
  FileOps.kt         native open/save dialogs, openUrl
  SampleContent.kt   default showcase document
```

## Progress log

- **2026-06-12** — Inspected environment; confirmed toolchain + cached Compose 1.10.3.
  Launched research workflow to pin Jewel coordinates/APIs against Maven Central.
- **2026-06-13** — Research returned high-confidence, Maven-verified coordinates
  (`0.34.0-253.32098.101`). Downloaded the actual JARs + sources and introspected every
  API signature. Discovered the hard JBR requirement in `DecoratedWindow`; extracted and
  verified the cached JBR; pointed `org.gradle.java.home` at it. Wrote the full project
  (build files + 6 Kotlin sources) and generated the 9.4.1 wrapper.
- **2026-06-13** — `compileKotlin` succeeded against Jewel 0.34.0 with no errors. First
  `run` crashed at startup with `NoClassDefFoundError: com/sun/jna/Library` inside
  `IntUiTheme` — Jewel standalone needs **JNA** on the runtime classpath (its POM omits it).
  Added `net.java.dev.jna:jna:5.14.0`. Re-ran: **BUILD SUCCESSFUL**, window launched on JBR
  and composed the themed decorated window + editor + GFM preview with **no exceptions**;
  closing the window ended the run with exit 0. **App verified working.**

- **2026-06-13** — Switched the JBR strategy to a portable **foojay `vendor=JETBRAINS`
  toolchain** (dropped the hardcoded `org.gradle.java.home`). Verified `run` was still
  launching on JDK 25 (Compose `run` ignores the Java toolchain), so pinned
  `compose.desktop.application.javaHome` to the toolchain JBR. foojay downloaded JBR
  21.0.11; the app now runs on `…/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2`.
- **2026-06-13** — Fixed non-uniform dark background: painted the root container with
  `JewelTheme.globalColors.panelBackground` so toolbar/preview/status bar/gaps are themed
  (the editor keeps its own editor-field background). Recompiled and relaunched OK.

- **2026-06-13** — Added **syntax highlighting** (both editor and preview):
  - *Editor (Markdown source):* the new `TextFieldState` API has no per-token styling hook,
    so the editor was switched to the value-based `TextArea` (`TextFieldValue`) which accepts
    a `VisualTransformation`. `MarkdownSyntaxTransformation` regex-colors headings, bold/italic,
    code, links, quotes, lists, strikethrough and rules (theme-aware), with `OffsetMapping.Identity`.
    `AppState` moved from `TextFieldState` to `TextFieldValue` accordingly.
  - *Preview (code blocks):* implemented `JewelHighlightsCodeHighlighter` (replacing
    `NoOpCodeHighlighter`) backed by `dev.snipme:highlights:1.0.0`. Implements both
    `CodeHighlighter` overloads (the deprecated `MimeType?` one delegates to the
    `language: String` one); maps the fence language → `SyntaxLanguage`, highlights off-thread.
  - Compiles warning-clean; runs on JBR.

- **2026-06-13** — Major feature/UX expansion (committed incrementally): editor + preview
  syntax highlighting; IntelliJ-style panels with a dark Mica gradient; Markdown helper
  toolbar; multi-document **tabs** (reorder by drag, right-click close menu, file
  drag-and-drop); **project folder panel** + activity-bar toggle; **File/Edit/View menu bar
  in the title bar** (hover-switch, shortcut hints); **settings dialog** with categories,
  gradient presets, font choice and reset; **Visual-Studio-style keyboard shortcuts**
  (rebindable); draggable editor/preview splitter; preference + keymap **persistence**;
  readability fixes for quotes/code/alerts. Standalone-icon limitation worked around by
  using text/drawn glyphs (no IntelliJ SVG resources needed).

## Status: DONE

The app builds and runs. Launch with `gradlew run` (uses JBR automatically).
Possible future enhancements: a draggable splitter, find/replace, recent-files,
and packaging via `gradlew packageDistributionForCurrentOS`.
