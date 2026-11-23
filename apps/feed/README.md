# LNB Feed

Feed, the LNB Phone's social app (mod `lnbphone_feed`): the server's announcements and the players' posts, stored on
LNB's website and shown the same in game and in the browser. Its design is [`docs/feed-app.md`](../../docs/feed-app.md);
the contract with the site is `docs/feed-mod.md` in the LNB-Website repository.

Status: the server side, the payloads and the backends are built; the app's screens aren't yet (the app opens on a
placeholder line).

## Install

Required on the server and on every client, with:

| Mod              | Version                                        |
| ---------------- | ---------------------------------------------- |
| LNB Phone        | 0.1.1 up to, not including, 0.2 (written by the build) |
| LNB Warden       | 0.5.0 or newer                                 |
| Kotlin for Forge | 5.12 or newer                                  |

The server reaches the site with Warden's `api.url` and `api.token` (`config/lnbwarden-server.toml`); the token never
leaves the server. Feed's own settings are in `config/lnbphone_feed-server.toml`, read when the server starts:

```toml
[backend]
kind = "site"        # site: LNB's website. memory: an in-process stand-in, for development. Nothing is kept.

[memory]
seed = "none"        # none | sample (the art direction's sample posts)
guests = []          # usernames that read as guests
rateLimits = true    # the site's per-account limits
```

`/feed announcer add|remove <username>` and `/feed announcer list` give and take the site's announcer role, for site
moderators in game and for the server console.

## Building

LNB Warden is in no Maven repository and is never committed. Build its jar and put it in `apps/feed/libs/`:

```
git clone https://github.com/Charles-Mahoudeau/lnb-warden
cd lnb-warden
./gradlew jar
cp build/libs/lnbwarden-*.jar <this repository>/apps/feed/libs/
```

`libs/` is ignored by git. Without the jar, a local build still builds the phone but leaves Feed out (Gradle says so
with a warning); on CI (`CI=true`) a missing jar fails the build. The CI builds Warden from its public repository
first. Then, from the repository's root: `./gradlew :apps:feed:build`.

## Running without a site

`./gradlew :apps:feed:runClient` or `:apps:feed:runServer` (the game runs in `apps/feed/run/`, Warden is loaded next
to Feed). With `backend.kind = "memory"` in `apps/feed/run/config/lnbphone_feed-server.toml`:

- operators (permission level 2: `/op`, or single player with cheats) are moderators, and so announcers;
- `/feed announcer add <name>` makes an announcer; a name under `memory.guests` reads as a guest;
- `memory.seed = "sample"` loads the art direction's sample content, as seen by `Cam_Roux` (the mockups' viewer);
- `/feed dev offline on`, `/feed dev disabled on` and `/feed dev writing off` show the unavailable, switched-off and
  writing-paused states (operators, memory mode only);
- the system property `lnbphone_feed.memory.clock` fixes the memory backend's clock: `sample` for the moment the
  mockups draw (12 November 2026, 14:34 in Paris), or an ISO instant. The screenshot harness uses it.

Warden itself must let the dev account in: `auth.mode = "off"` in `apps/feed/run/config/lnbwarden-server.toml` for a
run without a site.

## Running against a local site

As `docs/feed-mod.md` section 14 says: the site on `http://localhost:3000` with `feed` on, Warden's `api.url` and
`api.token` pointing at it, `backend.kind = "site"`.
