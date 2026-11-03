plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/*
 * The UI's strings come from the Android app's strings.xml (English and German), so both apps show
 * the same text from one source. This writes them as a Kotlin table the shared UI reads with
 * `stringResource(Res.string.…)`, following the in-app language.
 */
val generateKgsStrings by tasks.registering {
    // The app's strings, plus the few that only other platforms use (shared/ui/strings).
    val sources = listOf("values" to "en", "values-de" to "de").flatMap { (dir, lang) ->
        listOf(
            lang to rootProject.file("app/src/main/res/$dir/strings.xml"),
            lang to project.file("strings/$dir/strings.xml"),
        )
    }
    val output = layout.buildDirectory.dir("generated/kgsStrings/commonMain/kotlin")
    sources.forEach { (_, file) -> inputs.files(file) }
    outputs.dir(output)
    doLast {
        fun androidText(raw: String): String {
            var text = raw
            val quoted = text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")
            text = if (quoted) text.substring(1, text.length - 1) else text.replace(Regex("\\s+"), " ").trim()
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    when (val n = text[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> { out.append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                        else -> out.append(n)
                    }
                    i += 2
                } else {
                    if (c != '"') out.append(c)
                    i++
                }
            }
            return out.toString()
        }
        fun innerText(node: org.w3c.dom.Node): String = buildString {
            val children = node.childNodes
            for (k in 0 until children.length) {
                val child = children.item(k)
                when (child.nodeType) {
                    org.w3c.dom.Node.TEXT_NODE, org.w3c.dom.Node.CDATA_SECTION_NODE -> append(child.nodeValue)
                    org.w3c.dom.Node.ELEMENT_NODE -> append(innerText(child))
                }
            }
        }
        val strings = linkedMapOf<String, MutableMap<String, String>>()
        val plurals = linkedMapOf<String, MutableMap<String, Map<String, String>>>()
        sources.filter { (_, file) -> file.exists() }.forEach { (lang, file) ->
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val stringNodes = doc.getElementsByTagName("string")
            for (k in 0 until stringNodes.length) {
                val el = stringNodes.item(k) as org.w3c.dom.Element
                strings.getOrPut(el.getAttribute("name")) { linkedMapOf() }[lang] = androidText(innerText(el))
            }
            val pluralNodes = doc.getElementsByTagName("plurals")
            for (k in 0 until pluralNodes.length) {
                val el = pluralNodes.item(k) as org.w3c.dom.Element
                val items = el.getElementsByTagName("item")
                val forms = (0 until items.length).associate { idx ->
                    val item = items.item(idx) as org.w3c.dom.Element
                    item.getAttribute("quantity") to androidText(innerText(item))
                }
                plurals.getOrPut(el.getAttribute("name")) { linkedMapOf() }[lang] = forms
            }
        }
        fun lit(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            .replace("\t", "\\t").replace("$", "\\$") + "\""
        val keywords = setOf("in", "is", "as", "do", "if", "for", "fun", "val", "var", "when", "try", "object", "class", "return", "package", "this", "super", "null", "true", "false", "throw", "while", "break", "continue", "typealias", "interface")
        fun id(name: String) = if (name in keywords) "`$name`" else name
        val code = buildString {
            appendLine("// Generated from app/src/main/res/values*/strings.xml by :shared:ui:generateKgsStrings. Do not edit.")
            appendLine("package com.kgs.calendar.ui.res")
            appendLine()
            appendLine("object Res {")
            appendLine("    object string {")
            strings.forEach { (name, byLang) ->
                val en = byLang["en"] ?: byLang.values.first()
                val de = byLang["de"]?.let(::lit) ?: "null"
                appendLine("        val ${id(name)}: StringResource get() = StringResource(${lit(name)}, ${lit(en)}, $de)")
            }
            appendLine("    }")
            appendLine("    object plurals {")
            plurals.forEach { (name, byLang) ->
                fun forms(map: Map<String, String>?) = map?.entries?.joinToString(", ", "mapOf(", ")") { (q, v) -> "${lit(q)} to ${lit(v)}" } ?: "null"
                appendLine("        val ${id(name)}: PluralsResource get() = PluralsResource(${lit(name)}, ${forms(byLang["en"] ?: byLang.values.first())}, ${forms(byLang["de"])})")
            }
            appendLine("    }")
            appendLine("}")
        }
        val dir = output.get().asFile.resolve("com/kgs/calendar/ui/res")
        dir.mkdirs()
        dir.resolve("KgsStrings.kt").writeText(code)
    }
}

kotlin {
    android {
        namespace = "com.kgs.calendar.shared.ui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get())) }
        withHostTest { isIncludeAndroidResources = true }
    }
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "KgsSharedUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.get().kotlin.srcDir(generateKgsStrings.map { layout.buildDirectory.dir("generated/kgsStrings/commonMain/kotlin").get() })
        commonTest.dependencies { implementation(kotlin("test")) }
        commonMain.dependencies {
            api(project(":core:shared"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.materialIconsExtended)
            api(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.compose.ui.backhandler)
        }
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("androidx.compose.animation.ExperimentalSharedTransitionApi")
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.window)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
