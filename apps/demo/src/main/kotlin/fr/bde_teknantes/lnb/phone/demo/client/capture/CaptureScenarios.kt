package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.demo.client.kit.KitHostScreen
import fr.bde_teknantes.lnb.phone.demo.client.kit.KitPages
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.demo.client.notifications.BannerTestScreen
import fr.bde_teknantes.lnb.phone.demo.client.notifications.LiveBannerScreen
import fr.bde_teknantes.lnb.phone.demo.client.smooth.SmoothTestScreen
import net.minecraft.client.Minecraft

/**
 * The capture scripts, by name (the value of `-Dlnbphone.capture`). Later tasks add theirs here: the phone shell, the
 * UI kit, the notification banner.
 */
object CaptureScenarios {
    private val scenarios: Map<String, () -> CaptureScript> = linkedMapOf(
        "smooth" to ::smooth,
        "kit" to { kit(2..4) },
        "banner" to ::banner,
        "shell" to ShellScenarios::shell,
        "shell-pages" to ShellScenarios::shellPages,
        "shell-window" to ShellScenarios::shellWindow,
    )

    fun get(name: String): CaptureScript? = scenarios[name]?.invoke()

    fun names(): Set<String> = scenarios.keys

    /** Forced GUI scales and the sheet offsets (dp) shot at each: the start, the type ramp, the shapes. */
    private val HIGH_SCALES = listOf(
        5 to listOf(0 to 0, 264 to 236),
        6 to listOf(0 to 0, 264 to 0, 264 to 268),
        9 to listOf(0 to 0, 264 to 0, 264 to 330, 400 to 380),
    )

    /**
     * Task 3: the smooth layer's test sheet at GUI scale 2, 3 and 4 in one run, again after a resource reload (which
     * drops its fonts and atlas), then over a world.
     */
    private fun smooth() = CaptureScript().apply {
        for (scale in 2..4) {
            guiScale(scale)
            // As the phone key will: the test sheet's first frame then shows what is left to do cold.
            run { LnbPhone.LOGGER.info("Smooth layer pre-warmed for GUI {} in {} us", scale, SmoothCanvas.prewarm() / 1000) }
            screen { SmoothTestScreen() }
            waitTicks(20)
            shot("smooth_gui$scale")
        }
        // A canvas left drawing by a caught exception must not clip the frames after it.
        screen { SmoothTestScreen(abandonOnce = true) }
        waitTicks(5)
        // Scales above 4 ("auto" on 1920 x 1200, 2560 x 1440 and 4K) need a larger window than this machine's screen
        // allows, so they are forced: the sheet is cut off, and scrolled to show its far parts.
        for ((scale, offsets) in HIGH_SCALES) {
            forceGuiScale(scale)
            for ((x, y) in offsets) {
                screen { SmoothTestScreen(offsetX = x, offsetY = y) }
                waitTicks(10)
                shot("smooth_forced_gui${scale}_at_${x}_$y")
            }
        }
        guiScale(3)
        reloadResources()
        screen { SmoothTestScreen() }
        waitTicks(20)
        shot("smooth_reloaded_gui3")
        enterWorld()
        guiScale(3)
        screen { SmoothTestScreen() }
        waitTicks(60)
        shot("smooth_world_gui3")
    }

    /**
     * Task 5: the UI kit's gallery and the composed Feed screens, two viewports per shot, in French (the mockups'
     * language) at each GUI scale of [scales]; then Global in English at GUI scale 2 (art direction 18.2, sheet 5).
     */
    private fun kit(scales: IntRange) = CaptureScript().apply {
        language("fr_fr")
        val pages = KitPages.all
        for (scale in scales) {
            guiScale(scale)
            for (start in pages.indices step 2) {
                val pair = pages.subList(start, minOf(start + 2, pages.size))
                screen { KitHostScreen(pair) }
                waitTicks(16)
                shot("kit_gui${scale}_%02d".format(start / 2 + 1))
            }
        }
        if (2 in scales) {
            language("en_us")
            guiScale(2)
            screen { KitHostScreen(KitPages.composed.filter { it.caption.startsWith("Global (") || it.caption.startsWith("Compose, 142") }) }
            waitTicks(16)
            shot("kit_english_gui2")
        }
    }

    /**
     * Task 6: the notification banner's states and its P-10 frames at GUI scale 2, 3 and 4; then, in a world, a real
     * notification sent by `/phonedemo` and read back from the client's store, and cleared.
     */
    private fun banner() = CaptureScript().apply {
        for (scale in 2..4) {
            guiScale(scale)
            screen { BannerTestScreen(BannerTestScreen.Mode.STATES) }
            waitTicks(20)
            shot("banner_states_gui$scale")
            screen { BannerTestScreen(BannerTestScreen.Mode.FRAMES) }
            waitTicks(10)
            shot("banner_frames_gui$scale")
        }
        enterWorld()
        guiScale(3)
        command("phonedemo notify @a 3")
        waitTicks(40)
        run { LnbPhone.LOGGER.info("Capture banner: after notify, client store has {}", LiveBannerScreen.summary()) }
        screen { LiveBannerScreen() }
        waitTicks(20)
        shot("banner_live_gui3")
        command("phonedemo clear @a")
        waitTicks(40)
        run { LnbPhone.LOGGER.info("Capture banner: after clear, client store has {}", LiveBannerScreen.summary()) }
        waitTicks(5)
        shot("banner_live_cleared_gui3")
    }

    private fun CaptureScript.command(command: String) = run {
        Minecraft.getInstance().player!!.connection.sendCommand(command)
    }
}
