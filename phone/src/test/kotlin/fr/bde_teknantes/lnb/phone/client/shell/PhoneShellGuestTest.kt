package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/**
 * The phone as a guest in Minecraft's input system: held keys and their repeats.
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
}
