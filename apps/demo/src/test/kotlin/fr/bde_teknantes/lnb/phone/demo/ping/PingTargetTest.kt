package fr.bde_teknantes.lnb.phone.demo.ping

import fr.bde_teknantes.lnb.phone.api.AppTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PingTargetTest {
    private val id = UUID.fromString("0f3c2a6e-1b7d-4c55-9a1e-2d4b6c8e0f12")

    @Test
    fun `a target reads back as written`() {
        val target = PingTarget(id, "Cam_Roux")
        assertEquals("from:0f3c2a6e-1b7d-4c55-9a1e-2d4b6c8e0f12/Cam_Roux", target.toString())
        assertEquals(target, PingTarget.parse(target.toString()))
    }

    @Test
    fun `the longest name keeps the target within the phone's limit`() {
        assertTrue(PingTarget(id, "A".repeat(16)).toString().length <= AppTarget.MAX_LENGTH)
    }

    @Test
    fun `targets Ping doesn't know read as none`() {
        assertNull(PingTarget.parse(""))
        assertNull(PingTarget.parse("depth:3"))
        assertNull(PingTarget.parse("from:"))
        assertNull(PingTarget.parse("from:$id"))
        assertNull(PingTarget.parse("from:$id/"))
        assertNull(PingTarget.parse("from:not-a-uuid/Cam_Roux"))
    }
}
