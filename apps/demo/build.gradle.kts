plugins {
    id("lnbphone.app")
}

neoForge {
    runs {
        // Unattended client that plays a capture scenario (see client/capture) and quits:
        //   ./gradlew :apps:demo:runCapture -Pcapture=smooth [-PcaptureDir=<absolute dir>] [-PcaptureSize=1920x1080]
        //   [-PdemoApps=<n>]
        // The window is large enough for GUI scale 4 (Minecraft allows scale n only from 320n x 240n pixels).
        // demoApps fills the home screen with n sample apps (see DemoAppClient).
        register("capture") {
            client()
            systemProperty("lnbphone.capture", providers.gradleProperty("capture").getOrElse("smooth"))
            providers.gradleProperty("captureDir").orNull?.let { systemProperty("lnbphone.capture.dir", it) }
            providers.gradleProperty("demoApps").orNull?.let { systemProperty("lnbphone.demo.apps", it) }
            val (width, height) = providers.gradleProperty("captureSize").getOrElse("1280x960").split("x")
            programArguments.addAll("--width", width, "--height", height)
        }
    }
}

// GuideSamplesTest checks the code samples of docs/writing-an-app.md against the files they quote: the guide and every
// file one of its samples names are inputs, so the test runs again when any of them changes.
val guide = rootProject.file("docs/writing-an-app.md")
val guideSources = provider {
    Regex("<!-- sample: (\\S+) -->").findAll(guide.readText()).map { rootProject.file(it.groupValues[1]) }.filter { it.isFile }.toList()
}
tasks.test {
    systemProperty("lnbphone.repoRoot", rootDir.absolutePath)
    inputs.file(guide).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(guideSources).withPathSensitivity(PathSensitivity.RELATIVE)
}
