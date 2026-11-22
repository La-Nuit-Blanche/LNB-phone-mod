// The phone's sprite generator: every PNG under phone/.../textures/gui is drawn by this code, from the numbers in
// design/art-direction.md. Change a number, run `./gradlew :tools:sprites:generateSprites`, commit the PNGs.

plugins {
    id("lnbphone.tool")
}

val guiTextures = rootProject.layout.projectDirectory.dir("phone/src/main/resources/assets/lnbphone/textures/gui")
val mainClassName = "fr.bde_teknantes.lnb.phone.sprites.MainKt"

// Pixels per dp of the smooth set. 2 is the art direction's value; -PpxPerDp=3 re-exports it at another density.
val pxPerDp = providers.gradleProperty("pxPerDp").orElse("2")

tasks.register<JavaExec>("generateSprites") {
    group = "lnbphone"
    description = "Draws every phone sprite into the phone's assets."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs("-Djava.awt.headless=true")
    args("--out", guiTextures.asFile.absolutePath, "--px-per-dp", pxPerDp.get())
}

tasks.register<JavaExec>("contactSheet") {
    group = "lnbphone"
    description = "Draws every sprite, enlarged and labelled, into one PNG for checking by eye (-Psheet=<path>)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    jvmArgs("-Djava.awt.headless=true")
    val sheet = providers.gradleProperty("sheet")
        .orElse(layout.buildDirectory.file("contact-sheet.png").map { it.asFile.absolutePath })
    args("--sheet", sheet.get(), "--px-per-dp", pxPerDp.get())
}

tasks.test {
    // The test checks the committed PNGs against what the generator draws now.
    systemProperty("lnbphone.guiTextures", guiTextures.asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    inputs.dir(guiTextures)
}
