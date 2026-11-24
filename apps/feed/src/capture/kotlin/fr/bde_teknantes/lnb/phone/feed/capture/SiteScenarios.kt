package fr.bde_teknantes.lnb.phone.feed.capture

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.client.FeedAvailability
import fr.bde_teknantes.lnb.phone.feed.client.FeedClient
import fr.bde_teknantes.lnb.phone.feed.client.widgets.CardLook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardLayout
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Scenarios against LNB's real website and a dedicated server (`TESTING.md`, "Feed against a local website"): the
 * client joins `-PcaptureServer=<host:port>` instead of opening a world, and plays the part of a person whose
 * Minecraft account the site knows. What other people do, it does through the site's browser routes, as them, with
 * the session cookies of `FEED_LIVE_COOKIES` (`FEED_LIVE_SITE` is the site); what it can't do from the client (kill the
 * site, restart the server) it asks the run's orchestrator for ([FeedCapture.Script.control]).
 *
 * Logs name post IDs, never a post's text or an anonymous post's author.
 */
object SiteScenarios {
    fun get(name: String): (FeedCapture.Script.() -> Unit)? = when (name) {
        "site-player" -> { { player() } }
        "site-moderator" -> { { moderator() } }
        "site-announcer" -> { { announcer() } }
        "site-guest" -> { { guest() } }
        "site-banned" -> { { banned() } }
        "site-outage" -> { { outage() } }
        "site-return" -> { { comeBack() } }
        "site-flags" -> { { flags() } }
        else -> null
    }

    private const val AWAY_X = -20
    private const val GLOBAL_TAB_X = 192
    private const val ANNONCES_TAB_X = 64

    private val server: String get() = System.getProperty("lnbphone_feed.capture.server") ?: error("No -PcaptureServer")
    private val session get() = FeedClient.session

    // ---- The site, as the people the scenario plays with ----

    private object Site {
        private val base = System.getenv("FEED_LIVE_SITE")?.trimEnd('/').orEmpty()
        private val cookies: JsonObject by lazy { JsonParser.parseString(System.getenv("FEED_LIVE_COOKIES") ?: "{}").asJsonObject }

        // HTTP/1.1, as Warden's ApiClient on loopback.
        private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

        fun call(who: String, method: String, path: String, body: String?): Pair<Int, JsonObject?> {
            val request = HttpRequest.newBuilder(URI.create(base + path))
                .header("cookie", cookies.get(who)?.asString ?: error("No cookie for $who in FEED_LIVE_COOKIES"))
                .header("origin", base)
                .header("accept", "application/json")
                .apply { if (body != null) header("content-type", "application/json") }
                .method(method, if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(20))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            val json = response.body().takeIf { it.isNotBlank() }?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
            return response.statusCode() to json
        }
    }

    /** A call of the site's browser routes as [who], when the step runs; [then] gets the answer's body. */
    private fun FeedCapture.Script.site(who: String, method: String, path: String, body: String? = null, then: (JsonObject?) -> Unit = {}) = run {
        val (status, json) = Site.call(who, method, path, body)
        FeedMod.LOGGER.info("Feed capture: {} {} as {} answered {}", method, path, who, status)
        then(json)
    }

    private fun FeedCapture.Script.flag(key: String, enabled: Boolean) = site("root", "PATCH", "/api/admin/flags/$key", """{"enabled":$enabled}""")

    private fun newPost(feed: String, text: String) = """{"idempotencyKey":"${UUID.randomUUID()}","feed":"$feed","text":"$text","anonymous":false}"""

    private fun newReply(text: String) = """{"idempotencyKey":"${UUID.randomUUID()}","replyTo":null,"text":"$text","anonymous":false}"""

    private fun JsonObject?.postId(): UUID? = this?.getAsJsonObject("post")?.get("id")?.asString?.let(UUID::fromString)

    // ---- Where things are ----

    private fun height(post: PostView) = PostCardLayout(post, CardLook(more = false, pin = false), "", TextMetrics, FeedClient.strings).height

    /** The centre of [id]'s heart in the feed [kind], in viewport dp. */
    private fun heart(kind: FeedKind, id: UUID): Pair<Float, Float> {
        val list = session.feed(kind)
        var y = 44f - list.scroll.offset
        for (each in list.pinned + list.posts) {
            val p = session.post(each) ?: continue
            if (each == id) return 128f to y + PostCardLayout(p, CardLook(more = false, pin = false), "", TextMetrics, FeedClient.strings).actionY + 14f
            y += height(p)
        }
        error("$id isn't in $kind")
    }

