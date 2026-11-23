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

- **Feed**, the first real app (`apps/feed`, mod `lnbphone_feed`): a Twitter-like for the server, with the server's
  announcements and the players' posts, replies and likes, pinned announcements, anonymous posts, and a notification
  for each announcement and each reply. Its data lives on LNB's website, so the game and the browser show the same
  posts; the Minecraft server calls the site (through the connection of LNB Warden), never the player's game. It needs
  LNB Warden and the website; for development it has an in-memory stand-in for the site. See
  [`apps/feed/README.md`](apps/feed/README.md).

The phone and Feed are in English, and in French for players whose game is in French.

## Documents

- [`docs/writing-an-app.md`](docs/writing-an-app.md): how to write an app, through the example app Ping.
- [`apps/demo/README.md`](apps/demo/README.md): what is in the demo mod, the example app and the development tools.
- [`apps/feed/README.md`](apps/feed/README.md): Feed, for a server owner (install, configuration, the command) and for
  a developer (layout, backends, running, tests, captures).
- [`docs/feed-app.md`](docs/feed-app.md): how Feed is designed: its modules, the backend, the payloads, the server side
  and the client's state. A second, larger example of an app, next to Ping.
- [`TESTING.md`](TESTING.md): what to try in the game before a release, and the automated checks.
- [`design/art-direction.md`](design/art-direction.md): the look, sizes, timings and strings of the phone and of Feed.

## Install

`lnbphone` is required on the server and on every client, with Kotlin for Forge. An app's mod is required on both
sides too. The phone depends on nothing else. Each player's settings are in `config/lnbphone-client.json` on their
computer.

Feed (`lnbphone_feed`) is installed with the phone, on the server and on every client, and also needs LNB Warden
(0.5.0 or later, not part of this repository). Warden has no releases: build its jar from
`https://github.com/Charles-Mahoudeau/lnb-warden` with `./gradlew jar` (the commands are under [Building](#building)), or
get it from whoever runs the server's mods. Feed is built and tested against Warden 0.5.0 at commit `5a9dba1`, the
commit CI pins (`ref` in `.github/workflows/build.yml`). `apps/feed/README.md` has the details and Feed's
configuration.

## Layout

- `phone/`: the phone, mod `lnbphone`. Released with Feed (see [Releasing](#releasing)). The API for apps is in its
  `api` package.
- `apps/<name>/`: apps, each a mod of its own that depends on `lnbphone`.
- `apps/feed/`: Feed, the social app, mod `lnbphone_feed`. Shipped with the phone.
- `apps/demo/`: Ping, the example app, and the development tools (the UI kit's gallery, "Post sample", a feed of
  made-up posts that shows the kit and is not the Feed app, test screens, `/phonedemo`, the screenshot harness). Never
  shipped.
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

Feed compiles against LNB Warden, which is in no Maven repository and is never committed. A fresh clone gets its jar by
building it from Warden's public repository, once:

```
git clone https://github.com/Charles-Mahoudeau/lnb-warden
cd lnb-warden
git checkout 5a9dba1
./gradlew jar
cp build/libs/lnbwarden-*.jar <this repository>/apps/feed/libs/
```

(`apps/feed/libs/` is ignored by git; create it if it isn't there. `5a9dba1` is Warden 0.5.0, the commit CI pins and
builds the same way.)
Without that jar a local build leaves Feed out, with a warning, and builds the rest. Feed's jar is
`apps/feed/build/libs/lnbphone_feed-<version>.jar`.

## Running

```
./gradlew :apps:demo:runClient
./gradlew :apps:demo:runServer
```

Both load the phone and the demo mod. The game runs in `apps/demo/run/`; the server needs `eula=true` in
`apps/demo/run/eula.txt`. The server listens on 25565; `-Pserver_port=25566` picks another port, for servers from
several checkouts at once. Every app has the same two runs (`./gradlew :apps:<name>:runClient`).

Feed runs the same way, in `apps/feed/run/`, with the phone, Feed and Warden loaded (not the demo). Without a website,
use its in-memory backend with the sample posts:

```
./gradlew :apps:feed:prepareMemoryRun
./gradlew :apps:feed:runClient
```

`prepareMemoryRun` copies `apps/feed/example-config/` into `apps/feed/run/config`, so no file is written by hand. In a
single-player world you are the only player; start `:apps:feed:runServer` and join it with `runClient` to be two.
`apps/feed/README.md` says more, and how to run Feed against a local copy of the site.

## Screenshots

```
./gradlew :apps:demo:runCapture -Pcapture=<scenario> [-PcaptureDir=<absolute directory>]
```

Starts a client that plays a capture scenario by itself and quits. The scenarios and their options are listed in
[`TESTING.md`](TESTING.md). Feed has its own, which sets its game folder up itself:

```
./gradlew :apps:feed:runCapture -Pcapture=root|guest|thread|compose [-PcaptureDir=<absolute directory>]
```

## Releasing

A tag `v<version>` (for example `v0.2.0`) makes CI build every jar as that version and publish a GitHub release with
two jars: `lnbphone-<version>.jar` (the phone) and `lnbphone_feed-<version>.jar` (Feed), whose dependency on the phone
is written by the build as `[<version>, next minor)`, so the two load together. The demo's jar and LNB Warden's are not
part of a release. A branch build uploads both jars as artifacts without releasing them.

## License

AGPL-3.0-or-later.

The bundled font files in `phone/src/main/resources/assets/lnbphone/font/` are not covered by that licence: Inter
(`inter_*.ttf`) is © The Inter Project Authors and Noto Emoji (`noto_emoji.ttf`, version 3.002, unmodified) is
© Google LLC, both under the SIL Open Font License 1.1 (texts in `inter_ofl.txt` and `noto_emoji_ofl.txt` next to
them), which allows bundling them but not relicensing them.
