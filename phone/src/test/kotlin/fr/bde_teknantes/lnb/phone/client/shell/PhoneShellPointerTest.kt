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

/** The mouse on the open phone: home icons, the home button, the app viewport, the wheel (2.3, 6, P-6, P-7). */
internal class PhoneShellPointerTest : ShellTestBase() {
    private val root = ScriptedScreen("root", log)
    private val second = ScriptedScreen("second", log)

    private fun registerFeed() = register(testApp("feed") { root }, testApp("other"))

    @Test
    fun `releasing a click on an icon opens its app (P-6)`() {
        registerFeed()
        openFully()
        host.sounds.clear()
        val (x, y) = iconCentre(0)
        press(x, y)
        assertEquals(0, shell.pressedApp)
        assertTrue(shell.pressedAppShown(shell.now()))
        assertTrue(host.navigator.isHome)
        release(x, y)
        assertSame(root, host.navigator.currentScreen)
        assertEquals(listOf(ShellSound.TAP), host.sounds)
    }

    @Test
    fun `the pressed look lasts while held, and at least 100 ms after a quick click`() {
        registerFeed()
        openFully()
        val (x, y) = iconCentre(1)
        press(x, y)
        tick(5)
        assertTrue(shell.pressedAppShown(shell.now()))
        release(x, y)
        // Held longer than 100 ms: the look ends with the release.
        assertFalse(shell.pressedAppShown(shell.now()))

        val (x2, y2) = iconCentre(1)
        clickHomeButton()
        press(x2, y2)
        val pressed = shell.now()
        release(x2, y2)
        assertTrue(shell.pressedAppShown(pressed + 1.99f))
        assertFalse(shell.pressedAppShown(pressed + 2f))
    }

    @Test
    fun `a click that leaves its icon before the release opens nothing`() {
        registerFeed()
        openFully()
        val (x, y) = iconCentre(0)
        // Released on the next icon, in an empty part of the grid, on the bezel, outside the device.
        val elsewhere = listOf(iconCentre(1), device(80.0, 120.0), device(2.0, 100.0), 10.0 to 10.0)
        for ((rx, ry) in elsewhere) {
            press(x, y)
            release(rx, ry)
            assertTrue(host.navigator.isHome, "released at ($rx, $ry)")
            assertEquals(State.OPEN, shell.state)
        }
    }

