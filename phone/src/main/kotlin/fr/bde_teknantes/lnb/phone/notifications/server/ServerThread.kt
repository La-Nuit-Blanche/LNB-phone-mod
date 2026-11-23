package fr.bde_teknantes.lnb.phone.notifications.server

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.util.thread.BlockableEventLoop

/**
 * How [fr.bde_teknantes.lnb.phone.api.PhoneNotifications] reaches the server thread from any thread. Takes the event
 * loop rather than the server so that tests can drive it with a loop of their own.
 */
internal object ServerThread {
    /**
     * Runs [action] on [loop]'s thread: at once when called from there, queued (in call order) from any other thread.
     * Dropped, with a warning, when the loop isn't [isRunning] or has stopped by the time the task would run.
     */
    fun run(loop: BlockableEventLoop<*>, isRunning: () -> Boolean, action: () -> Unit) {
        if (loop.isSameThread) return action()
        // A stopped server runs execute()'s task in the caller's thread instead of queueing it: never touch the store
        // from there.
        if (!isRunning()) return dropped()
        loop.execute { if (loop.isSameThread) action() else dropped() }
    }

    private fun dropped() = LnbPhone.LOGGER.warn("The server is stopping: phone notification change dropped")
}
