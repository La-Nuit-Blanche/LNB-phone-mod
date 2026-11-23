package fr.bde_teknantes.lnb.phone.notifications.server

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * The [NotificationStore], saved with the world in the overworld's data folder as `lnbphone_notifications.dat`.
 * Server thread only. Whoever changes [store] calls [setDirty] so the next save writes it.
 *
 * Each save first drops the notifications past their age ([NotificationStore.MAX_AGE_MS]); loading drops them too.
 *
 * @param clock the time in milliseconds since the epoch, for the expiry.
 */
class NotificationSavedData internal constructor(
    val store: NotificationStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedData() {
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        store.expire(clock())
        val saved = store.save(registries.createSerializationContext(NbtOps.INSTANCE)) { error ->
            LnbPhone.LOGGER.warn("A phone notification couldn't be saved and was left out of the file: {}", error)
        }
        return tag.merge(saved)
    }

    companion object {
        private const val NAME = "lnbphone_notifications"

        /**
         * The server's notifications, or null before its levels exist (during `ServerAboutToStartEvent`, say), when
         * there is nowhere to keep them yet.
         */
        fun getOrNull(server: MinecraftServer): NotificationSavedData? =
            server.overworld()?.dataStorage?.computeIfAbsent(Factory({ NotificationSavedData(NotificationStore()) }, ::load, null), NAME)

        internal fun load(tag: CompoundTag, registries: HolderLookup.Provider): NotificationSavedData {
            val store = NotificationStore.load(tag, registries.createSerializationContext(NbtOps.INSTANCE)) { error ->
                LnbPhone.LOGGER.warn("A saved phone notification couldn't be read and was dropped: {}", error)
            }
            return NotificationSavedData(store)
        }
    }
}
