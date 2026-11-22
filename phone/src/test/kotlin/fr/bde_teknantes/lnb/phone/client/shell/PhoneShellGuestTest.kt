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

/**
 * The phone as a guest in Minecraft's input and screen system: held keys and their repeats, screens that open over
 * the rising phone, dialogs that come back to the phone's screen, mods that refuse or replace screens.
 */
internal class PhoneShellGuestTest : ShellTestBase() {
    private val root = ScriptedScreen("root", log)
    private val deep = ScriptedScreen("deep", log)
    private val deeper = ScriptedScreen("deeper", log)

    private fun openOnRoot() {
        register(testApp("app") { root })
        openFully()
        clickIcon(0)
        tick(3)
        log.clear()
    }

    // ---- Held keys ----

    @Test
    fun `holding the phone key in game opens the phone once and never closes it again until released`() {
        registerApps(1)
        host.holdKey(UP, 20)
        assertEquals(State.OPENING, shell.state)
        tick(OPEN_TICKS)
        assertEquals(State.OPEN, shell.state)
        // Still held as its screen opens: about 30 repeats a second reach the screen, none of them for the phone.
        host.repeatKey(UP, 30)
        assertEquals(State.OPEN, shell.state)
        assertEquals(listOf(ShellSound.OPEN), host.sounds)
        // Released, the next press closes it.
        host.releaseKey(UP)
        host.pressKey(UP)
        assertEquals(State.CLOSING, shell.state)
        assertEquals(listOf(ShellSound.OPEN, ShellSound.CLOSE), host.sounds)
    }

    @Test
    fun `holding the phone key in the open phone closes it once, and its repeats don't reopen it`() {
        registerApps(1)
        openFully()
        host.holdKey(UP, 30)
        assertEquals(State.CLOSING, shell.state)
        closeFully()
        host.repeatKey(UP, 30)
        assertEquals(State.CLOSED, shell.state)
        assertEquals(listOf(ShellSound.OPEN, ShellSound.CLOSE), host.sounds)
    }

    @Test
    fun `holding Backspace goes back one screen, not several`() {
        openOnRoot()
        root.handle.push(deep)
        deep.handle.push(deeper)
        tick(3)
        host.holdKey(BACKSPACE, 10)
        assertSame(deep, host.navigator.currentScreen)
        host.releaseKey(BACKSPACE)
        host.pressKey(BACKSPACE)
        assertSame(root, host.navigator.currentScreen)
    }

    @Test
    fun `a held key still reaches a focused field on every repeat`() {
        openOnRoot()
        root.handle.push(deep)
        tick(3)
        deep.focus = true
        log.clear()
        host.holdKey(BACKSPACE, 3)
        assertEquals(4, log.count { it == "deep:key $BACKSPACE" })
        assertSame(deep, host.navigator.currentScreen)
    }

    @Test
    fun `Esc held in an app closes its dialog without closing the phone on the repeats`() {
        openOnRoot()
        root.keeps = setOf(ESC)
        host.keyDown(ESC)
        root.keeps = emptySet()
        host.repeatKey(ESC, 5)
        assertEquals(State.OPEN, shell.state)
    }

    @Test
    fun `the phone key held from the press that opened the phone types nothing`() {
        host.phoneKey = GLFW.GLFW_KEY_P
        openOnRoot()
        root.focus = true
        host.pressKey(GLFW.GLFW_KEY_ESCAPE)
        closeFully()
        host.keyDown(GLFW.GLFW_KEY_P)
        tick(OPEN_TICKS)
        assertEquals(State.OPEN, shell.state)
        log.clear()
        host.repeatKey(GLFW.GLFW_KEY_P, 3)
        assertFalse(shell.charTyped('p', 0))
        assertEquals(emptyList<String>(), log)
        host.releaseKey(GLFW.GLFW_KEY_P)
        assertTrue(shell.charTyped('p', 0))
    }

