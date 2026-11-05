import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Properties
import javax.imageio.ImageIO
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/** The app's version as the other platforms show it: V.MAJOR.MINOR.PATCH, or V.MAJOR.MINOR.PATCH-BUILD for a rebuild. */
val appVersionName = "V.2.0.0"

/**
 * The installer's version. Windows only installs a package over the one before it (keeping its data)
 * when this version is higher, and it compares just three numbers, so every build handed out needs
 * a higher one: V.2.0.0 → 2.0.0, V.2.0.0-3 → 2.0.3, V.2.0.1 → 2.0.100.
 */
val installerVersion: String = run {
    val match = Regex("""V\.(\d+)\.(\d+)\.(\d+)(?:-(\d+))?""").matchEntire(appVersionName)
        ?: error("Unexpected version name $appVersionName")
    val (major, minor, patch, build) = match.destructured
    val buildNumber = patch.toInt() * 100 + (build.toIntOrNull() ?: 0)
    check(build.isEmpty() || build.toInt() < 100) { "A rebuild number must stay below 100" }
    "$major.$minor.$buildNumber"
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared:ui"))
            implementation(compose.desktop.currentOs)
            implementation(libs.jna.platform)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kgs.calendar.desktop.MainKt"
        jvmArgs += listOf("-Dkgs.version=$appVersionName", "-Xss2m")
        // Development runs: `KGS_DESKTOP_DATA_DIR=<folder> KGS_DESKTOP_DEMO=1 gradlew :desktopApp:run`.
        System.getenv("KGS_DESKTOP_DATA_DIR")?.let { args += listOf("--data-dir", it) }
        if (System.getenv("KGS_DESKTOP_DEMO") == "1") args += "--demo"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // The JVM options file (see below), in the app's resources folder.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "KGS Calendar"
            // Windows installers need a plain MAJOR.MINOR.BUILD version.
            packageVersion = installerVersion
            // Windows shows this as the program's name (Task Manager, "Open with").
            description = "KGS Calendar"
            vendor = "KGS501"
            copyright = "KGS501"
            // From suggestRuntimeModules, plus what is only used at runtime: TLS key exchange for
            // servers, localized month and day names, logging, JNDI for ical4j and screen readers;
            // jdk.management writes the class archive (ClassDataArchive).
            modules(
                "java.instrument", "java.sql", "jdk.unsupported", "java.logging", "java.naming",
                "java.management", "jdk.management", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets",
                "jdk.accessibility",
            )
            windows {
                iconFile.set(project.file("icons/kgs_calendar.ico"))
                menu = true
                // Straight into the Start menu's list, not into a folder (jpackage's default is one called "Unknown").
                menuGroup = ""
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Keeps updates installing over the previous version; never change it (UpdateWatcher knows it too).
                upgradeUuid = "e37ae039-a4f6-46a7-a7fd-14f524e68795"
            }
        }
    }
}

// Faster starts: the JVM loads classes from class-data-sharing archives instead of reading and verifying
// them from the jars:
// - the runtime's own (JDK) classes from bin/server/classes.jsa, made here once the runtime is linked;
// - the app's classes from an archive the app builds on the user's computer (ClassDataArchive), from
//   the list of classes it loads; kgs-jvm.options then names it. Only the packaged app gets these
//   options: `$APPDIR` exists only there.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    val jdkHome = javaHome
    val runtimeDir = destinationDir
    doLast {
        val runtime = runtimeDir.get().asFile
        // The linked runtime has no launcher. The JDK's own (the same version) runs the runtime's JVM;
        // javaw stays, for the app to build its archive with.
        val java = File(runtime, "bin/java.exe")
        File(jdkHome.get(), "bin/java.exe").copyTo(java, overwrite = true)
        File(jdkHome.get(), "bin/javaw.exe").copyTo(File(runtime, "bin/javaw.exe"), overwrite = true)
        try {
            val process = ProcessBuilder(java.absolutePath, "-Xshare:dump").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0 && File(runtime, "bin/server/classes.jsa").isFile) { "CDS dump failed:\n$output" }
        } finally {
            java.delete()
        }
    }
}

