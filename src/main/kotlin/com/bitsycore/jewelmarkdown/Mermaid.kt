package com.bitsycore.jewelmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color as FxColor
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.ui.component.Text
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities

// Mermaid diagrams are rendered through an embedded JavaFX WebView (WebKit) — small footprint,
// no native conflict with JBR's bundled JCEF, fully offline once mermaid.min.js is bundled.
// mermaid.min.js is downloaded into resources at build time by the `downloadMermaid` Gradle task.
//
// Performance design: at startup we pre-warm a pool of WebViews that already have mermaid.js
// parsed in their JS context. To render a diagram we hand one out and call window.setMermaid()
// — no page reload, no mermaid.js re-parse. Editing an existing diagram is also a JS call on
// the same WebView, so updates are instant.

// Status of the JavaFX runtime, observed by the renderer to switch between a placeholder and an
// actual WebView-backed view.
enum class MermaidStatus { NotStarted, Initializing, Ready, Failed }

// How many WebViews are kept primed (mermaid.js loaded, ready to render). One is consumed each
// time a new diagram is first displayed; the pool is topped up asynchronously.
private const val kPrimedPoolTarget = 2

// Global handle to JavaFX readiness and the warmup WebView pool.
object MermaidRuntime {
	var status by mutableStateOf(MermaidStatus.NotStarted)
		private set

	// While suppressed, MermaidView falls back to the placeholder instead of mounting a
	// JFXPanel. The Settings overlay sets this so the heavyweight WebView doesn't eat the
	// outside-tap that would otherwise dismiss the modal.
	var suppressed by mutableStateOf(false)

	// Cached mermaid.min.js body, loaded from resources on first use.
	private val mermaidJs: String by lazy {
		Mermaid::class.java.getResourceAsStream("/mermaid.min.js")
			?.bufferedReader()
			?.use { it.readText() }
			?: ""
	}

	// Pre-warmed WebViews. Each one has loaded the shell HTML below and parsed mermaid.js; its
	// JS context exposes a window.setMermaid(source, theme) function ready to be called.
	// Accessed only from the JavaFX Application Thread.
	private val primedPool: ArrayDeque<WebView> = ArrayDeque()

	// Boots JavaFX and starts populating the WebView pool. Safe to call multiple times.
	suspend fun ensureInitialized() {
		if (status != MermaidStatus.NotStarted) return
		status = MermaidStatus.Initializing
		withContext(Dispatchers.IO) {
			runCatching {
				// Constructing the first JFXPanel boots the JavaFX Application Thread; must
				// happen on the EDT.
				SwingUtilities.invokeAndWait { JFXPanel() }
				Platform.setImplicitExit(false)
				status = MermaidStatus.Ready
				Platform.runLater { topUpPool() }
			}.onFailure { status = MermaidStatus.Failed }
		}
	}

	// Adds new WebViews to the pool until it hits the target. Each one immediately loads the
	// shell HTML; once Worker.SUCCEEDED fires it can be checked out and used.
	private fun topUpPool() {
		while (primedPool.size < kPrimedPoolTarget) {
			val vView = buildPrimedWebView()
			primedPool.addLast(vView)
		}
	}

	// Creates one WebView, starts loading the shell HTML on it, and returns it immediately. The
	// caller is responsible for waiting on Worker.SUCCEEDED before calling setMermaid().
	private fun buildPrimedWebView(): WebView {
		val vView = WebView().apply {
			isContextMenuEnabled = false
			style = "-fx-background-color: transparent;"
		}
		vView.engine.loadContent(buildShellHtml())
		return vView
	}

	// Hands out one WebView from the pool. If the pool is empty (e.g. several diagrams opened
	// at once), a fresh one is built on the spot. After checkout we top the pool up again so
	// subsequent diagrams stay instant. Must be called on the JavaFX Application Thread.
	fun checkoutWebView(): WebView {
		val vView = primedPool.removeFirstOrNull() ?: buildPrimedWebView()
		topUpPool()
		return vView
	}

	// Returns whether the WebView has finished loading the shell HTML. Until this is true,
	// callers must wait — calling executeScript("setMermaid(...)") on a still-loading page
	// raises an error in the WebKit engine.
	fun isReady(inView: WebView): Boolean =
		inView.engine.loadWorker.state == Worker.State.SUCCEEDED

