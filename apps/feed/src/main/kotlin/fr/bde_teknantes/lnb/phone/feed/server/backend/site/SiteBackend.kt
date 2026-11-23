package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerState
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * LNB's website, through LNB Warden's `ApiClient` and its server config (`docs/feed-app.md` 3.2): every route of
 * `docs/feed-mod.md` under `/api/feed/server`, with the server's token.
 *
 * Every call is asynchronous (`HttpClient.sendAsync`): nothing here blocks the thread that calls it, and the futures
 * complete on the HTTP client's threads. Bodies are decoded here ([SiteJson]), since `ApiClient.send` keeps only
 * `data.problem` and Feed needs `data.oldest` and `Retry-After` too. Every outcome is told to Warden's `ApiHealth`, so
 * operators hear of an outage once.
 *
 * @param timeout how long a call may wait for its answer; tests pass a shorter one.
 * @param connection the site's base URL and the server's token, read at each call (null when either is blank).
 */
class SiteBackend(
    private val timeout: Duration = TIMEOUT,
    private val connection: () -> Pair<String, String>? = {
        if (ServerConfig.isApiConfigured) ServerConfig.API_URL.get() to ServerConfig.API_TOKEN.get() else null
    },
) : FeedBackend {
    private val events = FeedEvents(connection)

    override fun player(player: UUID) = get("player", "/players/$player", SiteJson::playerState)

    override fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int): CompletableFuture<BackendResult<FeedPage>> =
        get("posts", "/posts" + query("feed" to feed.wire, "viewer" to viewer.toString(), "limit" to limit.toString(), "before" to before), SiteJson::feedPage)

    override fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int): CompletableFuture<BackendResult<ThreadPage>> =
        get("thread", "/posts/$threadId" + query("viewer" to viewer.toString(), "limit" to limit.toString(), "after" to after), SiteJson::threadPage)

    override fun views(viewers: List<UUID>, ids: List<UUID>): CompletableFuture<BackendResult<Map<UUID, List<PostView>>>> {
        val body = JsonObject().apply {
            add("viewers", uuids(viewers))
            add("ids", uuids(ids))
        }
        return call("views", "POST", "/views", body) { SiteJson.views(SiteJson.parse(it), viewers) }
    }

    override fun createPost(key: UUID, player: UUID, feed: FeedKind, text: String, anonymous: Boolean): CompletableFuture<BackendResult<Created>> {
        val body = JsonObject().apply {
            addProperty("idempotencyKey", key.toString())
            addProperty("player", player.toString())
            addProperty("feed", feed.wire)
            addProperty("text", text)
            addProperty("anonymous", anonymous)
        }
        return call("create post", "POST", "/posts", body) { SiteJson.created(SiteJson.parse(it)) }
    }

    override fun createReply(key: UUID, player: UUID, threadId: UUID, replyTo: UUID?, text: String, anonymous: Boolean): CompletableFuture<BackendResult<Created>> {
        val body = JsonObject().apply {
            addProperty("idempotencyKey", key.toString())
            addProperty("player", player.toString())
            if (replyTo != null) addProperty("replyTo", replyTo.toString()) else add("replyTo", JsonNull.INSTANCE)
            addProperty("text", text)
            addProperty("anonymous", anonymous)
        }
        return call("create reply", "POST", "/posts/$threadId/replies", body) { SiteJson.created(SiteJson.parse(it)) }
    }

    override fun like(player: UUID, postId: UUID, liked: Boolean): CompletableFuture<BackendResult<LikeState>> {
        val decode = { body: String -> SiteJson.likeState(SiteJson.parse(body)) }
        return if (liked) {
            call("like", "PUT", "/posts/$postId/like", JsonObject().apply { addProperty("player", player.toString()) }, decode)
        } else {
            call("like", "DELETE", "/posts/$postId/like" + query("player" to player.toString()), null, decode)
        }
    }

    override fun pin(player: UUID, postId: UUID, pinned: Boolean, replace: Boolean): CompletableFuture<BackendResult<PinState>> {
        val decode = { body: String -> SiteJson.pinState(SiteJson.parse(body)) }
        return if (pinned) {
            val body = JsonObject().apply {
                addProperty("player", player.toString())
                addProperty("replace", replace)
            }
            call("pin", "PUT", "/posts/$postId/pin", body, decode)
        } else {
            call("pin", "DELETE", "/posts/$postId/pin" + query("player" to player.toString()), null, decode)
        }
    }

    override fun delete(player: UUID, postId: UUID) = call("delete", "DELETE", "/posts/$postId" + query("player" to player.toString()), null, NO_BODY)

    override fun markFeedSeen(player: UUID, feed: FeedKind, upTo: Instant): CompletableFuture<BackendResult<Unit>> {
        val body = JsonObject().apply {
            addProperty("player", player.toString())
            addProperty("feed", feed.wire)
            addProperty("upTo", SiteJson.date(upTo))
        }
        return call("seen", "POST", "/seen", body, NO_BODY)
    }

    override fun markThreadSeen(player: UUID, threadId: UUID) =
        call("thread seen", "POST", "/posts/$threadId/seen", JsonObject().apply { addProperty("player", player.toString()) }, NO_BODY)

    override fun pendingNotifications(): CompletableFuture<BackendResult<NotificationBatch>> = get("notifications", "/notifications", SiteJson::notifications)

    override fun ackNotifications(ids: List<UUID>) =
        call("notifications ack", "POST", "/notifications/ack", JsonObject().apply { add("ids", uuids(ids)) }, NO_BODY)

    override fun setAnnouncer(target: UUID, announcer: Boolean, issuer: Issuer?): CompletableFuture<BackendResult<AnnouncerState>> {
        val body = JsonObject().apply {
            addProperty("announcer", announcer)
            // Left out only for the server console.
            if (issuer != null) {
                add(
                    "issuer",
                    JsonObject().apply {
                        addProperty("uuid", issuer.uuid.toString())
                        addProperty("username", issuer.username)
                    },
                )
            }
        }
        return call("announcer", "PUT", "/announcers/$target", body) { SiteJson.announcerState(SiteJson.parse(it)) }
    }

    override fun announcers(): CompletableFuture<BackendResult<List<AnnouncerEntry>>> = get("announcers", "/announcers", SiteJson::announcers)

    override fun start(listener: FeedEventListener) = events.start(listener)

    override fun stop() = events.stop()

    // ---- Plumbing ----

    private fun <T> get(what: String, path: String, decode: (JsonElement) -> T): CompletableFuture<BackendResult<T>> =
        call(what, "GET", path, null) { decode(SiteJson.parse(it)) }

    private fun <T> call(what: String, method: String, path: String, body: JsonObject?, decode: (String) -> T): CompletableFuture<BackendResult<T>> {
        val route = "feed $what"
        val (url, token) = connection() ?: return done(route, SiteAnswers.network(IllegalStateException(NOT_CONFIGURED)), NOT_CONFIGURED)
        val request = try {
            // JsonObject.toString writes null members, which the site wants present (replyTo).
            val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body.toString())
            ApiClient.request(url, BASE + path, token)
                .timeout(timeout)
                .apply { if (body != null) header("Content-Type", "application/json") }
                .method(method, publisher)
                .build()
        } catch (e: Exception) {
            // A malformed api.url.
            return done(route, SiteAnswers.network(e), null)
        }
        return ApiClient.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle { response, error ->
            val classified = if (response != null) {
                SiteAnswers.classify(response.statusCode(), response.body(), response.headers().firstValue("Retry-After").orElse(null), decode)
            } else {
                SiteAnswers.network(error?.cause ?: error ?: IllegalStateException("no answer"))
            }
            ApiHealth.record(route, classified.health)
            classified.result
        }
    }

    private fun <T> done(route: String, classified: SiteAnswers.Classified<T>, reason: String?): CompletableFuture<BackendResult<T>> {
        ApiHealth.record(route, classified.health)
        val result = if (reason != null) BackendResult.Unavailable(reason) else classified.result
        return CompletableFuture.completedFuture(result)
    }

    private fun uuids(ids: List<UUID>) = JsonArray().apply { ids.forEach { add(it.toString()) } }

    private fun query(vararg params: Pair<String, String?>): String {
        val present = params.filter { it.second != null }
        if (present.isEmpty()) return ""
        return present.joinToString("&", prefix = "?") { (name, value) -> "$name=" + URLEncoder.encode(value, Charsets.UTF_8) }
    }

    companion object {
        const val BASE = "/api/feed/server"

        /** The art direction's threshold for "Feed unavailable" (9.12); `ApiClient`'s default is 15. */
        val TIMEOUT: Duration = Duration.ofSeconds(10)

        const val NOT_CONFIGURED = "api.url and api.token aren't set in lnbwarden-server.toml"

        private val NO_BODY: (String) -> Unit = {}
    }
}
