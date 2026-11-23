pluginManagement {
    // Build logic shared by the phone and every app (see build-logic/).
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download a matching JDK if none is installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "lnb-phone"

include(":phone")
include(":apps:demo")
include(":tools:sprites")

// Feed compiles against LNB Warden, whose jar is never committed (apps/feed/README.md says how to get it). Without
// the jar, Feed is left out of the build, with a warning, so that a fresh clone (and the CI, until it builds Warden)
// still builds the phone.
val wardenJars = file("apps/feed/libs").listFiles { file -> file.name.startsWith("lnbwarden") && file.name.endsWith(".jar") }
if (!wardenJars.isNullOrEmpty()) {
    include(":apps:feed")
} else {
    logger.warn("apps/feed is left out of the build: no lnbwarden*.jar in apps/feed/libs (see apps/feed/README.md)")
}