// The Microsoft Store package: the same app as the installer's, in the Store's format. `packageMsix` needs
// makeappx and makepri from the Windows SDK: the folder WINDOWS_SDK_BIN names, or an installed SDK.
// `-PmsixTest` makes a package with a test identity, for signing with a test certificate and trying it
// out (see docs/microsoft-store.md). The Store signs the real one itself and only takes one without it.
abstract class PackageMsixTask : DefaultTask() {
    @get:InputDirectory abstract val app: DirectoryProperty
    @get:InputDirectory abstract val template: DirectoryProperty
    @get:InputFile abstract val logo: RegularFileProperty
    @get:Input abstract val version: Property<String>
    @get:Input abstract val testPackage: Property<Boolean>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    /** Logo sizes in the names Windows looks for; each is the logo, centred, in that size. */
    private val logos = buildMap<String, Pair<Pair<Int, Int>, Double>> {
        for ((scale, percent) in listOf(100 to 1.0, 125 to 1.25, 150 to 1.5, 200 to 2.0, 400 to 4.0)) {
            fun add(name: String, width: Int, height: Int, fill: Double) {
                put("$name.scale-$scale.png", ((width * percent).toInt() to (height * percent).toInt()) to fill)
            }
            add("Square44x44Logo", 44, 44, 0.84)
            add("Square71x71Logo", 71, 71, 0.6)
            add("Square150x150Logo", 150, 150, 0.6)
            add("Square310x310Logo", 310, 310, 0.6)
            add("Wide310x150Logo", 310, 150, 0.6)
            add("StoreLogo", 50, 50, 0.84)
        }
        for (size in listOf(16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 256)) {
            val single = (size to size) to 0.92
            put("Square44x44Logo.targetsize-$size.png", single)
            put("Square44x44Logo.targetsize-${size}_altform-unplated.png", single)
            put("Square44x44Logo.targetsize-${size}_altform-lightunplated.png", single)
        }
    }

    @TaskAction
    fun pack() {
        val output = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val stage = File(output, "stage")
        app.get().asFile.copyRecursively(stage)
        // The folder is read-only, so the class list (see above) has nowhere to go, and there is no archive.
        File(stage, "app/KGS Calendar.cfg").let { cfg ->
            cfg.writeText(cfg.readLines().filterNot { "DumpLoadedClassList" in it || "VMOptionsFile" in it }.joinToString("\n", postfix = "\n"))
        }
        writeLogos(File(stage, "Assets"))

        val identity = Properties().apply { File(template.get().asFile, "store-identity.properties").inputStream().use(::load) }
        val test = testPackage.get()
        val identityName = identity.getProperty("identityName") + if (test) ".Test" else ""
        val publisher = if (test) "CN=KGS501 Test" else identity.getProperty("publisher")
        File(stage, "AppxManifest.xml").writeText(
            File(template.get().asFile, "AppxManifest.xml").readText()
                .replace("@IDENTITY_NAME@", identityName)
                .replace("@PUBLISHER@", publisher)
                .replace("@PUBLISHER_DISPLAY_NAME@", identity.getProperty("publisherDisplayName"))
                .replace("@DISPLAY_NAME@", "KGS Calendar")
                .replace("@VERSION@", version.get() + ".0"),
        )

        val tools = sdkTools()
        val config = File(output, "priconfig.xml")
        run(File(tools, "makepri.exe"), "createconfig", "/cf", config.path, "/dq", "en-US_de-DE", "/pv", "10.0.0", "/o")
        run(File(tools, "makepri.exe"), "new", "/pr", stage.path, "/cf", config.path, "/of", File(stage, "resources.pri").path, "/mn", File(stage, "AppxManifest.xml").path, "/o")
        val name = "KGS-Calendar-${version.get()}.0-x64" + if (test) "-test" else ""
        run(File(tools, "makeappx.exe"), "pack", "/d", stage.path, "/p", File(output, "$name.msix").path, "/o")
        logger.lifecycle("Store package: ${File(output, "$name.msix")}")
    }

