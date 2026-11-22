// An app: a mod of its own under apps/, built against the phone and run with it.

plugins {
    id("lnbphone.mod")
}

// The phone's source set and mod id are read below, so the phone must be configured first.
evaluationDependsOn(":phone")
val phone = project(":phone")

dependencies {
    implementation(phone)
}

neoForge {
    // Runs and unit tests load the phone from its classes next to the app, so a change to either needs no jar.
    mods {
        register(phone.property("mod_id").toString()) {
            sourceSet(phone.the<SourceSetContainer>().getByName("main"))
        }
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
            // -Pserver_port=25566 lets servers from several checkouts run at once without fighting over 25565.
            programArguments.addAll("--port", findProperty("server_port")?.toString() ?: "25565")
        }
    }
}

// The phone versions an app accepts: from the one it was built against up to the next minor (next major from 1.0),
// where the phone's API may break. A newer phone then stops at the loader's "requires lnbphone" screen instead of
// failing in game with a NoSuchMethodError.
fun phoneVersionRange(version: String): String {
    val (major, minor) = Regex("""^(\d+)\.(\d+)""").find(version)?.destructured
        ?: error("The phone's version '$version' doesn't start with major.minor")
    val next = if (major == "0") "0.${minor.toInt() + 1}" else "${major.toInt() + 1}"
    return "[$version,$next)"
}

// Written by the plugin rather than by each app's template, so no app forgets it or gets the range wrong.
val phoneDependency = """
    |
    |# Added by the lnbphone.app plugin: the phone this app was built against.
    |[[dependencies.${property("mod_id")}]]
    |    modId="${phone.property("mod_id")}"
    |    type="required"
    |    versionRange="${phoneVersionRange(phone.version.toString())}"
    |    ordering="AFTER"
    |    side="BOTH"
    |""".trimMargin()

tasks.named<ProcessResources>("generateModMetadata") {
    inputs.property("phoneDependency", phoneDependency)
    doLast {
        destinationDir.resolve("META-INF/neoforge.mods.toml").appendText(phoneDependency)
    }
}
