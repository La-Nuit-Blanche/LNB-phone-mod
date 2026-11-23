plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.148")
    // Must match the Kotlin version bundled by Kotlin for Forge (see kff_version in gradle.properties).
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.4.0")
}