    @Test
    fun `right and middle clicks on an icon don't open it`() {
        registerFeed()
        openFully()
        val (x, y) = iconCentre(0)
        click(x, y, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        click(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        assertTrue(host.navigator.isHome)
        assertNull(shell.pressedApp)
    }

    @Test
    fun `an icon answers over its whole hover ring, the ring's edge pixels included`() {
        registerApps(30)
        openFully()
        // Icon 6 (column 1, row 1): ring at viewport (28..51, 30..53).
        for ((vx, vy) in listOf(28.0 to 30.0, 51.999 to 30.0, 28.0 to 53.999, 51.999 to 53.999)) {
            val (x, y) = device(Device.VIEWPORT_X + vx, Device.VIEWPORT_Y + vy)
            press(x, y)
            assertEquals(6, shell.pressedApp, "viewport ($vx, $vy)")
            release(x, y)
            assertEquals(host.apps.apps[6], host.navigator.currentApp)
            clickHomeButton()
        }
    }

    @Test
    fun `the home button goes home at once, and only if released on it (P-7)`() {
        registerFeed()
        openFully()
        clickIcon(0)
        root.handle.push(second)
        tick(3)
        host.sounds.clear()
        // Pressed, then dragged off before the release: nothing.
        val (hx, hy) = device(70.0, 218.0)
        press(hx, hy)
        assertTrue(shell.homeButtonPressedShown(shell.now()))
        release(hx + 20, hy)
        assertSame(second, host.navigator.currentScreen)
        assertEquals(emptyList<ShellSound>(), host.sounds)
        // A real click.
        clickHomeButton()
        assertTrue(host.navigator.isHome)
        assertNull(shell.screenSlide)
        assertEquals(listOf(ShellSound.TAP), host.sounds)
        assertEquals(listOf("second:closed", "root:closed"), log.filter { it.endsWith("closed") })
    }

    @Test
    fun `the home button is the 12 x 12 square at device (64, 212)`() {
        // Apps whose screens are new at each opening.
        register(testApp("feed"))
        for ((dx, dy) in listOf(64.0 to 212.0, 75.999 to 212.0, 64.0 to 223.999, 75.999 to 223.999)) {
            openFully()
            clickIcon(0)
            device(dx, dy).let { (x, y) -> click(x, y) }
            assertTrue(host.navigator.isHome, "device-local ($dx, $dy)")
            shell.close()
            closeFully()
        }
        for ((dx, dy) in listOf(63.999 to 218.0, 76.0 to 218.0, 70.0 to 211.999, 70.0 to 224.0)) {
            openFully()
            clickIcon(0)
            device(dx, dy).let { (x, y) -> click(x, y) }
            assertFalse(host.navigator.isHome, "device-local ($dx, $dy)")
            shell.close()
            closeFully()
        }
    }

    @Test
    fun `the home button's pressed look lasts at least 100 ms`() {
        registerFeed()
        openFully()
        val (x, y) = device(70.0, 218.0)
        press(x, y)
        val pressed = shell.now()
        release(x, y)
        assertTrue(shell.homeButtonPressedShown(pressed + 1.99f))
        assertFalse(shell.homeButtonPressedShown(pressed + 2f))
    }

    @Test
    fun `clicks in an app's viewport reach it in viewport dp`() {
        registerFeed()
        openFully()
        clickIcon(0)
        tick(3)
        log.clear()
        // Viewport-local GUI px (10.25, 20.5) is (20.5, 41) dp.
        val (x, y) = device(Device.VIEWPORT_X + 10.25, Device.VIEWPORT_Y + 20.5)
        click(x, y, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        assertEquals(listOf("root:pressed 20.5,41.0,1", "root:released 20.5,41.0,1"), log)
        // The viewport's last pixel, and the status bar just above it (not the app's).
        log.clear()
        device(Device.VIEWPORT_X + 127.5, Device.VIEWPORT_Y + 184.5).let { (px, py) -> click(px, py) }
        assertEquals(listOf("root:pressed 255.0,369.0,0", "root:released 255.0,369.0,0"), log)
        log.clear()
        device(Device.VIEWPORT_X + 10.0, Device.VIEWPORT_Y - 0.5).let { (px, py) -> click(px, py) }
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `a drag that starts in the viewport follows the pointer out of it`() {
        registerFeed()
        openFully()
        clickIcon(0)
        tick(3)
        log.clear()
        val (x, y) = device(Device.VIEWPORT_X + 10.0, Device.VIEWPORT_Y + 10.0)
        press(x, y)
        shell.mouseDragged(x, y - 50, 0, 0.0, -50.0)
        release(x, y - 50)
        assertEquals(
            listOf("root:pressed 20.0,20.0,0", "root:dragged 20.0,-80.0,0,0.0,-100.0", "root:released 20.0,-80.0,0"),
            log,
        )
        // A drag that started on the bezel never reaches the app.
        log.clear()
        device(2.0, 100.0).let { (bx, by) ->
            press(bx, by)
            shell.mouseDragged(x, y, 0, 10.0, 0.0)
            release(x, y)
        }
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `a button held across a screen change isn't released on the new screen`() {
        registerFeed()
        openFully()
        clickIcon(0)
        tick(3)
        val (x, y) = device(Device.VIEWPORT_X + 10.0, Device.VIEWPORT_Y + 10.0)
        press(x, y)
        root.handle.push(second)
        log.clear()
        shell.mouseDragged(x, y, 0, 1.0, 0.0)
        release(x, y)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `the wheel turns home pages over the viewport only, and the page is kept while the phone is away`() {
        registerApps(65)
        openFully()
        val (x, y) = device(70.0, 100.0)
        shell.mouseScrolled(x, y, 0.0, -1.0)
        assertEquals(1, shell.page())
        shell.mouseScrolled(x, y, 0.0, -1.0)
        shell.mouseScrolled(x, y, 0.0, -1.0)
        assertEquals(2, shell.page())
        // Over the bezel or the status bar: nothing.
        device(2.0, 100.0).let { (bx, by) -> shell.mouseScrolled(bx, by, 0.0, 1.0) }
        device(70.0, 15.0).let { (bx, by) -> shell.mouseScrolled(bx, by, 0.0, 1.0) }
        assertEquals(2, shell.page())
        // The icons on page 3: the first is app 60.
        clickIcon(0)
        assertEquals(host.apps.apps[60], host.navigator.currentApp)
        clickHomeButton()
        host.pressKey(ESC)
        closeFully()
        openFully()
        assertEquals(2, shell.page())
        shell.mouseScrolled(x, y, 0.0, 1.0)
        assertEquals(1, shell.page())
    }

    @Test
    fun `with one page or none the wheel and the grid do nothing`() {
        registerApps(0)
        openFully()
        val (x, y) = device(70.0, 100.0)
        shell.mouseScrolled(x, y, 0.0, -1.0)
        assertEquals(0, shell.page())
        clickIcon(0)
        assertTrue(host.navigator.isHome)
        assertEquals(State.OPEN, shell.state)
    }

    @Test
    fun `in an app the wheel scrolls the app`() {
        registerFeed()
        openFully()
        clickIcon(0)
        tick(3)
        log.clear()
        device(70.0, 100.0).let { (x, y) -> shell.mouseScrolled(x, y, 0.0, -2.0) }
        assertEquals(listOf("root:scrolled -2.0"), log)
        assertEquals(0, shell.page())
    }

    @Test
    fun `at GUI scale 1 the home screen and the home button work, the app gets no input (P-15)`() {
        registerFeed()
        host.guiScale = 1.0
        openFully()
        clickIcon(0)
        assertSame(root, host.navigator.currentScreen)
        log.clear()
        device(70.0, 100.0).let { (x, y) ->
            click(x, y)
            shell.mouseScrolled(x, y, 0.0, 1.0)
        }
        tick()
        assertEquals(emptyList<String>(), log)
        clickHomeButton()
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `the pointer state is forgotten when the phone closes`() {
        registerFeed()
        openFully()
        iconCentre(0).let { (x, y) -> press(x, y) }
        host.pressKey(ESC)
        assertNull(shell.pressedApp)
        closeFully()
        openFully()
        // The release of that old press opens nothing.
        iconCentre(0).let { (x, y) -> shell.mouseReleased(x, y, 0) }
        assertTrue(host.navigator.isHome)
    }
}
