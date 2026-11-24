package fr.bde_teknantes.lnb.phone.feed

import fr.bde_teknantes.lnb.phone.api.AppTarget
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class FeedTargetTest {
    private val post = UUID.fromString("5f6a7b8c-9d0e-4f1a-b2c3-d4e5f6a7b8c9")
    private val thread = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")
    private val reply = UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e")

    @Test
    fun `targets read back as written`() {
        val announcement = FeedTarget.Post(post)
        assertEquals("post:$post", announcement.toString())
        assertEquals(announcement, FeedTarget.parse(announcement.toString()))
        val answer = FeedTarget.Reply(FeedKind.GLOBAL, thread, reply)
        assertEquals("reply:global:$thread:$reply", answer.toString())
        assertEquals(answer, FeedTarget.parse(answer.toString()))
    }

    @Test
    fun `the longest target is 93 characters, under the phone's limit`() {
        val longest = FeedTarget.Reply(FeedKind.ANNOUNCEMENTS, thread, reply).toString()
        assertEquals(FeedTarget.MAX_LENGTH, longest.length)
        assertTrue(longest.length <= AppTarget.MAX_LENGTH)
    }

    @Test
    fun `unknown and malformed targets read as nothing`() {
        for (target in listOf(
            null, "", "post:", "post:nope", "post:$post:extra", "reply:global:$thread", "reply:other:$thread:$reply",
            "reply:global:$thread:$reply:x", "thread:$thread", "POST:$post", "post:1-1-1-1-1",
            "reply:global:$thread:$reply" + "x".repeat(100),
        )) {
            assertNull(FeedTarget.parse(target), "for $target")
        }
    }
}
