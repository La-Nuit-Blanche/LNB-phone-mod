package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.resources.ResourceLocation

/** Tells the shell how many notifications of an app the player hasn't seen. */
internal fun interface BadgeSource {
    fun unseen(app: ResourceLocation): Int
}

/**
 * Where the home badges and the LED get their counts (art direction 6.1 and 2.1). No notifications until the
 * notification store plugs itself in by setting [source]. Client thread only.
 */
internal object PhoneBadges {
    private val NONE = BadgeSource { 0 }

    var source: BadgeSource = NONE

    private var warned = false

    /** Unseen notifications of [app], never negative. A failing source counts as none. */
    fun count(app: ResourceLocation): Int = try {
        source.unseen(app).coerceAtLeast(0)
    } catch (e: Exception) {
        if (!warned) {
            warned = true
            LnbPhone.LOGGER.error("The phone's badge source failed; badges show none", e)
        }
        0
    }
}
