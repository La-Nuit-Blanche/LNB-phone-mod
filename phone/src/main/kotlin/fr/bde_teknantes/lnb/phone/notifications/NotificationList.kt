package fr.bde_teknantes.lnb.phone.notifications

import net.minecraft.resources.ResourceLocation

/**
 * One player's notifications, oldest first (by [PhoneNotification.id]), at most [capacity] of them: adding one more
 * drops the oldest. Plain logic, used by the server's store and by the client's mirror of it, which apply the same
 * rules to the same changes and so stay identical.
 *
 * Not thread-safe: each side touches it from one thread (the server thread, the client thread).
 */
class NotificationList(val capacity: Int = CAPACITY) {
    init {
        require(capacity > 0) { "Capacity must be positive" }
    }

    // Sorted by id; ids are unique.
    private val items = ArrayList<PhoneNotification>()

    /** Every notification, oldest first. A copy. */
    val all: List<PhoneNotification> get() = items.toList()

    val size: Int get() = items.size

    fun isEmpty() = items.isEmpty()

    operator fun get(id: Long): PhoneNotification? = indexOf(id).takeIf { it >= 0 }?.let(items::get)

    /** The newest notification, or null when there is none. */
    val newest: PhoneNotification? get() = items.lastOrNull()

    /** How many notifications [appId] has: its badge. */
    fun count(appId: ResourceLocation): Int = items.count { it.appId == appId }

    /** [appId]'s notifications, oldest first. */
    fun of(appId: ResourceLocation): List<PhoneNotification> = items.filter { it.appId == appId }

    /** The id of [appId]'s newest notification, or null when it has none. */
    fun newestId(appId: ResourceLocation): Long? = items.lastOrNull { it.appId == appId }?.id

    /**
     * Adds [notification], or replaces the one with the same id (so receiving the same notification twice changes
     * nothing). Past [capacity], the oldest are dropped, possibly [notification] itself if it is older than all the
     * others. Returns what was dropped.
     */
    fun add(notification: PhoneNotification): List<PhoneNotification> {
        val index = indexOf(notification.id)
        if (index >= 0) {
            items[index] = notification
            return emptyList()
        }
        items.add(-(index + 1), notification)
        return trim()
    }

    /** Replaces everything with [notifications] (any order), keeping the newest [capacity]. Returns what was dropped. */
    fun replaceAll(notifications: Collection<PhoneNotification>): List<PhoneNotification> {
        items.clear()
        notifications.distinctBy { it.id }.sortedBy { it.id }.let(items::addAll)
        return trim()
    }

    /** Removes [appId]'s notifications whose id is at most [upToId] (all of them by default). Returns them. */
    fun removeApp(appId: ResourceLocation, upToId: Long = Long.MAX_VALUE): List<PhoneNotification> =
        removeIf { it.appId == appId && it.id <= upToId }

    /** Removes [appId]'s notifications that lead to [target]. Returns them. */
    fun removeTarget(appId: ResourceLocation, target: String): List<PhoneNotification> =
        removeIf { it.appId == appId && it.target == target }

    /** Removes the notification [id]. Returns it, or null if there was none. */
    fun remove(id: Long): PhoneNotification? {
        val index = indexOf(id)
        return if (index >= 0) items.removeAt(index) else null
    }

    /** Applies [change] to every notification (to mark them delivered, say). Ids must not change. */
    fun update(change: (PhoneNotification) -> PhoneNotification) {
        for (i in items.indices) {
            val updated = change(items[i])
            require(updated.id == items[i].id) { "A notification's id can't change" }
            items[i] = updated
        }
    }

    fun clear() = items.clear()

    private fun removeIf(predicate: (PhoneNotification) -> Boolean): List<PhoneNotification> {
        val removed = items.filter(predicate)
        if (removed.isNotEmpty()) items.removeAll(predicate)
        return removed
    }

    private fun trim(): List<PhoneNotification> {
        if (items.size <= capacity) return emptyList()
        val excess = items.subList(0, items.size - capacity)
        val dropped = excess.toList()
        excess.clear()
        return dropped
    }

    private fun indexOf(id: Long): Int = items.binarySearch { it.id.compareTo(id) }

    companion object {
        /** How many notifications a player keeps (the brief's "about 50"). */
        const val CAPACITY = 50
    }
}
