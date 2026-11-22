package fr.bde_teknantes.lnb.phone.demo.ping

import fr.bde_teknantes.lnb.phone.api.PhoneNotifications
import fr.bde_teknantes.lnb.phone.api.TextLimits
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Ping's server side: checks what a client sent, passes the ping on and answers the sender. Server thread only.
 *
 * The server decides, never the client: a modified client can send anything, as often as it likes. So the text is
 * checked again, each player may send one ping a second, and only to a player who is connected.
 */
object PingServer {
    /** The shortest time between two pings of one player, in milliseconds. */
    const val COOLDOWN_MS = 1000L

    // When each connected player last sent a ping, by [Util.getMillis].
    private val lastPing = HashMap<UUID, Long>()

    fun handle(payload: SendPingPayload, context: IPayloadContext) {
        val sender = context.player() as? ServerPlayer ?: return
        val text = payload.text.trim()
        // The client never sends that: no answer.
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH)) return
        val now = Util.getMillis()
        val last = lastPing[sender.uuid]
        if (last != null && now - last < COOLDOWN_MS) return answer(sender, payload, PingOutcome.TOO_FAST)
        // The text lives in the recipient's session (PingSession), so a recipient who isn't here could never read it.
        val recipient = sender.server.playerList.getPlayer(payload.to) ?: return answer(sender, payload, PingOutcome.OFFLINE)
        lastPing[sender.uuid] = now

        // The content, for the conversation on the recipient's phone.
        PacketDistributor.sendToPlayer(recipient, PingReceivedPayload(sender.uuid, text))
        // The notification points to it: a badge, a banner or the phone peeking, and the way to the conversation.
        PhoneNotifications.send(
            recipient,
            Ping.APP_ID,
            title = Component.translatable("notif.lnbphone_demo.ping.title"),
            // The player's text goes in as an argument, never as the key: see PhoneNotifications.
            text = Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text),
            target = PingTarget(sender.uuid, sender.gameProfile.name).toString(),
        )
        answer(sender, payload, PingOutcome.SENT)
    }

    private fun answer(sender: ServerPlayer, payload: SendPingPayload, outcome: PingOutcome) {
        PacketDistributor.sendToPlayer(sender, PingAnswerPayload(payload.to, payload.text.trim(), outcome))
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity.uuid
        lastPing.remove(player)
        // The texts of the player's pings left with their session: notifications would now point to nothing.
        PhoneNotifications.clearApp(player, Ping.APP_ID)
    }
}
