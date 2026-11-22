package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
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
        "banner" to ::banner,
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
