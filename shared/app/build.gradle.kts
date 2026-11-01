import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Generates `com.kgs.calendar.res.R` (string and plural ids) and the string tables of every
 * language from the shared Android string resources, applying Android's escaping rules.
 */
@CacheableTask
abstract class GenerateStringTables : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = resources.get().asFile
        val default = parse(root.resolve("values/strings.xml"))
        val languages = root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") && it.resolve("strings.xml").isFile }
            .sortedBy { it.name }
            .associate { it.name.removePrefix("values-") to parse(it.resolve("strings.xml")) }
        val stringNames = default.strings.keys.toList()
        val pluralNames = default.plurals.keys.toList()
        val directory = output.get().asFile.resolve("com/kgs/calendar/res").apply {
            deleteRecursively()
            mkdirs()
        }

        directory.resolve("R.kt").writeText(buildString {
            appendLine("// Generated from shared/strings. Do not edit.")
            appendLine("@file:Suppress(\"ClassName\", \"ObjectPropertyName\", \"unused\")")
            appendLine()
            appendLine("package com.kgs.calendar.res")
            appendLine()
            appendLine("object R {")
            appendLine("    object string {")
            stringNames.forEachIndexed { index, name -> appendLine("        const val $name: Int = $index") }
            appendLine("    }")
            appendLine()
            appendLine("    object plurals {")
            pluralNames.forEachIndexed { index, name -> appendLine("        const val $name: Int = $index") }
            appendLine("    }")
            appendLine("}")
        })

        directory.resolve("StringTables.kt").writeText(buildString {
            appendLine("// Generated from shared/strings. Do not edit.")
            appendLine()
            appendLine("package com.kgs.calendar.res")
            appendLine()
            appendLine("internal object StringTables {")
            appendLine("    val strings: Array<String> = arrayOf(")
            stringNames.forEach { appendLine("        ${literal(default.strings.getValue(it))},") }
            appendLine("    )")
            appendLine()
            appendLine("    /** Each plural's texts by quantity: zero, one, two, few, many, other. */")
            appendLine("    val plurals: Array<Array<String?>> = arrayOf(")
            pluralNames.forEach { appendLine("        ${quantities(default.plurals.getValue(it))},") }
            appendLine("    )")
            appendLine()
            appendLine("    /** Translations by language; a missing entry falls back to [strings] or [plurals]. */")
            appendLine("    val translations: Map<String, Translation> = mapOf(")
            languages.forEach { (language, table) ->
                appendLine("        ${literal(language)} to Translation(")
                appendLine("            strings = arrayOf(")
                stringNames.forEach { appendLine("                ${table.strings[it]?.let(::literal) ?: "null"},") }
                appendLine("            ),")
                appendLine("            plurals = arrayOf(")
                pluralNames.forEach { appendLine("                ${table.plurals[it]?.let(::quantities) ?: "null"},") }
                appendLine("            ),")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("}")
            appendLine()
            appendLine("internal class Translation(val strings: Array<String?>, val plurals: Array<Array<String?>?>)")
        })
    }

    private class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun parse(file: java.io.File): Table {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> strings[name] = androidText(element.textContent)
                "plurals" -> {
                    val items = element.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate { item ->
                        val node = items.item(item) as Element
                        node.getAttribute("quantity") to androidText(node.textContent)
                    }
                }
            }
        }
        return Table(strings, plurals)
    }

    /** Applies aapt's rules: whitespace collapses outside quotes, quotes are removed, escapes resolve. */
    private fun androidText(raw: String): String {
        val result = StringBuilder()
        var quoted = false
        var pendingSpace = false
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '\\' && index + 1 < raw.length -> {
                    if (pendingSpace) {
                        result.append(' ')
                        pendingSpace = false
                    }
                    index++
                    when (val escaped = raw[index]) {
                        'n' -> result.append('\n')
                        't' -> result.append('\t')
                        'u' -> {
                            result.append(raw.substring(index + 1, index + 5).toInt(16).toChar())
                            index += 4
                        }
                        else -> result.append(escaped)
                    }
                }
                char == '"' -> quoted = !quoted
                char.isWhitespace() && !quoted -> pendingSpace = result.isNotEmpty()
                else -> {
                    if (pendingSpace) {
                        result.append(' ')
                        pendingSpace = false
                    }
                    result.append(char)
                }
            }
            index++
        }
        return result.toString()
    }

    private fun quantities(items: Map<String, String>): String =
        listOf("zero", "one", "two", "few", "many", "other")
            .joinToString(prefix = "arrayOf(", postfix = ")") { items[it]?.let(::literal) ?: "null" }

    private fun literal(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(char)
            }
        }
        append('"')
    }
}

val generateStringTables = tasks.register<GenerateStringTables>("generateStringTables") {
    resources.set(rootProject.layout.projectDirectory.dir("shared/strings/res"))
    output.set(layout.buildDirectory.dir("generated/strings/commonMain/kotlin"))
}

kotlin {
    android {
        namespace = "com.kgs.calendar.shared.app"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get())) }

        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    // The desktop app. These screens are shared with Android only, so their common code may use the JDK.
    jvm()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        commonMain {
            kotlin.srcDir(generateStringTables.map { it.output })
            dependencies {
                api(project(":core:data"))
                api(project(":shared:ui"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.animation)
                api(compose.material3)
                api(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                api(libs.androidx.annotation)
                compileOnly(libs.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.window)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jna.platform)
        }
    }
}

compose.resources {
    packageOfResClass = "com.kgs.calendar.res"
    publicResClass = false
}
