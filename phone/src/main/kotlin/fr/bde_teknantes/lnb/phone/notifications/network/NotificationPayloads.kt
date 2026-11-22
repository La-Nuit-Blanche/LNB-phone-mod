package fr.bde_teknantes.lnb.phone.notifications.network

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.notifications.NotificationCodecs
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

private fun <T : CustomPacketPayload> type(name: String) =
    CustomPacketPayload.Type<T>(ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, name))

// ---- Server to client ----

/**
 * Every notification the player has, replacing what the client had: sent when they log in ([login] true), and again
 * when their client fell out of step ([login] false, every notification then already delivered).
 */
data class NotificationsSyncPayload(val notifications: List<PhoneNotification>, val login: Boolean = true) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<NotificationsSyncPayload>("notifications_sync")
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NotificationsSyncPayload> = StreamCodec.composite(
            NotificationCodecs.STREAM_CODEC.apply(ByteBufCodecs.list(NotificationList.CAPACITY)), NotificationsSyncPayload::notifications,
            ByteBufCodecs.BOOL, NotificationsSyncPayload::login,
            ::NotificationsSyncPayload,
        )
    }
}

/** A notification just sent to the connected player. */
data class NotificationAddedPayload(val notification: PhoneNotification) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<NotificationAddedPayload>("notification_added")
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, NotificationAddedPayload> =
            NotificationCodecs.STREAM_CODEC.map(::NotificationAddedPayload, NotificationAddedPayload::notification)
    }
}

/** The app's server side cleared some of the player's notifications: all of [appId]'s, or those leading to [target]. */
data class NotificationsClearedPayload(val appId: ResourceLocation, val target: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<NotificationsClearedPayload>("notifications_cleared")
        val STREAM_CODEC: StreamCodec<ByteBuf, NotificationsClearedPayload> = StreamCodec.composite(
            NotificationCodecs.APP_ID, NotificationsClearedPayload::appId,
            NotificationCodecs.TARGET, { java.util.Optional.ofNullable(it.target) },
        ) { appId, target -> NotificationsClearedPayload(appId, target.orElse(null)) }
    }
}

// ---- Client to server ----

/**
 * The player opened [appId]: its notifications up to [upToId] (the newest the client knew of) are read. A notification
 * that reached the server after the client sent this has a bigger id and survives, so it isn't lost unseen.
 */
data class ClearAppNotificationsPayload(val appId: ResourceLocation, val upToId: Long) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<ClearAppNotificationsPayload>("clear_app_notifications")
        val STREAM_CODEC: StreamCodec<ByteBuf, ClearAppNotificationsPayload> = StreamCodec.composite(
            NotificationCodecs.APP_ID, ClearAppNotificationsPayload::appId,
            ByteBufCodecs.VAR_LONG, ClearAppNotificationsPayload::upToId,
            ::ClearAppNotificationsPayload,
        )
    }
}

/** The player saw notification [id] without opening it the usual way (its target was already on screen). */
data class DismissNotificationPayload(val id: Long) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<DismissNotificationPayload>("dismiss_notification")
        val STREAM_CODEC: StreamCodec<ByteBuf, DismissNotificationPayload> =
            ByteBufCodecs.VAR_LONG.map(::DismissNotificationPayload, DismissNotificationPayload::id)
    }
}
