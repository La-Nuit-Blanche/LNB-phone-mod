package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import org.lwjgl.glfw.GLFW

/**
 * Task 7: notifications reaching the phone, in a world, through art direction 17's flows P-4, P-5, P-8 to P-11 and
 * P-14, at GUI scale 2, 3 and 4. Real notifications, sent by `/phonedemo notify` to the demo's Hello app (home slot 5
 * with `-PdemoApps=6`). Run with `-PcaptureSize=1920x1080`: at GUI scale 4 the window is the flows' 480 x 270.
 *
 * Mid-slide frames hold the animation at half its eased course, where the flows measure them.
 */
internal object NotificationScenarios {
    private const val HELLO_SLOT = 5

    private const val ANNOUNCEMENT = "Lune Garnier : La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00."
    private const val REPLY = "Mika Laurent : Je l'ai vu près du moulin, côté est !"
    private const val PVP = "Kael_Builder : Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn."

    fun notifications() = CaptureScript().apply {
        enterWorld()
        quietCommands()
        reset()
        for (scale in 2..4) {
            guiScale(scale)
            waitTicks(10)
            closedFlows(scale)
            openFlows(scale)
            inventoryFlows(scale)
            previewOff(scale)
        }
        narrow()
        reset()
    }

    /** A notification survives leaving the world and coming back: it is saved with the world. */
    fun persistence() = CaptureScript().apply {
        enterWorld(PERSIST_WORLD)
        quietCommands()
        reset()
        guiScale(3)
        notify(2, REPLY)
        waitTicks(5)
        notify(3, ANNOUNCEMENT)
        waitUntil { CaptureHooks.totalUnread == 2 }
        logUnread("before leaving")
        waitUntil { !CaptureHooks.isPeeking }
        openPhone()
        shot("persist_1_before_leaving_gui3")
        closePhone()
        leaveWorld()
        logUnread("on the title screen")
        openWorld(PERSIST_WORLD)
        waitUntil { CaptureHooks.totalUnread == 2 }
        waitTicks(20)
        logUnread("back in the world")
        // No peek and no chime for what the login sync brings.
        run { LnbPhone.LOGGER.info("Capture persistence: peeking after login: {}", CaptureHooks.isPeeking) }
        run { CaptureHooks.holdLedOn(true) }
        openPhone()
        pointAtNothing()
        waitTicks(2)
        shot("persist_2_after_coming_back_gui3")
        closePhone()
        reset()
    }

    private const val PERSIST_WORLD = "lnbphone-persist"

    // P-8, P-9: the phone closed.
    private fun CaptureScript.closedFlows(scale: Int) {
        // P-8.2: rising, held half way.
        hold(0.5f)
        notify(1, ANNOUNCEMENT)
        waitUntil { CaptureHooks.isPeeking }
        waitTicks(3)
        shot("p8_2_peek_rising_gui$scale")
        hold(null)
        // P-8.3: risen, holding.
        waitTicks(8)
        shot("p8_3_peek_gui$scale")
        logPeek(scale)
        waitUntil { !CaptureHooks.isPeeking }
        waitTicks(2)
        shot("p8_4_peek_gone_gui$scale")

        // P-8.5 to P-8.8: the badge waits on the home screen and clears when the app opens.
        run { CaptureHooks.holdLedOn(true) }
        openPhone()
        pointAtNothing()
        waitTicks(2)
        shot("p8_5_open_badge_gui$scale")
        mouseTo { icon(HELLO_SLOT) }
        waitTicks(2)
        shot("p8_6_hover_badged_gui$scale")
        mouseDown()
        mouseUp()
        waitTicks(6)
        shot("p8_7_app_opened_gui$scale")
        clickHomeButton()
        waitTicks(4)
        shot("p8_8_home_no_badge_gui$scale")
        run { CaptureHooks.holdLedOn(false) }
        closePhone()

        // P-9: the phone key during the peek opens the notification's target, rising from the peek.
        notify(2, REPLY)
        waitUntil { CaptureHooks.isPeeking }
        waitTicks(8)
        // Held after the key: the device leaves from where the peek really is, then stops half way to (334, 117).
        key(GLFW.GLFW_KEY_UP)
        hold(0.5f)
        waitTicks(2)
        shot("p9_2_rising_from_peek_gui$scale")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        mouseTo { device(70, 140) }
        waitTicks(3)
        shot("p9_3_target_gui$scale")
        clickHomeButton()
        waitTicks(3)
        shot("p9_4_home_badge_cleared_gui$scale")
    }

