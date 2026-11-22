package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationStoreTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val mail = ResourceLocation.fromNamespaceAndPath("test", "mail")
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)

    private fun NotificationStore.add(player: UUID, app: ResourceLocation, text: String, target: String? = null) =
        add(player, app, Component.literal("t"), Component.literal(text), target, 1000L, delivered = false)

    @Test
    fun `keeps the newest per player and drops the oldest`() {
        val store = NotificationStore(capacity = 3)
        repeat(5) { store.add(alice, feed, "n$it") }
        store.add(bob, feed, "b")
        assertEquals(listOf("n2", "n3", "n4"), store.of(alice).map { it.text.string })
        assertEquals(1, store.of(bob).size)
        assertEquals(7L, store.nextId)
    }

    @Test
    fun `clearing an app stops at the id the client knew`() {
        val store = NotificationStore()
        val first = store.add(alice, feed, "a")
        store.add(alice, mail, "m")
        store.add(alice, feed, "late")
        assertEquals(listOf(first), store.clearApp(alice, feed, upToId = first.id))
        assertEquals(listOf("m", "late"), store.of(alice).map { it.text.string })
        // Another player's id does nothing.
        assertNull(store.remove(bob, first.id + 1))
    }

    @Test
    fun `survives a save and a load`() {
        val ops = RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE)
        val store = NotificationStore()
        store.add(alice, feed, "a", target = "thread:12")
        store.add(alice, feed, "b")
        store.add(bob, mail, "c")
        store.clearApp(bob, mail)
        assertTrue(store.markDelivered(alice))

        val loaded = NotificationStore.load(store.save(ops), ops)
        assertEquals(store.of(alice), loaded.of(alice))
        assertEquals(emptyList<PhoneNotification>(), loaded.of(bob))
        assertEquals(store.nextId, loaded.nextId)
    }

    @Test
    fun `cuts long texts and refuses long targets`() {
        val long = "é".repeat(500)
        val text = NotificationLimits.text(Component.translatable("notif.test", "Mika", long))
        val args = (text.contents as TranslatableContents).args
        assertEquals("Mika", args[0])
        assertEquals(NotificationLimits.MAX_TEXT_LENGTH - 4, args[1].toString().length)
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkAddress(feed, "x".repeat(257)) }
        assertNull(NotificationLimits.checkAddress(feed, ""))
    }
}
