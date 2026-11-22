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
