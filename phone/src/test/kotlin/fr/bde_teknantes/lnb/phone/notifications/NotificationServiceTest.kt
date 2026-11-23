package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationHost
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationSavedData
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationService
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The server side of notifications over a host standing in for the running server. */
class NotificationServiceTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val mail = ResourceLocation.fromNamespaceAndPath("test", "mail")
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)
    private val carol = UUID(0, 3)
    private val title = Component.literal("title")
    private val text = Component.literal("text")

    private class FakeHost(var store: NotificationStore? = NotificationStore()) : NotificationHost {
        val online = HashSet<UUID>()
        val known = HashSet<UUID>()
        val sent = ArrayList<Pair<UUID, CustomPacketPayload>>()
        var time = 1000L
        var broken: (PhoneNotification) -> Boolean = { false }
        private var saved: NotificationSavedData? = null

        override val data: NotificationSavedData?
            get() = store?.let { s -> saved?.takeIf { it.store === s } ?: NotificationSavedData(s).also { saved = it } }

        override fun isOnline(player: UUID) = player in online

        override fun send(player: UUID, payload: CustomPacketPayload) {
            if (player in online) sent += player to payload
        }

        override fun knownPlayers(): Collection<UUID> = known + online

        override fun encodedSize(notification: PhoneNotification): Int {
            if (broken(notification)) return -1
            val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            NotificationCodecs.STREAM_CODEC.encode(buf, notification)
            return buf.readableBytes()
        }

        override fun now() = time

        fun sentTo(player: UUID) = sent.filter { it.first == player }.map { it.second }
    }

    private var clock = 0L
    private val service = NotificationService { clock }
    private val host = FakeHost()
    private val store get() = host.store!!

    // ---- Sending ----

    @Test
    fun `a connected player gets the notification at once, delivered, and an absent one finds it stored`() {
        host.online += alice
        assertEquals(2, service.send(host, listOf(alice, bob), feed, title, text, "thread:1"))
        val forAlice = store.of(alice).single()
        assertTrue(forAlice.delivered)
        assertEquals(listOf(NotificationAddedPayload(forAlice)), host.sentTo(alice))
        assertFalse(store.of(bob).single().delivered)
        assertEquals(1000L, forAlice.time)
        assertTrue(host.data!!.isDirty)
    }

    @Test
    fun `a player named twice gets one notification, and nobody marks nothing dirty`() {
        assertEquals(1, service.send(host, listOf(alice, alice), feed, title, text, null))
        assertEquals(1, store.of(alice).size)
        val fresh = FakeHost()
        assertEquals(0, service.send(fresh, emptyList(), feed, title, text, null))
        assertFalse(fresh.data!!.isDirty)
    }

    @Test
    fun `everyone means every player who has played here, connected or not`() {
        host.known += listOf(alice, bob)
        host.online += carol
        assertEquals(3, service.sendToEveryone(host, feed, title, text, null))
        assertEquals(setOf(alice, bob, carol), store.playersWithNotifications)
    }

    @Test
    fun `a notification too large to send is not stored`() {
        host.broken = { true }
        host.online += alice
        assertEquals(0, service.send(host, listOf(alice), feed, title, text, null))
        assertEquals(emptySet<UUID>(), store.playersWithNotifications)
        assertEquals(emptyList<Any>(), host.sent)
    }

    @Test
    fun `before the levels exist, calls do nothing and don't throw`() {
        val early = FakeHost(store = null)
        early.online += alice
        assertEquals(0, service.send(early, listOf(alice), feed, title, text, null))
        service.clear(early, alice, feed, null)
        service.clearEverywhere(early, feed, null)
        service.login(early, alice)
        service.clientClearedApp(early, alice, feed, 1)
        service.clientDismissed(early, alice, 1)
        service.resync(early)
        assertEquals(emptyList<Any>(), early.sent)
    }

    // ---- Clearing from the app ----

    @Test
    fun `clearing tells a connected client only when something went`() {
        host.online += alice
        service.send(host, listOf(alice), feed, title, text, "thread:1")
        service.send(host, listOf(alice), feed, title, text, "thread:2")
        host.sent.clear()
        service.clear(host, alice, feed, "thread:3")
        assertEquals(emptyList<Any>(), host.sent)
        service.clear(host, alice, feed, "thread:1")
        assertEquals(listOf<Any>(NotificationsClearedPayload(feed, "thread:1")), host.sentTo(alice))
        service.clear(host, alice, feed, null)
        assertEquals(NotificationsClearedPayload(feed, null), host.sentTo(alice).last())
        assertEquals(emptyList<PhoneNotification>(), store.of(alice))
    }

    @Test
    fun `clearing everywhere reaches every player who had one, connected or not`() {
        host.online += alice
        service.send(host, listOf(alice, bob, carol), feed, title, text, "post:9")
        service.send(host, listOf(bob), feed, title, text, "post:10")
        service.send(host, listOf(carol), mail, title, text, "post:9")
        host.sent.clear()
        service.clearEverywhere(host, feed, "post:9")
        assertEquals(emptyList<PhoneNotification>(), store.of(alice))
        assertEquals(listOf("post:10"), store.of(bob).map { it.target })
        assertEquals(listOf(mail), store.of(carol).map { it.appId })
        assertEquals(listOf<Pair<UUID, Any>>(alice to NotificationsClearedPayload(feed, "post:9")), host.sent)
        service.clearEverywhere(host, feed, null)
        assertEquals(emptyList<PhoneNotification>(), store.of(bob))
        assertEquals(1, store.of(carol).size)
    }

    // ---- Login ----

    @Test
    fun `login sends everything as it was, then marks it delivered`() {
        service.send(host, listOf(alice), feed, title, text, null)
        host.online += alice
        service.login(host, alice)
        val sync = host.sentTo(alice).single() as NotificationsSyncPayload
        assertTrue(sync.login)
        assertFalse(sync.notifications.single().delivered)
        assertTrue(store.of(alice).single().delivered)
    }

    @Test
    fun `login drops what has expired before the sync`() {
        service.send(host, listOf(alice), feed, title, Component.literal("old"), null)
        host.time += NotificationStore.MAX_AGE_MS
        service.send(host, listOf(alice), feed, title, Component.literal("new"), null)
        host.time += 1
        host.online += alice
        service.login(host, alice)
        val sync = host.sentTo(alice).single() as NotificationsSyncPayload
        assertEquals(listOf("new"), sync.notifications.map { it.text.string })
        assertEquals(1, store.of(alice).size)
    }

    @Test
    fun `a notification that can't be sent is removed instead of failing the whole sync`() {
        service.send(host, listOf(alice), feed, title, Component.literal("good"), null)
        service.send(host, listOf(alice), feed, title, Component.literal("bad"), null)
        host.broken = { it.text.string == "bad" }
        host.online += alice
        service.login(host, alice)
        val sync = host.sentTo(alice).single() as NotificationsSyncPayload
        assertEquals(listOf("good"), sync.notifications.map { it.text.string })
        assertEquals(listOf("good"), store.of(alice).map { it.text.string })
    }

    // ---- Requests from clients ----

    @Test
    fun `a client clears its own app up to the newest it knew, and dismisses only its own`() {
        val first = service.send(host, listOf(alice), feed, title, text, null).let { store.of(alice).last() }
        service.send(host, listOf(alice), feed, title, text, null)
        service.send(host, listOf(bob), feed, title, text, null)
        service.clientClearedApp(host, alice, feed, first.id)
        assertEquals(1, store.of(alice).size)
        // Bob's notification can't be dismissed by Alice.
        service.clientDismissed(host, alice, store.of(bob).single().id)
        assertEquals(1, store.of(bob).size)
        service.clientDismissed(host, alice, store.of(alice).single().id)
        assertEquals(emptyList<PhoneNotification>(), store.of(alice))
    }

    @Test
    fun `a refused request puts the client back in step at the next resync, once`() {
        host.online += alice
        repeat(12) { service.send(host, listOf(alice), feed, title, text, null) }
        host.sent.clear()
        val ids = store.of(alice).map { it.id }
        // Ten at once are allowed, the eleventh is refused and its notification stays.
        for (id in ids.take(11)) service.clientDismissed(host, alice, id)
        assertEquals(ids.drop(10), store.of(alice).map { it.id })
        assertEquals(emptyList<Any>(), host.sent)
        service.resync(host)
        val sync = host.sentTo(alice).single() as NotificationsSyncPayload
        assertFalse(sync.login)
        assertEquals(ids.drop(10), sync.notifications.map { it.id })
        service.resync(host)
        assertEquals(1, host.sent.size)
        // Time refills the bucket.
        clock += 500
        service.clientDismissed(host, alice, ids[10])
        assertEquals(listOf(ids[11]), store.of(alice).map { it.id })
    }

    @Test
    fun `a player who left is not resynced, and comes back with a fresh allowance`() {
        host.online += alice
        repeat(11) { service.send(host, listOf(alice), feed, title, text, null) }
        host.sent.clear()
        repeat(11) { service.clientClearedApp(host, alice, mail, 1) }
        host.online -= alice
        service.logout(alice)
        service.resync(host)
        assertEquals(emptyList<Any>(), host.sent)
        host.online += alice
        val ids = store.of(alice).map { it.id }
        for (id in ids.take(10)) service.clientDismissed(host, alice, id)
        assertEquals(1, store.of(alice).size)
    }

    // ---- The backstop against an app that loops ----

    @Test
    fun `one mod may send one player 20 at once, then one every 3 seconds`() {
        repeat(NotificationService.APP_BURST) { assertEquals(1, service.send(host, listOf(alice), feed, title, text, null)) }
        assertEquals(0, service.send(host, listOf(alice), feed, title, text, null))
        assertEquals(NotificationService.APP_BURST, store.of(alice).size)
        clock += 2_999
        assertEquals(0, service.send(host, listOf(alice), feed, title, text, null))
        clock += 1
        assertEquals(1, service.send(host, listOf(alice), feed, title, text, null))
    }

    @Test
    fun `the backstop is per player and per mod, so a flood stops nobody else`() {
        val quests = ResourceLocation.fromNamespaceAndPath("quests", "quests")
        repeat(30) { service.send(host, listOf(alice), feed, title, text, null) }
        // The same mod under another path shares the allowance.
        assertEquals(0, service.send(host, listOf(alice), mail, title, text, null))
        // Another mod for the same player, and the same mod for another player, are untouched.
        assertEquals(1, service.send(host, listOf(alice), quests, title, text, null))
        assertEquals(2, service.send(host, listOf(alice, bob), quests, title, text, null))
        assertEquals(1, store.of(bob).size)
        // A broadcast skips only the players over the limit.
        assertEquals(1, service.send(host, listOf(alice, carol), feed, title, text, null))
        assertEquals(1, store.of(carol).size)
    }

    @Test
    fun `an announcement and a reply in the same second both arrive`() {
        host.online += alice
        service.sendToEveryone(host, feed, Component.literal("New announcement"), text, null)
        service.send(host, listOf(alice), feed, Component.literal("New reply"), text, "thread:1")
        assertEquals(listOf("New announcement", "New reply"), store.of(alice).map { it.title.string })
        assertEquals(2, host.sentTo(alice).size)
    }

    @Test
    fun `allowances that filled up again are forgotten`() {
        repeat(NotificationService.APP_BURST) { service.send(host, listOf(alice), feed, title, text, null) }
        clock += 60_000
        service.resync(host)
        // A fresh allowance: a burst of 20 again, not 20 plus what built up.
        repeat(NotificationService.APP_BURST) { assertEquals(1, service.send(host, listOf(alice), feed, title, text, null)) }
        assertEquals(0, service.send(host, listOf(alice), feed, title, text, null))
    }
}
