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
  the phone and the runs. Versions are in the root `gradle.properties`, and each mod's id, name and description in
  its own `gradle.properties`.

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

## Screenshots

```
./gradlew :apps:demo:runCapture -Pcapture=smooth [-PcaptureDir=<absolute directory>]
```

Starts a client that plays a capture scenario by itself (GUI scale changes, screens, a throwaway world) and quits.
Shots go to `captureDir`, or `apps/demo/run/captures/<scenario>/`. Scenarios are in the demo's
`client/capture/CaptureScenarios.kt`.

## License

AGPL-3.0-or-later.
