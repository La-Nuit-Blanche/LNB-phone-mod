package fr.bde_teknantes.lnb.phone.api

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.notifications.NotificationLimits
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationService
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import fr.bde_teknantes.lnb.phone.notifications.server.ServerNotifications
import fr.bde_teknantes.lnb.phone.notifications.server.ServerThread
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

/**
 * Tells players that something happened in your app, from your mod's server side.
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
 * it is closed, and a chime; opening the notification opens your app at its target. A player who isn't connected gets
 * it when they next log in, as a badge. Each player keeps the newest [MAX_PER_PLAYER] notifications, saved with the
 * world, for at most [MAX_AGE_DAYS] days; they go away when the player opens the app (by any route), or when you
 * clear them ([clearApp], [clearTarget] and their forms for everyone).
 *
 * **Text.** Use translatable components for your wording (keys in your `en_us.json` and `fr_fr.json`): they resolve
 * on the player's client, in the player's language, even for a notification sent while they were away. Player content
 * (a name, the start of a post) goes in as **arguments**, never as the translation key: a key resolves to whatever
 * language entry has that name, and shows raw when there is none, so a player could make their post read as any text
 * of the game. The title is drawn on one line and the text on two, in a font without emoji or non-Latin scripts.
 * Styles (colours, bold, fonts, click and hover events) are not kept. Titles keep at most [MAX_TITLE_LENGTH]
 * characters of plain text and texts [MAX_TEXT_LENGTH] (all parts and arguments together), with at most 16
 * arguments per translation: longer ones are cut, so you can pass a post's whole text.
 *
 * **Overloads.** Each `send` comes with and without a `target`, rather than with a default value: a default compiles
 * into your mod as a call to a generated method, which a later version of the phone adding a parameter would remove.
 *
 * **Threads.** Every function here may be called from any thread: the server thread, a network or HTTP callback, a
 * scheduler. Arguments are checked at once, in your thread (so a bad target throws where you made it); the change is
 * then made on the server thread, at once if you are on it, otherwise shortly after the call returns. Calls made one
 * after the other from one thread are applied in that order. The call does nothing, with a warning in the log, when
 * no server is running, when it is stopping, or before its levels are loaded (from `ServerAboutToStartEvent`, say).
 * One gap remains, of a few milliseconds: a call from another thread that comes just as the server halts may be
 * queued after the server's last task and lost without a warning.
 *
 * This object is common code: call it on the logical server only (a dedicated server, or the integrated server of a
 * single-player game). Notifications are sent to players by the server; a client can't send one.
 */
object PhoneNotifications {
    /** How many notifications a player keeps; past this, the oldest is dropped. */
    const val MAX_PER_PLAYER = NotificationList.CAPACITY

    /** How long a notification is kept, in days, if the player doesn't open its app. */
    const val MAX_AGE_DAYS = NotificationStore.MAX_AGE_DAYS

    /** Characters of plain text kept in a title. */
    const val MAX_TITLE_LENGTH = NotificationLimits.MAX_TITLE_LENGTH

    /** Characters of plain text kept in a text. */
    const val MAX_TEXT_LENGTH = NotificationLimits.MAX_TEXT_LENGTH

    /**
     * Notifies [player], connected or not, by UUID: what to use for someone who may be offline (the author of a post
     * someone just replied to). A UUID that has never joined gets it at their first login, if within [MAX_AGE_DAYS].
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
    fun send(player: UUID, appId: ResourceLocation, title: Component, text: Component, target: String?) =
        send(listOf(player), appId, title, text, target)

    /** Notifies [player] by UUID, leading to your app's first screen. See the [send] by UUID with a target. */
    @JvmStatic
    fun send(player: UUID, appId: ResourceLocation, title: Component, text: Component) = send(player, appId, title, text, null)

    /** Notifies a connected [player], leading to your app's first screen. See the [send] by UUID for the parameters. */
    @JvmStatic
    fun send(player: ServerPlayer, appId: ResourceLocation, title: Component, text: Component) = send(player, appId, title, text, null)

    /** Notifies a connected [player]. See the [send] by UUID for the parameters. */
    @JvmStatic
    fun send(player: ServerPlayer, appId: ResourceLocation, title: Component, text: Component, target: String?) {
        // By UUID on the server thread: the player may have left by then, and still gets it at their next login.
        val request = Request(appId, title, text, target)
        val players = listOf(player.uuid)
        onServerThread(player.server) { server -> ServerNotifications.service.send(server, players, request) }
    }

    /**
     * Notifies each of [players], connected or not, in one call: for an event that concerns many (a reply to a thread
     * that several follow). The collection is read during the call. See the [send] by UUID for the other parameters.
     */
    @JvmStatic
    fun send(players: Collection<UUID>, appId: ResourceLocation, title: Component, text: Component, target: String?) {
        val request = Request(appId, title, text, target)
        val copy = players.toList()
        onServerThread(currentServer("send a notification") ?: return) { server -> ServerNotifications.service.send(server, copy, request) }
    }