    private fun sdkTools(): File {
        val fromEnvironment = System.getenv("WINDOWS_SDK_BIN")?.let(::File)
        val installed = File("C:/Program Files (x86)/Windows Kits/10/bin").listFiles().orEmpty()
            .filter { File(it, "x64/makeappx.exe").isFile }.maxByOrNull { it.name }?.let { File(it, "x64") }
        return listOfNotNull(fromEnvironment, installed).firstOrNull { File(it, "makeappx.exe").isFile }
            ?: error("makeappx.exe not found: install the Windows SDK or set WINDOWS_SDK_BIN to its folder")
    }

    private fun run(tool: File, vararg arguments: String) {
        val process = ProcessBuilder(tool.path, *arguments).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "${tool.name} failed:\n$log" }
    }

    private fun writeLogos(assets: File) {
        assets.mkdirs()
        val source = croppedToLogo(ImageIO.read(logo.get().asFile))
        for ((name, spec) in logos) {
            val (size, fill) = spec
            val (width, height) = size
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val side = (minOf(width, height) * fill).toInt().coerceAtLeast(1)
            image.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(scaledDown(source, side), (width - side) / 2, (height - side) / 2, null)
                dispose()
            }
            ImageIO.write(image, "png", File(assets, name))
        }
    }

    /** The drawn part of [image], centred in a square, so the logo fills the sizes above as they say. */
    private fun croppedToLogo(image: BufferedImage): BufferedImage {
        val drawn = (0 until image.height).flatMap { y -> (0 until image.width).filter { x -> (image.getRGB(x, y) ushr 24) > 20 }.map { x -> x to y } }
        val left = drawn.minOf { it.first }
        val top = drawn.minOf { it.second }
        val width = drawn.maxOf { it.first } - left + 1
        val height = drawn.maxOf { it.second } - top + 1
        val side = maxOf(width, height)
        return BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB).also { square ->
            square.createGraphics().apply {
                drawImage(image, (side - width) / 2 - left, (side - height) / 2 - top, null)
                dispose()
            }
        }
    }

    /** Halves the logo step by step, which stays smooth where one big step wouldn't. */
    private fun scaledDown(image: BufferedImage, size: Int): BufferedImage {
        fun resized(from: BufferedImage, side: Int) = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB).also { to ->
            to.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                drawImage(from, 0, 0, side, side, null)
                dispose()
            }
        }
        var current = image
        while (current.width / 2 >= size * 2) current = resized(current, current.width / 2)
        return resized(current, size)
    }
}

tasks.register<PackageMsixTask>("packageMsix") {
    group = "compose desktop"
    description = "Builds the Microsoft Store package (.msix)."
    app.set(layout.buildDirectory.dir("compose/binaries/main/app/KGS Calendar"))
    template.set(layout.projectDirectory.dir("msix"))
    logo.set(layout.projectDirectory.file("src/jvmMain/resources/kgs_logo.png"))
    version.set(installerVersion)
    testPackage.set(providers.gradleProperty("msixTest").isPresent)
    outputDir.set(layout.buildDirectory.dir("msix"))
    dependsOn("createDistributable")
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    // Every package is made from scratch (the installers don't reuse the app image), so each gets them.
    run {
        freeArgs.addAll(
            "--java-options", "-XX:VMOptionsFile=\$APPDIR/resources/kgs-jvm.options",
            "--java-options", "-XX:DumpLoadedClassList=\$APPDIR/resources/kgs-classes.lst",
        )
    }
}