    private fun firstPost(kind: FeedKind): UUID? = session.feed(kind).posts.firstOrNull()

    // ---- Steps ----

    /** The pointer off the phone, and the game's own toasts (a server's unsigned chat) gone: before a shot. */
    private fun FeedCapture.Script.away() {
        mouseTo(AWAY_X, 200)
        run { Minecraft.getInstance().toasts.clear() }
        waitTicks(2)
    }

    private fun FeedCapture.Script.join() {
        language(FeedCapture.Options.language)
        guiScale(FeedCapture.Options.scale)
        joinServer(server)
        waitTicks(60)
    }

    private fun FeedCapture.Script.openFeedAndWait() {
        openFeed()
        waitUntil(400, "Feed's first page") { session.availability != FeedAvailability.LOADING && session.availability != FeedAvailability.UNKNOWN }
        waitTicks(30)
        away()
    }

    /** Feed opened afresh with nothing held, as after joining. */
    private fun FeedCapture.Script.reopenFresh() {
        run { session.clear() }
        openFeedAndWait()
    }

    private fun FeedCapture.Script.chat(text: String) = run { Minecraft.getInstance().setScreen(ChatScreen(text)) }

    private fun FeedCapture.Script.command(text: String) = run { Minecraft.getInstance().player!!.connection.sendCommand(text) }

    // ---- Scenarios ----

    /**
     * As Camille R. (`Cam_Roux`), a plain player: the site's content, a post made in game, what the browser does
     * reaching the open phone, the flags, the site killed and restarted, a notification while offline, the server
     * restarted.
     */
    private fun FeedCapture.Script.player() {
        join()
        openPhone()
        openFeedAndWait()
        shot("p01_annonces")
        log("Annonces loaded")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("p02_global")

        // A post made in game, then read back through the browser's routes.
        var gamePost: UUID? = null
        click(220, 334)
        waitTicks(10)
        run { session.compose.text.text = "Posté depuis le jeu, sur le serveur dédié." }
        waitTicks(2)
        click(200, 22)
        waitUntil(200, "the post's answer") { !session.compose.sending && session.compose.text.text.isEmpty() }
        waitTicks(10)
        away()
        shot("p03_posted_in_game")
        run { gamePost = session.feed(FeedKind.GLOBAL).posts.mapNotNull(session::post).firstOrNull { it.mine && it.text.startsWith("Posté depuis le jeu") }?.id }
        site("mika", "GET", "/api/feed/posts?feed=global") { json ->
            val ids = json?.getAsJsonArray("posts")?.map { it.asJsonObject.get("id").asString }.orEmpty()
            FeedMod.LOGGER.info("Feed capture: the post made in game ({}) is on the browser's Global: {}", gamePost, gamePost.toString() in ids)
        }

        // The browser, as other people, while Camille's Feed is open on Global at the top.
        var livePost: UUID? = null
        site("mika", "POST", "/api/feed/posts", newPost("global", "En direct du navigateur : quelqu'un au spawn ?")) { livePost = it.postId() }
        waitUntil(200, "the browser's post, live") { livePost != null && session.post(livePost!!) != null }
        waitTicks(6)
        shot("p04_live_post_from_browser")
        // A reply to Camille's post, in the browser: the banner, and the count moving.
        replyAs("mika", { gamePost }, "Je réponds depuis le navigateur !")
        waitUntil(200, "the reply's banner") { CaptureHooks.bannerShown }
        waitTicks(6)
        shot("p05_reply_banner")
        waitUntil(200, "the reply count") { gamePost?.let(session::post)?.replies == 1 }
        waitUntil(200, "the banner gone") { !CaptureHooks.bannerShown }
        away()
        shot("p06_reply_counted")
        likeAs("steve") { gamePost }
        waitUntil(200, "the like, live") { gamePost?.let(session::post)?.likes == 1 }
        waitTicks(4)
        shot("p07_like_from_browser")

        // An announcement and its pin from the browser, by an announcer: on Annonces, the group follows.
        var announcement: UUID? = null
        site("kael", "POST", "/api/feed/posts", newPost("announcements", "Maintenance du serveur dimanche à 9 h.")) { announcement = it.postId() }
        waitTicks(40)
        click(ANNONCES_TAB_X, 22)
        waitTicks(20)
        pinAs("kael") { announcement }
        waitUntil(200, "the pin, live") { announcement != null && announcement in session.feed(FeedKind.ANNOUNCEMENTS).pinned }
        waitUntil(200, "the announcement's banner gone") { !CaptureHooks.bannerShown }
        waitTicks(6)
        away()
        shot("p08_pin_from_browser")

        // Mika deletes her post in the browser: it leaves Camille's Global.
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        deleteAs("mika") { livePost }
        waitUntil(200, "the deletion, live") { livePost != null && session.post(livePost!!) == null }
        waitTicks(10)
        away()
        shot("p09_deleted_from_browser")

        // feed.writing off, then on: a Feed opened afresh has no compose button, a heart says why.
        flag("feed.writing", false)
        reopenFresh()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("p10_writing_off")
        clickAt { heart(FeedKind.GLOBAL, firstPost(FeedKind.GLOBAL) ?: error("Global is empty")) }
        waitTicks(5)
        shot("p11_writing_off_like")
        flag("feed.writing", true)
        waitTicks(80)
        reopenFresh()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("p12_writing_on")

        // feed off: opened afresh, "switched off"; on again, the client's own retry brings it back.
        flag("feed", false)
        reopenFresh()
        shot("p13_feed_off")
        log("feed off")
        flag("feed", true)
        waitUntil(700, "Feed back after the flag") { session.availability == FeedAvailability.OK }
        waitTicks(40)
        away()
        shot("p14_feed_on_again")
        log("feed on again")

        // The site killed under a connected player, then started again.
        click(GLOBAL_TAB_X, 22)
        waitTicks(10)
        control("kill-site")
        waitUntil(1200, "the connection lost") { session.availability == FeedAvailability.UNAVAILABLE }
        waitTicks(10)
        away()
        shot("p15_site_killed")
        log("site killed")
        control("start-site")
        waitUntil(2400, "Feed back after the site's restart") { session.availability == FeedAvailability.OK }
        waitTicks(40)
        away()
        shot("p16_site_back")
        log("site back")

        // Offline: a reply made in the browser while Camille is away is waiting at her next login.
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        leaveServer()
        waitTicks(40)
        replyAs("lea", { gamePost }, "Réponse pendant que tu n'étais pas là.")
        waitTicks(200)
        joinServer(server)
        waitTicks(80)
        openPhone()
        away()
        run { FeedMod.LOGGER.info("Feed capture: Feed's badge after the login: {}", CaptureHooks.unreadCount(Feed.APP_ID)) }
        shot("p17_badge_after_offline_reply")
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("p18_marker_after_offline_reply")

        // The dedicated server restarted under the player: back in, the posts are still there.
        control("restart-server")
        joinServer(server)
        waitTicks(80)
        openPhone()
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("p19_after_server_restart")
        log("after the server's restart")
    }

