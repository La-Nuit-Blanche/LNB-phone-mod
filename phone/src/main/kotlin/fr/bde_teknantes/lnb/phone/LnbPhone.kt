package fr.bde_teknantes.lnb.phone

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationNetwork
import fr.bde_teknantes.lnb.phone.notifications.server.ServerNotifications
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

/**
 * Common entrypoint, loaded on both sides. The phone itself is drawn on the client; the server keeps each player's
 * notifications (package `notifications`, common code) and sends them to their client.
 */
@Mod(LnbPhone.ID)
class LnbPhone(modBus: IEventBus) {
    init {
        modBus.addListener(NotificationNetwork::register)
        ServerNotifications.register()
        // Referenced by its full name, behind the check, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.phone.client.LnbPhoneClient.init(modBus)
    }

    companion object {
        const val ID = "lnbphone"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
