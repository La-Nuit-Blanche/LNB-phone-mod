// Everything a mod of this repository needs to build: the phone and every app apply it. Values come from the root
// gradle.properties (versions, licence) and from the subproject's own gradle.properties (mod_id, mod_name...).

import java.util.Properties

plugins {
    `java-library`
    idea
    id("net.neoforged.moddev")
    kotlin("jvm")
}

// gradle.properties uses snake_case keys (MDK convention), so map them explicitly. Read from the project rather than
// providers.gradleProperty: only the project sees its own subproject gradle.properties.
fun prop(name: String): String = property(name).toString()

val modId = prop("mod_id")

version = prop("mod_version")
group = prop("mod_group_id")

base {
    archivesName = modId
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        name = "Kotlin for Forge"
        content { includeGroup("thedarkcolour") }
    }
}

neoForge {
    version = prop("neo_version")

    parchment {
        mappingsVersion = prop("parchment_mappings_version")
        minecraftVersion = prop("parchment_minecraft_version")
    }

    runs {
        configureEach {
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    // Unit tests run with the game and this mod loaded (FML's JUnit launcher), so they can use items and registries.
    // Kotlin is then loaded twice (Kotlin for Forge's and the test classpath's): kotlin.test assertions that use
    // reflection (assertIs, assertFailsWith) break, so tests use JUnit's assertInstanceOf and assertThrows instead.
    unitTest {
        enable()
        testedMod = mods.getByName(modId)
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${prop("kff_version")}")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The template sees the shared keys below plus every key of the subproject's own gradle.properties, so an app adds
// a value for its template (a version range...) there, without touching this file.
val sharedMetadataKeys = listOf(
    "minecraft_version",
    "minecraft_version_range",
    "neo_version_range",
    "loader_version_range",
    "kff_version",
    "mod_license",
    "mod_version",
    "mod_authors",
)
val ownMetadataKeys = Properties().apply {
    val own = file("gradle.properties")
    if (own.exists()) own.reader(Charsets.UTF_8).use(::load)
}.stringPropertyNames()

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = (sharedMetadataKeys + ownMetadataKeys).associateWith(::prop)
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main {
    resources.srcDir(generateModMetadata)
}
neoForge.ideSyncTask(generateModMetadata)

tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-run").get().asFile.apply { mkdirs() }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
