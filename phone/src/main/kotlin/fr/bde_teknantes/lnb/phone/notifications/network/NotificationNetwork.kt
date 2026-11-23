package fr.bde_teknantes.lnb.phone.notifications.network

import fr.bde_teknantes.lnb.phone.notifications.server.ServerNotifications
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * The phone is required on both sides, like LNB's other mods (lnb-quests, lnb-economy), so every payload is required
 * too: a client without the phone can't join a server that has it, and the other way round. Bump [PROTOCOL_VERSION]
 * whenever a payload changes, so mismatched client and server versions refuse to connect.
 *
 * Handlers run on the game thread of the receiving side (NeoForge's default), never on a network thread.
 */
object NotificationNetwork {
    private const val PROTOCOL_VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)
        registrar.playToServer(ClearAppNotificationsPayload.TYPE, ClearAppNotificationsPayload.STREAM_CODEC, ServerNotifications::handleClearApp)
        registrar.playToServer(DismissNotificationPayload.TYPE, DismissNotificationPayload.STREAM_CODEC, ServerNotifications::handleDismiss)
        // Client handlers sit in lambdas and are named in full, so their client-only class loads only when a packet
        // arrives, which never happens on a dedicated server.
        registrar.playToClient(NotificationsSyncPayload.TYPE, NotificationsSyncPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler.handleSync(payload)
        }
        registrar.playToClient(NotificationAddedPayload.TYPE, NotificationAddedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler.handleAdded(payload)
        }
        registrar.playToClient(NotificationsClearedPayload.TYPE, NotificationsClearedPayload.STREAM_CODEC) { payload, _ ->
            fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler.handleCleared(payload)
        }
    }
}
