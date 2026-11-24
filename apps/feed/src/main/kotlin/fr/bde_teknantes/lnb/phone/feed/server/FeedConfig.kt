package fr.bde_teknantes.lnb.phone.feed.server

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Feed's server settings, in `config/lnbphone_feed-server.toml` (`docs/feed-app.md` 3.4). Registered as a COMMON
 * config, like Warden's server config, so that it is never sent to players; read by the logical server only, when it
 * starts: a change needs a restart.
 *
 * The site's URL and the server's token are Warden's (`api.url` and `api.token` in `lnbwarden-server.toml`), so the
 * secret lives in one place.
 */
object FeedConfig {
    enum class Kind { SITE, MEMORY }

    enum class Seed { NONE, SAMPLE }

    private val BUILDER = ModConfigSpec.Builder()

    private val KIND: ModConfigSpec.ConfigValue<String> = BUILDER
        .push("backend")
        .comment(
            "site: LNB's website, through api.url and api.token in lnbwarden-server.toml.",
            "memory: in-process stand-in, for development and tests. Nothing is kept.",
        )
        .define("kind", "site") { it is String && Kind.entries.any { kind -> kind.name.equals(it, ignoreCase = true) } }

    private val SEED: ModConfigSpec.ConfigValue<String> = BUILDER
        .pop()
        .comment("Only read with backend.kind = \"memory\".")
        .push("memory")
        .comment("none: start empty. sample: the art direction's sample posts, replies, likes and pins.")
        .define("seed", "none") { it is String && Seed.entries.any { seed -> seed.name.equals(it, ignoreCase = true) } }

    private val GUESTS = BUILDER
        .comment("Usernames that read as guests (no LNB account): they can read but not write.")
        .defineListAllowEmpty("guests", emptyList<String>(), { "" }) { it is String }

    private val RATE_LIMITS: ModConfigSpec.BooleanValue = BUILDER
        .comment("Whether the site's per-account limits apply (3 posts a minute...).")
        .define("rateLimits", true)

    val SPEC: ModConfigSpec = BUILDER.pop().build()

    val kind: Kind get() = Kind.entries.firstOrNull { it.name.equals(KIND.get(), ignoreCase = true) } ?: Kind.SITE
    val seed: Seed get() = Seed.entries.firstOrNull { it.name.equals(SEED.get(), ignoreCase = true) } ?: Seed.NONE
    val guests: List<String> get() = GUESTS.get()
    val rateLimits: Boolean get() = RATE_LIMITS.get()
}