    // P-10, P-11, and a notification whose target is on screen: the phone open.
    private fun CaptureScript.openFlows(scale: Int) {
        // P-10.1: open on home. The banner's very start, to check that no shadow shows above it.
        pointAtNothing()
        hold(0.1f)
        notify(2, REPLY)
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(2)
        shot("p10_1_banner_entering_gui$scale")
        hold(0.5f)
        waitTicks(2)
        shot("p10_2_banner_sliding_gui$scale")
        hold(null)
        waitTicks(4)
        shot("p10_3_banner_gui$scale")
        // P-10.4: hover at app-local (128, 42) dp, then pressed.
        mouseTo { device(6 + 64, 23 + 21) }
        waitTicks(2)
        shot("p10_4_banner_hover_gui$scale")
        mouseDown()
        waitTicks(1)
        shot("p10_4_banner_pressed_gui$scale")
        // Held after the release: held before, the banner would move out from under the pointer.
        mouseUp()
        hold(0.5f)
        waitTicks(2)
        shot("p10_5_target_sliding_in_gui$scale")
        hold(null)
        waitTicks(5)
        shot("p10_5_target_gui$scale")

        // The demo screen at depth 2 shows target depth:2: a notification for it is read in place.
        notify(2, REPLY)
        waitTicks(10)
        shot("target_on_screen_no_banner_gui$scale")
        run { LnbPhone.LOGGER.info("Capture GUI {}: target on screen, banner {}, unread {}", scale, CaptureHooks.bannerShown, CaptureHooks.totalUnread) }

        // P-11: over another app, then it leaves and the badge waits.
        clickHomeButton()
        waitTicks(2)
        mouseTo { icon(0) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        mouseTo { device(70, 160) }
        notify(1, PVP)
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(5)
        shot("p11_2_banner_over_app_gui$scale")
        waitUntil { !CaptureHooks.bannerShown }
        clickHomeButton()
        waitTicks(3)
        shot("p11_4_home_badge_gui$scale")
        // Read it, so the next flows start without news; then leave the phone on another app, for P-4 to reopen.
        mouseTo { icon(HELLO_SLOT) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        clickHomeButton()
        waitTicks(2)
        mouseTo { icon(0) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        closePhone()
    }

    // P-4, P-5: the inventory button.
    private fun CaptureScript.inventoryFlows(scale: Int) {
        screen { InventoryScreen(Minecraft.getInstance().player!!) }
        mouseTo { inventory(108, 150) }
        waitTicks(3)
        shot("p4_1_inventory_gui$scale")
        mouseTo { phoneButton(13, 14) }
        waitTicks(3)
        shot("p4_2_hover_gui$scale")
        hold(0.5f)
        mouseDown()
        mouseUp()
        waitTicks(3)
        shot("p4_3_opening_gui$scale")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        waitTicks(3)
        shot("p4_4_open_on_last_screen_gui$scale")
        key(GLFW.GLFW_KEY_ESCAPE)
        waitUntil { CaptureHooks.isClosed && Minecraft.getInstance().screen is InventoryScreen }
        mouseTo { inventory(108, 150) }
        waitTicks(3)
        shot("p4_5_inventory_back_gui$scale")

        // P-5: three notifications while the inventory is open; the button lights up with the count.
        notify(1, ANNOUNCEMENT)
        waitTicks(2)
        notify(2, REPLY)
        waitTicks(2)
        notify(3, PVP)
        waitUntil { CaptureHooks.totalUnread == 3 }
        waitTicks(40)
        shot("p5_1_inventory_new_gui$scale")
        mouseTo { phoneButton(13, 14) }
        waitTicks(3)
        shot("p5_2_hover_new_gui$scale")
        mouseDown()
        mouseUp()
        waitUntil { CaptureHooks.isOpen }
        run { CaptureHooks.holdLedOn(true) }
        mouseTo { device(70, 160) }
        waitTicks(3)
        shot("p5_3_open_home_gui$scale")
        run { CaptureHooks.holdLedOn(false) }
        // Read them, and close back to the inventory, then to the game.
        mouseTo { icon(HELLO_SLOT) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        clickHomeButton()
        key(GLFW.GLFW_KEY_ESCAPE)
        waitUntil { CaptureHooks.isClosed && Minecraft.getInstance().screen is InventoryScreen }
        screen { null }
        waitTicks(2)
    }

    // P-14: preview off; no peek, but the badge and the banners inside the open phone.
    private fun CaptureScript.previewOff(scale: Int) {
        run { CaptureHooks.setPreview(false) }
        notify(1, ANNOUNCEMENT)
        waitUntil { CaptureHooks.totalUnread == 1 }
        waitTicks(8)
        shot("p14_3_no_peek_gui$scale")
        run { LnbPhone.LOGGER.info("Capture GUI {}: preview off, peeking {}", scale, CaptureHooks.isPeeking) }
        run { CaptureHooks.holdLedOn(true) }
        openPhone()
        pointAtNothing()
        waitTicks(2)
        shot("p14_4_open_badge_gui$scale")
        notify(2, REPLY)
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(5)
        shot("p14_5_banner_gui$scale")
        run {
            CaptureHooks.holdLedOn(false)
            CaptureHooks.setPreview(true)
        }
        pointAtNothing()
        waitUntil { !CaptureHooks.bannerShown }
        mouseTo { icon(HELLO_SLOT) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        clickHomeButton()
        closePhone()
    }

    // A window narrower than 476 GUI px: the peek rises 112 px.
    private fun CaptureScript.narrow() {
        forceGuiScale(5)
        waitTicks(5)
        notify(1, ANNOUNCEMENT)
        waitUntil { CaptureHooks.isPeeking }
        waitTicks(8)
        shot("p8_3_peek_narrow_forced_gui5")
        logPeek(5)
        waitUntil { !CaptureHooks.isPeeking }
        guiScale(4)
        waitTicks(2)
        // Read it.
        openPhone()
        mouseTo { icon(HELLO_SLOT) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        clickHomeButton()
        closePhone()
    }

    // The commands' replies would fill the chat over the HUD in every shot.
    private fun CaptureScript.quietCommands() {
        run { Minecraft.getInstance().player!!.connection.sendCommand("gamerule sendCommandFeedback false") }
        waitTicks(5)
    }

    private fun CaptureScript.notify(depth: Int, text: String) = run {
        Minecraft.getInstance().player!!.connection.sendCommand("phonedemo notify @a $depth $text")
    }

    private fun CaptureScript.logPeek(scale: Int) = run {
        val window = window()
        LnbPhone.LOGGER.info(
            "Capture GUI {}: window {} x {}, peek at {}", scale, window.first, window.second,
            CaptureHooks.peekPosition()?.joinToString(),
        )
    }

    private fun CaptureScript.logUnread(moment: String) = run {
        LnbPhone.LOGGER.info(
            "Capture persistence {}: unread {} (Hello {})", moment, CaptureHooks.totalUnread,
            CaptureHooks.unreadCount(DemoApp.HELLO_ID),
        )
    }

    private fun CaptureScript.reset() = run {
        CaptureHooks.setCentred(false)
        CaptureHooks.setPreview(true)
        CaptureHooks.fakeBadges(null)
        CaptureHooks.fakeLatency(null)
        CaptureHooks.holdLedOn(false)
        CaptureHooks.holdAnimations(null)
    }

    private fun CaptureScript.openPhone() {
        key(GLFW.GLFW_KEY_UP)
        waitUntil { CaptureHooks.isOpen }
        waitTicks(2)
    }

    private fun CaptureScript.closePhone() {
        key(GLFW.GLFW_KEY_ESCAPE)
        waitUntil { CaptureHooks.isClosed }
        waitTicks(2)
    }

    private fun CaptureScript.clickHomeButton() {
        mouseTo { device(70, 218) }
        mouseDown()
        mouseUp()
    }

    // The pointer on an empty part of the home grid, hovering nothing (P-10.1: GUI px (70, 120) screen-local).
    private fun CaptureScript.pointAtNothing() = mouseTo { device(6 + 70, 12 + 120) }

    private fun CaptureScript.hold(progress: Float?) = run { CaptureHooks.holdAnimations(progress) }

    private fun window(): Pair<Int, Int> = Minecraft.getInstance().window.let { it.guiScaledWidth to it.guiScaledHeight }

    /** A device-local point in window GUI px. */
    private fun device(dx: Int, dy: Int): Pair<Int, Int> {
        val position = CaptureHooks.devicePosition() ?: error("The phone is away")
        return position[0] + dx to position[1] + dy
    }

    /** The middle of home icon [slot]. */
    private fun icon(slot: Int): Pair<Int, Int> = device(6 + 6 + 24 * (slot % 5) + 10, 23 + 8 + 24 * (slot / 5) + 10)

    private fun inventoryScreen(): InventoryScreen = Minecraft.getInstance().screen as? InventoryScreen ?: error("No inventory")

    /** An inventory-local point in window GUI px. */
    private fun inventory(x: Int, y: Int): Pair<Int, Int> = inventoryScreen().let { it.guiLeft + x to it.guiTop + y }

    /** A point on the phone button, button-local: the button is at (guiLeft - 22, guiTop + 46) (art direction 11). */
    private fun phoneButton(x: Int, y: Int): Pair<Int, Int> = inventory(-22 + x, 46 + y)
}
