package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import net.neoforged.fml.ModList
import org.junit.jupiter.api.Assertions.assertThrows
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppRegistrationTest {
    /** Runs [block] with [listener] on the phone's real mod bus, and takes it off afterwards. */
    private fun withListener(listener: Consumer<RegisterPhoneAppsEvent>, block: () -> Unit) {
        val bus = ModList.get().getModContainerById(LnbPhone.ID).get().eventBus!!
        bus.addListener(RegisterPhoneAppsEvent::class.java, listener)
        try {
            block()
        } finally {
            bus.unregister(listener)
        }
    }

    @Test
    fun `a mod that throws while registering keeps the apps it registered before, and registration then ends`() {
        val registry = AppRegistry()
        val kept = testApp("kept")
        var event: RegisterPhoneAppsEvent? = null

        withListener({
            event = it
            it.register(kept)
            error("a broken listener")
        }) { AppRegistration.fire(registry) }

        assertEquals(listOf(kept), registry.apps)
        assertTrue(registry.isFrozen)
        assertThrows(IllegalStateException::class.java) { event!!.register(testApp("late")) }
    }
}
