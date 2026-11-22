package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * The phone's button on the survival inventory (art direction 11): a miniature of the phone's face on a side tab, under
 * the quest book's button of lnb-quests (which needn't be installed), lit with the unread count while notifications
 * wait. Attached the way the quest button is: added to the inventory screen when it initialises, placed again before
 * each frame so it follows the inventory when the recipe book moves it.
 *
 * Only on the survival inventory, as the quest button: the creative inventory keeps that side for its own tabs, and the
 * phone key works there. A mod that replaces the inventory with a screen of its own gets no button; one that extends
 * the vanilla screen keeps it. The hooks run for every screen: for any other screen they cost one type test.
 *
 * Client thread only.
 */
internal object PhoneInventoryButton {
    // Over the tab and the quest button's bracket, under the recipe book (drawn 100 up), which covers the button as it
    // covers the quest button when it is open.
    private const val Z_BADGE = 50f

    private fun sprite(path: String) = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, path)

    private val NORMAL = sprite("inventory/button")
    private val HOVER = sprite("inventory/button_hover")
    private val NEW = sprite("inventory/button_new")
    private val NEW_HOVER = sprite("inventory/button_new_hover")
    private val BADGE = sprite("home/badge")
    private val BADGE_PULSE = sprite("home/badge_pulse")

    /** `accent`, the colour of the tooltip's unread line. */
    private const val UNREAD_TEXT = 0x3A8DF0

    // No reference to the button or its screen is kept here: it would hold the player, and through them the level,
    // after a disconnection or a change of dimension. The screen holds its button; each frame finds it there.

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        val button = PhoneButton(screen)
        button.place()
        event.addListener(button)
    }

    fun onRender(event: ScreenEvent.Render.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        for (child in screen.children()) if (child is PhoneButton) child.place(event.mouseX, event.mouseY)
    }

    /**
     * The tooltip for [unread] notifications: the phone's name, and how many are new. The narrator reads the name as
     * the button's own; the tooltip adds only the unread line, so the name isn't said twice.
     */
    private fun tooltip(unread: Int): Tooltip {
        val name = Component.translatable(InventoryButtonLayout.NAME_KEY)
        val key = InventoryButtonLayout.unreadKey(unread) ?: return Tooltip.create(name, null)
        val line = Component.translatable(key, unread).withColor(UNREAD_TEXT)
        return Tooltip.create(name.copy().append("\n").append(line), line)
    }

    // What the inventory turns into when the phone closes: a fresh one for the player as they are then, as the
    // inventory key would open (the creative inventory for a player now in creative). None once the player is gone.
    private fun freshInventory(): Screen? = Minecraft.getInstance().player?.let(::InventoryScreen)

    private class PhoneButton(val screen: InventoryScreen) : AbstractButton(
        0, 0, InventoryButtonLayout.WIDTH, InventoryButtonLayout.HEIGHT, Component.translatable(InventoryButtonLayout.NAME_KEY),
    ) {
        // The count the tooltip says.
        private var tooltipCount = -1
        private val pulse = BadgePulse()

        // The open recipe book's area this frame, null when it is closed, and the pointer: the part of the button the
        // book covers neither lights up nor takes a click that the book itself doesn't use.
        private var book: InventoryButtonLayout.Area? = null
        private var mouseX = 0
        private var mouseY = 0

        fun place(mouseX: Int = Int.MIN_VALUE, mouseY: Int = Int.MIN_VALUE) {
            val at = InventoryButtonLayout.position(screen.guiLeft, screen.guiTop)
            x = at.x
            y = at.y
            this.mouseX = mouseX
            this.mouseY = mouseY
            val recipes = screen.recipeBookComponent
            book = if (recipes.isVisible) {
                InventoryButtonLayout.recipeBookArea(screen.width, screen.height)
            } else {
                null
            }
            val unread = unread()
            if (unread != tooltipCount) {
                tooltipCount = unread
                setTooltip(tooltip(unread))
            }
        }

        // Every app's badge, read from the store now: in the tick a notification arrives, a click must see it.
        private fun unread(): Int = PhoneShell.unreadNow()

        override fun onPress() = PhoneShell.openFromInventory(::freshInventory)

        override fun isHovered(): Boolean = super.isHovered() && book?.contains(mouseX, mouseY) != true

        override fun clicked(mouseX: Double, mouseY: Double): Boolean =
            super.clicked(mouseX, mouseY) && book?.contains(mouseX.toInt(), mouseY.toInt()) != true

        // No vanilla click: the phone plays its own opening sound, which the Sounds setting mutes.
        override fun playDownSound(handler: SoundManager) {}

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val unread = unread()
            pulse.update(unread, PhoneShell.ticks)
            val sprite = when (InventoryButtonLayout.look(unread, isHoveredOrFocused)) {
                InventoryButtonLayout.Look.NEW_HOVER -> NEW_HOVER
                InventoryButtonLayout.Look.NEW -> NEW
                InventoryButtonLayout.Look.HOVER -> HOVER
                InventoryButtonLayout.Look.NORMAL -> NORMAL
            }
            graphics.blitSprite(sprite, x, y, InventoryButtonLayout.WIDTH, InventoryButtonLayout.HEIGHT)
            val text = HomeGrid.badgeText(unread) ?: return
            val badgeAt = InventoryButtonLayout.badgePosition(Point(x, y))
            graphics.pose().pushPose()
            graphics.pose().translate(0f, 0f, Z_BADGE)
            val badge = if (pulse.lit(PhoneShell.ticks)) BADGE_PULSE else BADGE
            graphics.blitSprite(badge, badgeAt.x, badgeAt.y, HomeGrid.badgeWidth(text), HomeGrid.BADGE_HEIGHT)
            graphics.drawString(
                Minecraft.getInstance().font, text, badgeAt.x + HomeGrid.BADGE_TEXT_X, badgeAt.y + HomeGrid.BADGE_TEXT_Y,
                ShellColors.BADGE_TEXT, false,
            )
            graphics.pose().popPose()
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}

