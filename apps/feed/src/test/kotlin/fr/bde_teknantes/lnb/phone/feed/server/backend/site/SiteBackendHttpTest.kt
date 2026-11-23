package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * [SiteBackend] against a site in process: every route's method, path, query and body as `docs/feed-mod.md` 4 writes
 * them, the answers of its examples, and each problem of section 5 as the backend maps it (`docs/feed-app.md` 3.2).
 */
class SiteBackendHttpTest {
    private val site = FakeSite()
    private val backend = SiteBackend { site.url to "s3cret" }

    private val camille = UUID.fromString("3f0b2c1a-8d4e-3b7f-9a12-6c5d4e3f2a10")
    private val mika = UUID.fromString("7a1e4b2c-3d5f-3a6b-8c9d-0e1f2a3b4c5d")
    private val lune = UUID.fromString("9c8b7a6d-5e4f-3d2c-9b1a-0f9e8d7c6b5a")
    private val kael = UUID.fromString("2b3c4d5e-6f7a-3b8c-9d0e-1f2a3b4c5d6e")
    private val visitor = UUID.fromString("6d7e8f9a-0b1c-3d2e-8f4a-5b6c7d8e9f0a")
    private val postId = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")
    private val replyId = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")
    private val pinnedId = UUID.fromString("4e5f6a7b-8c9d-4e0f-a1b2-c3d4e5f6a7b8")
    private val key = UUID.fromString("c0a8e1a2-7b0f-4f6e-9a55-3d3b6a0f0c11")

    @AfterEach
    fun close() = site.close()

    /** A full post view, as section 3 writes one. */
    private fun postJson(
        id: UUID = postId,
        feed: String = "global",
        threadId: UUID? = null,
        author: String? = """{ "uuid": "$camille", "username": "Cam_Roux", "realName": "Camille R.", "announcer": false }""",
        anonymous: Boolean = author == null,
        pinnedAt: String? = null,
        mine: Boolean = false,
    ) = """
        { "id": "$id", "feed": "$feed", "threadId": ${threadId?.let { "\"$it\"" }}, "replyTo": null,
          "text": "Qui est chaud pour une virée dans le Nether ce soir ?", "createdAt": "2026-11-23T20:14:05.000Z",
          "anonymous": $anonymous, "author": $author, "likes": 12, "replies": 3,
          "pinnedAt": ${pinnedAt?.let { "\"$it\"" }}, "liked": false, "mine": $mine, "canDelete": $mine, "canPin": false,
          "unseenReplies": 0, "answersMe": false }
    """.trimIndent()

    private fun <T> CompletableFuture<BackendResult<T>>.result(): BackendResult<T> = get(15, TimeUnit.SECONDS)

    private fun <T> CompletableFuture<BackendResult<T>>.ok(): T {
        val result = result()
        assertInstanceOf(BackendResult.Ok::class.java, result, "expected Ok, got $result")
        return (result as BackendResult.Ok).body
    }

    private fun answer(status: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        site.answer = { FakeSite.Reply(status, body, headers) }
    }

    private fun json(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private fun query(request: FakeSite.Request): Map<String, String> =
        request.query.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }

    private fun assertHeaders(request: FakeSite.Request, withBody: Boolean) {
        assertEquals("Bearer s3cret", request.headers["authorization"])
        assertEquals("application/json", request.headers["accept"])
        if (withBody) assertEquals("application/json", request.headers["content-type"]) else assertEquals("", request.body)
    }

    // ---- Reads ----

    @Test
    fun `GET players reads the state of section 4_1`() {
        answer(
            200,
            """{ "linked": true, "banned": false, "writing": true,
              "me": { "uuid": "$camille", "username": "Cam_Roux", "realName": "Camille R.", "announcer": false },
              "rights": { "write": true, "announce": false, "moderate": false },
              "unread": { "announcements": false, "global": true } }""",
        )
        val state = backend.player(camille).ok()
        val request = site.nextRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/feed/server/players/$camille", request.path)
        assertNull(request.query)
        assertHeaders(request, withBody = false)
        assertTrue(state.linked && state.writing && !state.banned && state.rights.write && state.unread.global)
        assertEquals("Camille R.", state.me?.realName)

        answer(200, """{ "linked": false, "banned": false, "writing": true, "me": null,
            "rights": { "write": false, "announce": false, "moderate": false }, "unread": { "announcements": false, "global": false } }""")
        val guest = backend.player(visitor).ok()
        assertFalse(guest.linked)
        assertNull(guest.me)
    }

