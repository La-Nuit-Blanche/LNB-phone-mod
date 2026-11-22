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