/** The inventory button's place and looks (art direction 11) as plain logic, apart from the widget, for tests. */
internal object InventoryButtonLayout {
    const val WIDTH = 27
    const val HEIGHT = 28

    // 5 px over the inventory's left edge, 4 px under the quest button's lower bracket (which ends at guiTop + 41).
    private const val LEFT = -22
    private const val TOP = 46

    // The badge's top-left, from the button's (11): over the tab and the quest button's bracket.
    private const val BADGE_OFFSET = -3

    const val NAME_KEY = "gui.lnbphone.inventory"
    const val UNREAD_ONE_KEY = "gui.lnbphone.inventory.unread.one"
    const val UNREAD_KEY = "gui.lnbphone.inventory.unread"

    enum class Look { NORMAL, HOVER, NEW, NEW_HOVER }

    /** A rectangle in window GUI px. */
    data class Area(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(px: Int, py: Int) = px >= x && px < x + width && py >= y && py < y + height
    }

    /**
     * Where vanilla draws the open recipe book (147 x 166) in a screen of [width] x [height] GUI px: 86 px left of
     * centre, next to the inventory; centred over it in a window under 379 px, too narrow for both.
     */
    fun recipeBookArea(width: Int, height: Int) =
        Area((width - 147) / 2 - (if (width >= 379) 86 else 0), (height - 166) / 2, 147, 166)

    /** The button's top-left for an inventory whose top-left is ([guiLeft], [guiTop]), window GUI px. */
    fun position(guiLeft: Int, guiTop: Int) = Point(guiLeft + LEFT, guiTop + TOP)

    /** The unread badge's top-left for a button at [button]. */
    fun badgePosition(button: Point) = Point(button.x + BADGE_OFFSET, button.y + BADGE_OFFSET)

    /** The sprite for [unread] notifications, under the pointer or not. */
    fun look(unread: Int, hovered: Boolean): Look = when {
        unread > 0 && hovered -> Look.NEW_HOVER
        unread > 0 -> Look.NEW
        hovered -> Look.HOVER
        else -> Look.NORMAL
    }

    /** The translation key of the tooltip's second line for [unread] notifications (`{n}` in it), or null for none. */
    fun unreadKey(unread: Int): String? = when {
        unread <= 0 -> null
        unread == 1 -> UNREAD_ONE_KEY
        else -> UNREAD_KEY
    }
}

/**
 * When the inventory button's badge pulses (11): when the count rises while the inventory is open, not when the
 * inventory opens with news already there.
 */
internal class BadgePulse {
    // The count last drawn, -1 before the first frame.
    private var drawnCount = -1

    /** The tick the last pulse started on. */
    var start = Long.MIN_VALUE
        private set

    /** Each frame, with the count drawn and the shell's clock. */
    fun update(unread: Int, ticks: Long) {
        if (drawnCount >= 0 && unread > drawnCount) start = ticks
        drawnCount = unread
    }

    /** Whether the badge shows its light frame at [ticks]. */
    fun lit(ticks: Long): Boolean = Motion.pulseLit(start, ticks)
}
