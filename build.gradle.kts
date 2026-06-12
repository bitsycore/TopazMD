import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Build script for the Jewel Markdown desktop app.
// Versions are pinned to a combination verified against Maven Central:
// Jewel 0.34.0 (IntelliJ Platform 253) was compiled against stable Compose 1.10.0,
// Kotlin 2.2.0 and JDK 21 — matching the Compose plugin and JBR used here.

plugins {
	kotlin("jvm") version "2.2.0"
	id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
	id("org.jetbrains.compose") version "1.10.3"
}

group = "com.bitsycore"
version = "1.0.0"

repositories {
	mavenCentral()
	google()
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
		nativeDistributions {
			targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
			packageName = "JewelMarkdown"
			packageVersion = "1.0.0"
		}
	}
}
