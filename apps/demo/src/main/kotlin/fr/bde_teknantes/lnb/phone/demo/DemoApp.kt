package fr.bde_teknantes.lnb.phone.demo

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.neoforged.fml.common.Mod

/** Development app: proves an app in its own mod loads with the phone. Never shipped. */
@Mod(DemoApp.ID)
class DemoApp {
    init {
        // The phone's logger rather than our own: the line only shows if the app reaches the phone's classes at
        // runtime, not just at compile time (LnbPhone.ID alone is inlined).
        LnbPhone.LOGGER.info("{} loaded next to {}", ID, LnbPhone.ID)
    }

    companion object {
        const val ID = "lnbphone_demo"
    }
}
