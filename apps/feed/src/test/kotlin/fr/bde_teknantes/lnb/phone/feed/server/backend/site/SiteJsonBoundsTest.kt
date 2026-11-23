package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A broken or hostile answer from the site (`docs/feed-mod.md` 3 and 4): every field that would go on to a client must
 * fail in [SiteJson], making the call `Unavailable`, rather than fail to encode and drop the player's connection.
 */
class SiteJsonBoundsTest {
    private val id = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")

    private fun post(
        text: String = "\"Salut\"",
        createdAt: String = "\"2026-11-23T20:14:05.000Z\"",
        author: String = """{ "uuid": "$id", "username": "Cam_Roux", "realName": "Camille R.", "announcer": false }""",
        replyTo: String = "null",
        likes: String = "12",
        liked: String = "false",
        pinnedAt: String = "null",
        threadId: String = "null",
    ) = """
        { "id": "$id", "feed": "global", "threadId": $threadId, "replyTo": $replyTo, "text": $text, "createdAt": $createdAt,
          "anonymous": false, "author": $author, "likes": $likes, "replies": 0, "pinnedAt": $pinnedAt, "liked": $liked,
          "mine": false, "canDelete": false, "canPin": false, "unseenReplies": 0, "answersMe": false }
    """.trimIndent()

    private fun refused(body: String) {
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(body)) }
    }

    @Test
    fun `the longest honest values are read`() {
        val longest = SiteJson.post(
            SiteJson.parse(
                post(
                    text = "\"${"👍".repeat(280)}\"",
                    author = """{ "uuid": "$id", "username": "${"u".repeat(16)}", "realName": "${"😀".repeat(64)}", "announcer": true }""",
                    replyTo = """{ "id": null, "anonymous": false, "name": "${"😀".repeat(64)}" }""",
                    likes = "2147483647",
                ),
            ),
        )
        assertEquals(560, longest.text.length)
        assertNull(longest.replyTo?.id)
        assertEquals(Int.MAX_VALUE, longest.likes)
    }

    @Test
    fun `each string past what the payloads carry is refused`() {
        refused(post(text = "\"${"a".repeat(561)}\""))
        refused(post(author = """{ "uuid": "$id", "username": "${"u".repeat(17)}", "realName": null, "announcer": false }"""))
        refused(post(author = """{ "uuid": "$id", "username": "Cam_Roux", "realName": "${"r".repeat(129)}", "announcer": false }"""))
        refused(post(replyTo = """{ "id": null, "anonymous": false, "name": "${"n".repeat(129)}" }"""))
    }

    @Test
    fun `each wrong type is refused, naming the field and not the value`() {
        refused(post(likes = "\"12\""))
        refused(post(likes = "1.5"))
        refused(post(likes = "2147483648"))
        refused(post(liked = "1"))
        refused(post(liked = "null"))
        refused(post(createdAt = "\"yesterday\""))
        refused(post(createdAt = "1764000000000"))
        refused(post(threadId = "\"not-a-uuid\""))
        refused(post(threadId = "42"))
        refused(post(author = "\"Cam_Roux\""))
        refused(post(replyTo = "[]"))
        val error = assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(post(text = "42"))) }
        assertEquals("text: not a string", error.message)
    }

    @Test
    fun `lists past their bounds are refused`() {
        val views = (0..200).joinToString { """{ "viewer": "${UUID(0, it.toLong())}", "posts": [] }""" }
        assertThrows(SiteFormatException::class.java) { SiteJson.views(SiteJson.parse("""{ "views": [ $views ] }"""), (0..200).map { UUID(0, it.toLong()) }) }
        val many = List(21) { post() }.joinToString()
        assertThrows(SiteFormatException::class.java) { SiteJson.views(SiteJson.parse("""{ "views": [ { "viewer": "$id", "posts": [ $many ] } ] }"""), listOf(id)) }
        val pins = List(4) { """{ "id": "$id", "pinnedAt": "2026-11-23T21:10:00.000Z" }""" }.joinToString()
        assertThrows(SiteFormatException::class.java) { SiteJson.pinState(SiteJson.parse("""{ "pinned": [ $pins ], "unpinned": null }""")) }
        assertThrows(SiteFormatException::class.java) { SiteJson.pins(SiteJson.parse("""{ "pinned": [ $pins ] }""")) }
        fun row(n: Int, kind: String = "reply") =
            """{ "id": "${UUID(1, n.toLong())}", "kind": "$kind", "createdAt": "2026-11-23T21:05:10.000Z", "players": [], "except": [],
                "post": { "id": "$id", "feed": "global", "threadId": "$id", "text": "x", "anonymous": true, "author": null } }"""
        val rows = List(101) { row(it) }.joinToString()
        assertThrows(SiteFormatException::class.java) { SiteJson.notifications(SiteJson.parse("""{ "items": [ $rows ], "more": true }""")) }
        assertEquals(1, SiteJson.notifications(SiteJson.parse("""{ "items": [ ${row(0)} ], "more": false }""")).items.size)
        assertThrows(SiteFormatException::class.java) { SiteJson.notifications(SiteJson.parse("""{ "items": [ ${row(0, "mention")} ], "more": false }""")) }
    }

    @Test
    fun `a date past what epoch milliseconds hold is refused before it reaches a payload`() {
        val far = "\"+300000000-01-01T00:00:00Z\""
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(post(createdAt = far))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(post(pinnedAt = far))) }
        assertThrows(SiteFormatException::class.java) { SiteJson.pins(SiteJson.parse("""{ "pinned": [ { "id": "$id", "pinnedAt": $far } ] }""")) }
        val farPast = "\"-300000000-01-01T00:00:00Z\""
        assertThrows(SiteFormatException::class.java) { SiteJson.post(SiteJson.parse(post(createdAt = farPast))) }
    }
}
