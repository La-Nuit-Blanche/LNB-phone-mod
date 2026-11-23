package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.server.NotificationSavedData
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** The store as Minecraft saves it with the world, through [NotificationSavedData]. */
class NotificationSavedDataTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val alice = UUID(0, 1)
    private val day = 24 * 60 * 60 * 1000L

    private fun NotificationStore.add(time: Long, text: String) =
        add(alice, feed, Component.literal("t"), Component.literal(text), null, time, false)

    @Test
    fun `what the world saves, the world loads`() {
        val now = System.currentTimeMillis()
        val store = NotificationStore().apply {
            add(now, "a")
            add(now, "b")
        }
        val tag = NotificationSavedData(store).save(CompoundTag(), RegistryAccess.EMPTY)
        val loaded = NotificationSavedData.load(tag, RegistryAccess.EMPTY)
        assertEquals(store.of(alice), loaded.store.of(alice))
        assertEquals(store.nextId, loaded.store.nextId)
    }

    @Test
    fun `each save first drops what has expired`() {
        val store = NotificationStore().apply {
            add(0L, "old")
            add(20 * day, "recent")
        }
        val data = NotificationSavedData(store) { 31 * day }
        val tag = data.save(CompoundTag(), RegistryAccess.EMPTY)
        assertEquals(listOf("recent"), store.of(alice).map { it.text.string })
        val reloaded = NotificationStore.load(tag, RegistryAccess.EMPTY.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), now = 31 * day)
        assertEquals(listOf("recent"), reloaded.of(alice).map { it.text.string })
    }
}
