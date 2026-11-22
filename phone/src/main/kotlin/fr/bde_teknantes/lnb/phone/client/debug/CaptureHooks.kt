package fr.bde_teknantes.lnb.phone.client.debug

import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettings
import fr.bde_teknantes.lnb.phone.client.shell.BadgeSource
import fr.bde_teknantes.lnb.phone.client.shell.Device
import fr.bde_teknantes.lnb.phone.client.shell.PhoneBadges
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShell
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import fr.bde_teknantes.lnb.phone.client.shell.ShellRenderer
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import org.jetbrains.annotations.ApiStatus

/**
 * Levers for the demo's screenshot harness, which plays scenarios in an unattended client: freeze an animation half
 * way, fake notification counts or a latency, switch the placement. **Not part of the app API**: development only,
 * may change or disappear at any time. Client thread only.
 *
 * The levers do nothing in a production game (the jar a player installs): there they would let any mod freeze the
 * phone or rewrite a player's settings. The read-only accessors work everywhere.
 */
@ApiStatus.Internal
object CaptureHooks {
    private val enabled = !FMLEnvironment.production

    // The badge source in place before the first fake, put back when the fake is lifted.
    private var realBadges: BadgeSource? = null

    /** Holds every shell animation at this eased progress (0 to 1) until called with null. */
    fun holdAnimations(progress: Float?) {
        if (!enabled) return
        PhoneShell.heldProgress = progress
    }

    /** Unseen notification counts by app id, in place of the real ones; null for the real ones again. */
    fun fakeBadges(counts: ((ResourceLocation) -> Int)?) {
        if (!enabled) return
        if (counts != null) {
            if (realBadges == null) realBadges = PhoneBadges.source
            PhoneBadges.source = BadgeSource(counts)
        } else {
            realBadges?.let { PhoneBadges.source = it }
            realBadges = null
        }
    }

    /** Keeps the blinking LED lit, so a capture catches it on. */
    fun holdLedOn(on: Boolean) {
        if (!enabled) return
        ShellRenderer.ledHeldOn = on
    }

    /** A latency in ms for the signal bars, in place of the player's own; null for the real one. */
    fun fakeLatency(ms: Int?) {
        if (!enabled) return
        ShellRenderer.latencyOverride = ms
    }

    /** Switches the placement setting (saved like a player's choice): centred if [centred], else the corner. */
    fun setCentred(centred: Boolean) {
        if (!enabled) return
        LnbPhoneClient.settings.update { it.copy(placement = if (centred) Placement.CENTRE else Placement.CORNER) }
    }

    /** Puts the player's settings back to the defaults (corner, sounds on, preview on), saved like a player's choice. */
    fun resetSettings() {
        if (!enabled) return
        LnbPhoneClient.settings.update { PhoneSettings() }
    }

    /** Whether the phone stands open with its screen (the slide up is over). */
    val isOpen: Boolean get() = PhoneShell.state == PhoneShellCore.State.OPEN

    /** Whether the phone is put away (the slide down is over). */
    val isClosed: Boolean get() = PhoneShell.state == PhoneShellCore.State.CLOSED

    /** The device's top-left corner in window GUI px as `[x, y]`, or null while it is away. */
    fun devicePosition(): IntArray? = PhoneShell.currentPose()?.let { intArrayOf(it.x, it.y) }

    /** The app viewport's top-left corner, device-local GUI px. */
    val viewportOffset: IntArray get() = intArrayOf(Device.VIEWPORT_X, Device.VIEWPORT_Y)
}