    /** Notifies each of [players], leading to your app's first screen. See the [send] by UUID for the parameters. */
    @JvmStatic
    fun send(players: Collection<UUID>, appId: ResourceLocation, title: Component, text: Component) = send(players, appId, title, text, null)

    /**
     * Notifies every player who has played on this world, connected or not (a new announcement): those with a save in
     * the world's `playerdata` folder when the call runs, and those connected. A player who joins for the first time
     * afterwards doesn't get it. See the [send] by UUID for the parameters.
     */
    @JvmStatic
    fun sendToEveryone(appId: ResourceLocation, title: Component, text: Component, target: String?) {
        val request = Request(appId, title, text, target)
        onServerThread(currentServer("send a notification") ?: return) { server ->
            val host = ServerNotifications.host(server)
            ServerNotifications.service.sendToEveryone(host, request.appId, request.title, request.text, request.target)
        }
    }

    /** Notifies every player who has played on this world, leading to your app's first screen. See the other [sendToEveryone]. */
    @JvmStatic
    fun sendToEveryone(appId: ResourceLocation, title: Component, text: Component) = sendToEveryone(appId, title, text, null)

    /** Removes all of [player]'s notifications from [appId], connected or not: the badge goes and no banner comes back. */
    @JvmStatic
    fun clearApp(player: UUID, appId: ResourceLocation) {
        NotificationLimits.checkAppId(appId)
        onServerThread(currentServer("clear notifications") ?: return) { server -> clear(server, player, appId, null) }
    }

    /** Removes all of a connected [player]'s notifications from [appId]. */
    @JvmStatic
    fun clearApp(player: ServerPlayer, appId: ResourceLocation) {
        NotificationLimits.checkAppId(appId)
        val uuid = player.uuid
        onServerThread(player.server) { server -> clear(server, uuid, appId, null) }
    }

    /**
     * Removes [player]'s notifications from [appId] that lead to [target] (the thread of a post that was deleted, say),
     * connected or not.
     *
     * @throws IllegalArgumentException if [target] is empty (to clear the whole app, call [clearApp]) or longer than
     * [AppTarget.MAX_LENGTH].
     */
    @JvmStatic
    fun clearTarget(player: UUID, appId: ResourceLocation, target: String) {
        NotificationLimits.checkAppId(appId)
        NotificationLimits.checkTarget(target)
        onServerThread(currentServer("clear notifications") ?: return) { server -> clear(server, player, appId, target) }
    }

    /** Removes a connected [player]'s notifications from [appId] that lead to [target]. See the other [clearTarget]. */
    @JvmStatic
    fun clearTarget(player: ServerPlayer, appId: ResourceLocation, target: String) {
        NotificationLimits.checkAppId(appId)
        NotificationLimits.checkTarget(target)
        val uuid = player.uuid
        onServerThread(player.server) { server -> clear(server, uuid, appId, target) }
    }

    /**
     * Removes the notifications from [appId] that lead to [target] from every player who has one, connected or not:
     * when what they point to is gone for everyone (a deleted announcement). Your app needn't know who got them.
     *
     * @throws IllegalArgumentException as [clearTarget] does.
     */
    @JvmStatic
    fun clearTargetForEveryone(appId: ResourceLocation, target: String) {
        NotificationLimits.checkAppId(appId)
        NotificationLimits.checkTarget(target)
        onServerThread(currentServer("clear notifications") ?: return) { server ->
            ServerNotifications.service.clearEverywhere(ServerNotifications.host(server), appId, target)
        }
    }

    /** Removes every notification from [appId], of every player, connected or not. */
    @JvmStatic
    fun clearAppForEveryone(appId: ResourceLocation) {
        NotificationLimits.checkAppId(appId)
        onServerThread(currentServer("clear notifications") ?: return) { server ->
            ServerNotifications.service.clearEverywhere(ServerNotifications.host(server), appId, null)
        }
    }

    /** A notification checked and limited in the caller's thread, ready for the server thread. */
    private class Request(val appId: ResourceLocation, title: Component, text: Component, target: String?) {
        val target: String? = NotificationLimits.checkAddress(appId, target)

        init {
            NotificationLimits.checkSender(appId) { ModList.get().isLoaded(it) }
        }

        val title: Component = NotificationLimits.title(title)
        val text: Component = NotificationLimits.text(text)
    }

    private fun NotificationService.send(server: MinecraftServer, players: List<UUID>, request: Request) {
        send(ServerNotifications.host(server), players, request.appId, request.title, request.text, request.target)
    }

    private fun clear(server: MinecraftServer, player: UUID, appId: ResourceLocation, target: String?) =
        ServerNotifications.service.clear(ServerNotifications.host(server), player, appId, target)

    private fun currentServer(what: String): MinecraftServer? = ServerLifecycleHooks.getCurrentServer().also {
        if (it == null) LnbPhone.LOGGER.warn("No server is running: can't {}", what)
    }

    private fun onServerThread(server: MinecraftServer, action: (MinecraftServer) -> Unit) =
        ServerThread.run(server, server::isRunning) { action(server) }
}