    /**
     * As Camille R., with Global loaded: `feed` switched off and on under an open Feed, the switched-off block and its
     * Retry, then the site killed and started again under it.
     */
    private fun FeedCapture.Script.outage() {
        join()
        openPhone()
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("o01_global_loaded")
        flag("feed", false)
        waitTicks(100)
        away()
        shot("o02_feed_off_under_open_feed")
        log("feed off, nothing done")
        clickAt { heart(FeedKind.GLOBAL, firstPost(FeedKind.GLOBAL) ?: error("Global is empty")) }
        waitTicks(20)
        shot("o03_feed_off_a_like")
        log("feed off, after a like")
        flag("feed", true)
        waitUntil(700, "Feed back after the flag") { session.availability == FeedAvailability.OK }
        waitTicks(40)
        away()
        shot("o04_feed_on_again")
        log("feed on again")

        flag("feed", false)
        reopenFresh()
        shot("o05_switched_off_block")
        flag("feed", true)
        waitTicks(20)
        click(128, RETRY_Y)
        waitUntil(200, "Feed back after Retry") { session.availability == FeedAvailability.OK }
        waitTicks(30)
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("o06_after_retry")
        log("after Retry")

        control("kill-site")
        waitUntil(1200, "the connection lost") { session.availability == FeedAvailability.UNAVAILABLE }
        waitTicks(10)
        away()
        shot("o07_site_killed_connection_lost")
        log("site killed")
        control("start-site")
        waitUntil(2400, "Feed back after the site's restart") { session.availability == FeedAvailability.OK }
        waitTicks(40)
        away()
        shot("o08_site_back")
        log("site back")
    }

