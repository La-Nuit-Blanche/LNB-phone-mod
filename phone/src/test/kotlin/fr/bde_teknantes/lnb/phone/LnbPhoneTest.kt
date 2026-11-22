package fr.bde_teknantes.lnb.phone

import net.neoforged.fml.ModList
import kotlin.test.Test
import kotlin.test.assertTrue

class LnbPhoneTest {
    @Test
    fun `tests run with the phone loaded`() {
        assertTrue(ModList.get().isLoaded(LnbPhone.ID))
    }
}
