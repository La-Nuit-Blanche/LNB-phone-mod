package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.feed.Feed
import net.minecraft.network.chat.Component

/** A placeholder root screen, until Feed's screens are built: one centred line. */
class BuildingScreen : AppScreen() {
    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        canvas.fillRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Colors.BG)
        val text = Component.translatable("${Feed.MOD_ID}.building").string
        canvas.drawTextCentered(text, canvas.width / 2f, canvas.height / 2f, TextStyle.BODY, Colors.TEXT_2)
    }
}