	// Updates the diagram displayed in the given primed WebView. Replaces just the diagram
	// source via the setMermaid() JS function — much faster than re-loading the full HTML
	// because mermaid.js stays parsed and the WebKit context is reused.
	fun renderInto(inView: WebView, inSource: String, inIsDark: Boolean) {
		val vTheme = if (inIsDark) "dark" else "default"
		val vEscaped = jsEscape(inSource)
		val vScript = "window.setMermaid && window.setMermaid('$vEscaped', '$vTheme');"
		inView.engine.executeScript(vScript)
	}

	// JS string-literal escaping for the small set of characters that can appear in a Mermaid
	// diagram and break a single-quoted string literal.
	private fun jsEscape(inText: String): String =
		inText
			.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\r", "")
			.replace(" ", "\\u2028")
			.replace(" ", "\\u2029")

	// Builds the shell HTML loaded once into every primed WebView. Bundles mermaid.js and
	// exposes a window.setMermaid(source, theme) function the host calls to swap the diagram
	// without reparsing the whole page.
	private fun buildShellHtml(): String {
		return """
			<!doctype html>
			<html>
			<head>
				<meta charset="utf-8">
				<style>
					html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
					body { display: flex; align-items: center; justify-content: center; height: 100vh; }
					::-webkit-scrollbar { display: none; }
					.mermaid { font-family: -apple-system, system-ui, sans-serif; max-width: 100%; max-height: 100%; }
					.mermaid svg { max-width: 100%; max-height: 100vh; height: auto; display: block; }
				</style>
			</head>
			<body>
				<pre id="m" class="mermaid"></pre>
				<script>$mermaidJs</script>
				<script>
					window.setMermaid = async function(source, theme) {
						try {
							mermaid.initialize({ startOnLoad: false, theme: theme, securityLevel: 'loose' });
							var node = document.getElementById('m');
							node.removeAttribute('data-processed');
							node.textContent = source;
							await mermaid.run({ nodes: [node] });
						} catch (e) { console.error(e); }
					};
				</script>
			</body>
			</html>
		""".trimIndent()
	}
}

// Convenient anchor for class-loader resource lookups.
private class Mermaid

// A MarkdownBlock that carries the raw Mermaid diagram source. Built by post-processing Jewel's
// parsed block list (replacing FencedCodeBlocks whose language is "mermaid").
class MermaidBlock(val source: String) : MarkdownBlock.CustomBlock

// Jewel extension entry point: contributes the block renderer below.
class MermaidJewelExtension(private val inIsDark: Boolean) : MarkdownRendererExtension {
	override val blockRenderer: MarkdownBlockRendererExtension = MermaidBlockRenderer(inIsDark)
}

// Renders MermaidBlocks by hosting a JavaFX WebView in a SwingPanel. While JavaFX is still
// warming up the block falls back to a plain code listing so the user can still see the source.
private class MermaidBlockRenderer(private val inIsDark: Boolean) : MarkdownBlockRendererExtension {
	override fun canRender(block: MarkdownBlock.CustomBlock): Boolean = block is MermaidBlock

	@Composable
	override fun RenderCustomBlock(
		block: MarkdownBlock.CustomBlock,
		blockRenderer: MarkdownBlockRenderer,
		inlineRenderer: InlineMarkdownRenderer,
		enabled: Boolean,
		modifier: Modifier,
		onUrlClick: (String) -> Unit,
	) {
		val vMermaid = block as MermaidBlock
		MermaidView(vMermaid.source, inIsDark, modifier)
	}
}

// Embeds a single Mermaid diagram. Recreates the WebView's content whenever the source or theme
// changes so the diagram tracks document edits and dark/light switches.
@Composable
fun MermaidView(inSource: String, inIsDark: Boolean, inModifier: Modifier = Modifier) {
	val vStatus = MermaidRuntime.status
	val vBorderColor = JewelTheme.globalColors.borders.normal
	val vShape = RoundedCornerShape(6.dp)

	Box(
		modifier =
			inModifier
				.fillMaxWidth()
				.height(280.dp)
				.clip(vShape)
				.border(1.dp, vBorderColor, vShape)
				.background(JewelTheme.globalColors.panelBackground),
	) {
		when {
			// While a modal overlay is up, render nothing so clicks fall through to Compose
			// (the heavyweight JFXPanel would otherwise swallow the outside-tap-to-dismiss).
			MermaidRuntime.suppressed -> {}
			vStatus == MermaidStatus.Ready -> MermaidWebView(inSource, inIsDark)
			vStatus == MermaidStatus.Failed -> MermaidPlaceholder(inSource, "WebView failed to start. Showing source instead.")
			else -> MermaidPlaceholder(inSource, "Preparing the Mermaid renderer…")
		}
	}
}

