# LNB Phone Demo

A mod for development, loaded with the phone in this repository's runs. Never shipped to players. It holds two things,
kept apart:

- **Ping**, the example app: `src/main/kotlin/.../demo/ping/`. Start there. [`docs/writing-an-app.md`](../../docs/writing-an-app.md)
  walks through it.
- **Development tools**, which is how the phone is checked: everything else.

## Ping

| File | Side | What |
|---|---|---|
| `ping/Ping.kt` | both | the app's id and start-up |
| `ping/PingNetwork.kt` | both | the three payloads: a ping, the server's answer, a ping received |
| `ping/PingServer.kt` | server | checks a ping (length, one a second, recipient online), passes it on with a notification, answers the sender |
| `ping/PingTarget.kt` | both | the notification's target, written and read back |
| `ping/client/PingClient.kt` | client | registers the app, opens a notification's target |
| `ping/client/PingSession.kt` | client | drafts, conversations (the newest 50 messages each), answers awaited, scroll position; kept until disconnection |
| `ping/client/PlayersScreen.kt` | client | the first screen: the players online |
| `ping/client/PingScreen.kt` | client | writing to one player |

Its strings are the `ping` keys of `lang/en_us.json` and `fr_fr.json`, its icon `textures/gui/ping.png`. Its tests are
`src/test/.../ping/PingTargetTest.kt`, `ping/client/PingSessionTest.kt` and `ping/client/PingScreenTest.kt` (the conversation
screen played by the phone's `PhoneTester`); `src/test/.../guide/` keeps the guide's samples true.

## Development tools

They stay because the screenshot harness and the tasks' checks rely on them. Don't take one as a model of how an app
should be written: some reach into the phone's internals, by reflection, to show or drive what no app can.

- **Apps on the home screen** (`client/DemoAppClient.kt`): "Hello" (`client/HelloScreen.kt`), a bare `AppScreen`
  drawn without the kit, where every click opens one more screen, to try navigation, and a right click opens a
  vanilla dialog over the phone; "UI kit" (`client/kit/Gallery.kt`,
  `KitPages.kt`), every widget of the kit in every state; "Post sample" (`client/kit/FeedScreens.kt`,
  `PostCard.kt`, `FeedSample.kt`), a feed of made-up posts (two tabs, a thread, Compose) built from the kit, the kit's
  showcase. It is not the Feed app, which is a mod of its own, [`apps/feed`](../feed/README.md); it has its own name and
  its own teal tile so that the two can't be mistaken for each other on one home screen.
  `-PdemoApps=<n>` adds n sample icons, to fill the home screen.
- **`/phonedemo`** (`DemoCommands.kt`, operators): `notify <players> [depth] [text]` sends a test notification from
  Hello, leading to its screen at that depth; `clear <players>` removes them.
- **Test sheets**: `client/smooth/SmoothTestScreen.kt` (the smooth drawing layer: type, shapes, icons, faces) and
  `client/notifications/BannerTestScreen.kt` (the notification banner), drawn as plain game screens.
- **The screenshot harness** (`client/capture/`): an unattended client that plays a scenario and quits. See
  [`TESTING.md`](../../TESTING.md). `HomeSlots` finds a home icon by its app's id.
- `scripts/gen_spike_sprites.py` and `textures/gui/spike/`: images the smooth layer's test sheet compares against.
