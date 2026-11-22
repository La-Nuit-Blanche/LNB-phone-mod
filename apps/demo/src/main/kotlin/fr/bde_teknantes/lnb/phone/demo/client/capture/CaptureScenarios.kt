package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.demo.client.kit.KitHostScreen
import fr.bde_teknantes.lnb.phone.demo.client.kit.KitPages
import fr.bde_teknantes.lnb.phone.demo.client.smooth.SmoothTestScreen

/**
 * The capture scripts, by name (the value of `-Dlnbphone.capture`). Later tasks add theirs here: the phone shell, the
 * UI kit, the notification banner.
 */
object CaptureScenarios {
    private val scenarios: Map<String, () -> CaptureScript> = linkedMapOf(
        "smooth" to ::smooth,
        "kit" to { kit(2..4) },
        "kit5" to { kit(5..5) },
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
     * `kit5` needs a window of 1600 x 1200 (`-PcaptureWidth=1600 -PcaptureHeight=1200`).
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
}
