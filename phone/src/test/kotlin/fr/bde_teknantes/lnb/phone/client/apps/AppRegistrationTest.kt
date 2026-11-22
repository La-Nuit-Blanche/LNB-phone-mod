package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import net.neoforged.fml.ModList
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppRegistrationTest {
    @Test
    fun `a mod that throws while registering keeps the apps it registered before, and registration then ends`() {
        val registry = AppRegistry()
        val kept = testApp("kept")
        var event: RegisterPhoneAppsEvent? = null
        val bus = ModList.get().getModContainerById(LnbPhone.ID).get().eventBus!!
        // The listener stays on the phone's bus for the rest of the run: it acts only on this test's registry.
        bus.addListener<RegisterPhoneAppsEvent> {
            if (event == null) {
                event = it
                it.register(kept)
                error("a broken listener")
            }
        }

        AppRegistration.fire(registry)

        assertEquals(listOf(kept), registry.apps)
        assertTrue(registry.isFrozen)
        assertThrows(IllegalStateException::class.java) { event!!.register(testApp("late")) }
    }
}
