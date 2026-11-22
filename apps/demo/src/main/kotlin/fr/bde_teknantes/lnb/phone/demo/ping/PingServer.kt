package fr.bde_teknantes.lnb.phone.demo.ping

import fr.bde_teknantes.lnb.phone.api.PhoneNotifications
import fr.bde_teknantes.lnb.phone.api.TextLimits
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Ping's server side: checks what a client sent and passes the ping on. Server thread only.
 *
 * A real app would also limit how often a player may send; Ping, which is never shipped, doesn't.
 */
object PingServer {
    fun handle(payload: SendPingPayload, context: IPayloadContext) {
        val sender = context.player() as? ServerPlayer ?: return
        // The client checks the text too, but a server never trusts what a client sends.
        val text = payload.text.trim()
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH)) return
        // Only players who have been on this server: a notification to anyone else would wait 30 days for nothing.
        val server = sender.server
        if (server.profileCache?.get(payload.to)?.isPresent != true) return

        // For the conversation on the recipient's screen, if they are here.
        server.playerList.getPlayer(payload.to)?.let { PacketDistributor.sendToPlayer(it, PingReceivedPayload(sender.uuid, text)) }
        // The notification: a badge, a banner or the phone peeking, and the way to the screen to answer. Offline
        // players get it when they next log in.
        PhoneNotifications.send(
            payload.to,
            Ping.APP_ID,
            title = Component.translatable("notif.lnbphone_demo.ping.title"),
            // The player's text goes in as an argument, never as the key: see PhoneNotifications.
            text = Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text),
            target = PingTarget(sender.uuid, sender.gameProfile.name).toString(),
        )
    }
}