    @Test
    fun `GET posts asks for a page with its query, and passes the cursor back encoded`() {
        answer(200, """{ "feed": "announcements", "pinned": [ ${postJson(pinnedId, "announcements", pinnedAt = "2026-11-20T18:01:12.000Z")} ], "posts": [], "next": null }""")
        val page = backend.page(camille, FeedKind.ANNOUNCEMENTS, null, 20).ok()
        val request = site.nextRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/feed/server/posts", request.path)
        assertEquals(mapOf("feed" to "announcements", "viewer" to camille.toString(), "limit" to "20"), query(request))
        assertEquals(listOf(pinnedId), page.pinned.map { it.id })
        assertEquals(Instant.parse("2026-11-20T18:01:12Z"), page.pinned.single().pinnedAt)
        assertNull(page.next)

        val cursor = "1764000000000:$postId+&=/?é"
        answer(200, """{ "feed": "global", "pinned": [], "posts": [ ${postJson()} ], "next": "c2" }""")
        val second = backend.page(camille, FeedKind.GLOBAL, cursor, 20).ok()
        val asked = site.nextRequest()
        assertEquals(cursor, query(asked)["before"], "the cursor goes back unchanged")
        assertFalse(asked.query!!.contains("+&="), "and encoded in the query")
        assertEquals("c2", second.next)
    }

    @Test
    fun `GET a thread asks with viewer, limit and after`() {
        answer(200, """{ "post": ${postJson()}, "replies": [ ${postJson(replyId, threadId = postId, author = null)} ], "next": null }""")
        val thread = backend.thread(mika, postId, "c1", 50).ok()
        val request = site.nextRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/feed/server/posts/$postId", request.path)
        assertEquals(mapOf("viewer" to mika.toString(), "limit" to "50", "after" to "c1"), query(request))
        assertEquals(postId, thread.post.id)
        assertNull(thread.replies.single().author)

        backend.thread(mika, postId, null, 50).ok()
        assertEquals(mapOf("viewer" to mika.toString(), "limit" to "50"), query(site.nextRequest()))
    }

    @Test
    fun `POST views sends viewers and ids, and reads each viewer's own posts`() {
        val forCamille = postJson(replyId, threadId = postId, mine = true)
        val forLune = postJson(replyId, threadId = postId)
        val forVisitor = postJson(replyId, threadId = postId, author = null)
        answer(
            200,
            """{ "views": [ { "viewer": "$camille", "posts": [ $forCamille ] }, { "viewer": "$lune", "posts": [ $forLune ] },
                { "viewer": "$visitor", "posts": [ $forVisitor ] } ] }""",
        )
        val views = backend.views(listOf(camille, lune, visitor), listOf(replyId)).ok()
        val request = site.nextRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/feed/server/views", request.path)
        assertHeaders(request, withBody = true)
        val body = json(request.body)
        assertEquals(listOf(camille, lune, visitor).map { it.toString() }, body.getAsJsonArray("viewers").map { it.asString })
        assertEquals(listOf(replyId.toString()), body.getAsJsonArray("ids").map { it.asString })
        assertTrue(views.getValue(camille).single().mine)
        assertEquals("Cam_Roux", views.getValue(lune).single().author?.username)
        assertNull(views.getValue(visitor).single().author)
    }

    @Test
    fun `a views answer naming a viewer not asked for is thrown away whole`() {
        answer(200, """{ "views": [ { "viewer": "$camille", "posts": [ ${postJson(mine = true)} ] }, { "viewer": "$mika", "posts": [ ${postJson(mine = true)} ] } ] }""")
        assertInstanceOf(BackendResult.Unavailable::class.java, backend.views(listOf(camille), listOf(postId)).result())
    }

    // ---- Writes ----

    @Test
    fun `POST posts sends the body of section 4_5 and reads the created post`() {
        answer(201, """{ "replayed": false, "post": ${postJson(feed = "announcements", mine = true)} }""")
        val created = backend.createPost(key, kael, FeedKind.ANNOUNCEMENTS, "Événement PvP dimanche", false).ok()
        val request = site.nextRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/feed/server/posts", request.path)
        assertHeaders(request, withBody = true)
        val body = json(request.body)
        assertEquals(setOf("idempotencyKey", "player", "feed", "text", "anonymous"), body.keySet())
        assertEquals(key.toString(), body["idempotencyKey"].asString)
        assertEquals(kael.toString(), body["player"].asString)
        assertEquals("announcements", body["feed"].asString)
        assertEquals("Événement PvP dimanche", body["text"].asString)
        assertFalse(body["anonymous"].asBoolean)
        assertFalse(created.replayed)
        assertTrue(created.post.mine)

        answer(201, """{ "replayed": true, "post": ${postJson(mine = true)} }""")
        assertTrue(backend.createPost(key, kael, FeedKind.GLOBAL, "x", true).ok().replayed)
    }

