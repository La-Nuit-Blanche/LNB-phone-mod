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
// the jar, a local build leaves Feed out, with a warning, so that a fresh clone still builds the phone; on CI (which
// builds Warden first) a missing jar fails the build, so that Feed is never silently left unchecked.
val wardenJars = file("apps/feed/libs").listFiles { file -> file.name.startsWith("lnbwarden") && file.name.endsWith(".jar") }
if (!wardenJars.isNullOrEmpty()) {
    include(":apps:feed")
} else if (System.getenv("CI") == "true") {
    throw GradleException("No lnbwarden*.jar in apps/feed/libs: the CI must build LNB Warden before Feed")
} else {
    logger.warn("apps/feed is left out of the build: no lnbwarden*.jar in apps/feed/libs (see apps/feed/README.md)")
}
