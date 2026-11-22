package fr.bde_teknantes.lnb.phone.demo.ping

import fr.bde_teknantes.lnb.phone.demo.DemoApp
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

// Twice the limit in UTF-16 units: a character outside the basic plane takes two.
private val TEXT_CODEC = ByteBufCodecs.stringUtf8(Ping.MAX_LENGTH * 2)

/** Client to server: the player pings [to] with [text]. */
data class SendPingPayload(val to: UUID, val text: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SendPingPayload>(ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "send_ping"))
        val STREAM_CODEC: StreamCodec<ByteBuf, SendPingPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SendPingPayload::to,
            TEXT_CODEC, SendPingPayload::text,
            ::SendPingPayload,
        )
    }
}

/** Server to client: [from] pinged this player with [text], for the conversation on Ping's screen. */
data class PingReceivedPayload(val from: UUID, val text: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PingReceivedPayload>(ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "ping_received"))
        val STREAM_CODEC: StreamCodec<ByteBuf, PingReceivedPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PingReceivedPayload::from,
            TEXT_CODEC, PingReceivedPayload::text,
            ::PingReceivedPayload,
        )
    }
}

/** Ping's payloads, registered on both sides. NeoForge runs each handler on the game thread of the side receiving it. */
object PingNetwork {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(SendPingPayload.TYPE, SendPingPayload.STREAM_CODEC, PingServer::handle)
        // The client's handler is named in full inside the lambda: its class loads only when a payload arrives, which
        // never happens on a dedicated server.
        registrar.playToClient(PingReceivedPayload.TYPE, PingReceivedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.received(payload.from, payload.text)
        }
    }
}
