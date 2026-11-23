// A development tool under tools/: plain Kotlin on the JVM, run from Gradle, never part of a mod jar.

plugins {
    kotlin("jvm")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
}

dependencies {
    // The root gradle.properties turns the stdlib off because Kotlin for Forge ships it to mods; a tool runs alone.
    implementation(kotlin("stdlib"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
