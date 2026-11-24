package fr.bde_teknantes.lnb.phone.feed

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.phone.feed.network.FeedNetwork
import fr.bde_teknantes.lnb.phone.feed.server.FeedConfig
import fr.bde_teknantes.lnb.phone.feed.server.FeedServer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.Logger

/**
 * Feed, a Twitter-like app for the LNB Phone, backed by LNB's website (`docs/feed-app.md`). Common entrypoint, loaded
 * on both sides: the config and the payloads everywhere, the logical server's side (package `server`, common code
 * that only the logical server runs), and the client behind the dist check.
 *
 * Feed never logs a post's text or the author of an anonymous post (`docs/feed-mod.md` 12): IDs and problem codes only.
 */
@Mod(Feed.MOD_ID)
class FeedMod(modBus: IEventBus, container: ModContainer) {
    init {
        // COMMON, like Warden's server config: a SERVER config would be sent to every player who connects.
        container.registerConfig(ModConfig.Type.COMMON, FeedConfig.SPEC, "${Feed.MOD_ID}-server.toml")
        modBus.addListener(FeedNetwork::register)
        FeedServer.init()
        // Referenced by its full name, behind the check, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.phone.feed.client.FeedClient.init(modBus)
    }

    companion object {
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
