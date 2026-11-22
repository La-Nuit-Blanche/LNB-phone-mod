package fr.bde_teknantes.lnb.phone.notifications.server

import com.mojang.serialization.DynamicOps
import fr.bde_teknantes.lnb.phone.notifications.NotificationCodecs
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Every player's notifications, online or not, as plain logic: what the server saves with the world
 * ([NotificationSavedData] wraps it). Ids come from one counter for the whole server, saved with the rest, so they
 * never repeat, even across restarts.
 *
 * Not thread-safe: the server thread alone uses it ([ServerNotifications] makes sure of that).
 */
class NotificationStore(private val capacity: Int = NotificationList.CAPACITY) {
    private val players = HashMap<UUID, NotificationList>()

    /** The id the next notification gets. */
    var nextId = 1L
        private set

    /** The players who have notifications. */
    val playersWithNotifications: Set<UUID> get() = players.keys.toSet()

    /** [player]'s notifications, oldest first. */
    fun of(player: UUID): List<PhoneNotification> = players[player]?.all.orEmpty()

    /**
     * Creates a notification for [player] with the next id; the oldest of theirs is dropped past the capacity.
     * Arguments are stored as given: [fr.bde_teknantes.lnb.phone.notifications.NotificationLimits] is applied before.
     */
    fun add(
        player: UUID,
        appId: ResourceLocation,
        title: Component,
        text: Component,
        target: String?,
        time: Long,
        delivered: Boolean,
    ): PhoneNotification {
        val notification = PhoneNotification(nextId++, appId, title, text, target, time, delivered)
        players.getOrPut(player) { NotificationList(capacity) }.add(notification)
        return notification
    }

    /** Removes [player]'s notifications from [appId] up to [upToId] included. Returns them. */
    fun clearApp(player: UUID, appId: ResourceLocation, upToId: Long = Long.MAX_VALUE): List<PhoneNotification> =
        change(player) { it.removeApp(appId, upToId) }

    /** Removes [player]'s notifications from [appId] that lead to [target]. Returns them. */
    fun clearTarget(player: UUID, appId: ResourceLocation, target: String): List<PhoneNotification> =
        change(player) { it.removeTarget(appId, target) }

    /** Removes [player]'s notification [id]; another player's id does nothing. Returns it, or null. */
    fun remove(player: UUID, id: Long): PhoneNotification? = change(player) { listOfNotNull(it.remove(id)) }.firstOrNull()

    /** Marks all of [player]'s notifications delivered. Returns whether any changed. */
    fun markDelivered(player: UUID): Boolean {
        val list = players[player] ?: return false
        var changed = false
        list.update {
            if (it.delivered) it else it.copy(delivered = true).also { changed = true }
        }
        return changed
    }

    private fun change(player: UUID, action: (NotificationList) -> List<PhoneNotification>): List<PhoneNotification> {
        val list = players[player] ?: return emptyList()
        val removed = action(list)
        if (list.isEmpty()) players.remove(player)
        return removed
    }

    /** The saved form. [ops] are registry ops ([net.minecraft.core.HolderLookup.Provider.createSerializationContext]). */
    fun save(ops: DynamicOps<Tag>): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(NEXT_ID, nextId)
        val list = ListTag()
        for ((player, notifications) in players) {
            val entry = CompoundTag()
            entry.putUUID(PLAYER, player)
            val saved = ListTag()
            for (notification in notifications.all) {
                NotificationCodecs.CODEC.encodeStart(ops, notification).result().ifPresent(saved::add)
            }
            entry.put(NOTIFICATIONS, saved)
            list.add(entry)
        }
        tag.put(PLAYERS, list)
        return tag
    }

    companion object {
        private const val NEXT_ID = "next_id"
        private const val PLAYERS = "players"
        private const val PLAYER = "player"
        private const val NOTIFICATIONS = "notifications"

        /**
         * Reads what [save] wrote. A notification that can't be read (an unknown component format, say) is skipped and
         * reported to [onError]; the others load.
         */
        fun load(
            tag: CompoundTag,
            ops: DynamicOps<Tag>,
            capacity: Int = NotificationList.CAPACITY,
            onError: (String) -> Unit = {},
        ): NotificationStore {
            val store = NotificationStore(capacity)
            val players = tag.getList(PLAYERS, Tag.TAG_COMPOUND.toInt())
            var highest = 0L
            for (i in 0 until players.size) {
                val entry = players.getCompound(i)
                if (!entry.hasUUID(PLAYER)) {
                    onError("a player entry without a UUID")
                    continue
                }
                val list = NotificationList(capacity)
                val saved = entry.getList(NOTIFICATIONS, Tag.TAG_COMPOUND.toInt())
                for (j in 0 until saved.size) {
                    val parsed = NotificationCodecs.CODEC.parse(ops, saved[j])
                    parsed.error().ifPresent { onError(it.message()) }
                    parsed.result().ifPresent { notification ->
                        list.add(notification)
                        highest = maxOf(highest, notification.id)
                    }
                }
                if (!list.isEmpty()) store.players[entry.getUUID(PLAYER)] = list
            }
            // Never below a stored id, even if the counter was lost: ids must not repeat.
            store.nextId = maxOf(tag.getLong(NEXT_ID), highest + 1, 1L)
            return store
        }
    }
}
