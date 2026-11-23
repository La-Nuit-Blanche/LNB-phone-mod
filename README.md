# LNB Phone

A phone for LNB's Minecraft server, for NeoForge 1.21.1 (Kotlin for Forge). A key opens a phone in the corner of the
screen, and the phone runs apps. Apps are mods of their own: other mods add them without the phone's code changing.

- **The phone**: the Up arrow (or a button on the survival inventory) slides it up into the bottom-right corner, or
  the centre of the screen if the player prefers. The world stays visible and the game isn't paused. A home screen
  of app icons, a status bar with the in-game time, the server's latency and the player's hunger as a battery.
- **Notifications**: an app's server side notifies players, connected or not. They show as badges on the app's icon,
  as a banner over the open phone, or as the phone peeking up from the corner when it is closed, with a chime;
  opening one lands in the app on the right screen. They are kept per player on the server, saved with the world.
- **Settings**, the phone's built-in app: the phone's position, its sounds, and the notification preview, per player.
- **For app authors**: an API to register apps and navigate between screens, a UI kit (headers, lists, buttons, text
  fields, dialogs, snackbars...) in the phone's look, and the server-side notification API.

The first real app, Feed (a Twitter-like for the server), is planned, as is access to LNB's website; neither exists
yet. In English, and in French for players whose game is in French.

## Documents

- [`docs/writing-an-app.md`](docs/writing-an-app.md): how to write an app, through the example app Ping.
- [`apps/demo/README.md`](apps/demo/README.md): what is in the demo mod, the example app and the development tools.
- [`TESTING.md`](TESTING.md): what to try in the game before a release, and the automated checks.
- [`design/art-direction.md`](design/art-direction.md): the look, sizes, timings and strings of the phone and of Feed.

## Install

`lnbphone` is required on the server and on every client, with Kotlin for Forge. An app's mod is required on both
sides too. The phone depends on nothing else. Each player's settings are in `config/lnbphone-client.json` on their
computer.

## Layout

- `phone/`: the phone, mod `lnbphone`. The only jar released. The API for apps is in its `api` package.
- `apps/<name>/`: apps, each a mod of its own that depends on `lnbphone`.
- `apps/demo/`: Ping, the example app, and the development tools (the UI kit's gallery, a sample of Feed, test
  screens, `/phonedemo`, the screenshot harness). Never shipped.
- `tools/sprites/`: draws every image of the phone from the numbers of the art direction. Nothing is drawn by hand:
  to change an image, change its number there, run `./gradlew :tools:sprites:generateSprites` and commit the PNGs.
  `./gradlew :tools:sprites:contactSheet -Psheet=<file.png>` draws the whole set enlarged, for checking by eye.
- `build-logic/`: the build shared by every mod. `lnbphone.mod` builds a mod; `lnbphone.app` adds the dependency on
  the phone and the runs. Each mod's id and name are in its own `gradle.properties`, and so is its description
  unless its `en_us.json` has one (key `fml.menu.mods.info.description.<mod id>`), which the build then uses.

Versions are in the root `gradle.properties`, except those of the ModDevGradle and Kotlin plugins, in
`build-logic/build.gradle.kts`. Kotlin must be the version Kotlin for Forge bundles, so a change to `kff_version`
means checking both files.

## Building

```
./gradlew build
```

Needs Java 21 (`JAVA_HOME`). Runs the unit tests too. The phone's jar is `phone/build/libs/lnbphone-<version>.jar`.

## Running

```
./gradlew :apps:demo:runClient
./gradlew :apps:demo:runServer
```

Both load the phone and the demo mod. The game runs in `apps/demo/run/`; the server needs `eula=true` in
`apps/demo/run/eula.txt`. The server listens on 25565; `-Pserver_port=25566` picks another port, for servers from
several checkouts at once. Every app has the same two runs (`./gradlew :apps:<name>:runClient`).

## Screenshots

```
./gradlew :apps:demo:runCapture -Pcapture=<scenario> [-PcaptureDir=<absolute directory>]
```

Starts a client that plays a capture scenario by itself and quits. The scenarios and their options are listed in
[`TESTING.md`](TESTING.md).

## License

AGPL-3.0-or-later.

The bundled font files in `phone/src/main/resources/assets/lnbphone/font/` are not covered by that licence: Inter
(`inter_*.ttf`) is © The Inter Project Authors and Noto Emoji (`noto_emoji.ttf`, version 3.002, unmodified) is
© Google LLC, both under the SIL Open Font License 1.1 (texts in `inter_ofl.txt` and `noto_emoji_ofl.txt` next to
them), which allows bundling them but not relicensing them.
