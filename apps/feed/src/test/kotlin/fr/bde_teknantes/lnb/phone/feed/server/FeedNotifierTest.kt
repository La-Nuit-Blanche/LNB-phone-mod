package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationPost
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Draining the site's notification queue (`docs/feed-mod.md` 9, `docs/feed-app.md` 6): `more`, failed calls, one drain
 * at a time, what each kind sends and to whom, and taking notifications back.
 */
class FeedNotifierTest {
    private val host = FakeHost()
    private val sink = RecordingSink()
    private val backend = ScriptedBackend()
    private val notifier = FeedNotifier(backend, host, sink)

    private val kael = UUID(1, 1)
    private val kaelAlt = UUID(1, 2)
    private val mika = UUID(1, 3)
    private val thread = UUID(2, 1)
    private var ids = 0L

    private fun announcement(author: Author? = Author(kael, "Kael_Builder", null, true), except: List<UUID> = listOf(kael, kaelAlt)) =
        QueuedNotification(
            UUID(3, ids++), QueuedNotification.Kind.ANNOUNCEMENT, Instant.EPOCH, players = null, except = except,
            post = NotificationPost(UUID(4, ids), FeedKind.ANNOUNCEMENTS, null, "Événement PvP", author == null, author),
        )

    private fun reply(players: List<UUID>?, author: Author? = Author(mika, "MikaPvP", "Mika L.", false), threadId: UUID? = thread) =
        QueuedNotification(
            UUID(3, ids++), QueuedNotification.Kind.REPLY, Instant.EPOCH, players = players, except = emptyList(),
            post = NotificationPost(UUID(4, ids), FeedKind.GLOBAL, threadId, "Moi !", author == null, author),
        )

    private fun batch(vararg rows: QueuedNotification, more: Boolean = false) = BackendResult.Ok(NotificationBatch(rows.toList(), more))

    private fun fetches() = backend.calls("pendingNotifications")

    private fun acks() = backend.calls("ackNotifications")

    private fun key(component: Component) = (component.contents as TranslatableContents).key

    private fun args(component: Component) = (component.contents as TranslatableContents).args.toList()

    @Test
    fun `while the site says more, each batch is delivered, acknowledged, and the next fetched`() {
        notifier.drain()
        val first = listOf(announcement(), reply(listOf(mika)))
        fetches().single().answer(batch(*first.toTypedArray(), more = true))
        assertEquals(2, sink.sent.size)
        assertEquals(first.map { it.id }, acks().single().arg<List<UUID>>(0))
        assertEquals(1, fetches().size, "the next batch waits for the acknowledgement")
        acks().single().answer(BackendResult.Ok(Unit))

        val second = reply(listOf(mika))
        fetches()[1].answer(batch(second, more = false))
        acks()[1].answer(BackendResult.Ok(Unit))
        assertEquals(listOf(second.id), acks()[1].arg<List<UUID>>(0))
        assertEquals(2, fetches().size, "no more: done")

        notifier.drain()
        assertEquals(3, fetches().size, "a new drain once the last one finished")
    }

    @Test
    fun `a failed acknowledgement ends the drain, and the next drain delivers the rows again`() {
        notifier.drain()
        val row = reply(listOf(mika))
        fetches().single().answer(batch(row, more = true))
        acks().single().answer(BackendResult.Unavailable("down"))
        assertEquals(1, fetches().size, "no fetch after a failed acknowledgement, even with more")

        notifier.drain()
        fetches()[1].answer(batch(row))
        assertEquals(2, sink.sent.size, "delivered twice rather than lost")
        acks()[1].fail(IllegalStateException("boom"))
        notifier.drain()
        assertEquals(3, fetches().size, "a broken future ends the drain too")
    }

    @Test
    fun `a failed fetch acknowledges nothing and ends the drain`() {
        notifier.drain()
        fetches().single().answer(BackendResult.Disabled("feed"))
        assertTrue(acks().isEmpty())
        notifier.drain()
        fetches()[1].fail(RuntimeException("boom"))
        notifier.drain()
        assertEquals(3, fetches().size)
        assertTrue(sink.sent.isEmpty())
    }

    @Test
    fun `drains asked for while one runs run once more after it, not once each`() {
        notifier.drain()
        notifier.drain()
        notifier.drain()
        notifier.drain()
        assertEquals(1, fetches().size, "never two at once")
        fetches().single().answer(batch(reply(listOf(mika))))
        assertEquals(1, fetches().size, "still running: waiting for the acknowledgement")
        acks().single().answer(BackendResult.Ok(Unit))
        assertEquals(2, fetches().size, "once more")
        fetches()[1].answer(batch())
        assertEquals(2, fetches().size)
        assertEquals(1, acks().size, "an empty batch acknowledges nothing")
    }

