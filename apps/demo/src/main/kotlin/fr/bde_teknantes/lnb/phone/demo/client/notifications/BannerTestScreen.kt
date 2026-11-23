package fr.bde_teknantes.lnb.phone.demo.client.notifications

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.client.notifications.BannerContent
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLayout
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLook
import fr.bde_teknantes.lnb.phone.client.notifications.NotificationBanner
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Test sheet for the notification banner (task 6), sized for the window at GUI scale 4 (640 x 480 dp).
 *
 * - [Mode.STATES]: on the app background, the banner's states (normal, hover, pressed) with the storyboard's texts,
 *   then the edge cases: characters the fonts lack and emoji, a long title, an item icon, a one-line text, no text,
 *   and emoji in a title and a text.
 * - [Mode.CHAIN]: a hundred emoji joined by ZWJ, as a player could paste them, in a banner.
 * - [Mode.FRAMES]: P-10 steps 3 and 2 on the home wallpaper (no icons yet: the home screen is task 4): resting at
 *   app-local (8, 4), and half-way down its slide at `y -36`, clipped to the viewport.
 */
class BannerTestScreen(private val mode: Mode) : Screen(Component.literal("Banner test")) {
    enum class Mode { STATES, FRAMES, CHAIN }

    private val banner = NotificationBanner()
    private val canvas = SmoothCanvas()

    private val feed = AppIcon.Glyph(TileColor.AZURE, ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/sprites/icon/feed.png"))
    private val reply = BannerContent(feed, "Nouvelle réponse", "Mika Laurent : Je l'ai vu près du moulin, côté est !")
    private val announcement = BannerContent(
        feed, "Nouvelle annonce",
        "Lune Garnier : La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00.",
    )
    private val pvp = BannerContent(
        feed, "Nouvelle annonce",
        "Kael_Builder : Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn.",
    )
    private val glyphless = BannerContent(
        feed, "🎉 Nouvelle réponse 🎉",
        "田中 : 🔥🔥 Rendez-vous\nau château 👍🏽 ce soir, ça va être énorme",
    )
    private val longTitle = BannerContent(
        AppIcon.Glyph(TileColor.VIOLET, ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/sprites/icon/quests.png")),
        "Marie-Charlotte de La Villemarqué a répondu",
        "Supercalifragilisticexpidelilicieusementinterminable mot sans espace pour la coupure",
    )
    private val emoji = BannerContent(
        feed, "Camille 🌸 Roux 🎉",
        "Soirée au spawn ce soir 🎉 On part au Nether 🔥 vers 21:00, ramenez des potions 🧪 et à manger 🍗.",
    )
    private val item = BannerContent(AppIcon.Item(TileColor.ORANGE, ItemStack(Items.DIAMOND)), "Vente conclue", "Ton annonce a trouvé preneur.")
    private val noText = BannerContent(
        AppIcon.Glyph(TileColor.GRAPHITE, ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/sprites/icon/settings.png")),
        "Réglages enregistrés", "",
    )

    override fun isPauseScreen() = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xFF0B0D11.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        when (mode) {
            Mode.STATES -> states(graphics)
            Mode.FRAMES -> frames(graphics)
            Mode.CHAIN -> chain(graphics)
        }
    }

    private fun states(graphics: GuiGraphics) {
        // Two app-background columns, 256 dp wide each (the viewport's width), 16 dp apart.
        canvas.begin(graphics, 0, 0, width, height)
        canvas.fillRect(0f, 0f, 256f, 480f, BG)
        canvas.fillRect(272f, 0f, 256f, 480f, BG)
        canvas.end()
        val rows = listOf(
            listOf(reply to BannerLook.NORMAL, announcement to BannerLook.HOVER, pvp to BannerLook.PRESSED, glyphless to BannerLook.NORMAL),
            listOf(longTitle to BannerLook.NORMAL, item to BannerLook.NORMAL, noText to BannerLook.NORMAL, emoji to BannerLook.NORMAL),
        )
        rows.forEachIndexed { column, list ->
            list.forEachIndexed { i, (content, look) ->
                // Each banner at the in-phone x, 96 dp apart, inside its own column.
                banner.render(graphics, column * 136, 0, 128, height, BannerLayout.IN_PHONE_X, BannerLayout.IN_PHONE_Y + i * 96f, content, look)
            }
        }
    }

    /** A hundred emoji joined by ZWJ in a banner: two lines inside the slot, the second ended by the ellipsis. */
    private fun chain(graphics: GuiGraphics) {
        canvas.begin(graphics, 0, 0, width, height)
        canvas.fillRect(0f, 0f, 256f, 200f, BG)
        canvas.end()
        banner.render(graphics, 0, 0, 128, height, BannerLayout.IN_PHONE_X, BannerLayout.IN_PHONE_Y, chainBanner, BannerLook.NORMAL)
    }

    private val chainBanner = BannerContent(feed, "Nouvelle réponse", fr.bde_teknantes.lnb.phone.demo.client.kit.EmojiPages.CHAIN)

    private fun frames(graphics: GuiGraphics) {
        // Resting, then half-way in position (P-10.2: y -36), whatever the easing.
        listOf(1f, 0.5f).forEachIndexed { i, progress ->
            val left = i * 136
            graphics.blitSprite(WALLPAPER, left, 0, 128, 196)
            graphics.fill(left, 0, left + 128, 11, STATUS_HOME)
            banner.render(graphics, left, 11, 128, 185, BannerLayout.IN_PHONE_X, BannerLayout.inPhoneY(progress), reply, BannerLook.NORMAL, progress)
        }
    }

    private companion object {
        const val BG = 0xFF12151B.toInt()
        const val STATUS_HOME = 0x66000000
        val WALLPAPER: ResourceLocation = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "wallpaper/dusk")
    }
}
