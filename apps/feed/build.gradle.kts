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

neoForge {
    mods.named(property("mod_id").toString()) {
        sourceSet(capture)
    }

    runs {
        // Unattended client that plays a Feed capture scenario against the memory backend and quits:
        //   ./gradlew :apps:feed:runCapture [-Pcapture=root|guest] [-PcaptureDir=<absolute dir>] [-PcaptureUser=<name>]
        // It needs run/config/lnbphone_feed-server.toml with backend.kind = "memory", memory.seed = "sample" (and
        // "Visiteur_42" in memory.guests for the guest scenario), and Warden's auth.mode = "OFF". The memory backend
        // and the app share the sample's fixed clock, so times read as the art direction draws them.
        register("capture") {
            client()
            val scenario = providers.gradleProperty("capture").getOrElse("root")
            systemProperty("lnbphone_feed.capture", scenario)
            systemProperty("lnbphone_feed.memory.clock", "sample")
            providers.gradleProperty("captureDir").orNull?.let { systemProperty("lnbphone_feed.capture.dir", it) }
            val user = providers.gradleProperty("captureUser").getOrElse(if (scenario == "guest") "Visiteur_42" else "Cam_Roux")
            programArguments.addAll("--username", user, "--width", "1280", "--height", "960")
        }
    }
}