// Hosts the actual JavaFX WebView via SwingPanel. The panel itself is stable across
// recompositions. On first mount we check out a primed WebView from the runtime; later source
// or theme changes just call window.setMermaid() via JS so mermaid.js never re-parses.
@Composable
private fun MermaidWebView(inSource: String, inIsDark: Boolean) {
	val vPanel =
		remember {
			object : JFXPanel() {
				init {
					isOpaque = false
					background = java.awt.Color(0, 0, 0, 0)
				}

				// Override Swing's wheel dispatch BEFORE the event reaches JavaFX, so the
				// markdown preview's scroll container keeps consuming the wheel even when the
				// pointer sits over the embedded WebView.
				override fun processMouseWheelEvent(e: MouseWheelEvent) {
					val vParent = parent ?: return
					val vPoint = SwingUtilities.convertPoint(this, e.x, e.y, vParent)
					vParent.dispatchEvent(
						MouseWheelEvent(
							vParent,
							e.id,
							e.`when`,
							e.modifiersEx,
							vPoint.x,
							vPoint.y,
							e.xOnScreen,
							e.yOnScreen,
							e.clickCount,
							e.isPopupTrigger,
							e.scrollType,
							e.scrollAmount,
							e.wheelRotation,
							e.preciseWheelRotation,
						)
					)
				}
			}
		}
	val vWebView = remember { arrayOfNulls<WebView>(1) }

	LaunchedEffect(inSource, inIsDark) {
		Platform.runLater {
			val vExisting = vWebView[0]
			if (vExisting == null) {
				val vNew = MermaidRuntime.checkoutWebView()
				vWebView[0] = vNew
				vPanel.scene = Scene(vNew).apply { fill = FxColor.TRANSPARENT }
				renderWhenReady(vNew, inSource, inIsDark)
			} else {
				renderWhenReady(vExisting, inSource, inIsDark)
			}
		}
	}

	SwingPanel(
		modifier = Modifier.fillMaxWidth(),
		factory = { vPanel },
	)
}

// Calls renderInto immediately if the WebView's shell is done loading, otherwise hooks the
// Worker.SUCCEEDED state change and renders then. Either way the diagram appears as soon as
// the WebKit context is ready — usually instantly since pool entries finish loading at startup.
private fun renderWhenReady(inView: WebView, inSource: String, inIsDark: Boolean) {
	if (MermaidRuntime.isReady(inView)) {
		MermaidRuntime.renderInto(inView, inSource, inIsDark)
		return
	}
	inView.engine.loadWorker.stateProperty().addListener { _, _, vNewState ->
		if (vNewState == Worker.State.SUCCEEDED) {
			MermaidRuntime.renderInto(inView, inSource, inIsDark)
		}
	}
}

// Pre-render fallback content: shows the diagram source in muted text so the user still sees
// something useful while JavaFX warms up or if it failed entirely.
@Composable
private fun MermaidPlaceholder(inSource: String, inMessage: String) {
	Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
		val vMuted = JewelTheme.globalColors.text.info
		androidx.compose.foundation.layout.Column {
			Text(inMessage, color = vMuted, fontSize = 12.sp)
			Box(Modifier.padding(top = 8.dp)) {
				Text(inSource, color = vMuted, fontSize = 12.sp)
			}
		}
	}
}

// Walks the parsed Markdown blocks and swaps each fenced code block whose language is
// "mermaid" for a MermaidBlock. Block-quotes are recursed into so quoted diagrams still render.
fun transformMermaidBlocks(inBlocks: List<MarkdownBlock>): List<MarkdownBlock> {
	return inBlocks.map { vBlock ->
		when (vBlock) {
			is MarkdownBlock.CodeBlock.FencedCodeBlock ->
				if (vBlock.language.equals("mermaid", ignoreCase = true)) {
					MermaidBlock(vBlock.content)
				} else {
					vBlock
				}
			is MarkdownBlock.BlockQuote -> MarkdownBlock.BlockQuote(transformMermaidBlocks(vBlock.children))
			else -> vBlock
		}
	}
}

// Convenience used by Main: triggers JavaFX init in the application's coroutine scope so the
// preview is ready by the time the user types or opens a file containing a Mermaid block.
@Composable
fun RememberMermaidInit() {
	LaunchedEffect(Unit) {
		MermaidRuntime.ensureInitialized()
	}
}
