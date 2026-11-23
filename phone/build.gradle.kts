plugins {
    id("lnbphone.mod")
}

// Test support for app authors (PhoneTester): plays an app's screens without the game. A source set of its own, so it
// is never in the jar a player installs; compiled against the phone's internals (the navigator, the sound output),
// and loaded as part of the phone wherever its classes are (unit tests, development runs), where nothing calls it
// unless a test does. An app's tests get it through the lnbphone.app plugin.
val testing: SourceSet = sourceSets.create("testing")

configurations.named(testing.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
neoForge.addModdingDependenciesTo(testing)

dependencies {
    "testingImplementation"(sourceSets.main.get().output)
    testImplementation(testing.output)
}

kotlin.target.compilations.named("testing") {
    associateWith(kotlin.target.compilations.getByName("main"))
}

neoForge.mods.named(property("mod_id").toString()) {
    sourceSet(testing)
}
