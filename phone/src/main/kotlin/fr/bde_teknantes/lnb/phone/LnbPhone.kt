package fr.bde_teknantes.lnb.phone

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

/**
 * Common entrypoint, loaded on both sides. The phone itself is drawn on the client; the server will only keep each
 * player's notifications.
 */
@Mod(LnbPhone.ID)
class LnbPhone(modBus: IEventBus) {
    init {
        // Referenced by its full name, behind the check, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.phone.client.LnbPhoneClient.init(modBus)
    }

    companion object {
        const val ID = "lnbphone"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
