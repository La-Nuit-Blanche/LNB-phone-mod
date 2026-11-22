package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The navigator's callbacks and other screens arriving in each state of the shell. */
internal class PhoneShellNavigationTest : ShellTestBase() {
    private val root = ScriptedScreen("root", log)
    private val second = ScriptedScreen("second", log)

    private fun registerApp() = register(testApp("feed") { root }, testApp("other"))

    private fun openApp() {
        clickIcon(0)
        tick(3)
        assertSame(root, host.navigator.currentScreen)
    }

    // ---- Viewport slides ----

    @Test
    fun `opening an app from home slides it in over the home screen for 150 ms`() {
        registerApp()
        openFully()
        clickIcon(0)
        assertTrue(shell.screenSlide!!.forward)
        assertNull(shell.slideOutgoing)
        assertEquals(listOf(ShellSound.OPEN, ShellSound.TAP), host.sounds)
        tick(2)
        assertNotNull(shell.screenSlide)
        tick()
        assertNull(shell.screenSlide)
    }

    @Test
    fun `push slides forward over the screen below, back slides the top one away`() {
        registerApp()
        openFully()
        openApp()
        root.handle.push(second)
        assertTrue(shell.screenSlide!!.forward)
        assertSame(root, shell.slideOutgoing)
        tick(3)
        host.pressKey(BACKSPACE)
        assertSame(root, host.navigator.currentScreen)
        assertEquals(false, shell.screenSlide!!.forward)
        assertSame(second, shell.slideOutgoing)
    }

    @Test
    fun `the home button shows home at once`() {
        registerApp()
        openFully()
        openApp()
        root.handle.push(second)
        clickHomeButton()
        assertTrue(host.navigator.isHome)
        assertNull(shell.screenSlide)
        assertNull(shell.slideOutgoing)
    }

    @Test
    fun `a transition while the phone is away animates nothing, now or at the next opening`() {
        registerApp()
        assertTrue(host.navigator.openApp(host.apps.apps[0]))
        assertNull(shell.screenSlide)
        openFully()
        assertNull(shell.screenSlide)
        assertSame(root, host.navigator.currentScreen)
    }

    // ---- An app puts the phone away (Phone.close) ----

    @Test
    fun `an app closing the open phone puts it away on the next frame, keeping its screen`() {
        registerApp()
        openFully()
        openApp()
        root.handle.close()
        // Not from inside the navigator's callback.
        assertEquals(State.OPEN, shell.state)
        host.runDeferred()
        assertEquals(State.CLOSING, shell.state)
        assertFalse(phoneScreenShown)
        closeFully()
        openFully()
        assertSame(root, host.navigator.currentScreen)
    }

