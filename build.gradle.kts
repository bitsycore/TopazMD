import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

// Build script for the Jewel Markdown desktop app.
// Versions are pinned to a combination verified against Maven Central:
// Jewel 0.34.0 (IntelliJ Platform 253) was compiled against stable Compose 1.10.0,
// Kotlin 2.2.0 and JDK 21 — matching the Compose plugin and JBR used here.

plugins {
	kotlin("jvm") version "2.2.0"
	id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
	id("org.jetbrains.compose") version "1.10.3"
	// Brings in JavaFX with the correct platform classifier — used to render Mermaid diagrams
	// in an embedded WebView (WebKit). Avoids the JCEF/JBR native conflict on macOS.
	id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.bitsycore"
version = "1.0.0"

repositories {
	mavenCentral()
	google()
}

// JavaFX modules required by the Mermaid renderer. The plugin auto-selects the right native
// classifier for the current host so `./gradlew run` and packaged distributions both work.
javafx {
	version = "21.0.4"
	modules("javafx.controls", "javafx.swing", "javafx.web")
}

// All org.jetbrains.jewel:* modules must share the exact same version string.
val kJewelVersion = "0.34.0-253.32098.101"

dependencies {
	implementation(compose.desktop.currentOs)

	// Jewel standalone declares JNA as compile-only (the IDE normally provides it),
	// so a standalone app must add it itself, or IntUiTheme fails with NoClassDefFoundError.
	implementation("net.java.dev.jna:jna:5.14.0")

	// Standalone Jewel theme + decorated window (DecoratedWindow / TitleBar).
	implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:$kJewelVersion")

	// Markdown engine + standalone Int UI styling for the preview pane.
	implementation("org.jetbrains.jewel:jewel-markdown-core:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-int-ui-standalone-styling:$kJewelVersion")

	// GitHub-flavored Markdown extensions (tables, alerts, strikethrough, autolinks).
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-tables:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-alerts:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-gfm-strikethrough:$kJewelVersion")
	implementation("org.jetbrains.jewel:jewel-markdown-extensions-autolink:$kJewelVersion")

	// Syntax-highlighting engine used to color fenced code blocks in the preview.
	implementation("dev.snipme:highlights:1.0.0")
}

// Downloads the mermaid.min.js bundle into the build's resources at build time, so the running
// app can load it from the classpath and render diagrams fully offline (no runtime web fetch).
val kMermaidVersion = "11.4.1"
val downloadMermaid by tasks.registering {
	val vOut = layout.buildDirectory.file("mermaid/mermaid.min.js").get().asFile
	outputs.file(vOut)
	doLast {
		if (!vOut.exists() || vOut.length() < 1024) {
			vOut.parentFile.mkdirs()
			val vUrl = URI("https://cdn.jsdelivr.net/npm/mermaid@$kMermaidVersion/dist/mermaid.min.js").toURL()
			vUrl.openStream().use { vInput -> vOut.outputStream().use { vInput.copyTo(it) } }
		}
	}
}

sourceSets.named("main") {
	resources.srcDir(layout.buildDirectory.dir("mermaid"))
}

tasks.named("processResources") { dependsOn(downloadMermaid) }

// Downloads Google's Material Icons Outlined font (legacy "icons" family — supports CSS-style
// ligatures, so writing Text("folder") with this font family renders the folder glyph). About
// 1MB OTF; bundled into the classpath so the app renders icons offline.
val downloadMaterialIcons by tasks.registering {
	val vOut = layout.buildDirectory.file("icons-font/material-icons-outlined.otf").get().asFile
	outputs.file(vOut)
	doLast {
		if (!vOut.exists() || vOut.length() < 1024) {
			vOut.parentFile.mkdirs()
			val vUrl = URI(
				"https://raw.githubusercontent.com/google/material-design-icons/master/font/MaterialIconsOutlined-Regular.otf"
			).toURL()
			vUrl.openStream().use { vInput -> vOut.outputStream().use { vInput.copyTo(it) } }
		}
	}
}

sourceSets.named("main") {
	resources.srcDir(layout.buildDirectory.dir("icons-font"))
}

tasks.named("processResources") { dependsOn(downloadMaterialIcons) }

// Pin the toolchain to the JetBrains Runtime so Kotlin compilation and the Compose
// `run` task both use JBR (required by DecoratedWindow). foojay downloads it if absent.
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
		// Jewel's Markdown and theming APIs are gated behind an opt-in annotation.
		optIn.addAll(
			"org.jetbrains.jewel.foundation.ExperimentalJewelApi",
			"androidx.compose.foundation.ExperimentalFoundationApi",
		)
	}
}

// Resolve the JBR provisioned by the toolchain so the Compose `run` task can target it.
val jbrLauncher =
	javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(21))
		vendor.set(JvmVendorSpec.JETBRAINS)
	}

compose.desktop {
	application {
		mainClass = "com.bitsycore.jewelmarkdown.MainKt"
		// The `run` task uses the Gradle daemon JVM unless javaHome is set; the daemon
		// may be a non-JBR JDK, which DecoratedWindow rejects. Force it onto the JBR.
		javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
		// ProGuard is on by default for release builds in Compose Desktop, but JavaFX
		// (Mermaid), Jewel, JNA and JBR rely on extensive reflection that would need
		// hundreds of keep rules to shrink safely. Disabling it makes packageRelease* just
		// bundle the JAR + JBR — larger but reliable, and that's what we ship in CI.
		buildTypes.release.proguard {
			isEnabled.set(false)
		}
		nativeDistributions {
			targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
			packageName = "JewelMarkdown"
			packageVersion = "1.0.0"
			description = "Compose for Desktop Markdown editor with Jewel UI."
			vendor = "Bitsy"
			licenseFile.set(project.file("LICENSE"))
		}
	}
}