    @Test
    fun `answers that come from another thread are delivered on the server thread`() {
        host.deferred = true
        notifier.drain()
        Thread { fetches().single().answer(batch(announcement())) }.apply { start() }.join()
        assertTrue(sink.sent.isEmpty())
        assertTrue(acks().isEmpty())
        host.runTasks()
        assertEquals(1, sink.sent.size)
        Thread { acks().single().answer(BackendResult.Ok(Unit)) }.apply { start() }.join()
        notifier.drain()
        assertEquals(1, fetches().size, "the drain ends only when the server thread has the acknowledgement")
        host.runTasks()
        assertEquals(2, fetches().size, "the drain asked for meanwhile runs after it")
    }

    @Test
    fun `an announcement goes to everyone but its author's accounts, anonymous or not`() {
        notifier.drain()
        val named = announcement()
        val anonymous = announcement(author = null)
        fetches().single().answer(batch(named, anonymous))

        val (first, second) = sink.sent
        assertNull(first.players)
        assertEquals(listOf(kael, kaelAlt), first.except)
        assertEquals("notif.lnbphone_feed.announcement", key(first.title))
        assertEquals("notif.lnbphone_feed.text", key(first.text))
        assertEquals(listOf<Any>("Kael_Builder", "Événement PvP"), args(first.text))
        assertEquals("post:${named.post.id}", first.target)

        assertNull(second.players)
        assertEquals(listOf(kael, kaelAlt), second.except, "an anonymous announcement leaves its author out too")
        val name = args(second.text).first() as Component
        assertEquals("lnbphone_feed.post.anonymous", key(name))
        assertEquals("Événement PvP", args(second.text)[1])
    }

    @Test
    fun `a reply goes to the recipient's accounts, with the author's display name, or Anonymous`() {
        notifier.drain()
        val named = reply(listOf(kael, kaelAlt))
        val anonymous = reply(listOf(mika), author = null)
        fetches().single().answer(batch(named, anonymous))
        val (first, second) = sink.sent
        assertEquals(listOf(kael, kaelAlt), first.players)
        assertEquals("notif.lnbphone_feed.reply", key(first.title))
        assertEquals("Mika L.", args(first.text).first(), "realName ?: username")
        assertEquals("reply:global:$thread:${named.post.id}", first.target)
        assertEquals("lnbphone_feed.post.anonymous", key(args(second.text).first() as Component))
    }

    @Test
    fun `an anonymous post's notification names nobody, even when the backend sent an author with it`() {
        notifier.drain()
        val leaky = reply(listOf(kael)).let { it.copy(post = it.post.copy(anonymous = true)) }
        val leakyAnnouncement = announcement().let { it.copy(post = it.post.copy(anonymous = true)) }
        fetches().single().answer(batch(leaky, leakyAnnouncement))
        for (sent in sink.sent) {
            assertEquals("lnbphone_feed.post.anonymous", key(args(sent.text).first() as Component))
            assertTrue(args(sent.text).none { it == "Mika L." || it == "MikaPvP" || it == "Kael_Builder" })
        }
        assertEquals(2, sink.sent.size)
    }

    @Test
    fun `rows that can't be sent are acknowledged without sending, and don't stop the others`() {
        notifier.drain()
        val unlinked = reply(emptyList())
        val noPlayers = reply(null)
        val noThread = reply(listOf(mika), threadId = null)
        val refused = announcement()
        val good = reply(listOf(mika))
        sink.refuses = { it == "post:${refused.post.id}" }
        fetches().single().answer(batch(unlinked, noPlayers, noThread, refused, good))
        assertEquals(listOf(good.post.id), sink.sent.map { it.target.substringAfterLast(':').let(UUID::fromString) })
        assertEquals(listOf(unlinked, noPlayers, noThread, refused, good).map { it.id }, acks().single().arg<List<UUID>>(0))
    }

    @Test
    fun `a deleted post or reply takes its notifications back from everyone`() {
        val main = UUID(5, 1)
        val answer = UUID(5, 2)
        val announcementReply = UUID(5, 3)
        notifier.takeBack(
            listOf(
                RemovedItem(main, FeedKind.ANNOUNCEMENTS, null),
                RemovedItem(answer, FeedKind.GLOBAL, thread),
                RemovedItem(announcementReply, FeedKind.ANNOUNCEMENTS, main),
            ),
        )
        assertEquals(listOf("post:$main", "reply:global:$thread:$answer", "reply:announcements:$main:$announcementReply"), sink.cleared)
    }

    @Test
    fun `the service drains at start and every 5 minutes`() {
        val service = FeedService(backend, host, sink)
        service.start()
        assertEquals(1, fetches().size)
        fetches().single().answer(batch())
        host.time += 5 * 60 * 1000L - 1
        service.tick()
        assertEquals(1, fetches().size)
        host.time += 1
        service.tick()
        assertEquals(2, fetches().size)
    }
}