    // ---- Screens over the rising phone ----

    @Test
    fun `a screen opening during the rise sends the device down behind it, at once`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(2)
        val here = pose!!
        val inventory = host.openOtherScreen("inventory")
        shell.frame()
        assertEquals(State.CLOSING, shell.state)
        assertFalse(host.navigator.isOpen)
        assertEquals(here, pose)
        tick(CLOSE_TICKS)
        assertEquals(State.CLOSED, shell.state)
        assertSame(inventory, host.screen)
        assertFalse(host.screensShown.any { host.isPhoneScreen(it) })
    }

    @Test
    fun `Esc during the rise opens the pause menu and the device goes back down`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(1)
        host.pressKey(ESC)
        tick()
        assertEquals(State.CLOSING, shell.state)
        assertEquals(FakeShellHost.PAUSE, (host.screen as TestScreen).name)
    }

    @Test
    fun `a mod refusing the phone's screen leaves the game with the mouse and the device going down`() {
        registerApps(1)
        pressPhoneKeyInGame()
        host.refuseNextScreen = true
        tick(OPEN_TICKS)
        assertEquals(State.CLOSING, shell.state)
        assertFalse(host.navigator.isOpen)
        assertNull(host.screen)
        closeFully()
    }

    @Test
    fun `a mod replacing the phone's screen leaves its screen alone and the device going down`() {
        registerApps(1)
        pressPhoneKeyInGame()
        val other = TestScreen("other")
        host.replaceNextScreen = other
        tick(OPEN_TICKS)
        assertEquals(State.CLOSING, shell.state)
        assertSame(other, host.screen)
    }

    // ---- The phone's screen shown again ----

    @Test
    fun `a dialog an app opens over the phone gives the phone back, standing, when it closes`() {
        openOnRoot()
        val phoneScreen = host.screen!!
        val standing = pose!!
        // ConfirmLinkScreen and the like: opened with the phone's screen as parent.
        host.openOtherScreen("confirm link")
        assertEquals(State.CLOSED, shell.state)
        assertEquals(listOf("root:hidden"), log)
        host.setScreen(phoneScreen)
        assertEquals(State.OPEN, shell.state)
        assertTrue(host.navigator.isOpen)
        assertSame(root, host.navigator.shownScreen)
        assertEquals(standing, pose)
        // And the phone answers again.
        host.pressKey(ESC)
        assertEquals(State.CLOSING, shell.state)
        assertNull(host.screen)
    }

    @Test
    fun `a mod refusing the screen the phone came from can't leave the phone's screen up`() {
        registerApps(1)
        val inventory = host.openOtherScreen("inventory")
        shell.open(returnTo = inventory)
        tick(OPEN_TICKS)
        assertTrue(phoneScreenShown)
        host.refuseNextScreen = true
        host.pressKey(ESC)
        assertEquals(State.CLOSING, shell.state)
        assertNull(host.screen)
    }

    // ---- Mouse button and disconnection ----

    @Test
    fun `a phone key on the left mouse button doesn't close the phone on the device`() {
        host.phoneMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT
        openOnRoot()
        host.pressKey(ESC)
        closeFully()
        pressPhoneKeyInGame()
        tick(OPEN_TICKS)
        clickHomeButton()
        assertEquals(State.OPEN, shell.state)
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `a phone key on another mouse button closes the phone wherever it is clicked`() {
        host.phoneMouseButton = GLFW.GLFW_MOUSE_BUTTON_RIGHT
        registerApps(1)
        openFully()
        val (x, y) = iconCentre(0)
        press(x, y, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        assertEquals(State.CLOSING, shell.state)
    }

    @Test
    fun `the player gone, the navigator is put away with the shell`() {
        openOnRoot()
        host.inWorld = false
        tick()
        assertEquals(State.CLOSED, shell.state)
        assertFalse(host.navigator.isOpen)
        assertEquals(listOf("root:hidden"), log)
    }
}
