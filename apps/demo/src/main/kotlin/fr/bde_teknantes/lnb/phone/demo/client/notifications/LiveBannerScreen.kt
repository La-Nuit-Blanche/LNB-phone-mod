package fr.bde_teknantes.lnb.phone.demo.client.notifications

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.notifications.BannerContent
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLayout
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLook
import fr.bde_teknantes.lnb.phone.client.notifications.NotificationBanner
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Shows what the client's notification store holds right now, for the end-to-end capture: the newest notification as
 * a banner, the unread count and the target, over the world. Proves the path command, server store, network, client
 * store and translation without the phone's screen.
 *
 * The store is internal to the phone, so this development screen reads it by reflection; nothing shipped does.
 */
class LiveBannerScreen : Screen(Component.literal("Live banner")) {
    private val banner = NotificationBanner()
    private val icon = AppIcon.Glyph(TileColor.TEAL, ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/hello.png"))

    override fun isPauseScreen() = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val (all, unread) = read()
        val newest = all.lastOrNull()
        val lines = listOf(
            "Client store: ${all.size} notification(s), ${unread} unread for installed apps",
            "Newest: " + (newest?.let { "#${it.id} ${it.appId} target=${it.target} delivered=${it.delivered}" } ?: "none"),
        )
        lines.forEachIndexed { i, line -> graphics.drawString(font, line, 4, 4 + i * 10, -1) }
        if (newest != null) {
            graphics.fill(4, 28, 132, 28 + 46, BG)
            banner.render(graphics, 4, 28, 128, 46, BannerLayout.IN_PHONE_X, BannerLayout.IN_PHONE_Y, content(newest), BannerLook.NORMAL)
        }
    }

    private fun content(notification: PhoneNotification) = BannerContent(icon, notification.title.string, notification.text.string)

    companion object {
        private const val BG = 0xFF12151B.toInt()

        /** The client store's notifications (oldest first) and its unread count for installed apps. */
        @Suppress("UNCHECKED_CAST")
        fun read(): Pair<List<PhoneNotification>, Int> {
            val field = LnbPhoneClient::class.java.getDeclaredField("notifications").apply { isAccessible = true }
            val store = field.get(LnbPhoneClient)
            val all = store.javaClass.getMethod("getAll").invoke(store) as List<PhoneNotification>
            val unread = store.javaClass.getMethod("getTotalUnread").invoke(store) as Int
            return all to unread
        }

        /** A line for the capture's log. */
        fun summary(): String = read().let { (all, unread) -> "${all.size} stored, $unread unread, newest ${all.lastOrNull()}" }
    }
}
