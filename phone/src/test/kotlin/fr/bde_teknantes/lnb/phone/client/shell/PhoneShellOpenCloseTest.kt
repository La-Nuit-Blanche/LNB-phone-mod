package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/** Bringing the phone out and putting it away (art direction 2.3, 14, flows P-1 to P-3 and P-7.8 to P-7.9). */
internal class PhoneShellOpenCloseTest : ShellTestBase() {
    @Test
    fun `the phone key slides the device up from the bottom edge in 250 ms, then its screen opens`() {
        registerApps(3)
        pressPhoneKeyInGame()
        assertEquals(State.OPENING, shell.state)
        assertTrue(host.navigator.isOpen)
        assertEquals(listOf(ShellSound.OPEN), host.sounds)
        // The game keeps the mouse while the device moves (P-1.2: no cursor).
        assertNull(host.screen)
        assertEquals(DevicePose(334, 270, 0f), pose)

        // Ease-out over 5 ticks: eased 0.36, 0.64, 0.84, 0.96 of the 234 px from y 270 to y 36.
        val ys = ArrayList<Int>()
        repeat(4) {
            tick()
            ys += pose!!.y
            assertEquals(State.OPENING, shell.state)
            assertFalse(phoneScreenShown)
        }
        assertEquals(listOf(186, 120, 73, 45), ys)
        tick()
        assertEquals(State.OPEN, shell.state)
        assertTrue(phoneScreenShown)
        assertEquals(DevicePose(334, 36, 0f), pose)
    }

    @Test
    fun `frames between ticks follow the partial tick`() {
        registerApps(1)
        pressPhoneKeyInGame()
        val start = shell.ticks.toFloat()
        // Half the time: three quarters of the way, y 94.5 shown as 94.
        assertEquals(DevicePose(334, 94, 0f), shell.currentPose(start + 2.5f))
        // Before the slide started (a clock read late) the device is still at the edge; long after, at its place.
        assertEquals(DevicePose(334, 270, 0f), shell.currentPose(start - 3f))
        assertEquals(DevicePose(334, 36, 0f), shell.currentPose(start + 1000f))
    }

    @Test
    fun `the flows' mid-slide frames, held as the screenshot harness holds them`() {
        registerApps(1)
        shell.heldProgress = 0.5f
        pressPhoneKeyInGame()
        tick(20)
        // P-1.2. Held, the slide never ends.
        assertEquals(DevicePose(334, 153, 0f), pose)
        assertEquals(State.OPENING, shell.state)
        shell.heldProgress = null
        tick()
        assertEquals(State.OPEN, shell.state)

        // P-2.3 and P-7.8: down from (334, 36).
        shell.heldProgress = 0.5f
        host.pressKey(ESC)
        assertEquals(DevicePose(334, 153, 0f), pose)
    }

    @Test
    fun `centred, the device rises to the middle while the scrim fades in, and back (P-3)`() {
        registerApps(1)
        host.settings.update { it.copy(placement = Placement.CENTRE) }
        shell.heldProgress = 0.5f
        pressPhoneKeyInGame()
        val rising = pose!!
        assertEquals(DevicePose(170, 146, 0.5f), rising)
        assertEquals(0x26000000, PhoneLayout.scrimColor(rising.scrim))
        shell.heldProgress = null
        tick(OPEN_TICKS)
        assertEquals(DevicePose(170, 21, 1f), pose)
        assertEquals(0x4D000000, PhoneLayout.scrimColor(pose!!.scrim))

        // P-3.3: a click on the dimmed area closes it.
        click(60.0, 135.0)
        assertEquals(State.CLOSING, shell.state)
        shell.heldProgress = 0.5f
        assertEquals(DevicePose(170, 146, 0.5f), pose)
    }

    @Test
    fun `Esc, the phone key and a click outside all put the phone away in 200 ms`() {
        registerApps(1)
        val ways = listOf<Pair<String, () -> Unit>>(
            "Esc" to { host.pressKey(ESC) },
            "phone key" to { host.pressKey(UP) },
            "click outside" to { click(120.0, 100.0) },
            "right click outside" to { click(120.0, 100.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT) },
        )
        for ((name, way) in ways) {
            openFully()
            host.sounds.clear()
            way()
            assertEquals(State.CLOSING, shell.state, name)
            assertFalse(host.navigator.isOpen, name)
            assertNull(host.screen, name)
            assertEquals(listOf(ShellSound.CLOSE), host.sounds, name)
            // Ease-in: 0, 1/16, 1/4, 9/16 of the way down, then away.
            val ys = ArrayList<Int>()
            repeat(CLOSE_TICKS) {
                ys += pose!!.y
                tick()
            }
            assertEquals(listOf(36, 51, 94, 168), ys, name)
            assertEquals(State.CLOSED, shell.state, name)
            assertNull(pose, name)
        }
    }

    @Test
    fun `the key that closes the phone doesn't reach the game as well`() {
        registerApps(1)
        openFully()
        host.pressKey(UP)
        tick(20)
        // Not reopened by the same press, and Esc never opened the pause menu.
        assertEquals(State.CLOSED, shell.state)
        openFully()
        host.pressKey(ESC)
        tick(20)
        assertEquals(State.CLOSED, shell.state)
        assertTrue(host.screensShown.none { (it as? TestScreen)?.name == FakeShellHost.PAUSE })
    }

