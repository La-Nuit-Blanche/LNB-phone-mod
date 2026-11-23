package fr.bde_teknantes.lnb.phone.feed.network

import fr.bde_teknantes.lnb.phone.feed.server.FeedServer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadHandler

/**
 * Feed's payloads, registered on both sides. Feed is required on both sides, like the phone and LNB's other mods, so
 * every payload is required too: a client without Feed can't join a server that has it, and the other way round.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so that mismatched clients and servers refuse to connect.
 *
 * Handlers run on the game thread of the receiving side (NeoForge's default).
 */
object FeedNetwork {
    const val PROTOCOL_VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)

        registrar.playToServer(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, server(FeedServer::onOpen))
        registrar.playToServer(LoadFeedPayload.TYPE, LoadFeedPayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(LoadThreadPayload.TYPE, LoadThreadPayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(CreatePostPayload.TYPE, CreatePostPayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(CreateReplyPayload.TYPE, CreateReplyPayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(LikePayload.TYPE, LikePayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(PinPayload.TYPE, PinPayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(DeletePayload.TYPE, DeletePayload.STREAM_CODEC, server(FeedServer::onRequest))
        registrar.playToServer(SeenFeedPayload.TYPE, SeenFeedPayload.STREAM_CODEC, server(FeedServer::onSeenFeed))
        registrar.playToServer(SeenThreadPayload.TYPE, SeenThreadPayload.STREAM_CODEC, server(FeedServer::onSeenThread))

        // The client's handlers are named in full inside the lambdas: their class loads only when a payload arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(StatePayload.TYPE, StatePayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onState(payload)
        }
        registrar.playToClient(PagePayload.TYPE, PagePayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onPage(payload)
        }
        registrar.playToClient(ThreadPayload.TYPE, ThreadPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onThread(payload)
        }
        registrar.playToClient(ResultPayload.TYPE, ResultPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onResult(payload)
        }
        registrar.playToClient(LivePayload.TYPE, LivePayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onLive(payload)
        }
        registrar.playToClient(RemovedPayload.TYPE, RemovedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onRemoved(payload)
        }
        registrar.playToClient(CountsPayload.TYPE, CountsPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onCounts(payload)
        }
        registrar.playToClient(PinsPayload.TYPE, PinsPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onPins(payload)
        }
        registrar.playToClient(ResetPayload.TYPE, ResetPayload.STREAM_CODEC) { _, _ ->
            fr.bde_teknantes.lnb.phone.feed.client.FeedClientPayloads.onReset()
        }
    }

    /** A server handler, given the sender: the only player a request may act for (`feed-app.md` 4.3). */
    private fun <T : CustomPacketPayload> server(handle: (ServerPlayer, T) -> Unit) = IPayloadHandler<T> { payload, context ->
        val player = context.player()
        if (player is ServerPlayer) handle(player, payload)
    }
}
