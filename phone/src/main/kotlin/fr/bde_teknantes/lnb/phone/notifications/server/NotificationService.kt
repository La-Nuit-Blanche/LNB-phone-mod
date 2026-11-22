package fr.bde_teknantes.lnb.phone.notifications.server

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * What the service needs from the running server, so that it can be tested without one ([ServerNotifications] gives
 * the real one). Server thread only.
 */
internal interface NotificationHost {
    /** The saved notifications, or null before the server's levels exist. */
    val data: NotificationSavedData?

    fun isOnline(player: UUID): Boolean

    /** Sends [payload] to [player] if they are connected. */
    fun send(player: UUID, payload: CustomPacketPayload)

    /** Every player who has played on this world, connected or not. */
    fun knownPlayers(): Collection<UUID>

    /** How many bytes [notification] takes on the wire, or -1 if it can't be encoded at all. */
    fun encodedSize(notification: PhoneNotification): Int

    /** Milliseconds since the epoch. */
    fun now(): Long
}

/**
 * The server side of notifications, as logic over a [NotificationHost]: changes the saved store and keeps connected
 * clients in step. Server thread only. Arguments come already limited ([fr.bde_teknantes.lnb.phone.notifications.NotificationLimits]).
 *
 * @param millis a monotonic clock in milliseconds, for the rate limiter.
 */
internal class NotificationService(millis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    // Clients clear an app's notifications when the player opens it: a few times a minute in normal play. Anything far
    // beyond is a modified client, and is ignored rather than allowed to keep the server busy.
    private val clientRequests = RateLimiter<UUID>(burst = 10, perSecond = 2.0, clock = millis)

    // Players whose client dropped out of step (a request was refused) and get the full list again at the next
    // resync(): at most once per resync period, so a flood of requests can't be turned into a flood of syncs.
    private val outOfStep = LinkedHashSet<UUID>()

    /**
     * Stores a notification for each of [players] and sends it to those connected. A player who has never joined
     * gets it at their first login (if within [NotificationStore.MAX_AGE_MS]). Returns how many were stored.
     */
    fun send(host: NotificationHost, players: Collection<UUID>, appId: ResourceLocation, title: Component, text: Component, target: String?): Int {
        val data = host.data ?: return unavailable("send a notification")
        val now = host.now()
        // One check for all: the content is the same for every player, only the id and the delivered flag differ.
        val probe = PhoneNotification(Long.MAX_VALUE, appId, title, text, target, now, true)
        if (!fits(host, probe)) {
            LnbPhone.LOGGER.warn("A phone notification from {} is too large to send and was dropped", appId)
            return 0
        }
        val distinct = players.toSet()
        for (player in distinct) {
            val online = host.isOnline(player)
            val notification = data.store.add(player, appId, title, text, target, now, delivered = online)
            if (online) host.send(player, NotificationAddedPayload(notification))
        }
        if (distinct.isNotEmpty()) data.setDirty()
        return distinct.size
    }

    /** [send] to every player who has played on this world, connected or not; not to players who join later. */
    fun sendToEveryone(host: NotificationHost, appId: ResourceLocation, title: Component, text: Component, target: String?): Int =
        send(host, host.knownPlayers(), appId, title, text, target)

    /** Removes [player]'s notifications from [appId] (only those leading to [target], if given), on their client too. */
    fun clear(host: NotificationHost, player: UUID, appId: ResourceLocation, target: String?) {
        val data = host.data ?: run {
            unavailable("clear notifications")
            return
        }
        val removed = if (target == null) data.store.clearApp(player, appId) else data.store.clearTarget(player, appId, target)
        if (removed.isEmpty()) return
        data.setDirty()
        host.send(player, NotificationsClearedPayload(appId, target))
    }

    /** Removes [appId]'s notifications (only those leading to [target], if given) from every player who has some. */
    fun clearEverywhere(host: NotificationHost, appId: ResourceLocation, target: String?) {
        val data = host.data ?: run {
            unavailable("clear notifications")
            return
        }
        val affected = data.store.clearEverywhere(appId, target)
        if (affected.isEmpty()) return
        data.setDirty()
        for (player in affected) host.send(player, NotificationsClearedPayload(appId, target))
    }

    /**
     * [player] logged in: their expired notifications go, and the rest is sent with their delivered flags as they
     * were (so the client can tell what came in while the player was away), then marked delivered.
     */
    fun login(host: NotificationHost, player: UUID) {
        val data = host.data ?: return
        if (data.store.expire(host.now(), player) > 0) data.setDirty()
        sync(host, data, player, login = true)
        if (data.store.markDelivered(player)) data.setDirty()
    }

    fun logout(player: UUID) {
        clientRequests.forget(player)
        outOfStep.remove(player)
    }

    /** The player opened [appId]: removes its notifications up to [upToId], the newest their client knew of. */
    fun clientClearedApp(host: NotificationHost, player: UUID, appId: ResourceLocation, upToId: Long) {
        val data = host.data ?: return
        if (!allowed(player)) return
        if (data.store.clearApp(player, appId, upToId).isNotEmpty()) data.setDirty()
    }

    /** The player saw notification [id] in place: removes it, if it is theirs. */
    fun clientDismissed(host: NotificationHost, player: UUID, id: Long) {
        val data = host.data ?: return
        if (!allowed(player)) return
        if (data.store.remove(player, id) != null) data.setDirty()
    }

    /** Sends the full list again to connected players whose client is out of step. Called every few seconds. */
    fun resync(host: NotificationHost) {
        if (outOfStep.isEmpty()) return
        val data = host.data ?: return
        val players = outOfStep.toList()
        outOfStep.clear()
        for (player in players) if (host.isOnline(player)) sync(host, data, player, login = false)
    }

    private fun allowed(player: UUID): Boolean {
        if (clientRequests.tryAcquire(player)) return true
        // The client already removed what it asked to remove; the server keeps it: put the client back in step.
        outOfStep += player
        return false
    }

    private fun sync(host: NotificationHost, data: NotificationSavedData, player: UUID, login: Boolean) {
        // The sync is one packet and a payload that fails to encode drops the connection: a notification that can't be
        // sent (none should, after the limits and the checks on load) is removed rather than allowed to lock the
        // player out at every login.
        val (sendable, broken) = data.store.of(player).partition { fits(host, it) }
        for (notification in broken) {
            data.store.remove(player, notification.id)
            LnbPhone.LOGGER.warn("Phone notification {} of {} can't be sent and was removed: {}", notification.id, player, notification)
        }
        if (broken.isNotEmpty()) data.setDirty()
        host.send(player, NotificationsSyncPayload(sendable, login))
    }

    private fun fits(host: NotificationHost, notification: PhoneNotification) =
        host.encodedSize(notification) in 0..MAX_ENCODED_BYTES

    private fun unavailable(what: String): Int {
        LnbPhone.LOGGER.warn("The server's levels aren't loaded yet: can't {}", what)
        return 0
    }

    companion object {
        /**
         * The most one notification may take on the wire. The limits keep any notification to a few KB; this bounds the
         * login sync of [fr.bde_teknantes.lnb.phone.notifications.NotificationList.CAPACITY] of them well under a
         * packet's 1 MiB.
         */
        const val MAX_ENCODED_BYTES = 8 * 1024
    }
}
