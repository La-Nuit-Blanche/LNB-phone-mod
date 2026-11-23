package fr.bde_teknantes.lnb.phone.feed.server

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The server, standing still: tasks run at once, as when already on the server thread. With [deferred], tasks wait
 * in a queue until [runTasks], as tasks handed over from another thread wait for the server's next tick.
 */
class FakeHost : FeedHost {
    val online = LinkedHashSet<UUID>()
    val sent = ArrayList<Pair<UUID, CustomPacketPayload>>()
    val commandRefreshes = ArrayList<UUID>()
    var time = 0L
    var deferred = false
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()

    override fun execute(task: () -> Unit) {
        if (deferred) tasks += task else task()
    }

    /** Runs the queued tasks, and those they queue, as the server thread would. Returns how many ran. */
    fun runTasks(): Int {
        var count = 0
        while (true) {
            val task = tasks.poll() ?: return count
            task()
            count++
        }
    }

    val queuedTasks: Int get() = tasks.size
    override fun onlinePlayers(): Collection<UUID> = online.toList()
    override fun isOnline(player: UUID) = player in online

    override fun send(player: UUID, payload: CustomPacketPayload) {
        if (player in online) sent += player to payload
    }

    override fun refreshCommands(player: UUID) {
        commandRefreshes += player
    }

    override fun millis() = time

    fun sentTo(player: UUID): List<CustomPacketPayload> = sent.filter { it.first == player }.map { it.second }

    inline fun <reified T : CustomPacketPayload> received(player: UUID): List<T> = sentTo(player).filterIsInstance<T>()
}

class RecordingSink : FeedNotificationSink {
    data class Sent(val players: Collection<UUID>?, val except: Collection<UUID>, val title: Component, val text: Component, val target: String)

    val sent = ArrayList<Sent>()
    val cleared = ArrayList<String>()

    /** Targets the phone refuses, by throwing as it does on an argument it won't take. */
    var refuses: (String) -> Boolean = { false }

    override fun send(players: Collection<UUID>, title: Component, text: Component, target: String) {
        require(!refuses(target)) { "refused" }
        sent += Sent(players.toList(), emptyList(), title, text, target)
    }

    override fun sendToEveryone(title: Component, text: Component, target: String, except: Collection<UUID>) {
        require(!refuses(target)) { "refused" }
        sent += Sent(null, except.toList(), title, text, target)
    }

    override fun clearTargetForEveryone(target: String) {
        cleared += target
    }
}
