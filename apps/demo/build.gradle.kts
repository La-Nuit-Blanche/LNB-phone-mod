plugins {
    id("lnbphone.app")
}

neoForge {
    runs {
        // Unattended client that plays a capture scenario (see client/capture) and quits:
        //   ./gradlew :apps:demo:runCapture -Pcapture=smooth [-PcaptureDir=<absolute dir>]
        // The window is large enough for GUI scale 4 (Minecraft allows scale n only from 320n x 240n pixels).
        register("capture") {
            client()
            systemProperty("lnbphone.capture", providers.gradleProperty("capture").getOrElse("smooth"))
            providers.gradleProperty("captureDir").orNull?.let { systemProperty("lnbphone.capture.dir", it) }
            // -PcaptureWidth=1600 -PcaptureHeight=1200 makes room for GUI scale 5.
            programArguments.addAll(
                "--width", providers.gradleProperty("captureWidth").getOrElse("1280"),
                "--height", providers.gradleProperty("captureHeight").getOrElse("960"),
            )
        }
    }
}
