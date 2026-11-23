# LNB Feed

Feed is the LNB Phone's social app (mod `lnbphone_feed`): the server's announcements and the players' posts, replies and
likes, shown the same in the game and in the browser. Its data is stored on LNB's website. The player's game never
talks to the site: it talks to the Minecraft server, which calls the site with the connection of LNB Warden, and the
site's address and secret never leave the server.

This page has two parts: [for a server owner](#for-a-server-owner) and [for a developer](#for-a-developer). Feed's
design is [`docs/feed-app.md`](../../docs/feed-app.md); the contract with the website is `docs/feed-mod.md` in the LNB
website's repository.

## For a server owner

### What to install

Feed is required on the server and on every client, with what the phone needs, plus LNB Warden:

| Mod | Version | Where it comes from |
| --- | --- | --- |
| LNB Feed (`lnbphone_feed-<version>.jar`) | the same version as the phone | the GitHub release of this repository |
| LNB Phone (`lnbphone-<version>.jar`) | 0.2.0 up to, not including, 0.3 (for Feed 0.2.0; the build writes the range) | the same release |
| LNB Warden (`lnbwarden-<version>.jar`) | 0.5.0 or newer; Feed is built and tested against 0.5.0, commit `5a9dba1` | Warden has no releases: build it (below), or get it from whoever runs the server's mods |
| NeoForge | 21.1.264 or newer, for Minecraft 1.21.1 | as for the phone |
| Kotlin for Forge | 5.12 or newer | as for the phone |

A release holds the phone's jar and Feed's jar, built from the same tag so that they load together. A client that lacks
one of them is refused at the connection with a message naming it. A player's own settings stay in the phone
(`config/lnbphone-client.json` on their computer); Feed has none.

LNB Warden is built from its public repository, `https://github.com/Charles-Mahoudeau/lnb-warden`, with Java 21:

```
git clone https://github.com/Charles-Mahoudeau/lnb-warden
cd lnb-warden
git checkout 5a9dba1
./gradlew jar
```

The jar is `build/libs/lnbwarden-0.5.0.jar`. `5a9dba1` is the commit Feed is built and tested against, the one this
repository's CI pins (`ref` in `.github/workflows/build.yml`); a newer Warden is allowed but untested.

Until the website has its own side switched on, Feed shows "Feed is switched off": the `feed` flag on the site ships
switched off. Players write only while `feed.writing` is on too; while it is off they read.

### Where the site's address and secret come from

Feed does not have its own address or token. It uses LNB Warden's: `api.url` and `api.token` in
`config/lnbwarden-server.toml`, the same file and the same secret as for Warden itself, so the secret lives in one place
and is never sent to players. With either one empty, Feed is unavailable and says so (below). Warden's `auth.mode` decides
who may join: `required` (every player must be allowed by the site), `optional` (players without an LNB account join as
guests, who can read Feed but not write) or `off` (Warden checks nobody and makes no API call of its own: for
development only). `off` is Warden's alone: Feed with `kind = "site"` still calls the site whenever `api.url` and
`api.token` are set.

### Feed's configuration: `config/lnbphone_feed-server.toml`

Created on the first start, in the server's `config` folder; read when the server starts, so a change needs a restart.
It is never sent to players.

```toml
[backend]
kind = "site"

[memory]
seed = "none"
guests = []
rateLimits = true
```

| Option | Default | What it does |
| --- | --- | --- |
| `backend.kind` | `"site"` | Where Feed's data lives. `"site"`: LNB's website, through Warden's `api.url` and `api.token`. `"memory"`: an in-process stand-in for the site, for development and tests, where nothing is kept when the server stops, every player counts as having an LNB account, and operators are moderators. Never use `"memory"` on a real server. |
| `memory.seed` | `"none"` | With `kind = "memory"` only. `"none"` starts empty; `"sample"` loads sample posts, replies, likes and pins. |
| `memory.guests` | `[]` | With `kind = "memory"` only. Usernames that count as guests (no LNB account): they can read but not write. |
| `memory.rateLimits` | `true` | With `kind = "memory"` only. Whether the site's per-account limits (for example three posts a minute) apply. |

A value that is not allowed is corrected to the default when the file is read (an unknown `kind` becomes `"site"`).

### Single-player and LAN worlds

Feed never contacts the site from a single-player world or a world opened to LAN: with `kind = "site"` it calls nothing
there, the log says `Feed calls LNB's website from dedicated servers only: unavailable in this world`, and the app shows
"Feed unavailable". This is the same rule as Warden's, which calls the site from a dedicated server only. The memory
backend works in any world.

### What players see when something is wrong

- **The site is unreachable, or does not answer within 10 seconds.** Feed opened with nothing loaded shows "Feed
  unavailable" with a "Retry" button. A Feed that is open keeps its lists readable and shows "Connection lost" with
  "Retry"; it tries again by itself about every 15 seconds and catches up when the site is back. Likes, posts, replies,
  pins and deletions that fail say so and keep what the player typed. Joining the server is never delayed by the site.
  The server logs the outage once, and again when it ends.
- **Feed is switched off on the site.** Feed opened with nothing loaded shows "Feed is switched off"; an open one reads
  as a lost connection. The server asks the site again every minute.
- **Writing is switched off on the site (`feed.writing`).** Players read, with no compose button, and a strip where the
  reply field was says "Writing is paused". Deleting and pinning go on: authors delete their own posts, moderators any
  post, announcers pin. The site sends no event when the flag changes: a player's Feed learns of it at its next state
  read, when Feed is opened, or when a post, reply or like is refused for it.
- **A player has no LNB account (a guest),** or is banned: they read, and a quiet line says why they cannot write.
- **Notifications** (a new announcement; a reply to a player's post or reply) are the phone's own: they are kept with
  the world for players who are offline, shown as a badge at their next login, and dropped after 30 days. The site
  queues them, and the server delivers them when it connects, when the site says there are some, and every five minutes.

### The `/feed announcer` command

```text
/feed announcer add <username>
/feed announcer remove <username>
/feed announcer list
```

Announcers are the players who may post announcements, pin and unpin them: the site stores the role (`feed-announcer`),
and its moderators are announcers too. The command gives and takes the role by asking the site.

**Who may run it:** a player whose LNB account is a moderator on the site (the `admin` or `warden-admin` role), and the
server console. Being a Minecraft operator is not enough: the site decides, and `/feed` does not even appear for anyone
else. A command block, a function or `execute as <moderator>` cannot run it in a moderator's name. `<username>` is a
Minecraft username, online or not; the server is in offline mode, so its UUID follows from the name. The answers are
in the player's language ("X can now post announcements.", "X is a moderator, so already posts announcements.", "X has
no LNB account.", and so on). A change takes effect on the player's phone within a second, with no new login. Pinning
and deleting posts have no command: they are done in the app, by announcers and moderators.

With `backend.kind = "memory"` the command also has `/feed dev offline|disabled|writing true|false`, for operators, to show
the unavailable, switched-off and writing-paused states.

## For a developer

### Layout

`apps/feed/` is a mod of its own built with the repository's `lnbphone.app` Gradle plugin, which adds the dependency on the
phone and the runs. [`docs/feed-app.md`](../../docs/feed-app.md) section 2 draws the module tree. In short, under
`src/main/kotlin/.../feed/`:

- `model/`, `rules/`, `network/`, `Feed.kt`, `FeedTarget.kt`: common code (the posts and their wire format, the site's
  text rule, the payloads), loaded on both sides.
- `server/`: the logical server only (a dedicated server, or a single-player game's integrated one): the requests from
  clients, their limits, the relay of the site's live changes, the rights cache, the notification queue and the command.
  `server/backend/` holds the interface and its two implementations.
- `client/`: the player's client only, reached from behind a dist check so that a dedicated server never loads it:
  the session (the client's state), the screens (`FeedScreen`, `ThreadScreen`, `ComposeScreen`) and the widgets.
- `src/capture/`: the screenshot harness, a source set that the jar never holds. `src/test/`: the unit tests.
- `example-config/`: the configuration files for development without a site (below).

The two backends answer the same interface (`FeedBackend`): `SiteBackend` calls the website with Warden's `ApiClient`
and keeps its live stream (`FeedEvents`) open; `MemoryBackend` applies the same rules in memory, so the app can be
developed, tested and photographed without a site.

### Building

LNB Warden is in no Maven repository and is never committed. Build its jar and put it in `apps/feed/libs/`:

```
git clone https://github.com/Charles-Mahoudeau/lnb-warden
cd lnb-warden
git checkout 5a9dba1
./gradlew jar
cp build/libs/lnbwarden-*.jar <this repository>/apps/feed/libs/
```

`libs/` is ignored by git, so create it if it is missing. Without the jar, a local build still builds the phone but leaves
Feed out (Gradle says so with a warning); on CI (`CI=true`) a missing jar fails the build. CI builds Warden from its
public repository at a pinned commit (`.github/workflows/build.yml`: to move it, build Warden at the new commit, check
Feed with `./gradlew build`, then set `ref` there and the commit in the `git checkout` lines of this page and of the
root `README.md`, and `lnbwarden_version_range` in `apps/feed/gradle.properties` if Feed now needs the newer Warden).
Then, from the repository's root:

```
./gradlew :apps:feed:build
```

builds Feed and runs its tests; the jar is `apps/feed/build/libs/lnbphone_feed-<version>.jar`. Feed's version is the
repository's (`mod_version` in the root `gradle.properties`, or `-Pmod_version=<version>`, which is what CI passes on a
tag), and its dependency on the phone is written for that version by the plugin.

### Running without a site

```
./gradlew :apps:feed:prepareMemoryRun
./gradlew :apps:feed:runClient        # or :apps:feed:runServer [-Pserver_port=25566]
```

The game runs in `apps/feed/run/` (git-ignored), with the phone, Feed and Warden loaded, not the demo.
`prepareMemoryRun` copies the files of `example-config/` into `apps/feed/run/config`: Feed on its memory backend with the
sample posts, `Visiteur_42` a guest, and Warden's `auth.mode = "OFF"` so that the development account may join. A file
that already exists there is kept (it may hold a site's token); `-Poverwrite=true` replaces it. The server needs
`eula=true` in `apps/feed/run/eula.txt`, which you write yourself. Then:

- operators (permission level 2: `/op`, or single player with cheats) are moderators, and so announcers;
- `/feed announcer add <name>` makes an announcer; a name under `memory.guests` reads as a guest;
- `memory.seed = "sample"` loads the art direction's sample content (its authors, posts 1 to 10, replies, likes and pins);
  the screenshot harness (below) shows it as the mockups' viewer, `Cam_Roux`, sees it;
- `/feed dev offline true`, `/feed dev disabled true` and `/feed dev writing false` show the unavailable, switched-off and
  writing-paused states (operators, memory mode only);
- the system property `lnbphone_feed.memory.clock` fixes the memory backend's clock: `sample` for the moment the mockups
  draw (12 November 2026, 14:34 in Paris), or an ISO instant. The screenshot harness uses it.

### Running against a local site

As `docs/feed-mod.md` section 14 of the website's repository says: the site on `http://localhost:3000` with the `feed`
flag on, Warden's `api.url` and `api.token` in `apps/feed/run/config/lnbwarden-server.toml` pointing at it, Warden's
`auth.mode` at `required` or `optional`, `backend.kind = "site"` in `lnbphone_feed-server.toml`, and the development
account linked through Warden's device flow on the site's `/link` page. (If `prepareMemoryRun` wrote the example files,
edit them: the example is the memory setup.)

### Tests

```
./gradlew :apps:feed:test
```

About 600 unit tests, which run with the game loaded: the text rule and the payloads, the memory backend against the
contract's test vectors, the site backend against a fake site, the server's rights, limits, relay, notifier and command,
the client's session, and the screens played through the phone's `PhoneTester`. `./gradlew build` runs them with
everything else. A dedicated server start belongs to every check (`TESTING.md` says how and what the log must not
contain).

### Screenshots

```
./gradlew :apps:feed:runCapture -Pcapture=root|guest|thread|compose -PcaptureDir=<absolute directory>
```

Optional: `-PcaptureLang=en_us`, `-PcaptureScale=<GUI scale>`, `-PcaptureSize=<w>x<h>`, `-PcaptureUser=<name>`. The task writes
the capture run's own game folder (`apps/feed/build/capture-run`) from `example-config/`, so it needs no setup and does
not touch `apps/feed/run`. `TESTING.md` lists what each scenario shows, what to do when the window is too small for a
GUI scale, and the harness's guards.

### The app's icon

Feed ships its own home icon, `assets/lnbphone_feed/textures/gui/feed.png`, not the phone's sprite. It is drawn, never by
hand, by `tools/sprites`: `./gradlew :tools:sprites:generateSprites` writes it from the art direction's glyph (section 7.4)
next to the phone's own, and a test fails when the committed PNG is stale.

### Releasing

A tag `v<version>` makes CI build the phone and Feed as that version and publish a release with both jars
(`lnbphone-<version>.jar` and `lnbphone_feed-<version>.jar`). Warden's jar is not part of it.
