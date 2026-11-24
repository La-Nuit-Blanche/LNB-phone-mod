package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The mod's own [SiteBackend] and [FeedEvents] against a **running** LNB website, through every call of the backend
 * interface, and the contract's test vectors (`docs/feed-mod.md` 13) on the real site rather than the memory backend.
 *
 * Not part of the normal build: it runs only when `FEED_LIVE_SITE` is set, by hand, against a local site whose
 * database it empties between checks. `TESTING.md` ("Feed against a local website") says how to seed that site:
 *
 * - `FEED_LIVE_SITE`: the site's base URL (`http://localhost:3010`);
 * - `FEED_LIVE_TOKEN`: its `WARDEN_SERVER_SECRET`;
 * - `FEED_LIVE_COOKIES`: a JSON object of the seeded people's session cookies (`{"camille": "better-auth.session_token=..."}`),
 *   for the browser's routes and the admin's;
 * - `FEED_LIVE_DB`: the site's database in the Compose PostgreSQL (`lnb_c1`), emptied of Feed's rows between checks.
 *
 * `./gradlew :apps:feed:test --tests '*LiveSiteCheck*'`. A failure here is a disagreement between the mod and the site.
 */
@EnabledIfEnvironmentVariable(named = "FEED_LIVE_SITE", matches = "https?://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LiveSiteCheck {
    private val site = System.getenv("FEED_LIVE_SITE").orEmpty().trimEnd('/')
    private val token = System.getenv("FEED_LIVE_TOKEN").orEmpty()
    private val db = System.getenv("FEED_LIVE_DB") ?: "lnb_c1"
    private val cookies: JsonObject by lazy { JsonParser.parseString(System.getenv("FEED_LIVE_COOKIES") ?: "{}").asJsonObject }
    private val backend = SiteBackend { site to token }
    // HTTP/1.1, as Warden's ApiClient on loopback: a dev server takes an h2c upgrade for its own websocket and drops it.
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    /** A seeded person: their LNB user ID and the Minecraft username they play as. */
    data class Person(val key: String, val username: String) {
        val userId = "c1-$key"
        val uuid: UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
    }

    private val camille = Person("camille", "Cam_Roux")
    private val steve = Person("steve", "SteveBuilds")
    private val lea = Person("lea", "LeaBuilds")
    private val leaAlt = Person("lea", "Lea_Alt")
    private val kael = Person("kael", "Kael_Builder")
    private val lune = Person("lune", "Admin_Lune")
    private val sam = Person("sam", "Sam_D")
    private val mika = Person("mika", "MikaPvP")
    private val visitor = Person("guest", "Visitor42")

    // ---- 4.1 The player's state, for each kind of person ----

    @Test
    @Order(1)
    fun `the state of each kind of person`() {
        reset()
        ok(backend.player(camille.uuid)).let {
            assertTrue(it.linked); assertFalse(it.banned); assertTrue(it.writing)
            assertEquals(camille.uuid, it.me?.uuid); assertEquals("Cam_Roux", it.me?.username); assertEquals("Camille R.", it.me?.realName)
            assertEquals(listOf(true, false, false), listOf(it.rights.write, it.rights.announce, it.rights.moderate))
        }
        ok(backend.player(steve.uuid)).let { assertNull(it.me?.realName, "a hidden real name is null"); assertEquals("SteveBuilds", it.me?.username) }
        ok(backend.player(leaAlt.uuid)).let { assertEquals(leaAlt.uuid, it.me?.uuid, "me is the account played on"); assertEquals("Lea_Alt", it.me?.username) }
        ok(backend.player(lea.uuid)).let { assertEquals("LeaBuilds", it.me?.username) }
        ok(backend.player(kael.uuid)).let { assertTrue(it.rights.announce); assertFalse(it.rights.moderate); assertTrue(it.me!!.announcer) }
        ok(backend.player(lune.uuid)).let { assertTrue(it.rights.write && it.rights.announce && it.rights.moderate) }
        ok(backend.player(sam.uuid)).let {
            assertTrue(it.linked); assertTrue(it.banned)
            assertFalse(it.rights.write || it.rights.announce || it.rights.moderate)
        }
        for (guest in listOf(visitor.uuid, UUID.randomUUID())) {
            ok(backend.player(guest)).let {
                assertFalse(it.linked); assertNull(it.me)
                assertFalse(it.rights.write || it.rights.announce || it.rights.moderate)
                assertFalse(it.unread.announcements || it.unread.global)
            }
        }
    }

    // ---- 13.1 Text ----

    @Test
    @Order(2)
    fun `the text vectors, on the real site`() {
        reset()
        val thread = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "Fil des vecteurs", false) }.post
        val writers = listOf(steve, lea, mika, kael, lune)
        val vectors = listOf(
            "  Salut  " to "Salut",
            "été" to "été",
            "👍🏽" to "👍🏽",
            "🇫🇷" to "🇫🇷",
            "a".repeat(280) to "a".repeat(280),
            "a".repeat(281) to "tooLong",
            "a".repeat(279) + "é" to "a".repeat(279) + "é",
            "   \n   " to "emptyText",
            "" to "emptyText",
            "a\r\nb" to "a\nb",
            "a\rb" to "a\nb",
            "a\tb" to "a b",
            "§cRouge" to "cRouge",
            "a\u0000b\u0085c" to "abc",
            "a\n\n\n\nb" to "a\n\nb",
            "　bonjour " to "bonjour",
            "﻿hi" to "﻿hi",
            "a​b" to "a​b",
        )
        vectors.forEachIndexed { i, (input, expected) ->
            val who = writers[i % writers.size]
            val result = writeResult { backend.createReply(UUID.randomUUID(), who.uuid, thread.id, null, input, false) }
            if (expected == "tooLong" || expected == "emptyText") {
                assertRefused(400, expected, result, "vector $i")
            } else {
                val stored = (result as? BackendResult.Ok)?.body?.post?.text ?: fail("vector $i: $result")
                assertEquals(expected, stored, "vector $i")
            }
        }
        // And one main post in each feed, as the table's answers are a post's.
        assertEquals("Salut", write { backend.createPost(UUID.randomUUID(), steve.uuid, FeedKind.GLOBAL, "  Salut  ", false) }.post.text)
        assertRefused(400, "tooLong", writeResult { backend.createPost(UUID.randomUUID(), kael.uuid, FeedKind.ANNOUNCEMENTS, "a".repeat(281), false) })
    }

    // ---- 13.2 Idempotency ----

    @Test
    @Order(3)
    fun `idempotency, as the contract's vector`() {
        reset()
        val key = UUID.randomUUID()
        val first = write { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut", false) }
        assertFalse(first.replayed)
        val again = write { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut", false) }
        assertTrue(again.replayed); assertEquals(first.post.id, again.post.id)
        assertRefused(409, "idempotencyMismatch", writeResult { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut !", false) })
        assertRefused(409, "idempotencyMismatch", writeResult { backend.createPost(key, mika.uuid, FeedKind.GLOBAL, "Salut", false) })
        assertTrue(write { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "  Salut  ", false) }.replayed)
        flag("feed.writing", false)
        try {
            assertEquals(BackendResult.Disabled("feed.writing"), await(backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut", false)))
        } finally {
            flag("feed.writing", true)
        }
        assertTrue(write { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut", false) }.replayed)
        assertEquals(1, ok(backend.page(camille.uuid, FeedKind.GLOBAL, null, 20)).posts.size, "one post exists")
        ok(backend.delete(camille.uuid, first.post.id))
        assertRefused(404, "notFound", writeResult { backend.createPost(key, camille.uuid, FeedKind.GLOBAL, "Salut", false) })

        // Retries with one key sent at once wait for each other: one post, the others replay it.
        val racing = UUID.randomUUID()
        val answers = (1..3).map { backend.createPost(racing, steve.uuid, FeedKind.GLOBAL, "En même temps", false) }.map { await(it) }
        val created = answers.map { (it as? BackendResult.Ok)?.body ?: fail("a racing retry: $it") }
        assertEquals(1, created.map { it.post.id }.toSet().size)
        assertEquals(1, created.count { !it.replayed })

        // A reply's key too.
        val replyKey = UUID.randomUUID()
        val thread = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "Fil", false) }.post
        val reply = write { backend.createReply(replyKey, camille.uuid, thread.id, null, "Réponse", true) }
        val replay = write { backend.createReply(replyKey, camille.uuid, thread.id, null, "Réponse", true) }
        assertTrue(replay.replayed); assertEquals(reply.post.id, replay.post.id)
        assertRefused(409, "idempotencyMismatch", writeResult { backend.createReply(replyKey, camille.uuid, thread.id, null, "Réponse", false) })
    }

    // ---- 13.3 Anonymity, by viewer, with the notifications ----

    @Test
    @Order(4)
    fun `anonymity by viewer, and what the notifications carry`() {
        reset()
        val post = write { backend.createPost(UUID.randomUUID(), lea.uuid, FeedKind.GLOBAL, "Anonyme", true) }.post
        assertNotNull(post.author, "the author's own view carries the author"); assertTrue(post.mine)
        val viewers = listOf(lea, leaAlt, lune, kael, mika, visitor)
        val views = ok(backend.views(viewers.map { it.uuid }, listOf(post.id)))
        assertEquals(viewers.map { it.uuid }, views.keys.toList(), "every viewer, in the order sent")
        fun row(p: Person) = views.getValue(p.uuid).single().let { listOf(it.author != null, it.mine, it.canDelete, it.canPin) }
        assertEquals(listOf(true, true, true, false), row(lea), "the author, from the account posted from")
        assertEquals(listOf(true, true, true, false), row(leaAlt), "the author, from a second linked account")
        assertEquals(listOf(true, false, true, false), row(lune), "a moderator")
        assertEquals(listOf(false, false, false, false), row(kael), "an announcer who isn't a moderator")
        assertEquals(listOf(false, false, false, false), row(mika))
        assertEquals(listOf(false, false, false, false), row(visitor), "a guest")

        val mikaReply = write { backend.createReply(UUID.randomUUID(), mika.uuid, post.id, null, "Je viens", false) }.post
        assertNull(mikaReply.replyTo, "a reply to the main post has no reply-to line")
        val luneReply = write { backend.createReply(UUID.randomUUID(), lune.uuid, post.id, mikaReply.id, "Moi aussi", true) }.post
        val forKael = ok(backend.views(listOf(kael.uuid), listOf(luneReply.id))).getValue(kael.uuid).single()
        assertNull(forKael.author)
        assertEquals(ReplyRef(mikaReply.id, false, "Mika L."), forKael.replyTo)

        val queue = ok(backend.pendingNotifications()).items
        val toLea = queue.single { it.post.id == mikaReply.id }
        assertEquals(QueuedNotification.Kind.REPLY, toLea.kind)
        assertEquals(setOf(lea.uuid, leaAlt.uuid), toLea.players!!.toSet(), "every account of the recipient")
        assertEquals("Mika L.", toLea.post.author?.realName)
        assertEquals(post.id, toLea.post.threadId)
        val toMika = queue.single { it.post.id == luneReply.id }
        assertEquals(listOf(mika.uuid), toMika.players)
        assertNull(toMika.post.author, "an anonymous reply's notification names nobody")

        val announcement = write { backend.createPost(UUID.randomUUID(), kael.uuid, FeedKind.ANNOUNCEMENTS, "Annonce anonyme", true) }.post
        val aViews = ok(backend.views(listOf(kael, lune, camille, visitor).map { it.uuid }, listOf(announcement.id)))
        fun aRow(p: Person) = aViews.getValue(p.uuid).single().let { listOf(it.author != null, it.mine, it.canDelete, it.canPin) }
        assertEquals(listOf(true, true, true, true), aRow(kael))
        assertEquals(listOf(true, false, true, true), aRow(lune))
        assertEquals(listOf(false, false, false, false), aRow(camille))
        assertEquals(listOf(false, false, false, false), aRow(visitor))
        val toAll = ok(backend.pendingNotifications()).items.single { it.post.id == announcement.id }
        assertEquals(QueuedNotification.Kind.ANNOUNCEMENT, toAll.kind)
        assertNull(toAll.players)
        assertEquals(listOf(kael.uuid), toAll.except)
        assertNull(toAll.post.author)
    }

    // ---- 13.4 Pagination ----

    @Test
    @Order(5)
    fun `keyset pages, as the contract's vector`() {
        reset()
        val g = (1..25).map { insertPost("global", mika, "G$it", minutesAgo = 100 - it) }
        val first = ok(backend.page(camille.uuid, FeedKind.GLOBAL, null, 20))
        assertEquals(g.reversed().take(20), first.posts.map { it.id }, "G25 to G6")
        assertTrue(first.pinned.isEmpty())
        val c1 = first.next ?: fail("a first page of 20 out of 25 has a next")
        assertTrue(c1.length <= 128)
        insertPost("global", mika, "G26", minutesAgo = 0)
        ok(backend.delete(mika.uuid, g[2]))
        val second = ok(backend.page(camille.uuid, FeedKind.GLOBAL, c1, 20))
        assertEquals(listOf(g[4], g[3], g[1], g[0]), second.posts.map { it.id }, "G5, G4, G2, G1")
        assertNull(second.next)

        val a = (1..5).map { insertPost("announcements", kael, "A$it", minutesAgo = 50 - it) }
        ok(backend.pin(lune.uuid, a[1], true, false))
        Thread.sleep(5)
        ok(backend.pin(lune.uuid, a[3], true, false))
        val announcements = ok(backend.page(camille.uuid, FeedKind.ANNOUNCEMENTS, null, 20))
        assertEquals(listOf(a[3], a[1]), announcements.pinned.map { it.id }, "A4, A2")
        assertEquals(listOf(a[4], a[2], a[0]), announcements.posts.map { it.id }, "A5, A3, A1")
        assertTrue(announcements.pinned.all { it.pinnedAt != null })
        val small = ok(backend.page(camille.uuid, FeedKind.ANNOUNCEMENTS, null, 2))
        assertEquals(listOf(a[3], a[1]), small.pinned.map { it.id })
        val later = ok(backend.page(camille.uuid, FeedKind.ANNOUNCEMENTS, small.next ?: fail("a next"), 2))
        assertTrue(later.pinned.isEmpty(), "pinned only on the first page")
        assertEquals(listOf(a[0]), later.posts.map { it.id })
        // The mod asks pages of 20 and threads of 50; the site's ceiling is 50.
        assertEquals(26 - 1, ok(backend.page(camille.uuid, FeedKind.GLOBAL, null, 50)).posts.size)
    }

    // ---- 4.3 Threads, with paging ----

    @Test
    @Order(6)
    fun `threads and their pages`() {
        reset()
        val post = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "Fil", false) }.post
        val replies = (1..7).map { insertPost("global", mika, "R$it", minutesAgo = 0, threadId = post.id, secondsAgo = 60 - it) }
        val p1 = ok(backend.thread(camille.uuid, post.id, null, 3))
        assertEquals(post.id, p1.post.id)
        assertEquals(replies.take(3), p1.replies.map { it.id }, "oldest first")
        val p2 = ok(backend.thread(camille.uuid, post.id, p1.next ?: fail("next"), 3))
        assertEquals(replies.subList(3, 6), p2.replies.map { it.id })
        val p3 = ok(backend.thread(camille.uuid, post.id, p2.next ?: fail("next"), 3))
        assertEquals(replies.subList(6, 7), p3.replies.map { it.id })
        assertNull(p3.next)
        assertEquals(7, ok(backend.thread(camille.uuid, post.id, null, 50)).replies.size)
        assertRefused(404, "notFound", await(backend.thread(camille.uuid, replies[0], null, 50)), "a reply's ID")
        assertRefused(404, "notFound", await(backend.thread(camille.uuid, UUID.randomUUID(), null, 50)), "an unknown ID")
        ok(backend.delete(camille.uuid, post.id))
        assertRefused(404, "notFound", await(backend.thread(camille.uuid, post.id, null, 50)), "a deleted thread")
    }

    // ---- 4.4 Views ----

    @Test
    @Order(7)
    fun `views for many viewers`() {
        reset()
        val a = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "Un", false) }.post
        val b = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "Deux", false) }.post
        val gone = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "Trois", false) }.post
        ok(backend.delete(mika.uuid, gone.id))
        val viewers = listOf(camille.uuid, visitor.uuid) + (1..198).map { UUID.randomUUID() }
        val views = ok(backend.views(viewers, listOf(b.id, gone.id, UUID.randomUUID(), a.id)))
        assertEquals(viewers, views.keys.toList())
        assertEquals(listOf(b.id, a.id), views.getValue(camille.uuid).map { it.id }, "deleted and unknown IDs left out, order kept")
        assertTrue(views.getValue(camille.uuid).last().mine)
        assertFalse(views.getValue(visitor.uuid).any { it.mine || it.liked || it.canDelete })
        assertRefused(400, null, await(backend.views(viewers + UUID.randomUUID(), listOf(a.id))), "201 viewers")
        assertRefused(400, null, await(backend.views(listOf(camille.uuid), (1..21).map { UUID.randomUUID() })), "21 IDs")
    }

    // ---- 4.7 Likes ----

    @Test
    @Order(8)
    fun `likes and unlikes`() {
        reset()
        val post = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "Aimez-moi", false) }.post
        assertEquals(listOf(1, 1), ok(backend.like(camille.uuid, post.id, true)).let { listOf(it.likes, if (it.liked) 1 else 0) })
        assertEquals(1, ok(backend.like(camille.uuid, post.id, true)).likes, "liking twice changes nothing")
        assertEquals(2, ok(backend.like(leaAlt.uuid, post.id, true)).likes)
        assertEquals(2, ok(backend.like(lea.uuid, post.id, true)).likes, "one like per LNB account, whatever the Minecraft account")
        ok(backend.like(camille.uuid, post.id, false)).let { assertEquals(1, it.likes); assertFalse(it.liked) }
        assertEquals(1, ok(backend.like(camille.uuid, post.id, false)).likes, "unliking twice changes nothing")
        val reply = write { backend.createReply(UUID.randomUUID(), camille.uuid, post.id, null, "Réponse", false) }.post
        assertEquals(1, ok(backend.like(mika.uuid, reply.id, true)).likes, "a reply can be liked")
        assertRefused(404, "notLinked", await(backend.like(visitor.uuid, post.id, true)))
        assertRefused(403, "banned", await(backend.like(sam.uuid, post.id, true)))
        assertRefused(404, "notFound", await(backend.like(camille.uuid, UUID.randomUUID(), true)))
        flag("feed.writing", false)
        try {
            assertEquals(BackendResult.Disabled("feed.writing"), await(backend.like(camille.uuid, post.id, true)))
            assertEquals(BackendResult.Disabled("feed.writing"), await(backend.like(camille.uuid, post.id, false)))
        } finally {
            flag("feed.writing", true)
        }
        val seen = ok(backend.page(lea.uuid, FeedKind.GLOBAL, null, 20)).posts.single()
        assertTrue(seen.liked); assertEquals(1, seen.likes)
    }

    // ---- 13.5 Pins ----

    @Test
    @Order(9)
    fun `pins, as the contract's vector`() {
        reset()
        val (a, b, c, d) = (1..4).map { insertPost("announcements", kael, "P$it", minutesAgo = 10 - it) }
        val global = insertPost("global", mika, "Global", minutesAgo = 1)
        for (id in listOf(a, b, c)) {
            ok(backend.pin(lune.uuid, id, true, false)); Thread.sleep(5)
        }
        val limit = await(backend.pin(lune.uuid, d, true, false))
        assertRefused(409, "pinLimit", limit)
        assertEquals(a, (limit as BackendResult.Refused).oldestPin, "data.oldest names the pin that would go")
        val replaced = ok(backend.pin(lune.uuid, d, true, true))
        assertEquals(listOf(d, c, b), replaced.pinned.map { it.id }); assertEquals(a, replaced.unpinned)
        val same = ok(backend.pin(lune.uuid, d, true, false))
        assertEquals(listOf(d, c, b), same.pinned.map { it.id }); assertNull(same.unpinned)
        assertEquals(listOf(d, b), ok(backend.pin(kael.uuid, c, false, false)).pinned.map { it.id }, "an announcer unpins")
        assertRefused(403, "notAllowed", await(backend.pin(mika.uuid, b, true, false)))
        assertRefused(409, "notPinnable", await(backend.pin(lune.uuid, global, true, false)))
        assertRefused(403, "banned", await(backend.pin(sam.uuid, b, true, false)))
        assertRefused(404, "notLinked", await(backend.pin(visitor.uuid, b, true, false)))
        assertRefused(404, "notFound", await(backend.pin(lune.uuid, UUID.randomUUID(), true, false)))
        assertEquals(listOf(d, b), ok(backend.pin(lune.uuid, global, false, false)).pinned.map { it.id }, "unpinning a Global post answers the group")
        flag("feed.writing", false)
        try {
            assertEquals(listOf(c, d, b), ok(backend.pin(lune.uuid, c, true, false)).pinned.map { it.id }, "pinning isn't behind feed.writing")
            ok(backend.pin(lune.uuid, c, false, false))
        } finally {
            flag("feed.writing", true)
        }
        ok(backend.delete(lune.uuid, d))
        assertEquals(listOf(b), ok(backend.page(camille.uuid, FeedKind.ANNOUNCEMENTS, null, 20)).pinned.map { it.id })
    }

    // ---- 4.9 Deletes ----

    @Test
    @Order(10)
    fun `deleting, by whom`() {
        reset()
        val mine = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "À moi", false) }.post
        val reply = write { backend.createReply(UUID.randomUUID(), mika.uuid, mine.id, null, "Réponse", false) }.post
        assertRefused(403, "notAllowed", await(backend.delete(mika.uuid, mine.id)))
        assertRefused(404, "notLinked", await(backend.delete(visitor.uuid, mine.id)))
        assertRefused(404, "notFound", await(backend.delete(camille.uuid, UUID.randomUUID())))
        flag("feed.writing", false)
        try {
            ok(backend.delete(camille.uuid, mine.id))
        } finally {
            flag("feed.writing", true)
        }
        ok(backend.delete(camille.uuid, mine.id)) // again: 204, a retry is harmless
        ok(backend.delete(mika.uuid, reply.id)) // a reply gone with its thread, deleted again by its author, who could: 204
        val other = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "À Mika", false) }.post
        ok(backend.delete(lune.uuid, other.id)) // a moderator, anyone's
        val samPost = insertPost("global", sam, "Avant le ban", minutesAgo = 5)
        ok(backend.delete(sam.uuid, samPost)) // the author, banned
        assertRefused(404, "notFound", await(backend.delete(steve.uuid, other.id)), "gone, and Steve couldn't have deleted it")
    }

    // ---- 13.6 Unread and markers, 4.10, 4.11 ----

    @Test
    @Order(11)
    fun `unread dots and unseen replies, as the contract's vector`() {
        reset()
        val g1 = write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "G1", false) }.post
        assertTrue(ok(backend.player(camille.uuid)).unread.global, "never viewed")
        ok(backend.markFeedSeen(camille.uuid, FeedKind.GLOBAL, g1.createdAt))
        assertFalse(ok(backend.player(camille.uuid)).unread.global)
        val g2 = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "G2", false) }.post
        assertFalse(ok(backend.player(camille.uuid)).unread.global, "her own post")
        write { backend.createPost(UUID.randomUUID(), mika.uuid, FeedKind.GLOBAL, "G3", false) }
        assertTrue(ok(backend.player(camille.uuid)).unread.global)
        val reply = write { backend.createReply(UUID.randomUUID(), mika.uuid, g2.id, null, "Réponse", false) }.post
        assertEquals(1, ok(backend.page(camille.uuid, FeedKind.GLOBAL, null, 20)).posts.single { it.id == g2.id }.unseenReplies)
        assertTrue(ok(backend.views(listOf(camille.uuid), listOf(reply.id))).getValue(camille.uuid).single().answersMe)
        assertEquals(1, ok(backend.thread(camille.uuid, g2.id, null, 50)).post.unseenReplies, "computed before the mark")
        ok(backend.markThreadSeen(camille.uuid, g2.id))
        assertEquals(0, ok(backend.page(camille.uuid, FeedKind.GLOBAL, null, 20)).posts.single { it.id == g2.id }.unseenReplies)
        ok(backend.markFeedSeen(visitor.uuid, FeedKind.GLOBAL, Instant.now()))
        ok(backend.markThreadSeen(visitor.uuid, g2.id))
        assertRefused(404, "notFound", await(backend.markThreadSeen(camille.uuid, UUID.randomUUID())))
        // upTo in the future is kept as now at most: a newer post still lights the dot.
        ok(backend.markFeedSeen(camille.uuid, FeedKind.GLOBAL, Instant.now().plusSeconds(3600)))
        Thread.sleep(20)
        write { backend.createPost(UUID.randomUUID(), steve.uuid, FeedKind.GLOBAL, "G4", false) }
        assertTrue(ok(backend.player(camille.uuid)).unread.global, "a seen mark is never later than now")
    }

    // ---- 4.12, 4.13 The notification queue ----

    @Test
    @Order(12)
    fun `the notification queue, its pages and its acknowledgement`() {
        reset()
        val announcement = insertPost("announcements", kael, "Annonce", minutesAgo = 1)
        sql((1..150).joinToString("\n") { i ->
            "insert into feed_notification (id, kind, post_id, recipient_user_id, created_at) values (gen_random_uuid(), 'announcement', '$announcement', null, (now() at time zone 'utc') - interval '${200 - i} seconds');"
        })
        val first = ok(backend.pendingNotifications())
        assertEquals(100, first.items.size); assertTrue(first.more)
        assertEquals(first.items.sortedBy { it.createdAt }, first.items, "oldest first")
        assertTrue(first.items.all { it.players == null && it.except == listOf(kael.uuid) && it.post.author?.username == "Kael_Builder" })
        ok(backend.ackNotifications(first.items.map { it.id }))
        val second = ok(backend.pendingNotifications())
        assertEquals(50, second.items.size); assertFalse(second.more)
        ok(backend.ackNotifications(second.items.map { it.id } + UUID.randomUUID()))
        assertTrue(ok(backend.pendingNotifications()).items.isEmpty())
        // A post deleted before delivery takes its rows with it.
        val thread = write { backend.createPost(UUID.randomUUID(), camille.uuid, FeedKind.GLOBAL, "Fil", false) }.post
        val reply = write { backend.createReply(UUID.randomUUID(), mika.uuid, thread.id, null, "Réponse", false) }.post
        assertEquals(listOf(reply.id), ok(backend.pendingNotifications()).items.map { it.post.id })
        write { backend.createReply(UUID.randomUUID(), camille.uuid, thread.id, null, "À moi-même", false) }
        assertEquals(1, ok(backend.pendingNotifications()).items.size, "a reply to oneself notifies nobody")
        ok(backend.delete(mika.uuid, reply.id))
        assertTrue(ok(backend.pendingNotifications()).items.isEmpty())
    }

    // ---- 4.14, 4.15 The command's calls ----

    @Test
    @Order(13)
    fun `the announcer command's calls`() {
        reset()
        sql("update \"user\" set role = null where id in ('${camille.userId}', '${lea.userId}');")
        val asLune = Issuer(lune.uuid, lune.username)
        ok(backend.setAnnouncer(camille.uuid, true, asLune)).let {
            assertEquals(camille.uuid, it.uuid); assertEquals("Cam_Roux", it.username); assertTrue(it.announcer); assertFalse(it.viaRole)
        }
        assertTrue(ok(backend.setAnnouncer(camille.uuid, true, asLune)).announcer, "giving it twice changes nothing")
        assertTrue(ok(backend.player(camille.uuid)).rights.announce)
        val list = ok(backend.announcers())
        assertTrue(list.any { e -> e.realName == "Camille R." && e.accounts.map { it.username } == listOf("Cam_Roux") })
        assertFalse(list.any { e -> e.accounts.any { it.uuid == lune.uuid } }, "moderators aren't listed")
        assertFalse(ok(backend.setAnnouncer(camille.uuid, false, asLune)).announcer)
        assertRefused(403, "issuerNotAllowed", await(backend.setAnnouncer(steve.uuid, true, Issuer(camille.uuid, camille.username))))
        assertRefused(403, "issuerNotAllowed", await(backend.setAnnouncer(steve.uuid, true, Issuer(visitor.uuid, visitor.username))))
        assertRefused(409, "impliedByRole", await(backend.setAnnouncer(lune.uuid, false, asLune)))
        ok(backend.setAnnouncer(lune.uuid, true, null)).let { assertTrue(it.announcer); assertTrue(it.viaRole) }
        assertRefused(404, "notLinked", await(backend.setAnnouncer(visitor.uuid, true, asLune)))
        ok(backend.setAnnouncer(leaAlt.uuid, true, null)) // the console
        val lea2 = ok(backend.announcers()).single { e -> e.accounts.any { it.uuid == lea.uuid } }
        assertEquals(setOf("LeaBuilds", "Lea_Alt"), lea2.accounts.map { it.username }.toSet(), "every linked account")
        assertFalse(ok(backend.setAnnouncer(lea.uuid, false, null)).announcer)
    }

    // ---- 2.1, 2.3, 11.2 Flags and the secret ----

    @Test
    @Order(14)
    fun `the flags and a wrong secret`() {
        flag("feed.serverApi", false)
        try {
            assertEquals(BackendResult.Disabled("feed.serverApi"), await(backend.player(camille.uuid)))
            assertEquals(BackendResult.Disabled("feed.serverApi"), await(backend.page(camille.uuid, FeedKind.GLOBAL, null, 20)))
        } finally {
            flag("feed.serverApi", true)
        }
        flag("feed", false)
        try {
            assertEquals(BackendResult.Disabled("feed"), await(backend.player(camille.uuid)))
            assertEquals(BackendResult.Disabled("feed"), await(backend.pendingNotifications()))
        } finally {
            flag("feed", true)
        }
        assertTrue(await(SiteBackend { site to "not-the-secret-not-the-secret-123" }.player(camille.uuid)) is BackendResult.Unavailable)
        assertTrue(await(SiteBackend { "http://127.0.0.1:9" to token }.player(camille.uuid)) is BackendResult.Unavailable)
    }

    // ---- 8 The stream, with events made in the browser ----

    sealed interface Event {
        data object Connected : Event
        data class Disconnected(val disabled: Boolean) : Event
        data class Post(val id: UUID, val feed: FeedKind, val threadId: UUID?) : Event
        data class Deleted(val items: List<RemovedItem>) : Event
        data class Counts(val items: List<CountUpdate>) : Event
        data class Pins(val pinned: List<UUID>) : Event
        data class Rights(val players: List<UUID>) : Event
        data object Notification : Event
    }

    @Test
    @Order(15)
    fun `the stream, each event from the browser, Last-Event-ID, and the feature switched off`() {
        reset()
        val events = LinkedBlockingQueue<Event>()
        val listener = object : FeedEventListener {
            override fun onConnected() { events.put(Event.Connected) }
            override fun onDisconnected(disabled: Boolean) { events.put(Event.Disconnected(disabled)) }
            override fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant) { events.put(Event.Post(id, feed, threadId)) }
            override fun onDeleted(items: List<RemovedItem>) { events.put(Event.Deleted(items)) }
            override fun onCounts(items: List<CountUpdate>) { events.put(Event.Counts(items)) }
            override fun onPins(pinned: List<Pin>) { events.put(Event.Pins(pinned.map { it.id })) }
            override fun onRights(players: List<UUID>) { events.put(Event.Rights(players)) }
            override fun onNotification() { events.put(Event.Notification) }
        }
        val offset = AtomicLong()
        val sleeps = LinkedBlockingQueue<Long>()
        val gate = LinkedBlockingQueue<Unit>()
        val stream = FeedEvents({ site to token }, clock = { System.currentTimeMillis() + offset.get() }, sleep = { ms -> sleeps.put(ms); gate.take() })
        fun next(what: String, timeoutSeconds: Long = 15): Event = events.poll(timeoutSeconds, TimeUnit.SECONDS) ?: fail("no event: expected $what")
        fun expect(what: String, timeoutSeconds: Long = 15, test: (Event) -> Boolean): Event {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            val skipped = mutableListOf<Event>()
            while (System.nanoTime() < deadline) {
                val e = events.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (test(e)) return e
                skipped += e
            }
            throw AssertionError("expected $what; got $skipped")
        }
        val started = System.nanoTime()
        stream.start(listener)
        try {
            assertEquals(Event.Connected, next("the connection", 40))
            println("LiveSiteCheck: the stream connected after ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms")

            val post = browser("mika", "POST", "/api/feed/posts", """{"idempotencyKey":"${UUID.randomUUID()}","feed":"global","text":"Depuis le navigateur","anonymous":false}""")
                .getAsJsonObject("post")
            val postId = UUID.fromString(post.get("id").asString)
            expect("post") { it == Event.Post(postId, FeedKind.GLOBAL, null) }

            val reply = browser("camille", "POST", "/api/feed/posts/$postId/replies", """{"idempotencyKey":"${UUID.randomUUID()}","replyTo":null,"text":"Réponse du navigateur","anonymous":false}""")
                .getAsJsonObject("post")
            val replyId = UUID.fromString(reply.get("id").asString)
            expect("the reply's post") { it == Event.Post(replyId, FeedKind.GLOBAL, postId) }
            expect("the reply's counts") { e -> e is Event.Counts && e.items.any { it.id == postId && it.replies == 1 } }
            expect("the reply's notification") { it == Event.Notification }

            browser("steve", "PUT", "/api/feed/posts/$postId/like", null)
            expect("the like's counts") { e -> e is Event.Counts && e.items.any { it.id == postId && it.likes == 1 } }

            val announcement = browser("kael", "POST", "/api/feed/posts", """{"idempotencyKey":"${UUID.randomUUID()}","feed":"announcements","text":"Annonce du navigateur","anonymous":false}""")
                .getAsJsonObject("post")
            val announcementId = UUID.fromString(announcement.get("id").asString)
            expect("the announcement") { it == Event.Post(announcementId, FeedKind.ANNOUNCEMENTS, null) }
            expect("the announcement's notification") { it == Event.Notification }
            browser("kael", "PUT", "/api/feed/posts/$announcementId/pin", "{}")
            expect("the pin") { it == Event.Pins(listOf(announcementId)) }

            browser("root", "PUT", "/api/feed/admin/announcers/${camille.userId}", """{"announcer":true}""")
            expect("rights for the new announcer") { e -> e is Event.Rights && camille.uuid in e.players }
            browser("root", "PUT", "/api/feed/admin/announcers/${camille.userId}", """{"announcer":false}""")
            expect("rights again") { e -> e is Event.Rights && camille.uuid in e.players }

            browser("mika", "DELETE", "/api/feed/posts/$postId", null)
            expect("the deletion, with its thread") { e -> e is Event.Deleted && e.items.map { it.id }.toSet() == setOf(postId, replyId) }
            browser("kael", "DELETE", "/api/feed/posts/$announcementId", null)
            expect("the pinned announcement's deletion") { e -> e is Event.Deleted && e.items.any { it.id == announcementId } }
            expect("the pins after it") { it == Event.Pins(emptyList()) }

            // A dropped connection: the watchdog closes it, and the thread waits in its backoff while the browser posts.
            offset.addAndGet(100_000)
            stream.checkSilence()
            assertEquals(Event.Disconnected(false), expect("the loss") { it is Event.Disconnected })
            assertEquals(1_000L, sleeps.poll(10, TimeUnit.SECONDS), "the first backoff")
            val missed = UUID.fromString(
                browser("mika", "POST", "/api/feed/posts", """{"idempotencyKey":"${UUID.randomUUID()}","feed":"global","text":"Pendant la coupure","anonymous":false}""")
                    .getAsJsonObject("post").get("id").asString,
            )
            Thread.sleep(1500)
            gate.put(Unit)
            expect("the reconnection", 40) { it == Event.Connected }
            expect("the missed post, replayed after Last-Event-ID") { it == Event.Post(missed, FeedKind.GLOBAL, null) }

            // Feed switched off: an open stream isn't closed by the site; a new connection is refused with 503 disabled.
            flag("feed", false)
            browserStatus("mika", "GET", "/api/feed/me").let { assertEquals(503, it, "the flag took effect") }
            val quiet = events.poll(3, TimeUnit.SECONDS)
            println("LiveSiteCheck: after switching feed off, the open stream gave ${quiet ?: "nothing"}")
            offset.addAndGet(100_000)
            stream.checkSilence()
            expect("the loss") { it == Event.Disconnected(false) }
            // The clock jump makes the last connection look a long one, so the backoff may have started over.
            assertNotNull(sleeps.poll(10, TimeUnit.SECONDS), "a backoff")
            gate.put(Unit)
            expect("refused as switched off", 40) { it == Event.Disconnected(true) }
            assertEquals(60_000L, sleeps.poll(10, TimeUnit.SECONDS), "a minute while switched off")
            flag("feed", true)
            gate.put(Unit)
            expect("connected again once switched on", 40) { it == Event.Connected }
        } finally {
            stream.stop()
            flagQuietly("feed", true)
        }
    }

    // ---- Plumbing ----

    private fun <T> await(future: CompletableFuture<BackendResult<T>>): BackendResult<T> = future.get(30, TimeUnit.SECONDS)

    private fun <T> ok(future: CompletableFuture<BackendResult<T>>): T {
        val result = await(future)
        return (result as? BackendResult.Ok<T>)?.body ?: fail("expected an answer, got $result")
    }

    /** A write, waiting out the site's per-account rate limits (the same key: idempotent). */
    private fun <T> writeResult(call: () -> CompletableFuture<BackendResult<T>>): BackendResult<T> {
        repeat(30) {
            val result = await(call())
            if (result is BackendResult.Refused && result.status == 429) {
                Thread.sleep(((result.retryAfterSeconds ?: 5) + 1) * 1000L)
            } else {
                return result
            }
        }
        throw AssertionError("still rate limited")
    }

    private fun <T> write(call: () -> CompletableFuture<BackendResult<T>>): T {
        val result = writeResult(call)
        return (result as? BackendResult.Ok<T>)?.body ?: fail("expected an answer, got $result")
    }

    private fun assertRefused(status: Int, problem: String?, result: BackendResult<*>, what: String = "") {
        val refused = result as? BackendResult.Refused ?: fail("$what: expected $status $problem, got $result")
        assertEquals(status to problem, refused.status to refused.problem, what)
    }

    /** Empties Feed's tables, so each check starts from nothing (the site's rate limits stay: they are in memory). */
    private fun reset() {
        sql("delete from feed_notification; delete from feed_thread_seen; delete from feed_like; delete from feed_post; delete from feed_member;")
    }

    private fun insertPost(feed: String, author: Person, text: String, minutesAgo: Int, threadId: UUID? = null, secondsAgo: Int = 0): UUID {
        val id = UUID.randomUUID()
        val thread = threadId?.let { "'$it'" } ?: "null"
        sql(
            "insert into feed_post (id, feed, thread_id, author_user_id, minecraft_uuid, minecraft_username, source, text, anonymous, created_at, idempotency_key, fingerprint) " +
                "values ('$id', '$feed', $thread, '${author.userId}', '${author.uuid}', '${author.username}', 'server', '$text', false, " +
                "date_trunc('milliseconds', (now() at time zone 'utc') - interval '$minutesAgo minutes' - interval '$secondsAgo seconds'), '${UUID.randomUUID()}', 'live-check');" +
                (threadId?.let { " update feed_post set reply_count = reply_count + 1 where id = '$it';" } ?: ""),
        )
        return id
    }

    private fun sql(statement: String): String {
        val process = ProcessBuilder("docker", "exec", "-i", "lnb-website-postgres-1", "psql", "-U", "lnb", "-d", db, "-v", "ON_ERROR_STOP=1", "-At")
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(statement.toByteArray(Charsets.UTF_8)) }
        val out = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        if (process.waitFor() != 0) throw AssertionError("psql: $out")
        return out
    }

    private fun send(who: String, method: String, path: String, body: String?): HttpResponse<String> {
        val cookie = cookies.get(who)?.asString ?: fail("no cookie for $who in FEED_LIVE_COOKIES")
        val request = HttpRequest.newBuilder(URI.create(site + path))
            .header("cookie", cookie)
            .header("origin", site)
            .header("accept", "application/json")
            .apply { if (body != null) header("content-type", "application/json") }
            .method(method, if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(20))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /** A call of the browser's (or the admin's) routes as a seeded person; a refusal fails the check. */
    private fun browser(who: String, method: String, path: String, body: String?): JsonObject {
        repeat(30) {
            val response = send(who, method, path, body)
            if (response.statusCode() == 429) {
                Thread.sleep(((response.headers().firstValue("Retry-After").orElse("5").toIntOrNull() ?: 5) + 1) * 1000L)
            } else {
                if (response.statusCode() !in 200..299) throw AssertionError("$method $path as $who: ${response.statusCode()} ${response.body().take(300)}")
                return if (response.body().isBlank()) JsonObject() else JsonParser.parseString(response.body()).asJsonObject
            }
        }
        throw AssertionError("$method $path as $who: still rate limited")
    }

    private fun browserStatus(who: String, method: String, path: String): Int = send(who, method, path, null).statusCode()

    /** Switches a flag as the site's admin does, which also clears the site's flag cache at once. */
    private fun flag(key: String, enabled: Boolean) {
        browser("root", "PATCH", "/api/admin/flags/$key", """{"enabled":$enabled}""")
    }

    private fun flagQuietly(key: String, enabled: Boolean) = runCatching { flag(key, enabled) }
}
