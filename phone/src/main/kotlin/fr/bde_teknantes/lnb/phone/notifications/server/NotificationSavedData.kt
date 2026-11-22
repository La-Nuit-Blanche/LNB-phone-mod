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
 */
class NotificationSavedData private constructor(val store: NotificationStore) : SavedData() {
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag =
        tag.merge(store.save(registries.createSerializationContext(NbtOps.INSTANCE)))

    companion object {
        private const val NAME = "lnbphone_notifications"

        fun get(server: MinecraftServer): NotificationSavedData =
            server.overworld().dataStorage.computeIfAbsent(Factory({ NotificationSavedData(NotificationStore()) }, ::load, null), NAME)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): NotificationSavedData {
            val store = NotificationStore.load(tag, registries.createSerializationContext(NbtOps.INSTANCE)) { error ->
                LnbPhone.LOGGER.warn("A saved phone notification couldn't be read and was dropped: {}", error)
            }
            return NotificationSavedData(store)
        }
    }
}
