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
        }
    }
}
