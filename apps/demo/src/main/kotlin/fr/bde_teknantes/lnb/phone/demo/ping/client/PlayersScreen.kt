package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconView
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n

/** Ping's first screen: the players online, the player included (to try Ping alone). A click opens [PingScreen]. */
class PlayersScreen : KitScreen() {
    private val list = ScrollList(PingSession.playersScroll)

    init {
        add(Header(I18n.get("app.lnbphone_demo.ping")).at(0f, 0f))
        add(list.at(0f, 44f, 256f, 326f))
    }

    // Each time the screen shows (opened, or back from a ping): players come and go.
    override fun onScreenShown() {
        val mc = Minecraft.getInstance()
        val players = mc.connection?.onlinePlayers.orEmpty().map { it.profile }.sortedBy { it.name.lowercase() }
        list.removeAll()
        for (player in players) {
            list.add(ListRow(player.name) { phone.push(PingScreen(player.id, player.name)) }.apply {
                if (player.id == mc.player?.uuid) subtitle = I18n.get("lnbphone_demo.ping.you")
                leading = Avatar().apply { showPlayer(player.id, player.name) }
                trailing = IconView(Icon.CHEVRON).apply { color = Colors.TEXT_2 }
            })
        }
    }
}