    /**
     * As Camille R., joining after a reply was made in the browser while the server was stopped (the orchestrator does
     * that first); then the announcer role given and taken in the admin while Feed is open on Annonces.
     */
    private fun FeedCapture.Script.comeBack() {
        join()
        openPhone()
        away()
        run { FeedMod.LOGGER.info("Feed capture: Feed's badge after the login: {}", CaptureHooks.unreadCount(Feed.APP_ID)) }
        shot("r01_badge_after_reply_while_server_down")
        openFeedAndWait()
        shot("r02_annonces_plain_player")
        site("root", "PUT", "/api/feed/admin/announcers/c1-camille", """{"announcer":true}""")
        waitUntil(200, "the announce right, live") { session.state?.rights?.announce == true }
        waitTicks(10)
        away()
        shot("r03_announcer_given_in_admin")
        site("root", "PUT", "/api/feed/admin/announcers/c1-camille", """{"announcer":false}""")
        waitUntil(200, "the announce right gone, live") { session.state?.rights?.announce == false }
        waitTicks(10)
        away()
        shot("r04_announcer_taken_in_admin")
    }

    private val marks = HashMap<String, Long>()

    /** Starts the clock [name], for [elapsed]. */
    private fun FeedCapture.Script.mark(name: String) = run { marks[name] = System.nanoTime() }

    /** Logs how long since [mark] of [name], in seconds: the timings the run reports. */
    private fun FeedCapture.Script.elapsed(name: String, what: String) = run {
        val ms = (System.nanoTime() - (marks[name] ?: error("No mark $name"))) / 1_000_000
        FeedMod.LOGGER.info("Feed capture timing: {} after {} s", what, "%.1f".format(java.util.Locale.ROOT, ms / 1000.0))
    }

    /**
     * As Camille R., timed, with no click: `feed` and then `feed.serverApi` switched off and on under an open Feed (the
     * site closes the server's stream, the mod retries every minute); the switched-off block on Global coming back by
     * itself; the site killed and started; a reply while offline; the server restarted.
     */
    private fun FeedCapture.Script.flags() {
        join()
        openPhone()
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("e01_global_loaded")
        var camPost: UUID? = null
        run { camPost = session.feed(FeedKind.GLOBAL).posts.mapNotNull(session::post).firstOrNull { it.mine }?.id }

        for ((key, n) in listOf("feed" to 2, "feed.serverApi" to 4)) {
            mark(key)
            flag(key, false)
            waitUntil(600, "the open Feed told $key is off") { session.availability != FeedAvailability.OK }
            elapsed(key, "$key off: the open Feed lost its connection")
            waitUntil(600, "the open Feed told it is switched off") { session.availability == FeedAvailability.DISABLED }
            elapsed(key, "$key off: the open Feed is told it is switched off")
            waitTicks(10)
            away()
            shot("e0${n}_${key.replace('.', '_')}_off_open_feed")
            mark("$key on")
            flag(key, true)
            waitUntil(2400, "Feed back after $key") { session.availability == FeedAvailability.OK }
            elapsed("$key on", "$key on: the open Feed is back, no click")
            waitTicks(30)
            away()
            shot("e0${n + 1}_${key.replace('.', '_')}_on_again")
        }

        // Opened afresh while off: the switched-off block on Global, no compose button over it; back by itself.
        flag("feed", false)
        waitUntil(600, "the open Feed told it is switched off") { session.availability == FeedAvailability.DISABLED }
        reopenFresh()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("e06_disabled_block_global")
        mark("block")
        flag("feed", true)
        waitUntil(2400, "the block's list back") { session.feed(FeedKind.GLOBAL).posts.isNotEmpty() }
        elapsed("block", "feed on: the switched-off block replaced by the list, no click")
        waitTicks(30)
        away()
        shot("e07_block_back_no_click")

        // The site killed and started again under the loaded list.
        mark("kill")
        control("kill-site")
        waitUntil(1200, "the connection lost") { session.availability == FeedAvailability.UNAVAILABLE }
        elapsed("kill", "site killed: Connexion perdue")
        waitTicks(10)
        away()
        shot("e08_site_killed")
        control("start-site")
        mark("site up")
        waitUntil(2400, "Feed back after the site's restart") { session.availability == FeedAvailability.OK }
        elapsed("site up", "site answering again: Feed back")
        waitTicks(40)
        away()
        shot("e09_site_back")

        // A reply in the browser while Camille is away.
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        leaveServer()
        waitTicks(40)
        replyAs("lea", { camPost }, "Réponse pendant ton absence, deuxième tour.")
        waitTicks(200)
        joinServer(server)
        waitTicks(80)
        openPhone()
        away()
        run { FeedMod.LOGGER.info("Feed capture: Feed's badge after the login: {}", CaptureHooks.unreadCount(Feed.APP_ID)) }
        shot("e10_badge_after_offline_reply")
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("e11_marker_after_offline_reply")

        // The dedicated server restarted under the player.
        mark("restart")
        control("restart-server")
        elapsed("restart", "server restarted (Done)")
        joinServer(server)
        waitTicks(60)
        elapsed("restart", "joined again")
        openPhone()
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("e12_after_server_restart")
        log("after the server's restart")
    }

