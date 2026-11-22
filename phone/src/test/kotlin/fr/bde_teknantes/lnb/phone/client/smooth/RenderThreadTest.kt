package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderThreadTest {
    @Test
    fun `measuring text from a network thread fails with what to do instead`() {
        var failure: Throwable? = null
        val worker = Thread({
            try {
                SmoothResources.font(FontWeight.REGULAR)
            } catch (e: Throwable) {
                failure = e
            }
        }, "Netty Client IO #1")
        worker.start()
        worker.join()
        val e = failure
        assertNotNull(e)
        assertTrue(e is IllegalStateException, "$e")
        val message = e!!.message!!
        assertTrue("Netty Client IO #1" in message, message)
        assertTrue("render thread" in message && "Minecraft.getInstance().execute" in message, message)
    }
}
