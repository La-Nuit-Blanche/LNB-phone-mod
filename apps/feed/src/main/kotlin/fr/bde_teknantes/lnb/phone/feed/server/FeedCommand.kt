package fr.bde_teknantes.lnb.phone.feed.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerState
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/feed announcer add|remove <username>` and `/feed announcer list` (`docs/feed-app.md` 7), and in memory mode the
 * development switches `/feed dev offline|disabled|writing <on>`.
 *
 * Who may run `announcer`: a player whose cached state moderates (a site admin or Warden admin), typing it as
 * themselves (never through `execute as`), or the server console (permission level 4, which command blocks and
 * functions don't have). The site checks again: a player's call names them as `issuer`, the console's names nobody.
 * Answers are translatable, so each issuer reads them in their language. The command runs on the server thread and answers when the site does, without waiting for it.
 */
object FeedCommand {
    private const val KEY = "command.${Feed.MOD_ID}"
    private val USERNAME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, devCommands: Boolean) {
        val announcer = Commands.literal("announcer")
            .requires(::mayManageAnnouncers)
            .then(Commands.literal("add").then(username().executes { change(it, true) }))
            .then(Commands.literal("remove").then(username().executes { change(it, false) }))
            .then(Commands.literal("list").executes(::list))
        val root = Commands.literal("feed")
            .requires { mayManageAnnouncers(it) || (devCommands && mayUseDev(it)) }
            .then(announcer)
        if (devCommands) {
            val dev = Commands.literal("dev").requires(::mayUseDev)
            for (switch in listOf("offline", "disabled", "writing")) {
                dev.then(Commands.literal(switch).then(Commands.argument("on", BoolArgumentType.bool()).executes { setSwitch(it, switch) }))
            }
            root.then(dev)
        }
        dispatcher.register(root)
    }

    /** Who may act through `/feed announcer`: a player for themselves, or the console. */
    internal enum class Actor {
        /** A player who typed the command, running as themselves: the site gets them as the issuer. */
        PLAYER,

        /** The server console (or anything else at permission level 4 that isn't a player): no issuer. */
        CONSOLE,

        /** Anything else: refused. */
        NONE,
    }

    /**
     * Who runs the command, from where it came ([originIsPlayer]: the command source that started it, which `execute`
     * never changes), whether that player is the one the server's player list holds for their UUID ([listed]: a mod's
     * fake player made with a moderator's UUID is not), whether it still runs as that player ([runsAsOrigin]:
     * `execute as` changes the entity, not the origin) and the permission level. A player acts only as themselves:
     * `execute as <moderator>` run by an operator, a command block or a function can't borrow a moderator's name.
     * Nothing below level 4 reaches the console's path, whatever it runs as, since `execute` never raises the level.
     */
    internal fun actor(originIsPlayer: Boolean, listed: Boolean, runsAsOrigin: Boolean, level4: Boolean): Actor = when {
        originIsPlayer -> if (listed && runsAsOrigin) Actor.PLAYER else Actor.NONE
        level4 -> Actor.CONSOLE
        else -> Actor.NONE
    }

    private fun actor(source: CommandSourceStack): Actor {
        val origin = source.source
        val listed = origin is ServerPlayer && origin.server.playerList.getPlayer(origin.uuid) === origin
        return actor(origin is ServerPlayer, listed, source.entity === origin, source.hasPermission(4))
    }

    /** The player running the command for themselves, or null for the console. Only after [actor] said either. */
    private fun issuingPlayer(source: CommandSourceStack): ServerPlayer? =
        (source.source as? ServerPlayer)?.takeIf { actor(source) == Actor.PLAYER }

    private fun mayManageAnnouncers(source: CommandSourceStack): Boolean = when (actor(source)) {
        Actor.PLAYER -> FeedServer.current?.rights?.moderates((source.source as ServerPlayer).uuid) == true
        Actor.CONSOLE -> true
        Actor.NONE -> false
    }

    private fun mayUseDev(source: CommandSourceStack) = source.hasPermission(2) && FeedServer.current?.backend is MemoryBackend

    private fun username() = Commands.argument("username", StringArgumentType.word())
        .suggests { context, builder -> SharedSuggestionProvider.suggest(context.source.onlinePlayerNames, builder) }

    private fun change(context: CommandContext<CommandSourceStack>, add: Boolean): Int {
        val source = context.source
        val name = StringArgumentType.getString(context, "username")
        if (!USERNAME.matches(name)) {
            source.sendFailure(Component.translatable("$KEY.announcer.invalid_name", name))
            return 0
        }
        val service = FeedServer.current ?: return unavailable(source)
        val host = service.host
        // The server runs in offline mode: an account's UUID is the offline UUID of its name.
        val target = UUIDUtil.createOfflinePlayerUUID(name)
        // `requires` let this through; checked again here, since the issuer is what the site audits.
        if (actor(source) == Actor.NONE) {
            source.sendFailure(Component.translatable("$KEY.announcer.not_allowed"))
            return 0
        }
        val issuer = issuingPlayer(source)?.let { Issuer(it.uuid, it.gameProfile.name) }
        // The state before the change tells "done" from "already so": the site answers both the same way.
        service.backend.player(target).whenComplete { before, _ ->
            val announcedBefore = (before as? BackendResult.Ok)?.body?.rights?.announce
            service.backend.setAnnouncer(target, add, issuer).whenComplete { result, _ ->
                host.execute { answer(source, name, add, announcedBefore, result) }
            }
        }
        return 1
    }

    private fun answer(source: CommandSourceStack, name: String, add: Boolean, announcedBefore: Boolean?, result: BackendResult<AnnouncerState>?) {
        when (result) {
            is BackendResult.Ok -> {
                val state = result.body
                val key = when {
                    add && state.viaRole -> "via_role"
                    add && announcedBefore == true -> "already"
                    add -> "added"
                    announcedBefore == false -> "already_not"
                    else -> "removed"
                }
                source.sendSuccess({ Component.translatable("$KEY.announcer.$key", state.username) }, true)
            }
            is BackendResult.Refused -> {
                val key = when (result.problem) {
                    "issuerNotAllowed" -> "not_allowed"
                    "notLinked", "isGhost" -> "not_linked"
                    "impliedByRole" -> "implied_by_role"
                    else -> "unavailable"
                }
                source.sendFailure(Component.translatable("$KEY.announcer.$key", name))
            }
            else -> unavailable(source)
        }
    }

    private fun list(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val service = FeedServer.current ?: return unavailable(source)
        service.backend.announcers().whenComplete { result, _ ->
            service.host.execute {
                if (result !is BackendResult.Ok) {
                    unavailable(source)
                } else if (result.body.isEmpty()) {
                    source.sendSuccess({ Component.translatable("$KEY.announcer.list.empty") }, false)
                } else {
                    source.sendSuccess({ Component.translatable("$KEY.announcer.list", result.body.joinToString("; ", transform = ::describe)) }, false)
                }
            }
        }
        return 1
    }

    /** `Kael_Builder`, or `Camille R. (Cam_Roux, Cam_Alt)`: names the site gives for announcers, never an anonymous author. */
    private fun describe(entry: AnnouncerEntry): String {
        val accounts = entry.accounts.joinToString(", ") { it.username }
        return if (entry.realName != null) "${entry.realName} ($accounts)" else accounts
    }

    private fun setSwitch(context: CommandContext<CommandSourceStack>, switch: String): Int {
        val backend = FeedServer.current?.backend as? MemoryBackend ?: return unavailable(context.source)
        val on = BoolArgumentType.getBool(context, "on")
        when (switch) {
            "offline" -> backend.setOffline(on)
            "disabled" -> backend.setDisabled(on)
            "writing" -> backend.setWriting(on)
        }
        val state = Component.translatable(if (on) "options.on" else "options.off")
        context.source.sendSuccess({ Component.translatable("$KEY.dev.set", switch, state) }, true)
        return 1
    }

    private fun unavailable(source: CommandSourceStack): Int {
        source.sendFailure(Component.translatable("$KEY.announcer.unavailable"))
        return 0
    }
}