    @Test
    fun `clicks on the bezel and the status bar do nothing`() {
        registerApps(1)
        openFully()
        val bezel = listOf(
            0.0 to 0.0, 2.0 to 100.0, 137.0 to 100.0, 139.999 to 227.999, 70.0 to 5.0, 84.0 to 5.0, 20.0 to 215.0,
            5.0 to 50.0, 134.0 to 50.0, 63.0 to 218.0, 76.0 to 218.0,
            // The status bar, screen-local (70, 5).
            76.0 to 17.0,
        )
        for ((dx, dy) in bezel) {
            device(dx, dy).let { (x, y) -> click(x, y) }
            assertEquals(State.OPEN, shell.state, "device-local ($dx, $dy)")
        }
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `at the device's edge the last pixel inside is the bezel, the first outside closes`() {
        registerApps(1)
        for ((dx, dy) in listOf(-0.001 to 100.0, 140.0 to 100.0, 70.0 to -0.001, 70.0 to 228.0)) {
            openFully()
            device(dx, dy).let { (x, y) -> click(x, y) }
            assertEquals(State.CLOSING, shell.state, "device-local ($dx, $dy)")
            closeFully()
        }
    }

    @Test
    fun `a phone key bound to a mouse button closes the phone wherever it is clicked`() {
        registerApps(1)
        host.phoneMouseButton = GLFW.GLFW_MOUSE_BUTTON_MIDDLE
        openFully()
        iconCentre(0).let { (x, y) -> click(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) }
        assertEquals(State.CLOSING, shell.state)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `the phone key during the rise turns the device around where it is`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(2)
        host.partialTick = 0.3f
        val before = pose!!
        shell.toggle()
        assertEquals(State.CLOSING, shell.state)
        assertEquals(before, pose)
        assertFalse(host.navigator.isOpen)
        // Then down to the edge without going up first, 200 ms from the turn: the slide started 0.3 tick into a tick,
        // so the state follows at the fifth tick boundary.
        host.partialTick = 0f
        var last = before.y
        repeat(CLOSE_TICKS + 1) {
            tick()
            pose?.let {
                assertTrue(it.y >= last, "went up to ${it.y}")
                last = it.y
            }
        }
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.screensShown.any { host.isPhoneScreen(it) })
    }

    @Test
    fun `the phone key during the fall brings the device back up from where it is`() {
        registerApps(1)
        openFully()
        host.pressKey(UP)
        tick(2)
        host.partialTick = 0.6f
        val before = pose!!
        assertTrue(before.y in 37..269)
        shell.toggle()
        assertEquals(State.OPENING, shell.state)
        assertEquals(before, pose)
        assertTrue(host.navigator.isOpen)
        host.partialTick = 0f
        tick(OPEN_TICKS)
        // Turned 0.6 tick into a tick: 250 ms later is 0.6 tick past this boundary, the device 1 px short of its place.
        assertEquals(DevicePose(334, 37, 0f), pose)
        assertEquals(DevicePose(334, 36, 0f), shell.currentPose(shell.ticks + 0.6f))
        assertEquals(State.OPENING, shell.state)
        tick()
        assertEquals(State.OPEN, shell.state)
        assertTrue(phoneScreenShown)
    }

    @Test
    fun `the phone key pressed again and again mid-way through the game's input`() {
        registerApps(1)
        pressPhoneKeyInGame()
        repeat(7) {
            tick()
            pressPhoneKeyInGame()
        }
        // Eight presses: the phone is on its way down.
        assertEquals(State.CLOSING, shell.state)
        tick(10)
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
        // Two presses in one tick: open, then close again.
        host.pressKey(UP)
        host.pressKey(UP)
        tick()
        assertEquals(State.CLOSING, shell.state)
    }

    @Test
    fun `a lag spike or a jump in time ends the slide at its place`() {
        registerApps(1)
        pressPhoneKeyInGame()
        host.partialTick = 0.99f
        val late = shell.currentPose(shell.ticks + 500f)
        assertEquals(DevicePose(334, 36, 0f), late)
        host.partialTick = 0f
        tick(50)
        assertEquals(State.OPEN, shell.state)
    }

    @Test
    fun `the device follows the window when it is resized`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(2)
        host.guiWidth = 640
        host.guiHeight = 360
        tick(3)
        assertEquals(DevicePose(494, 126, 0f), pose)
        host.guiWidth = 320
        host.guiHeight = 240
        assertEquals(DevicePose(174, 6, 0f), pose)
    }

    @Test
    fun `the phone reopens on the screen it was put away on (P-7)`() {
        val root = ScriptedScreen("root", log)
        register(testApp("feed") { root })
        openFully()
        clickIcon(0)
        tick(3)
        assertSame(root, host.navigator.currentScreen)
        host.pressKey(ESC)
        closeFully()
        openFully()
        assertSame(root, host.navigator.currentScreen)
        assertEquals(listOf("root:shown", "root:key $ESC", "root:hidden", "root:shown"), log)
    }

    @Test
    fun `out of a world the phone key does nothing`() {
        registerApps(1)
        host.inWorld = false
        pressPhoneKeyInGame()
        assertEquals(State.CLOSED, shell.state)
        shell.open()
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
    }

    @Test
    fun `with another screen open the phone key is that screen's, not the phone's`() {
        registerApps(1)
        host.openOtherScreen("chat")
        pressPhoneKeyInGame()
        assertEquals(State.CLOSED, shell.state)
    }
}
