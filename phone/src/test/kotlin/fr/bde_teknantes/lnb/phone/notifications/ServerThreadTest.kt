package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.AppTarget
import fr.bde_teknantes.lnb.phone.api.PhoneNotifications
import fr.bde_teknantes.lnb.phone.notifications.server.ServerThread
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.thread.BlockableEventLoop
import net.neoforged.neoforge.server.ServerLifecycleHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch

/**
 * How [PhoneNotifications] reaches the server thread, driven with an event loop of the same kind as the server's
 * (Minecraft's [BlockableEventLoop]), whose "server thread" is the test's own thread.
 */
class ServerThreadTest {
    /** An event loop run by hand from [thread]; [stopped] makes it behave as a stopped server does in execute(). */
    private class Loop(private val thread: Thread) : BlockableEventLoop<Runnable>("test loop") {
        @Volatile
        var stopped = false

        override fun wrapRunnable(runnable: Runnable) = runnable
        override fun shouldRun(runnable: Runnable) = true
        override fun getRunningThread() = thread

        // MinecraftServer: super.scheduleExecutables() && !isStopped()
        override fun scheduleExecutables() = super.scheduleExecutables() && !stopped

        fun runQueued() = runAllTasks()
    }

    private val serverThread = Thread.currentThread()
    private val loop = Loop(serverThread)
    private val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val threads: MutableList<Thread> = Collections.synchronizedList(mutableListOf())

    private fun action(name: String): () -> Unit = {
        log += name
        threads += Thread.currentThread()
    }

    private fun fromAnotherThread(block: () -> Unit) {
        var failure: Throwable? = null
        val thread = Thread({ try { block() } catch (e: Throwable) { failure = e } }, "app thread")
        thread.start()
        thread.join()
        failure?.let { throw it }
    }

    @Test
    fun `on the server thread, the change is made at once`() {
        ServerThread.run(loop, { true }, action("now"))
        assertEquals(listOf("now"), log)
        assertEquals(0, loop.pendingTasksCount)
    }

    @Test
    fun `from another thread, the change waits for the server thread and runs there`() {
        fromAnotherThread { ServerThread.run(loop, { true }, action("later")) }
        assertEquals(emptyList<String>(), log)
        assertEquals(1, loop.pendingTasksCount)
        loop.runQueued()
        assertEquals(listOf("later"), log)
        assertSame(serverThread, threads.single())
    }

    @Test
    fun `calls from one thread are applied in their order`() {
        fromAnotherThread { repeat(200) { ServerThread.run(loop, { true }, action("$it")) } }
        loop.runQueued()
        assertEquals((0 until 200).map { "$it" }, log)
    }

    @Test
    fun `calls from several threads keep each thread's order`() {
        val start = CountDownLatch(1)
        val workers = (0 until 4).map { w ->
            Thread {
                start.await()
                repeat(100) { ServerThread.run(loop, { true }, action("$w:$it")) }
            }.apply { start() }
        }
        start.countDown()
        workers.forEach(Thread::join)
        loop.runQueued()
        assertEquals(400, log.size)
        for (w in 0 until 4) {
            assertEquals((0 until 100).map { "$w:$it" }, log.filter { it.startsWith("$w:") })
        }
        assertEquals(setOf(serverThread), threads.toSet())
    }

    @Test
    fun `a server no longer running drops the change without queueing it`() {
        fromAnotherThread { ServerThread.run(loop, { false }, action("dropped")) }
        assertEquals(0, loop.pendingTasksCount)
        loop.runQueued()
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `a server that stops between the check and the queueing drops the change`() {
        // Still "running" when asked, but already stopped when execute() decides: it then runs the task in the
        // caller's thread, which must not touch the store.
        loop.stopped = true
        fromAnotherThread { ServerThread.run(loop, { true }, action("in the wrong thread")) }
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `the server thread still changes at once while the server stops`() {
        // The stop sequence runs on the server thread (saving the world, say): a change made from there is kept.
        ServerThread.run(loop, { false }, action("during the stop"))
        assertEquals(listOf("during the stop"), log)
    }

    // ---- The public API, without a server ----

    // In the phone's namespace: send only takes app ids of loaded mods, and the phone is loaded in tests.
    private val feed = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "feed")

    @Test
    fun `bad arguments throw in the caller's thread, even without a server`() {
        val long = "t".repeat(AppTarget.MAX_LENGTH + 1)
        val player = UUID(0, 1)
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.send(player, feed, Component.literal("t"), Component.literal("x"), long) }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.clearTarget(player, feed, long) }
        val longApp = ResourceLocation.fromNamespaceAndPath("a", "p".repeat(200))
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.send(player, longApp, Component.literal("t"), Component.literal("x")) }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.clearApp(player, longApp) }
        // Clearing one target and clearing the app are two calls: an empty target is refused, never read as "all".
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.clearTarget(player, feed, "") }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.clearTargetForEveryone(feed, "") }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.clearTargetForEveryone(feed, long) }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.sendToEveryone(feed, Component.empty(), Component.empty(), long) }
        assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.send(listOf(player), longApp, Component.empty(), Component.empty()) }
        // An app id outside the namespace of a loaded mod can't send, whatever the form.
        for (id in listOf("test:feed", "minecraft:feed", "neoforge:feed").map(ResourceLocation::parse)) {
            assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.send(player, id, Component.empty(), Component.empty()) }
            assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.send(listOf(player), id, Component.empty(), Component.empty(), "t") }
            assertThrows(IllegalArgumentException::class.java) { PhoneNotifications.sendToEveryone(id, Component.empty(), Component.empty()) }
        }
    }

    @Test
    fun `without a server, a call does nothing and doesn't throw`() {
        assumeTrue(ServerLifecycleHooks.getCurrentServer() == null)
        val player = UUID(0, 1)
        PhoneNotifications.send(player, feed, Component.literal("t"), Component.literal("x"), "thread:1")
        PhoneNotifications.clearApp(player, feed)
        PhoneNotifications.clearTarget(player, feed, "thread:1")
        PhoneNotifications.clearTargetForEveryone(feed, "thread:1")
        PhoneNotifications.clearAppForEveryone(feed)
        PhoneNotifications.send(listOf(player, UUID(0, 2)), feed, Component.literal("t"), Component.literal("x"))
        PhoneNotifications.sendToEveryone(feed, Component.literal("t"), Component.literal("x"))
        fromAnotherThread { PhoneNotifications.send(player, feed, Component.literal("t"), Component.literal("x")) }
    }
}