    @Test
    fun `POST replies sends replyTo, null included, to the thread's path`() {
        answer(201, """{ "replayed": false, "post": ${postJson(replyId, threadId = postId, author = null, mine = true)} }""")
        backend.createReply(key, camille, postId, replyId, "Il y en a encore ?", true).ok()
        val request = site.nextRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/feed/server/posts/$postId/replies", request.path)
        val body = json(request.body)
        assertEquals(setOf("idempotencyKey", "player", "replyTo", "text", "anonymous"), body.keySet())
        assertEquals(replyId.toString(), body["replyTo"].asString)
        assertEquals(camille.toString(), body["player"].asString)
        assertTrue(body["anonymous"].asBoolean)

        backend.createReply(key, camille, postId, null, "Moi !", false).ok()
        val toMain = json(site.nextRequest().body)
        assertTrue(toMain.has("replyTo") && toMain["replyTo"].isJsonNull, "replyTo is present, as null")
    }

    @Test
    fun `likes are a PUT with the player in the body, and a DELETE with the player in the query`() {
        answer(200, """{ "id": "$postId", "likes": 13, "liked": true }""")
        val liked = backend.like(camille, postId, true).ok()
        val put = site.nextRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/feed/server/posts/$postId/like", put.path)
        assertEquals(camille.toString(), json(put.body)["player"].asString)
        assertEquals(13, liked.likes)
        assertTrue(liked.liked)

        answer(200, """{ "id": "$postId", "likes": 12, "liked": false }""")
        assertFalse(backend.like(camille, postId, false).ok().liked)
        val delete = site.nextRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/feed/server/posts/$postId/like", delete.path)
        assertEquals(mapOf("player" to camille.toString()), query(delete))
        assertEquals("", delete.body)
    }

    @Test
    fun `pins are a PUT with replace, and a DELETE with the player in the query`() {
        val group = """{ "pinned": [ { "id": "6a7b8c9d-0e1f-4a2b-c3d4-e5f6a7b8c9d0", "pinnedAt": "2026-11-23T21:10:00.000Z" },
            { "id": "$pinnedId", "pinnedAt": "2026-11-20T18:01:12.000Z" } ], "unpinned": null }"""
        answer(200, group)
        val state = backend.pin(lune, pinnedId, true, false).ok()
        val put = site.nextRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/feed/server/posts/$pinnedId/pin", put.path)
        val body = json(put.body)
        assertEquals(lune.toString(), body["player"].asString)
        assertFalse(body["replace"].asBoolean)
        assertEquals(listOf(Pin(UUID.fromString("6a7b8c9d-0e1f-4a2b-c3d4-e5f6a7b8c9d0"), Instant.parse("2026-11-23T21:10:00Z")), Pin(pinnedId, Instant.parse("2026-11-20T18:01:12Z"))), state.pinned)
        assertNull(state.unpinned)

        answer(200, """{ "pinned": [], "unpinned": "$pinnedId" }""")
        backend.pin(lune, pinnedId, true, true).ok()
        assertTrue(json(site.nextRequest().body)["replace"].asBoolean)

        backend.pin(kael, pinnedId, false, false).ok()
        val delete = site.nextRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/feed/server/posts/$pinnedId/pin", delete.path)
        assertEquals(mapOf("player" to kael.toString()), query(delete))
    }

    @Test
    fun `DELETE a post takes the player in the query and reads a 204`() {
        answer(204)
        assertEquals(Unit, backend.delete(camille, postId).ok())
        val request = site.nextRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/feed/server/posts/$postId", request.path)
        assertEquals(mapOf("player" to camille.toString()), query(request))
    }

    @Test
    fun `marking seen posts the player, the feed and the time with milliseconds`() {
        answer(204)
        backend.markFeedSeen(camille, FeedKind.GLOBAL, Instant.parse("2026-11-23T20:14:05Z")).ok()
        val seen = site.nextRequest()
        assertEquals("POST", seen.method)
        assertEquals("/api/feed/server/seen", seen.path)
        val body = json(seen.body)
        assertEquals(setOf("player", "feed", "upTo"), body.keySet())
        assertEquals("global", body["feed"].asString)
        assertEquals("2026-11-23T20:14:05.000Z", body["upTo"].asString)

        backend.markThreadSeen(camille, postId).ok()
        val thread = site.nextRequest()
        assertEquals("POST", thread.method)
        assertEquals("/api/feed/server/posts/$postId/seen", thread.path)
        assertEquals("""{"player":"$camille"}""", thread.body)
    }

