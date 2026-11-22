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

// Bounded: a client can't make the server read more than a ping can hold. Twice the limit in UTF-16 units, since a
// character outside the basic plane takes two.
private val TEXT_CODEC = ByteBufCodecs.stringUtf8(Ping.MAX_LENGTH * 2)

private fun <T : CustomPacketPayload> type(name: String) =
    CustomPacketPayload.Type<T>(ResourceLocation.fromNamespaceAndPath(DemoApp.ID, name))

/** Client to server: the player pings [to] with [text]. */
data class SendPingPayload(val to: UUID, val text: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<SendPingPayload>("send_ping")
        val STREAM_CODEC: StreamCodec<ByteBuf, SendPingPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SendPingPayload::to,
            TEXT_CODEC, SendPingPayload::text,
            ::SendPingPayload,
        )
    }
}

/** What became of a ping the player sent. */
enum class PingOutcome {
    SENT,

    /** The recipient isn't connected: Ping only carries pings between players who are both here. */
    OFFLINE,

    /** The sender pinged less than [PingServer.COOLDOWN_MS] ago. */
    TOO_FAST,
}

/** Server to client: the answer to the player's ping of [text] to [to]. */
data class PingAnswerPayload(val to: UUID, val text: String, val outcome: PingOutcome) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<PingAnswerPayload>("ping_answer")
        val STREAM_CODEC: StreamCodec<ByteBuf, PingAnswerPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PingAnswerPayload::to,
            TEXT_CODEC, PingAnswerPayload::text,
            ByteBufCodecs.VAR_INT.map({ PingOutcome.entries[it] }, { it.ordinal }), PingAnswerPayload::outcome,
            ::PingAnswerPayload,
        )
    }
}

/** Server to client: [from] pinged this player with [text]. The text itself: the notification only points to it. */
data class PingReceivedPayload(val from: UUID, val text: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<PingReceivedPayload>("ping_received")
        val STREAM_CODEC: StreamCodec<ByteBuf, PingReceivedPayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PingReceivedPayload::from,
            TEXT_CODEC, PingReceivedPayload::text,
            ::PingReceivedPayload,
        )
    }
}

/**
 * Ping's payloads, registered on both sides. NeoForge runs each handler on the game thread of the side receiving it.
 * Bump the version when a payload changes, so that mismatched clients and servers refuse to connect.
 */
object PingNetwork {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("2")
        registrar.playToServer(SendPingPayload.TYPE, SendPingPayload.STREAM_CODEC, PingServer::handle)
        // The client's handlers are named in full inside the lambdas: their class loads only when a payload arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(PingAnswerPayload.TYPE, PingAnswerPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.answered(payload.to, payload.text, payload.outcome)
        }
        registrar.playToClient(PingReceivedPayload.TYPE, PingReceivedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession.received(payload.from, payload.text)
        }
    }
}
