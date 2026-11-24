package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * `shell`: the phone shell, in a world, step by step through art direction 17's phone flows. Run with the sample apps
 * so the home screen looks like the reference sheet: `-PdemoApps=6` for [shell], `-PdemoApps=35` for [shellPages].
 *
 * Mid-slide frames hold the animation at half its eased course, which is where the art direction measures them
 * ("device mid-slide at (334, 153)", "Feed screen at x +128 dp").
 */
internal object ShellScenarios {
    fun shell() = CaptureScript().apply {
        enterWorld()
        reset()
        for (scale in 2..4) flows(scale)
        centred(4)
        centred(2)
        badges(3)
        clickOutside(3)
        guiScaleOne()
        run { CaptureHooks.setCentred(false) }
    }

    /** More apps than a page holds: the page dots, and the wheel turning the page. */
    fun shellPages() = CaptureScript().apply {
        enterWorld()
        reset()
        guiScale(3)
        waitTicks(5)
        openPhone()
        mouseTo { device(70, 120) }
        waitTicks(2)
        shot("pages_first_gui3")
        scroll(-1.0)
        mouseTo { icon(1) }
        waitTicks(2)
        shot("pages_second_gui3")
        closePhone()
    }

    /**
     * The flows' W frames, in their own 480 x 270 window (run with `-PcaptureSize=1920x1080`): the positions the art
     * direction gives, mid-slide frames included.
     */
    fun shellWindow() = CaptureScript().apply {
        enterWorld()
        reset()
        guiScale(4)
        waitTicks(10)
        hold(0.5f)
        key(GLFW.GLFW_KEY_UP)
        waitTicks(3)
        shot("w_p1_2_opening")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        waitTicks(3)
        shot("w_p1_3_open")
        hold(0.5f)
        run { CaptureHooks.setCentred(true) }
        waitTicks(2)
        shot("w_p12_4_glide_to_centre")
        hold(null)
        waitTicks(8)
        shot("w_p12_5_centre")
        closePhone()
        hold(0.5f)
        key(GLFW.GLFW_KEY_UP)
        waitTicks(3)
        shot("w_p3_2_opening_centre")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        hold(0.5f)
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(3)
        shot("w_p3_4_closing_centre")
        hold(null)
        waitUntil { CaptureHooks.isClosed }
        run { CaptureHooks.setCentred(false) }
    }

