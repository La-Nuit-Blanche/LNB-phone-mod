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

dependencies {
    compileOnly(wardenJar)
    localRuntime(wardenJar)
    testImplementation(wardenJar)
}
