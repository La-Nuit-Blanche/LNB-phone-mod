package fr.bde_teknantes.lnb.phone.notifications

import net.minecraft.resources.ResourceLocation

/**
 * One player's notifications, oldest first (by [PhoneNotification.id]), at most [capacity] of them. Plain logic, used
 * by the server's store and by the client's mirror of it, which apply the same rules to the same changes and so stay
 * identical.
 *
 * **Who loses a slot when the list is full.** The apps share the [capacity] slots, counted per mod: by the namespace
 * of the app id, so that a mod's apps, or a mod sending under many paths of its own, count as one. Past the capacity,
 * the oldest notification of the mod that holds the most is dropped (of the mods tied for the most, the one whose
 * notification is the oldest). So one app alone may use every slot, and an app that floods the list only pushes out
 * its own notifications: another mod's are dropped only once it holds as many as the busiest one. With several mods,
 * each keeps at least its fair share of the slots, and what one mod leaves unused stays usable by the others.
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
     * nothing). Past [capacity], one is dropped by the rule above: possibly [notification] itself, if it is the oldest
     * of the busiest mod. Returns what was dropped.
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

    /**
     * Replaces everything with [notifications] (any order), dropping the excess past [capacity] by the rule above, one
     * at a time. Returns what was dropped.
     */
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
        val dropped = ArrayList<PhoneNotification>()
        while (items.size > capacity) dropped += items.removeAt(evictionIndex())
        return dropped
    }

    /** The index of the oldest notification of the mod holding the most, ties going to the oldest notification. */
    private fun evictionIndex(): Int {
        val counts = items.groupingBy { it.appId.namespace }.eachCount()
        val most = counts.values.max()
        // Items are oldest first: the first one from a mod at the top count is the oldest among the tied mods.
        return items.indexOfFirst { counts.getValue(it.appId.namespace) == most }
    }

    private fun indexOf(id: Long): Int = items.binarySearch { it.id.compareTo(id) }

    companion object {
        /** How many notifications a player keeps (the brief's "about 50"). */
        const val CAPACITY = 50
    }
}
