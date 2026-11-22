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
import kotlin.math.roundToInt

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
internal class NotificationService(private val millis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    // Clients clear an app's notifications when the player opens it: a few times a minute in normal play. Anything far
    // beyond is a modified client, and is ignored rather than allowed to keep the server busy.
    private val clientRequests = RateLimiter<UUID>(burst = 10, perSecond = 2.0, clock = millis)

    // A backstop against an app that loops (a bug, or a modified client the app doesn't limit): per player and per mod,
    // a burst of 20, then one every 3 seconds. Far above what a real app sends one player, and it keeps such a loop
    // from sending the player a packet, a banner and a chime per call. Other mods' notifications are already safe from
    // it ([fr.bde_teknantes.lnb.phone.notifications.NotificationList]).
    private val appSends = RateLimiter<Pair<UUID, String>>(burst = APP_BURST, perSecond = APP_PER_SECOND, clock = millis)
    private val lastFloodWarning = HashMap<String, Long>()

    // Players whose client dropped out of step (a request was refused) and get the full list again at the next
    // resync(): at most once per resync period, so a flood of requests can't be turned into a flood of syncs.
    private val outOfStep = LinkedHashSet<UUID>()

    /**
     * Stores a notification for each of [players] and sends it to those connected. A player who has never joined
     * gets it at their first login (if within [NotificationStore.MAX_AGE_MS]). A player for whom [appId]'s mod is over
     * its rate ([APP_BURST], then [APP_PER_SECOND]) is skipped. Returns how many were stored.
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
        var stored = 0
        var refused = 0
        for (player in players.toSet()) {
            if (!appSends.tryAcquire(player to appId.namespace)) {
                refused++
                continue
            }
            val online = host.isOnline(player)
            val notification = data.store.add(player, appId, title, text, target, now, delivered = online)
            if (online) host.send(player, NotificationAddedPayload(notification))
            stored++
        }
        if (stored > 0) data.setDirty()
        if (refused > 0) warnFlood(appId, refused)
        return stored
    }

    private fun warnFlood(appId: ResourceLocation, refused: Int) {
        val now = millis()
        val last = lastFloodWarning[appId.namespace]
        if (last != null && now - last < FLOOD_WARNING_MS) return
        lastFloodWarning[appId.namespace] = now
        LnbPhone.LOGGER.warn(
            "Phone notifications from {} dropped for {} player(s): more than {} at once, then one every {} s, per player",
            appId, refused, APP_BURST, (1 / APP_PER_SECOND).roundToInt(),
        )
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

    /**
     * Sends the full list again to connected players whose client is out of step, and forgets the send allowances that
     * have filled up again. Called every few seconds.
     */
    fun resync(host: NotificationHost) {
        appSends.forgetIdle()
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

        /** Notifications one mod may send one player at once, before [APP_PER_SECOND] applies. */
        const val APP_BURST = 20

        /** Then how many a second: one every 3 seconds. */
        const val APP_PER_SECOND = 1.0 / 3

        private const val FLOOD_WARNING_MS = 60_000L
    }
}