    // ---- Notifications and announcers ----

    @Test
    fun `the notification queue is read and acknowledged as section 4_12 and 4_13 write`() {
        answer(
            200,
            """{ "items": [
              { "id": "e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b", "kind": "announcement", "createdAt": "2026-11-23T21:02:44.000Z",
                "players": null, "except": ["$kael"],
                "post": { "id": "5f6a7b8c-9d0e-4f1a-b2c3-d4e5f6a7b8c9", "feed": "announcements", "threadId": null, "text": "Événement PvP",
                          "anonymous": false, "author": { "uuid": "$kael", "username": "Kael_Builder", "realName": null, "announcer": true } } },
              { "id": "f2a3b4c5-d6e7-4f8a-9b0c-1d2e3f4a5b6c", "kind": "reply", "createdAt": "2026-11-23T21:05:10.000Z",
                "players": ["$mika"], "except": [],
                "post": { "id": "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e", "feed": "global", "threadId": "$postId", "text": "Il y en a encore ?",
                          "anonymous": true, "author": null } } ], "more": true }""",
        )
        val batch = backend.pendingNotifications().ok()
        val get = site.nextRequest()
        assertEquals("GET", get.method)
        assertEquals("/api/feed/server/notifications", get.path)
        assertTrue(batch.more)
        val (announcement, reply) = batch.items
        assertEquals(QueuedNotification.Kind.ANNOUNCEMENT, announcement.kind)
        assertEquals(listOf(kael), announcement.except)
        assertEquals("Kael_Builder", announcement.post.author?.displayName)
        assertEquals(listOf(mika), reply.players)
        assertEquals(postId, reply.post.threadId)
        assertNull(reply.post.author)

        answer(204)
        backend.ackNotifications(batch.items.map { it.id }).ok()
        val ack = site.nextRequest()
        assertEquals("POST", ack.method)
        assertEquals("/api/feed/server/notifications/ack", ack.path)
        assertEquals(batch.items.map { it.id.toString() }, json(ack.body).getAsJsonArray("ids").map { it.asString })
    }

    @Test
    fun `PUT announcers names the issuer for a player and nobody for the console`() {
        answer(200, """{ "uuid": "$kael", "username": "Kael_Builder", "announcer": true, "viaRole": false }""")
        val state = backend.setAnnouncer(kael, true, Issuer(lune, "Admin_Lune")).ok()
        val request = site.nextRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/feed/server/announcers/$kael", request.path)
        assertEquals(json("""{ "announcer": true, "issuer": { "uuid": "$lune", "username": "Admin_Lune" } }"""), json(request.body))
        assertEquals("Kael_Builder", state.username)
        assertTrue(state.announcer && !state.viaRole)

        backend.setAnnouncer(kael, false, null).ok()
        assertEquals(json("""{ "announcer": false }"""), json(site.nextRequest().body))
    }

    @Test
    fun `GET announcers lists people and their accounts`() {
        answer(200, """{ "items": [ { "realName": null, "accounts": [ { "uuid": "$kael", "username": "Kael_Builder" } ] },
            { "realName": "Camille R.", "accounts": [ { "uuid": "$camille", "username": "Cam_Roux" }, { "uuid": "$mika", "username": "Cam_Alt" } ] } ] }""")
        val list = backend.announcers().ok()
        assertEquals("/api/feed/server/announcers", site.nextRequest().path)
        assertEquals(
            listOf(
                AnnouncerEntry(null, listOf(AnnouncerEntry.Account(kael, "Kael_Builder"))),
                AnnouncerEntry("Camille R.", listOf(AnnouncerEntry.Account(camille, "Cam_Roux"), AnnouncerEntry.Account(mika, "Cam_Alt"))),
            ),
            list,
        )
    }

    // ---- Problems (section 5) ----

    private fun problem(status: Int, problem: String?, extra: String = ""): String {
        val data = if (problem == null) "" else """, "data": { "problem": "$problem"$extra }"""
        return """{ "statusCode": $status, "statusMessage": "${problem ?: "x"}", "message": "${problem ?: "x"}"$data }"""
    }

