package fr.bde_teknantes.lnb.phone.api

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.notifications.NotificationLimits
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.server.ServerNotifications
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

/**
 * Tells a player that something happened in your app, from your mod's server side.
 *
 * ```kotlin
 * PhoneNotifications.send(
 *     author.uuid,
 *     ResourceLocation.fromNamespaceAndPath("lnbfeed", "feed"),
 *     title = Component.translatable("notif.lnbfeed.reply"),
 *     text = Component.translatable("notif.lnbfeed.text", reply.authorName, reply.text),
 *     target = "thread:${post.id}",
 * )
 * ```
 *
 * The player sees a badge on the app's icon, a banner if the phone is open, the phone peeking up from the corner if
 * it is closed, and a chime; opening the notification opens your app at [target]. A player who isn't connected gets
 * it when they next log in, as a badge. Each player keeps the newest [MAX_PER_PLAYER] notifications, saved with the
 * world; they go away when the player opens the app (by any route), or when you [clear] them.
 *
 * **Text.** Use translatable components for your wording (keys in your `en_us.json` and `fr_fr.json`): they resolve
 * on the player's client, in the player's language, even for a notification sent while they were away. Player content
 * (a name, the start of a post) goes in as arguments. The title is drawn on one line and the text on two, in a font
 * without emoji or non-Latin scripts, and styles (colours, bold) are ignored. Titles keep at most [MAX_TITLE_LENGTH]
 * characters of plain text and texts [MAX_TEXT_LENGTH] (all parts and arguments together): longer ones are cut, so you
 * can pass a post's whole text. Click and hover events are dropped.
 *
 * **Threads.** Every function here may be called from any thread: the server thread, a network or HTTP callback, a
 * scheduler. Arguments are checked at once, in your thread (so a bad target throws where you made it); the
 * notification is then stored and sent on the server thread, shortly after the call returns. Calls made one after the
 * other from one thread are applied in that order. When no server is running (it stopped, or is stopping), the call
 * does nothing and logs a warning.
 *
 * This object is common code: call it on the logical server only (a dedicated server, or the integrated server of a
 * single-player game). Notifications are sent to players by the server; a client can't send one.
 */
object PhoneNotifications {
    /** How many notifications a player keeps; past this, the oldest is dropped. */
    const val MAX_PER_PLAYER = NotificationList.CAPACITY

    /** Characters of plain text kept in a title. */
    const val MAX_TITLE_LENGTH = NotificationLimits.MAX_TITLE_LENGTH

    /** Characters of plain text kept in a text. */
    const val MAX_TEXT_LENGTH = NotificationLimits.MAX_TEXT_LENGTH

    /**
     * Notifies [player], connected or not, by UUID: what to use for someone who may be offline (the author of a post
     * someone just replied to).
     *
     * @param appId your app's id, as registered with the phone on the client. A client that doesn't have this app
     * keeps the notification but shows nothing for it.
     * @param title what happened, in a few words (`New reply`).
     * @param text the detail, usually starting with who did it (`Mika: I saw it near the mill!`).
     * @param target where in your app the notification leads ([AppTarget]), or null for your app's first screen. An
     * empty string counts as null.
     * @throws IllegalArgumentException if [target] is longer than [AppTarget.MAX_LENGTH] characters, or [appId]
     * longer than 128.
     */
    @JvmStatic
    @JvmOverloads
    fun send(player: UUID, appId: ResourceLocation, title: Component, text: Component, target: String? = null) {
        val checkedTarget = NotificationLimits.checkAddress(appId, target)
        val safeTitle = NotificationLimits.title(title)
        val safeText = NotificationLimits.text(text)
        onServerThread(currentServer("send a notification") ?: return) { server ->
            ServerNotifications.send(server, player, appId, safeTitle, safeText, checkedTarget)
        }
    }

    /** Notifies a connected [player]. See the other [send] for the parameters. */
    @JvmStatic
    @JvmOverloads
    fun send(player: ServerPlayer, appId: ResourceLocation, title: Component, text: Component, target: String? = null) {
        val checkedTarget = NotificationLimits.checkAddress(appId, target)
        val safeTitle = NotificationLimits.title(title)
        val safeText = NotificationLimits.text(text)
        // By UUID on the server thread: the player may have left by then, and still gets it at their next login.
        val uuid = player.uuid
        onServerThread(player.server) { server -> ServerNotifications.send(server, uuid, appId, safeTitle, safeText, checkedTarget) }
    }

    /**
     * Removes [player]'s notifications from [appId], connected or not: the badge drops and the banner won't come back.
     * With a [target], only the notifications leading there (the thread of a post that was deleted, say).
     */
    @JvmStatic
    @JvmOverloads
    fun clear(player: UUID, appId: ResourceLocation, target: String? = null) {
        val checkedTarget = NotificationLimits.checkAddress(appId, target)
        onServerThread(currentServer("clear notifications") ?: return) { server ->
            ServerNotifications.clear(server, player, appId, checkedTarget)
        }
    }

    /** Removes a connected [player]'s notifications from [appId]. See the other [clear]. */
    @JvmStatic
    @JvmOverloads
    fun clear(player: ServerPlayer, appId: ResourceLocation, target: String? = null) {
        val checkedTarget = NotificationLimits.checkAddress(appId, target)
        val uuid = player.uuid
        onServerThread(player.server) { server -> ServerNotifications.clear(server, uuid, appId, checkedTarget) }
    }

    private fun currentServer(what: String): MinecraftServer? = ServerLifecycleHooks.getCurrentServer().also {
        if (it == null) LnbPhone.LOGGER.warn("No server is running: can't {}", what)
    }

    private fun onServerThread(server: MinecraftServer, action: (MinecraftServer) -> Unit) {
        if (server.isSameThread) return action(server)
        // A stopped server runs execute()'s task in the caller's thread instead of queueing it: never touch the store
        // from there.
        if (!server.isRunning) return dropped()
        server.execute { if (server.isSameThread) action(server) else dropped() }
    }

    private fun dropped() = LnbPhone.LOGGER.warn("The server is stopping: phone notification change dropped")
}
