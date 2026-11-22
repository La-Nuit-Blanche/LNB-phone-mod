package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PingScreenTest {
    private val dev = UUID.fromString("0f3c2a6e-1b7d-4c55-9a1e-2d4b6c8e0f12")

    // A phone without the game, open on the conversation with Dev.
    private val tester = PhoneTester { PingScreen(dev, "Dev") }

    @AfterEach
    fun tearDown() {
        tester.close()
        PingSession.clear()
    }

    @Test
    fun `Send waits for something to send`() {
        // The send button, right-aligned to x 244 at y 168: disabled, a click on it answers with the error sound.
        tester.click(200f, 184f)
        assertEquals(listOf(PhoneSound.ERROR), tester.sounds)
    }

    @Test
    fun `a draft outlives its screen`() {
        // The field has the focus when the screen opens: the player types straight away.
        tester.type("Meet me at the mill")
        tester.close()
        // A new visit builds a new screen, which finds the draft in PingSession.
        PhoneTester { PingScreen(dev, "Dev") }.use { again ->
            assertTrue(again.shows("Meet me at the mill"))
        }
    }
}
