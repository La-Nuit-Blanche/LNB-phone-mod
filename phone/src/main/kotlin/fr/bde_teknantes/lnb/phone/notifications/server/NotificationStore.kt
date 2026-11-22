package fr.bde_teknantes.lnb.phone.notifications.server

import com.mojang.serialization.DynamicOps
import fr.bde_teknantes.lnb.phone.notifications.NotificationCodecs
import fr.bde_teknantes.lnb.phone.notifications.NotificationLimits
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
 * Notifications older than [MAX_AGE_MS] are dropped by [expire], so that players who stopped coming don't keep a full
 * list for ever and the saved file doesn't grow with every player ever notified. Each player's saved form is kept
 * between saves and redone only when their notifications change, so an autosave re-encodes only what moved.
 *
 * Not thread-safe: the server thread alone uses it ([ServerNotifications] makes sure of that).
 */
class NotificationStore(private val capacity: Int = NotificationList.CAPACITY) {
    private val players = HashMap<UUID, NotificationList>()

    // Each player's saved notifications, from the last save, until their list changes.
    private val encoded = HashMap<UUID, ListTag>()

    /** The id the next notification gets. */
    var nextId = 1L
        private set

    /** The players who have notifications. */
    val playersWithNotifications: Set<UUID> get() = players.keys.toSet()

    /** [player]'s notifications, oldest first. */
    fun of(player: UUID): List<PhoneNotification> = players[player]?.all.orEmpty()

    /**
     * Creates a notification for [player] with the next id; the oldest of theirs is dropped past the capacity.
     * Arguments are stored as given: [NotificationLimits] is applied before.
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
        encoded.remove(player)
        return notification
    }

    /** Removes [player]'s notifications from [appId] up to [upToId] included. Returns them. */
    fun clearApp(player: UUID, appId: ResourceLocation, upToId: Long = Long.MAX_VALUE): List<PhoneNotification> =
        change(player) { it.removeApp(appId, upToId) }

    /** Removes [player]'s notifications from [appId] that lead to [target]. Returns them. */
    fun clearTarget(player: UUID, appId: ResourceLocation, target: String): List<PhoneNotification> =
        change(player) { it.removeTarget(appId, target) }

    /**
     * Removes [appId]'s notifications from every player, or only those leading to [target] when given. Returns the
     * players who lost some.
     */
    fun clearEverywhere(appId: ResourceLocation, target: String?): Set<UUID> =
        players.keys.toList().filterTo(HashSet()) { player ->
            (if (target == null) clearApp(player, appId) else clearTarget(player, appId, target)).isNotEmpty()
        }

    /** Removes [player]'s notification [id]; another player's id does nothing. Returns it, or null. */
    fun remove(player: UUID, id: Long): PhoneNotification? = change(player) { listOfNotNull(it.remove(id)) }.firstOrNull()

    /** Marks all of [player]'s notifications delivered. Returns whether any changed. */
    fun markDelivered(player: UUID): Boolean {
        val list = players[player] ?: return false
        var changed = false
        list.update {
            if (it.delivered) it else it.copy(delivered = true).also { changed = true }
        }
        if (changed) encoded.remove(player)
        return changed
    }

    /**
     * Drops the notifications sent more than [maxAgeMs] before [now] (milliseconds since the epoch), of [player] only
     * or of everyone. A notification from the future (a clock set back) is kept. Returns how many were dropped.
     */
    fun expire(now: Long, player: UUID? = null, maxAgeMs: Long = MAX_AGE_MS): Int {
        val who = if (player != null) listOf(player) else players.keys.toList()
        return who.sumOf { change(it) { list -> list.removeIf { n -> n.time < now - maxAgeMs } }.size }
    }

    private fun change(player: UUID, action: (NotificationList) -> List<PhoneNotification>): List<PhoneNotification> {
        val list = players[player] ?: return emptyList()
        val removed = action(list)
        if (removed.isNotEmpty()) encoded.remove(player)
        if (list.isEmpty()) players.remove(player)
        return removed
    }

    private fun NotificationList.removeIf(predicate: (PhoneNotification) -> Boolean): List<PhoneNotification> =
        all.filter(predicate).onEach { remove(it.id) }

    /**
     * The saved form. [ops] are registry ops ([net.minecraft.core.HolderLookup.Provider.createSerializationContext]).
     * A notification that can't be written is left out and reported to [onError]. The tag is the caller's: changing it
     * doesn't touch what the store keeps for its next save.
     */
    fun save(ops: DynamicOps<Tag>, onError: (String) -> Unit = {}): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(NEXT_ID, nextId)
        val list = ListTag()
        for ((player, notifications) in players) {
            val saved = encoded.getOrPut(player) {
                ListTag().also { out ->
                    for (notification in notifications.all) {
                        val result = NotificationCodecs.CODEC.encodeStart(ops, notification)
                        result.error().ifPresent { onError("notification ${notification.id} of $player: ${it.message()}") }
                        result.result().ifPresent(out::add)
                    }
                }
            }
            val entry = CompoundTag()
            entry.putUUID(PLAYER, player)
            entry.put(NOTIFICATIONS, saved.copy())
            list.add(entry)
        }
        tag.put(PLAYERS, list)
        return tag
    }

    companion object {
        /** How long a notification is kept, in days. */
        const val MAX_AGE_DAYS = 30

        /** [MAX_AGE_DAYS] in milliseconds. */
        const val MAX_AGE_MS = MAX_AGE_DAYS * 24L * 60 * 60 * 1000

        private const val NEXT_ID = "next_id"
        private const val PLAYERS = "players"
        private const val PLAYER = "player"
        private const val NOTIFICATIONS = "notifications"

        /**
         * Reads what [save] wrote, checking it again as [NotificationLimits] would at creation, since the file may come
         * from an older build or a hand edit and a notification that can't be sent would fail the player's login sync.
         *
         * - A notification that can't be read, or whose app id is too long for the network, is dropped and reported to
         *   [onError]; the others load. Titles and texts are cut to their limits; an empty target counts as none.
         * - Two entries for one player are merged.
         * - Notifications older than [MAX_AGE_MS] at [now] are dropped.
         */
        fun load(
            tag: CompoundTag,
            ops: DynamicOps<Tag>,
            capacity: Int = NotificationList.CAPACITY,
            now: Long = System.currentTimeMillis(),
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
                val player = entry.getUUID(PLAYER)
                val list = store.players[player] ?: NotificationList(capacity)
                val saved = entry.getList(NOTIFICATIONS, Tag.TAG_COMPOUND.toInt())
                for (j in 0 until saved.size) {
                    val parsed = NotificationCodecs.CODEC.parse(ops, saved[j])
                    parsed.error().ifPresent { onError(it.message()) }
                    val notification = parsed.result().orElse(null) ?: continue
                    // Its id counts even when it is dropped below: ids must not repeat.
                    highest = maxOf(highest, notification.id)
                    if (notification.appId.toString().length > NotificationLimits.MAX_APP_ID_LENGTH) {
                        onError("notification ${notification.id}: app id ${notification.appId} is too long")
                        continue
                    }
                    list.add(
                        notification.copy(
                            title = NotificationLimits.title(notification.title),
                            text = NotificationLimits.text(notification.text),
                            target = notification.target?.takeIf { it.isNotEmpty() },
                        ),
                    )
                }
                if (!list.isEmpty()) store.players[player] = list
            }
            // Never below a stored id, even if the counter was lost: ids must not repeat.
            store.nextId = maxOf(tag.getLong(NEXT_ID), highest + 1, 1L)
            store.expire(now)
            return store
        }
    }
}