    /** The Retry button of the unavailable and switched-off blocks, in viewport dp. */
    private const val RETRY_Y = 278

    private fun FeedCapture.Script.replyAs(who: String, thread: () -> UUID?, text: String) = run {
        val id = thread() ?: error("No thread to reply to")
        val (status, _) = Site.call(who, "POST", "/api/feed/posts/$id/replies", newReply(text))
        FeedMod.LOGGER.info("Feed capture: a reply to {} as {} answered {}", id, who, status)
    }

    private fun FeedCapture.Script.likeAs(who: String, post: () -> UUID?) = run {
        val id = post() ?: error("No post to like")
        FeedMod.LOGGER.info("Feed capture: a like of {} as {} answered {}", id, who, Site.call(who, "PUT", "/api/feed/posts/$id/like", null).first)
    }

    private fun FeedCapture.Script.pinAs(who: String, post: () -> UUID?) = run {
        val id = post() ?: error("No post to pin")
        FeedMod.LOGGER.info("Feed capture: a pin of {} as {} answered {}", id, who, Site.call(who, "PUT", "/api/feed/posts/$id/pin", "{}").first)
    }

    private fun FeedCapture.Script.deleteAs(who: String, post: () -> UUID?) = run {
        val id = post() ?: error("No post to delete")
        FeedMod.LOGGER.info("Feed capture: deleting {} as {} answered {}", id, who, Site.call(who, "DELETE", "/api/feed/posts/$id", null).first)
    }

    /** As Lune G. (`Admin_Lune`), a Warden admin: the compose button, more and pin buttons, the private line, the command. */
    private fun FeedCapture.Script.moderator() {
        join()
        openPhone()
        openFeedAndWait()
        shot("m01_annonces")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("m02_global")
        mouseTo(128, 200)
        scroll(-3.0)
        waitTicks(10)
        away()
        shot("m03_global_anonymous_author_line")
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        chat("/feed announcer ")
        waitTicks(20)
        shot("m04_command_offered")
        run { Minecraft.getInstance().setScreen(null) }
        command("feed announcer list")
        waitTicks(40)
        command("feed announcer add Cam_Roux")
        waitTicks(40)
        command("feed announcer remove Cam_Roux")
        waitTicks(40)
        command("feed announcer remove Admin_Lune")
        waitTicks(40)
        command("feed announcer add Visitor42")
        waitTicks(40)
        chat("")
        waitTicks(10)
        shot("m05_command_answers")
        run { Minecraft.getInstance().setScreen(null) }
    }

    /** As Kael M. (`Kael_Builder`), an announcer and no moderator: the compose and pin buttons, no `/feed`. */
    private fun FeedCapture.Script.announcer() {
        join()
        openPhone()
        openFeedAndWait()
        shot("a01_annonces")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("a02_global")
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        chat("/feed ")
        waitTicks(20)
        shot("a03_no_command")
        run { Minecraft.getInstance().setScreen(null) }
    }

    /** As `Visitor42`, a username with no LNB account: reading, no compose button, a heart that says why. */
    private fun FeedCapture.Script.guest() {
        join()
        openPhone()
        openFeedAndWait()
        shot("g01_annonces")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("g02_global")
        clickAt { heart(FeedKind.GLOBAL, firstPost(FeedKind.GLOBAL) ?: error("Global is empty")) }
        waitTicks(5)
        shot("g03_like_refused")
    }

    /** As Sam D. (`Sam_D`), Warden-banned (who joins only because Warden's authentication is off in this run). */
    private fun FeedCapture.Script.banned() {
        join()
        openPhone()
        openFeedAndWait()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        away()
        shot("b01_global")
        clickAt { heart(FeedKind.GLOBAL, firstPost(FeedKind.GLOBAL) ?: error("Global is empty")) }
        waitTicks(5)
        shot("b02_like_refused")
    }
}
