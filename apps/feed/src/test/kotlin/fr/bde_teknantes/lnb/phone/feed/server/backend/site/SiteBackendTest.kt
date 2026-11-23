package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Reading the site's answers and events, on the contract's examples (`docs/feed-mod.md` 2.3, 3, 4, 8). */
class SiteBackendTest {
    private val camille = UUID.fromString("3f0b2c1a-8d4e-3b7f-9a12-6c5d4e3f2a10")
    private val lune = UUID.fromString("9c8b7a6d-5e4f-3d2c-9b1a-0f9e8d7c6b5a")

    private val postJson = """
        {
          "id": "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e", "feed": "global", "threadId": "0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70",
          "replyTo": { "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "anonymous": false, "name": "Mika L." },
          "text": "Il y en a encore ?", "createdAt": "2026-11-23T20:18:02.000Z", "anonymous": true, "author": null,
          "likes": 0, "replies": 0, "pinnedAt": null, "liked": false, "mine": false, "canDelete": false, "canPin": false,
          "unseenReplies": 0, "answersMe": false
        }
    """.trimIndent()

    @Test
    fun `a post view reads as the contract writes it`() {
        val post = SiteJson.post(SiteJson.parse(postJson))
        assertEquals(UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70"), post.threadId)
        assertEquals(ReplyRef(UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"), false, "Mika L."), post.replyTo)
        assertEquals(Instant.parse("2026-11-23T20:18:02Z"), post.createdAt)
        assertNull(post.author)
        assertTrue(post.anonymous)
    }

    @Test
    fun `a body past the payloads' bounds, or missing a field, is refused`() {
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(postJson.replace("Il y en a encore ?", "a".repeat(561)))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(postJson.replace("\"likes\": 0,", ""))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(postJson.replace("\"likes\": 0", "\"likes\": -1"))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(postJson.replace("\"global\"", "\"other\""))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.parse("not json {") }
    }

    @Test
    fun `views go only to the viewers asked for`() {
        val body = """{ "views": [ { "viewer": "$camille", "posts": [ $postJson ] }, { "viewer": "$lune", "posts": [] } ] }"""
        val views = SiteJson.views(SiteJson.parse(body), listOf(camille, lune))
        assertEquals(1, views.getValue(camille).size)
        assertTrue(views.getValue(lune).isEmpty())
        assertThrows(SiteFormatException::class.java) { SiteJson.views(SiteJson.parse(body), listOf(camille)) }
        val twice = """{ "views": [ { "viewer": "$camille", "posts": [] }, { "viewer": "$camille", "posts": [] } ] }"""
        assertThrows(SiteFormatException::class.java) { SiteJson.views(SiteJson.parse(twice), listOf(camille, lune)) }
    }

    @Test
    fun `the player state and the notification queue read as written`() {
        val state = SiteJson.playerState(
            SiteJson.parse(
                """{ "linked": true, "banned": false, "writing": true,
                "me": { "uuid": "$camille", "username": "Cam_Roux", "realName": "Camille R.", "announcer": false },
                "rights": { "write": true, "announce": false, "moderate": false }, "unread": { "announcements": false, "global": true } }""",
            ),
        )
        assertEquals("Camille R.", state.me?.realName)
        assertTrue(state.unread.global)

        val batch = SiteJson.notifications(
            SiteJson.parse(
                """{ "items": [
                  { "id": "e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b", "kind": "announcement", "createdAt": "2026-11-23T21:02:44.000Z",
                    "players": null, "except": ["2b3c4d5e-6f7a-3b8c-9d0e-1f2a3b4c5d6e"],
                    "post": { "id": "5f6a7b8c-9d0e-4f1a-b2c3-d4e5f6a7b8c9", "feed": "announcements", "threadId": null, "text": "PvP",
                              "anonymous": true, "author": null } } ], "more": false }""",
            ),
        )
        val row = batch.items.single()
        assertEquals(QueuedNotification.Kind.ANNOUNCEMENT, row.kind)
        assertNull(row.players)
        assertEquals(1, row.except.size)
        assertFalse(batch.more)
        assertFalse(row.toString().contains("2b3c4d5e"), "a row's text form names no player")
    }

    @Test
    fun `answers become results as the design maps them`() {
        val ok = SiteAnswers.classify(200, "{}", null) { "decoded" }
        assertEquals(BackendResult.Ok("decoded"), ok.result)

        val pinLimit = SiteAnswers.classify(409, """{"statusCode":409,"data":{"problem":"pinLimit","oldest":{"id":"$lune"}}}""", null) { }
        assertEquals(BackendResult.Refused(409, "pinLimit", oldestPin = lune), pinLimit.result)
        assertEquals(ApiResult.Http(409, "pinLimit"), pinLimit.health)

        val rate = SiteAnswers.classify(429, """{"data":{"problem":"rateLimited"}}""", "40") { }
        assertEquals(BackendResult.Refused(429, "rateLimited", retryAfterSeconds = 40), rate.result)

        val disabled = SiteAnswers.classify(503, """{"data":{"problem":"disabled","flag":"feed.writing"}}""", null) { }
        assertEquals(BackendResult.Disabled("feed.writing"), disabled.result)
        assertInstanceOf(ApiResult.Ok::class.java, disabled.health, "a switched-off feature isn't an outage")

        assertInstanceOf(BackendResult.Unavailable::class.java, SiteAnswers.classify(401, null, null) { }.result)
        assertInstanceOf(BackendResult.Unavailable::class.java, SiteAnswers.classify(502, "<html>", null) { }.result)
        assertEquals(BackendResult.Refused(400, null), SiteAnswers.classify(400, "{}", null) { }.result)
        val broken = SiteAnswers.classify(200, "{}", null) { SiteJson.post(SiteJson.parse(it)) }
        assertInstanceOf(BackendResult.Unavailable::class.java, broken.result)
        // An answer the site gave, so not an outage for Warden's shared health.
        assertEquals(ApiResult.Http(200, SiteAnswers.MALFORMED), broken.health)
        assertFalse(broken.health.isUnreachable)
    }

    @Test
    fun `stream events reach the listener, and unknown or malformed ones are skipped`() {
        val heard = ArrayList<String>()
        val listener = object : FeedEventListener {
            override fun onConnected() = Unit
            override fun onDisconnected(disabled: Boolean) = Unit
            override fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant) { heard += "post $feed $threadId" }
            override fun onDeleted(items: List<RemovedItem>) { heard += "deleted ${items.size}" }
            override fun onCounts(items: List<CountUpdate>) { heard += "counts ${items.single().likes}" }
            override fun onPins(pinned: List<Pin>) { heard += "pins ${pinned.size}" }
            override fun onRights(players: List<UUID>) { heard += "rights ${players.single()}" }
            override fun onNotification() { heard += "notification" }
        }
        FeedEvents.dispatch(listener, "post", """{"id":"$lune","feed":"global","threadId":null,"createdAt":"2026-11-23T20:14:05.000Z"}""")
        FeedEvents.dispatch(listener, "deleted", """{"items":[{"id":"$lune","feed":"announcements","threadId":null},{"id":"$camille","feed":"announcements","threadId":"$lune"}]}""")
        FeedEvents.dispatch(listener, "counts", """{"items":[{"id":"$lune","likes":13,"replies":3}]}""")
        FeedEvents.dispatch(listener, "pins", """{"pinned":[{"id":"$lune","pinnedAt":"2026-11-23T21:10:00.000Z"}]}""")
        FeedEvents.dispatch(listener, "rights", """{"uuids":["$camille"]}""")
        FeedEvents.dispatch(listener, "notification", "{}")
        FeedEvents.dispatch(listener, "ping", "{}")
        FeedEvents.dispatch(listener, "economy", "{}")
        FeedEvents.dispatch(listener, "post", """{"id":"nope"}""")
        assertEquals(listOf("post GLOBAL null", "deleted 2", "counts 13", "pins 1", "rights $camille", "notification"), heard)
    }

    @Test
    fun `dates are written as the site writes them`() {
        assertEquals("2026-11-23T20:14:05.000Z", SiteJson.date(Instant.parse("2026-11-23T20:14:05Z")))
        assertEquals("2026-11-23T20:14:05.123Z", SiteJson.date(Instant.parse("2026-11-23T20:14:05.123456Z")))
    }
}