    @Test
    fun `an app closing the phone while it rises turns it around without ever opening its screen`() {
        registerApp()
        openFully()
        openApp()
        host.pressKey(ESC)
        closeFully()
        // Reopened on the app, which puts the phone away from its first tick.
        root.onTick = { root.handle.close() }
        pressPhoneKeyInGame()
        tick()
        root.onTick = {}
        assertFalse(host.navigator.isOpen)
        val here = pose!!
        host.runDeferred()
        assertEquals(State.CLOSING, shell.state)
        assertEquals(here, pose)
        tick(10)
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.screensShown.drop(2).any { host.isPhoneScreen(it) })
    }

    @Test
    fun `an app closing the phone from onShown as the phone comes out`() {
        registerApp()
        openFully()
        openApp()
        host.pressKey(ESC)
        closeFully()
        root.onShownAction = { root.handle.close() }
        pressPhoneKeyInGame()
        host.runDeferred()
        assertEquals(State.CLOSING, shell.state)
        tick(10)
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
    }

    @Test
    fun `an app's close that arrives after the player reopened the phone is ignored`() {
        registerApp()
        openFully()
        openApp()
        root.handle.close()
        // The player puts the phone away and brings it back before the deferred close runs.
        host.pressKey(ESC)
        tick(CLOSE_TICKS)
        pressPhoneKeyInGame()
        host.runDeferred()
        assertEquals(State.OPENING, shell.state)
    }

    // ---- An app fails ----

    @Test
    fun `an app failing during a slide sends the phone home at once, still open`() {
        registerApp()
        openFully()
        openApp()
        second.onShownAction = { throw IllegalStateException("broken screen") }
        root.handle.push(second)
        assertTrue(host.navigator.isHome)
        assertNull(shell.screenSlide)
        assertEquals(State.OPEN, shell.state)
        assertTrue(phoneScreenShown)
    }

    @Test
    fun `an app failing as the phone rises leaves it rising to the home screen`() {
        registerApp()
        openFully()
        openApp()
        host.pressKey(ESC)
        closeFully()
        root.onShownAction = { throw IllegalStateException("broken screen") }
        pressPhoneKeyInGame()
        assertTrue(host.navigator.isHome)
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        assertEquals(State.OPEN, shell.state)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `an app failing in its tick while open sends the phone home`() {
        registerApp()
        openFully()
        openApp()
        root.onTick = { throw IllegalStateException("broken tick") }
        tick()
        assertTrue(host.navigator.isHome)
        assertEquals(State.OPEN, shell.state)
    }

    // ---- Disconnection ----

    @Test
    fun `disconnecting while open forgets everything, the next session starts closed on the first home page`() {
        registerApps(40)
        openFully()
        // Page 2, then into its first app.
        device(70.0, 100.0).let { (x, y) -> shell.mouseScrolled(x, y, 0.0, -1.0) }
        assertEquals(1, shell.page())
        clickIcon(0)
        assertEquals(host.apps.apps[30], host.navigator.currentApp)

        // As LnbPhoneClient does on logging out, then the game shows the title screen.
        host.inWorld = false
        host.navigator.reset()
        shell.reset()
        host.openOtherScreen("title")
        host.runDeferred()
        tick()
        assertEquals(State.CLOSED, shell.state)
        assertNull(shell.screenSlide)
        assertEquals(0, shell.page())

        host.setScreen(null)
        host.inWorld = true
        openFully()
        assertTrue(host.navigator.isHome)
        assertEquals(0, shell.page())
    }

    @Test
    fun `the player leaving the world mid-slide puts the phone away at once`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick()
        host.inWorld = false
        tick()
        assertEquals(State.CLOSED, shell.state)
        assertNull(pose)
    }

    // ---- Other screens ----

    @Test
    fun `opened from another screen, the phone goes back to it when put away (P-4)`() {
        registerApps(1)
        val inventory = host.openOtherScreen("inventory")
        shell.open(returnTo = inventory)
        // The inventory closes and the phone slides up over the game.
        assertNull(host.screen)
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        assertTrue(phoneScreenShown)
        host.pressKey(ESC)
        assertSame(inventory, host.screen)
        assertEquals(State.CLOSING, shell.state)
        closeFully()
        assertSame(inventory, host.screen)
    }

    @Test
    fun `opened from another screen and put away while still rising, that screen comes back at once`() {
        registerApps(1)
        val inventory = host.openOtherScreen("inventory")
        shell.open(returnTo = inventory)
        tick(2)
        pressPhoneKeyInGame()
        assertSame(inventory, host.screen)
        assertEquals(State.CLOSING, shell.state)
        assertFalse(host.screensShown.any { host.isPhoneScreen(it) })
    }

    @Test
    fun `another screen opening during the rise makes the phone stand down when the slide ends`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(2)
        val pause = host.openOtherScreen("pause")
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
        assertSame(pause, host.screen)
        assertFalse(host.screensShown.any { host.isPhoneScreen(it) })
    }

    @Test
    fun `another screen replacing the open phone puts it away at once, without a slide`() {
        registerApp()
        openFully()
        openApp()
        host.sounds.clear()
        val death = host.openOtherScreen("death")
        assertEquals(State.CLOSED, shell.state)
        assertNull(pose)
        assertFalse(host.navigator.isOpen)
        assertSame(death, host.screen)
        assertEquals(emptyList<ShellSound>(), host.sounds)
        // The app is kept for the next opening.
        host.setScreen(null)
        openFully()
        assertSame(root, host.navigator.currentScreen)
    }

    @Test
    fun `a screen that replaces the phone opened from the inventory isn't followed by the inventory later`() {
        registerApps(1)
        val inventory = host.openOtherScreen("inventory")
        shell.open(returnTo = inventory)
        tick(OPEN_TICKS)
        host.openOtherScreen("death")
        host.setScreen(null)
        openFully()
        host.pressKey(ESC)
        assertNull(host.screen)
        assertEquals(1, host.screensShown.count { it === inventory }, "${host.screensShown}")
    }

    // ---- Badges ----

    @Test
    fun `badge counts are read every tick and a rising count starts the pulse`() {
        registerApps(2)
        val counts = HashMap<String, Int>()
        val saved = PhoneBadges.source
        PhoneBadges.source = BadgeSource { counts[it.path] ?: 0 }
        try {
            val first = host.apps.apps[0].id
            tick()
            assertEquals(0, shell.counts[first])
            assertNull(shell.pulseStart[first])
            counts["app0"] = 2
            tick()
            assertEquals(2, shell.counts[first])
            val pulse = shell.pulseStart[first]
            assertEquals(shell.ticks, pulse)
            // The same count, or fewer, doesn't pulse again.
            tick(3)
            counts["app0"] = 1
            tick()
            assertEquals(pulse, shell.pulseStart[first])
            // One more does.
            counts["app0"] = 4
            tick()
            assertEquals(shell.ticks, shell.pulseStart[first])
            // A source that throws counts as none; negative counts as none.
            PhoneBadges.source = BadgeSource { if (it.path == "app0") throw IllegalStateException() else -5 }
            tick()
            assertEquals(0, shell.counts[first])
            assertEquals(0, shell.counts[host.apps.apps[1].id])
        } finally {
            PhoneBadges.source = saved
        }
    }
}
