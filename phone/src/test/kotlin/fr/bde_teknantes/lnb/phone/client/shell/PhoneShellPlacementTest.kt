package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Changing the placement in Settings (art direction 12, 14, flow P-12). */
internal class PhoneShellPlacementTest : ShellTestBase() {
    private fun centre() = host.settings.update { it.copy(placement = Placement.CENTRE) }

    private fun corner() = host.settings.update { it.copy(placement = Placement.CORNER) }

    @Test
    fun `the open device glides to the centre in 250 ms while the scrim fades in, and back`() {
        registerApps(1)
        openFully()
        centre()
        assertEquals(DevicePose(334, 36, 0f), pose)
        // Ease-out over 5 ticks: eased 0.36, 0.64, 0.84, 0.96 of (-164, -15).
        val frames = (1..5).map {
            tick()
            pose!!
        }
        assertEquals(listOf(275, 229, 196, 177, 170), frames.map { it.x })
        assertEquals(listOf(31, 26, 23, 22, 21), frames.map { it.y })
        assertEquals(listOf(0.36f, 0.64f, 0.84f, 0.96f, 1f), frames.map { it.scrim }.map { Math.round(it * 100) / 100f })
        assertEquals(DevicePose(170, 21, 1f), pose)
        assertEquals(State.OPEN, shell.state)

        corner()
        tick(5)
        assertEquals(DevicePose(334, 36, 0f), pose)
    }

    @Test
    fun `P-12 mid-glide frames, both ways`() {
        registerApps(1)
        openFully()
        shell.heldProgress = 0.5f
        centre()
        assertEquals(DevicePose(252, 28, 0.5f), pose)
        assertEquals(0x26000000, PhoneLayout.scrimColor(pose!!.scrim))
        shell.heldProgress = null
        tick(5)
        shell.heldProgress = 0.5f
        corner()
        assertEquals(DevicePose(252, 28, 0.5f), pose)
    }

    @Test
    fun `the phone stays open and keeps its screen while it glides`() {
        registerApps(1)
        openFully()
        centre()
        tick(2)
        assertEquals(State.OPEN, shell.state)
        assertTrue(phoneScreenShown)
    }

    @Test
    fun `a placement chosen while the phone is away applies at the next opening`() {
        registerApps(1)
        centre()
        pressPhoneKeyInGame()
        assertEquals(DevicePose(170, 270, 0f), pose)
        tick(OPEN_TICKS)
        assertEquals(DevicePose(170, 21, 1f), pose)
    }

    @Test
    fun `a placement change while the device falls doesn't stop it`() {
        registerApps(1)
        openFully()
        host.pressKey(ESC)
        tick()
        val x = pose!!.x
        centre()
        tick()
        assertEquals(x, pose!!.x)
        tick(CLOSE_TICKS)
        assertEquals(State.CLOSED, shell.state)
        // And the next opening is centred.
        openFully()
        assertEquals(DevicePose(170, 21, 1f), pose)
    }

    @Test
    fun `closing mid-glide drops the device straight down from where it is`() {
        registerApps(1)
        openFully()
        centre()
        tick(2)
        val here = pose!!
        host.pressKey(ESC)
        assertEquals(here, pose)
        tick(2)
        assertEquals(here.x, pose!!.x)
        assertTrue(pose!!.y > here.y)
        assertTrue(pose!!.scrim < here.scrim)
    }

    @Test
    fun `a placement change during the rise turns the device without a jump`() {
        registerApps(1)
        pressPhoneKeyInGame()
        tick(2)
        val before = pose!!
        assertEquals(DevicePose(334, 120, 0f), before)
        centre()
        assertEquals(before, pose)
        tick(5)
        assertEquals(DevicePose(170, 21, 1f), pose)
        assertEquals(State.OPEN, shell.state)
    }

    @Test
    fun `switching back mid-glide turns the device without a jump`() {
        registerApps(1)
        openFully()
        centre()
        tick(2)
        val before = pose!!
        assertEquals(DevicePose(229, 26, before.scrim), before)
        corner()
        assertEquals(before, pose)
        // Then on to the corner, never through a pose further from it.
        var last = before
        repeat(5) {
            tick()
            val now = pose!!
            assertTrue(now.x >= last.x && now.y >= last.y && now.scrim <= last.scrim, "$last then $now")
            last = now
        }
        assertEquals(DevicePose(334, 36, 0f), pose)
    }

    @Test
    fun `changing something other than the placement doesn't move the device`() {
        registerApps(1)
        openFully()
        host.settings.update { it.copy(sounds = false, preview = false) }
        assertEquals(DevicePose(334, 36, 0f), pose)
        tick()
        assertEquals(DevicePose(334, 36, 0f), pose)
    }
}
