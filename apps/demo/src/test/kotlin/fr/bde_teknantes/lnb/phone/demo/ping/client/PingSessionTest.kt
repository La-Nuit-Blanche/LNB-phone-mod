package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.demo.ping.PingOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PingSessionTest {
    private val alex = UUID.fromString("0f3c2a6e-1b7d-4c55-9a1e-2d4b6c8e0f12")

    @BeforeEach
    fun clear() = PingSession.clear()

    @Test
    fun `a conversation keeps only its newest messages`() {
        repeat(PingSession.MAX_MESSAGES + 5) { PingSession.received(alex, "ping $it") }
        val kept = PingSession.conversation(alex)
        assertEquals(PingSession.MAX_MESSAGES, kept.size)
        assertEquals("ping 5", kept.first().text)
        assertEquals("ping ${PingSession.MAX_MESSAGES + 4}", kept.last().text)
    }

    @Test
    fun `a sent ping joins the conversation once the server answers`() {
        PingSession.sending(alex)
        assertTrue(PingSession.isWaiting(alex))
        assertTrue(PingSession.conversation(alex).isEmpty())
        PingSession.answered(alex, "hi", PingOutcome.SENT)
        assertFalse(PingSession.isWaiting(alex))
        assertTrue(PingSession.conversation(alex).single().mine)
        assertEquals(PingOutcome.SENT, PingSession.takeOutcome(alex))
        assertNull(PingSession.takeOutcome(alex))
    }

    @Test
    fun `a refused ping goes back to an empty field and leaves a new draft alone`() {
        PingSession.answered(alex, "hi", PingOutcome.OFFLINE)
        assertEquals("hi", PingSession.draft(alex).text)
        assertTrue(PingSession.conversation(alex).isEmpty())

        PingSession.draft(alex).text = "something else"
        PingSession.answered(alex, "hi", PingOutcome.TOO_FAST)
        assertEquals("something else", PingSession.draft(alex).text)
    }

    @Test
    fun `every change of a conversation moves the revision, clearing included`() {
        val before = PingSession.revision
        PingSession.received(alex, "hi")
        val after = PingSession.revision
        assertNotEquals(before, after)
        PingSession.clear()
        assertNotEquals(after, PingSession.revision)
        assertTrue(PingSession.conversation(alex).isEmpty())
    }
}
