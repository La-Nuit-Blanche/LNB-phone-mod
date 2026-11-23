plugins {
    id("lnbphone.app")
}

// LNB Warden is in no Maven repository and is never committed or redistributed: its jar goes in libs/ (built from
// lnb-warden with ./gradlew jar, see README.md). Feed calls the site with Warden's ApiClient and its server config.
val wardenJar = fileTree("libs") { include("lnbwarden*.jar") }

// Warden is loaded next to Feed in the runs, as in a real game, but never packed into Feed's jar.
val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath {
    extendsFrom(localRuntime)
}

// The screenshot harness: plays Feed's screens in an unattended client and quits (src/capture). A source set of its
// own, so it is never in the jar; loaded with Feed's mod in the runs, where nothing starts it unless the capture run's
// property is set.
val capture: SourceSet = sourceSets.create("capture")
configurations.named(capture.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
neoForge.addModdingDependenciesTo(capture)

dependencies {
    compileOnly(wardenJar)
    localRuntime(wardenJar)
    testImplementation(wardenJar)
    "captureImplementation"(sourceSets.main.get().output)
    "captureCompileOnly"(wardenJar)
}

kotlin.target.compilations.named("capture") {
    associateWith(kotlin.target.compilations.getByName("main"))
}

// Feed without a site: the memory backend, the sample content, Warden's authentication off. The configuration files
// are committed in example-config/ and copied into a run folder by the two tasks below, so nobody writes them by hand.
val exampleConfig = layout.projectDirectory.dir("example-config")
val devRunDir = layout.projectDirectory.dir("run")
val captureRunDir = layout.buildDirectory.dir("capture-run")

// For runClient and runServer: copies the example configuration into run/config. A file already there is kept (it may
// hold a site's token), unless -Poverwrite=true.
tasks.register("prepareMemoryRun") {
    group = "lnbphone"
    description = "Copies example-config/ into run/config so that runClient and runServer use the memory backend (-Poverwrite=true replaces existing files)."
    val overwrite = providers.gradleProperty("overwrite").map { it == "true" }.orElse(false)
    inputs.dir(exampleConfig)
    doLast {
        val target = devRunDir.dir("config").asFile.apply { mkdirs() }
        exampleConfig.asFile.listFiles { f -> f.isFile }!!.forEach { file ->
            val to = target.resolve(file.name)
            if (to.exists() && !overwrite.get()) {
                logger.lifecycle("Kept {} (it exists; -Poverwrite=true replaces it)", to)
            } else {
                file.copyTo(to, overwrite = true)
                logger.lifecycle("Wrote {}", to)
            }
        }
    }
}

// The capture run has a game folder of its own, rebuilt before every run, so a capture never depends on what a
// developer left in run/ and never changes it.
val prepareCapture = tasks.register("prepareCapture") {
    group = "lnbphone"
    description = "Writes the capture run's game folder (build/capture-run): the example configuration and options.txt."
    inputs.dir(exampleConfig)
    outputs.dir(captureRunDir)
    doLast {
        val dir = captureRunDir.get().asFile
        val config = dir.resolve("config").apply { mkdirs() }
        exampleConfig.asFile.listFiles { f -> f.isFile }!!.forEach { it.copyTo(config.resolve(it.name), overwrite = true) }
        // Without it a fresh game directory stops at the first-launch accessibility screen.
        dir.resolve("options.txt").writeText("onboardAccessibility:false\n")
    }
    // Always run: the folder is cheap to write, and a hand-edited copy must not survive.
    outputs.upToDateWhen { false }
}

neoForge {
    mods.named(property("mod_id").toString()) {
        sourceSet(capture)
    }

    runs {
        // Unattended client that plays a Feed capture scenario against the memory backend and quits (TESTING.md):
        //   ./gradlew :apps:feed:runCapture -Pcapture=root|guest|thread|compose -PcaptureDir=<absolute dir>
        //     [-PcaptureLang=fr_fr|en_us] [-PcaptureScale=<GUI scale>] [-PcaptureSize=<w>x<h>] [-PcaptureUser=<name>]
        // Its game folder is build/capture-run, written by prepareCapture from example-config/ (the memory backend with
        // the sample seed, "Visiteur_42" a guest, Warden's authentication off). The memory backend and the app share the
        // sample's fixed clock, so times read as the art direction draws them. Minecraft allows GUI scale n only from a
        // window of 320n x 240n pixels: the harness stops with a message when the window is too small for the scale.
        register("capture") {
            client()
            gameDirectory = captureRunDir
            taskBefore(prepareCapture)
            val scenario = providers.gradleProperty("capture").getOrElse("root")
            systemProperty("lnbphone_feed.capture", scenario)
            systemProperty("lnbphone_feed.memory.clock", "sample")
            providers.gradleProperty("captureDir").orNull?.let { systemProperty("lnbphone_feed.capture.dir", it) }
            providers.gradleProperty("captureLang").orNull?.let { systemProperty("lnbphone_feed.capture.lang", it) }
            providers.gradleProperty("captureScale").orNull?.let { systemProperty("lnbphone_feed.capture.scale", it) }
            val user = providers.gradleProperty("captureUser").getOrElse(if (scenario == "guest") "Visiteur_42" else "Cam_Roux")
            val (width, height) = providers.gradleProperty("captureSize").getOrElse("1280x960").split("x")
            programArguments.addAll("--username", user, "--width", width, "--height", height)
        }
    }
}