    @Test
    fun `each refusal of section 5 is Refused with its status and problem`() {
        val cases = listOf(
            400 to null, 400 to "emptyText", 400 to "tooLong", 403 to "banned", 403 to "notAllowed", 403 to "issuerNotAllowed",
            404 to null, 404 to "notLinked", 404 to "notFound", 409 to "idempotencyMismatch", 409 to "notPinnable",
            409 to "impliedByRole", 409 to "isGhost",
        )
        for ((status, code) in cases) {
            answer(status, problem(status, code))
            assertEquals(BackendResult.Refused(status, code), backend.createPost(key, camille, FeedKind.GLOBAL, "x", false).result(), "$status $code")
        }
    }

    @Test
    fun `pinLimit carries the oldest pin, and rateLimited the seconds of Retry-After`() {
        answer(409, problem(409, "pinLimit", """, "oldest": { "id": "$pinnedId" }"""))
        assertEquals(BackendResult.Refused(409, "pinLimit", oldestPin = pinnedId), backend.pin(lune, postId, true, false).result())

        answer(429, problem(429, "rateLimited"), mapOf("Retry-After" to "40"))
        assertEquals(BackendResult.Refused(429, "rateLimited", retryAfterSeconds = 40), backend.like(camille, postId, true).result())

        // The site's own middleware may answer 429 with no problem and a date (not seconds) as Retry-After.
        answer(429, "", mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(BackendResult.Refused(429, null), backend.like(camille, postId, true).result())
    }

    @Test
    fun `a 503 disabled names its flag, and is no outage`() {
        for (flag in listOf("feed", "feed.serverApi", "feed.writing")) {
            answer(503, problem(503, "disabled", """, "flag": "$flag""""))
            assertEquals(BackendResult.Disabled(flag), backend.createPost(key, camille, FeedKind.GLOBAL, "x", false).result())
        }
    }

    @Test
    fun `a wrong secret, a 5xx, a 503 without disabled and a body that doesn't parse are Unavailable`() {
        for ((status, body) in listOf(401 to problem(401, null), 500 to "", 502 to "<html>Bad gateway</html>", 503 to problem(503, null))) {
            answer(status, body)
            assertInstanceOf(BackendResult.Unavailable::class.java, backend.player(camille).result(), "$status")
        }
        for (body in listOf("", "not json", "[]", """{ "linked": "yes" }""", """{ "feed": "global", "pinned": [], "posts": [], "next": "${"c".repeat(129)}" }""")) {
            answer(200, body)
            assertInstanceOf(BackendResult.Unavailable::class.java, backend.page(camille, FeedKind.GLOBAL, null, 20).result(), body)
        }
        // A page holding more than the payloads can carry never reaches a client.
        answer(200, """{ "feed": "global", "pinned": [], "posts": [ ${List(51) { postJson() }.joinToString()} ], "next": null }""")
        assertInstanceOf(BackendResult.Unavailable::class.java, backend.page(camille, FeedKind.GLOBAL, null, 20).result())
        answer(200, """{ "feed": "announcements", "pinned": [ ${List(4) { postJson(feed = "announcements") }.joinToString()} ], "posts": [], "next": null }""")
        assertInstanceOf(BackendResult.Unavailable::class.java, backend.page(camille, FeedKind.ANNOUNCEMENTS, null, 20).result())
    }

    @Test
    fun `a site that doesn't answer within the timeout is Unavailable`() {
        site.answer = { FakeSite.Hang }
        val impatient = SiteBackend(Duration.ofMillis(200)) { site.url to "s3cret" }
        val result = impatient.createPost(key, camille, FeedKind.GLOBAL, "x", false).result()
        assertEquals(BackendResult.Unavailable("HttpTimeoutException"), result)
        assertEquals(Duration.ofSeconds(10), SiteBackend.TIMEOUT, "the art direction's threshold for Feed unavailable")
    }

    @Test
    fun `no site, no configuration or a malformed url are Unavailable, without throwing`() {
        val port = ServerSocket(0).use { it.localPort }
        val closed = SiteBackend { "http://127.0.0.1:$port" to "s3cret" }
        assertInstanceOf(BackendResult.Unavailable::class.java, closed.player(camille).result())

        val unset = SiteBackend { null }
        assertEquals(BackendResult.Unavailable(SiteBackend.NOT_CONFIGURED), unset.createPost(key, camille, FeedKind.GLOBAL, "x", false).result())
        assertEquals(BackendResult.Unavailable(SiteBackend.NOT_CONFIGURED), unset.announcers().result())

        val malformed = SiteBackend { "http://[not a url" to "s3cret" }
        assertInstanceOf(BackendResult.Unavailable::class.java, malformed.delete(camille, postId).result())
        assertTrue(site.requests.isEmpty())
    }
}