    // P-1, P-6, P-7, P-2 at one GUI scale.
    private fun CaptureScript.flows(scale: Int) {
        guiScale(scale)
        waitTicks(10)
        shot("p1_1_closed_gui$scale")
        hold(0.5f)
        key(GLFW.GLFW_KEY_UP)
        waitTicks(3)
        shot("p1_2_opening_gui$scale")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        mouseTo { window().let { (w, h) -> w / 2 to h / 2 } }
        waitTicks(3)
        shot("p1_3_open_gui$scale")

        mouseTo { icon(0) }
        waitTicks(2)
        shot("p6_2_hover_gui$scale")
        mouseDown()
        waitTicks(2)
        shot("p6_3_pressed_gui$scale")
        hold(0.5f)
        mouseUp()
        waitTicks(3)
        shot("p6_4_opening_app_gui$scale")
        hold(null)
        waitTicks(6)
        shot("p6_5_app_gui$scale")

        // The demo screen opens another one on each click: one forward, then back with Backspace, held half way.
        mouseTo { device(70, 120) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        hold(0.5f)
        key(GLFW.GLFW_KEY_BACKSPACE)
        waitTicks(3)
        shot("p7_3_back_gui$scale")
        hold(null)
        waitTicks(5)

        mouseTo { device(70, 218) }
        waitTicks(2)
        shot("p7_5_home_button_hover_gui$scale")
        mouseDown()
        waitTicks(1)
        shot("p7_6_home_button_pressed_gui$scale")
        mouseUp()
        waitTicks(4)
        shot("p7_7_home_gui$scale")

        hold(0.5f)
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(3)
        shot("p2_3_closing_gui$scale")
        hold(null)
        waitUntil { CaptureHooks.isClosed }
        waitTicks(2)
        shot("p2_4_closed_gui$scale")
    }

    // P-3 and P-12: the centred placement, its scrim, and the glide back to the corner.
    private fun CaptureScript.centred(scale: Int) {
        guiScale(scale)
        waitTicks(5)
        run { CaptureHooks.setCentred(true) }
        hold(0.5f)
        key(GLFW.GLFW_KEY_UP)
        waitTicks(3)
        shot("p3_2_opening_centre_gui$scale")
        hold(null)
        waitUntil { CaptureHooks.isOpen }
        mouseTo { 60 to window().second / 2 }
        waitTicks(3)
        shot("p3_3_open_centre_gui$scale")
        hold(0.5f)
        run { CaptureHooks.setCentred(false) }
        waitTicks(2)
        shot("p12_7_glide_to_corner_gui$scale")
        hold(null)
        waitTicks(8)
        shot("p12_8_corner_gui$scale")
        closePhone()
    }

    // P-8.5/6 stand-in: badges (one digit and 9+), the LED lit, 3 signal bars from a faked 200 ms latency.
    private fun CaptureScript.badges(scale: Int) {
        guiScale(scale)
        run {
            // `sample_feed` is art direction 7.4's Feed, the first sample app (the reference sheet's icon), not the
            // demo's "Post sample" (`feed_sample`).
            CaptureHooks.fakeBadges { id -> if (id.path == "sample_feed") 3 else if (id.path == "hello") 12 else 0 }
            CaptureHooks.holdLedOn(true)
            CaptureHooks.fakeLatency(200)
        }
        // Past the badges' 1.5 s pulse.
        waitTicks(35)
        openPhone()
        mouseTo { icon(0) }
        waitTicks(2)
        shot("p8_6_badges_hover_gui$scale")
        closePhone()
        run {
            CaptureHooks.fakeBadges(null)
            CaptureHooks.holdLedOn(false)
            CaptureHooks.fakeLatency(null)
        }
    }

    // P-2.2: a click on the world closes the phone.
    private fun CaptureScript.clickOutside(scale: Int) {
        guiScale(scale)
        openPhone()
        mouseTo { 20 to 20 }
        mouseDown()
        mouseUp()
        waitUntil { CaptureHooks.isClosed }
        waitTicks(2)
        shot("p2_2_click_outside_closed_gui$scale")
    }

    // P-15: GUI scale 1, in English, then the message in French.
    private fun CaptureScript.guiScaleOne() {
        guiScale(1)
        waitTicks(5)
        openPhone()
        mouseTo { icon(0) }
        waitTicks(2)
        shot("p15_1_hover_gui1")
        mouseDown()
        mouseUp()
        waitTicks(6)
        shot("p15_2_app_gui1")
        mouseTo { device(70, 218) }
        mouseDown()
        waitTicks(1)
        shot("p15_3_home_button_pressed_gui1")
        mouseUp()
        closePhone()
        language("fr_fr")
        openPhone()
        mouseTo { icon(0) }
        mouseDown()
        mouseUp()
        waitTicks(6)
        shot("p15_2_app_gui1_fr")
        closePhone()
        language("en_us")
    }

    private fun CaptureScript.reset() = run {
        CaptureHooks.setCentred(false)
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

    private fun CaptureScript.hold(progress: Float?) = run { CaptureHooks.holdAnimations(progress) }

    private fun window(): Pair<Int, Int> = Minecraft.getInstance().window.let { it.guiScaledWidth to it.guiScaledHeight }

    /** A device-local point in window GUI px. */
    private fun device(dx: Int, dy: Int): Pair<Int, Int> {
        val position = CaptureHooks.devicePosition() ?: error("The phone is away")
        return position[0] + dx to position[1] + dy
    }

    /** The middle of home icon [slot]: viewport (6 + 24c + 10, 8 + 24r + 10). */
    private fun icon(slot: Int): Pair<Int, Int> {
        val (vx, vy) = CaptureHooks.viewportOffset.let { it[0] to it[1] }
        return device(vx + 6 + 24 * (slot % 5) + 10, vy + 8 + 24 * (slot / 5) + 10)
    }
}
