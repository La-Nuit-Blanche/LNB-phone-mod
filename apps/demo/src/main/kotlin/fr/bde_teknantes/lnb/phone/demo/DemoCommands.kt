package fr.bde_teknantes.lnb.phone.demo

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.phone.api.PhoneNotifications
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * `/phonedemo`, to try notifications by hand or from a test (operators only):
 *
 * - `/phonedemo notify <players>`: a test notification from the demo app, opening its first screen;
 * - `/phonedemo notify <players> <depth>`: the same, leading to the demo screen at that depth (target `depth:<n>`);
 * - `/phonedemo notify <players> <depth> <text>`: with a text of your own, for long or unusual texts;
 * - `/phonedemo clear <players>`: removes the demo app's notifications.
 *
 * Players are names or player selectors (`@a`, `@p`, `@r`; not `@s`, which may be any entity); a known player who is
 * offline gets the notification at their next login.
 */
object DemoCommands {
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("phonedemo")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("notify").then(
                        Commands.argument("players", GameProfileArgument.gameProfile())
                            .executes { notify(it, null, null) }
                            .then(
                                Commands.argument("depth", IntegerArgumentType.integer(1, MAX_DEPTH))
                                    .executes { notify(it, IntegerArgumentType.getInteger(it, "depth"), null) }
                                    .then(
                                        Commands.argument("text", StringArgumentType.greedyString())
                                            .executes {
                                                notify(it, IntegerArgumentType.getInteger(it, "depth"), StringArgumentType.getString(it, "text"))
                                            },
                                    ),
                            ),
                    ),
                )
                .then(
                    Commands.literal("clear").then(
                        Commands.argument("players", GameProfileArgument.gameProfile()).executes(::clear),
                    ),
                ),
        )
    }

    private var sent = 0

    private fun notify(context: CommandContext<CommandSourceStack>, depth: Int?, text: String?): Int {
        val players = GameProfileArgument.getGameProfiles(context, "players")
        val number = ++sent
        val title = Component.translatable("notif.lnbphone_demo.test.title")
        val body = if (text != null) {
            Component.literal(text)
        } else {
            Component.translatable("notif.lnbphone_demo.test.text", context.source.textName, number)
        }
        for (profile in players) PhoneNotifications.send(profile.id, DemoApp.HELLO_ID, title, body, depth?.let { "depth:$it" })
        context.source.sendSuccess({ Component.translatable("commands.phonedemo.notify.done", players.size) }, true)
        return players.size
    }

    private fun clear(context: CommandContext<CommandSourceStack>): Int {
        val players = GameProfileArgument.getGameProfiles(context, "players")
        for (profile in players) PhoneNotifications.clear(profile.id, DemoApp.HELLO_ID)
        context.source.sendSuccess({ Component.translatable("commands.phonedemo.clear.done", players.size) }, true)
        return players.size
    }

    /** The deepest demo screen a notification can lead to. */
    const val MAX_DEPTH = 20
}
