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
