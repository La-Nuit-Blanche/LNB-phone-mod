# LNB Phone

A phone for LNB's Minecraft server, for NeoForge 1.21.1 (Kotlin for Forge). The goal: a key opens a phone in the
corner of the screen, and other mods add apps to it without the phone's code changing.

Work in progress: so far the repository holds the build and two empty mods, the phone and a demo app. The look,
sizes and strings of the phone and its apps are set in [`design/art-direction.md`](design/art-direction.md).

## Layout

- `phone/`: the phone, mod `lnbphone`. The only jar released.
- `apps/<name>/`: apps, each a mod of its own that depends on `lnbphone`.
- `apps/demo/`: an app for development, loaded with the phone in the runs below. Never shipped.
- `build-logic/`: the build shared by every mod. `lnbphone.mod` builds a mod; `lnbphone.app` adds the dependency on
  the phone and the runs. Each mod's id, name and description are in its own `gradle.properties`.

Versions are in the root `gradle.properties`, except those of the ModDevGradle and Kotlin plugins, in
`build-logic/build.gradle.kts`. Kotlin must be the version Kotlin for Forge bundles, so a change to `kff_version`
means checking both files.

## Building

```
./gradlew build
```

Needs Java 21 (`JAVA_HOME`). The phone's jar is `phone/build/libs/lnbphone-<version>.jar`.

## Running

```
./gradlew :apps:demo:runClient
./gradlew :apps:demo:runServer
```

Both load the phone and the demo app. The game runs in `apps/demo/run/`; the server needs `eula=true` in
`apps/demo/run/eula.txt`.
The server listens on 25565; `-Pserver_port=25566` picks another port, for servers from several checkouts at once.

## License

AGPL-3.0-or-later.
