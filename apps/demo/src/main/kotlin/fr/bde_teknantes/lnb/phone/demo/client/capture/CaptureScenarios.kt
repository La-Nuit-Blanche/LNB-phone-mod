package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.demo.client.smooth.SmoothTestScreen

/**
 * The capture scripts, by name (the value of `-Dlnbphone.capture`). Later tasks add theirs here: the phone shell, the
 * UI kit, the notification banner.
 */
object CaptureScenarios {
    private val scenarios: Map<String, () -> CaptureScript> = linkedMapOf(
        "smooth" to ::smooth,
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
}
