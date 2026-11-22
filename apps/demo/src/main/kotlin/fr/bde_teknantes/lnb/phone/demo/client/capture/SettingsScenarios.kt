package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import org.lwjgl.glfw.GLFW

/**
 * Task 8: the Settings app in the real phone, in a world, through the game's own input path, step by step through
 * art direction 17's flows P-12 (placement), P-13 (sounds) and P-14 (preview, the steps this branch can show). Run with
 * `-PdemoApps=1` so that Settings is the fifth icon of row 0, as in P-12.1.
 *
 * [settings] plays the flows at GUI scale 2, 3 and 4 in French, shows the screen in English at each scale, and ends
 * with the phone centred and its sounds off, saved. [window] is the next launch, in a 480 x 270 window
 * (`-PcaptureSize=1920x1080`): it first shows that both settings came back, then plays P-12's window frames at the
 * positions the art direction gives.
 */
internal object SettingsScenarios {
    fun settings() = CaptureScript().apply {
        enterWorld()
        language("fr_fr")
        reset()
        for (scale in 2..4) flows(scale)
        language("en_us")
        for (scale in 2..4) english(scale)
        leaveCentredAndSilent()
    }

    fun window() = CaptureScript().apply {
        enterWorld()
        language("fr_fr")
        guiScale(4)
        waitTicks(10)
        log("at launch, before anything is set")
        openPhone()
        mouseTo { 240 to 135 }
        waitTicks(2)
        shot("w_restart_open")
        log("after the restart, open")
        // Back to the defaults: the open phone glides to the corner.
        run { CaptureHooks.resetSettings() }
        waitTicks(8)
        click { device(70, 218) }
        waitTicks(4)
        click { icon(4) }
        waitTicks(8)
        mouseToApp(186, 116)
        waitTicks(3)
        shot("w_p12_3_hover_centre")
        clickHeld()
        shot("w_p12_4_glide_to_centre")
        release()
        shot("w_p12_5_centre")
        mouseToApp(70, 116)
        waitTicks(3)
        shot("w_p12_6_hover_coin")
        clickHeld()
        shot("w_p12_7_glide_to_corner")
        release()
        shot("w_p12_8_corner")
        closePhone()
        run { CaptureHooks.resetSettings() }
    }

    private fun CaptureScript.flows(s: Int) {
        val name = { step: String -> "${step}_gui$s" }
        guiScale(s)
        waitTicks(10)
        openPhone()
        // The phone reopens where it was put away: start from home.
        click { device(70, 218) }
        waitTicks(4)

        // P-12: placement.
        mouseTo { icon(4) }
        waitTicks(2)
        shot(name("p12_1_home_hover_settings"))
        click { icon(4) }
        waitTicks(8)
        mouseToApp(128, 300)
        waitTicks(3)
        shot(name("p12_2_settings"))
        mouseToApp(186, 116)
        waitTicks(3)
        shot(name("p12_3_hover_centre"))
        clickHeld()
        shot(name("p12_4_glide_to_centre"))
        release()
        shot(name("p12_5_centre"))
        mouseToApp(70, 116)
        waitTicks(3)
        shot(name("p12_6_hover_coin"))
        clickHeld()
        shot(name("p12_7_glide_to_corner"))
        release()
        shot(name("p12_8_corner"))
        log("P-12 done at GUI $s")

        // P-13: sounds.
        mouseToApp(226, 169)
        waitTicks(3)
        shot(name("p13_1_sounds_on_hover"))
        mouseDown()
        mouseUp()
        waitTicks(4)
        shot(name("p13_2_sounds_off_hover"))
        log("P-13 sounds off at GUI $s")

        // P-14: preview, steps 1 and 2 (the peek and the banner are task 7's).
        mouseToApp(226, 218)
        waitTicks(3)
        shot(name("p14_1_preview_on_hover"))
        mouseDown()
        mouseUp()
        waitTicks(4)
        shot(name("p14_2_preview_off_hover"))

        // Both back on, from their rows' labels this time: the whole row switches its toggle.
        click { app(60, 218) }
        click { app(60, 169) }
        waitTicks(4)
        mouseToApp(128, 300)
        waitTicks(3)
        shot(name("settings_restored"))
        log("restored at GUI $s")
        closePhone()
    }

    private fun CaptureScript.english(s: Int) {
        guiScale(s)
        waitTicks(5)
        openPhone()
        click { device(70, 218) }
        waitTicks(4)
        mouseTo { icon(4) }
        waitTicks(2)
        shot("en_home_hover_settings_gui$s")
        click { icon(4) }
        waitTicks(8)
        mouseToApp(226, 218)
        waitTicks(3)
        shot("en_settings_gui$s")
        closePhone()
    }

    /** Through the screen, as a player would, so that the next launch ([window]) finds them saved. */
    private fun CaptureScript.leaveCentredAndSilent() {
        guiScale(3)
        waitTicks(5)
        openPhone()
        click { app(186, 116) }
        waitTicks(8)
        click { app(226, 169) }
        waitTicks(4)
        shot("left_centred_silent_gui3")
        log("left for the next launch")
        closePhone()
    }

    // A click on a segment with the shell's glide held half way; the pill (the kit's own clock) finishes its 150 ms.
    private fun CaptureScript.clickHeld() {
        run { CaptureHooks.holdAnimations(0.5f) }
        mouseDown()
        mouseUp()
        waitTicks(4)
    }

    private fun CaptureScript.release() {
        run { CaptureHooks.holdAnimations(null) }
        waitTicks(8)
    }

    private fun CaptureScript.log(what: String) = run {
        LnbPhone.LOGGER.info(
            "Capture settings, {}: device at {}, sounds play: {}",
            what, CaptureHooks.devicePosition()?.joinToString(), PhoneSounds.enabled,
        )
    }

    private fun CaptureScript.reset() = run {
        CaptureHooks.resetSettings()
        CaptureHooks.fakeBadges(null)
        CaptureHooks.holdAnimations(null)
    }

    private fun CaptureScript.click(position: () -> Pair<Int, Int>) {
        mouseTo(position)
        mouseDown()
        mouseUp()
    }

    private fun CaptureScript.mouseToApp(x: Int, y: Int) = mouseTo { app(x, y) }

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

    /** A device-local point in window GUI px. */
    private fun device(dx: Int, dy: Int): Pair<Int, Int> {
        val position = CaptureHooks.devicePosition() ?: error("The phone is away")
        return position[0] + dx to position[1] + dy
    }

    /** An app-local point ([x], [y]) dp in window GUI px (2 dp per GUI px). */
    private fun app(x: Int, y: Int): Pair<Int, Int> {
        val (vx, vy) = CaptureHooks.viewportOffset.let { it[0] to it[1] }
        return device(vx + x / 2, vy + y / 2)
    }

    /** The middle of home icon [slot]. */
    private fun icon(slot: Int): Pair<Int, Int> {
        val (vx, vy) = CaptureHooks.viewportOffset.let { it[0] to it[1] }
        return device(vx + 6 + 24 * (slot % 5) + 10, vy + 8 + 24 * (slot / 5) + 10)
    }
}
