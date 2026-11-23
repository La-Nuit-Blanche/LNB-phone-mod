package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The online players' state (`docs/feed-app.md` 4.2, `docs/feed-mod.md` 10): how fresh it is, and who hears of it. */
class RightsCacheTest {
    private val host = FakeHost()
    private val backend = ScriptedBackend()
    private val watching = HashSet<UUID>()
    private val rights = RightsCache(backend, host) { it in watching }

    private val lune = UUID(1, 1)
    private val camille = UUID(1, 2)
    private val moderator = Rights(write = true, announce = true, moderate = true)
    private val player = Rights(write = true, announce = false, moderate = false)

    init {
        host.online += listOf(lune, camille)
    }

    private fun reads(of: UUID) = backend.calls("player").count { it.arg<UUID>(0) == of }

    private fun answerLast(of: UUID, rights: Rights) =
        backend.calls("player").last { it.arg<UUID>(0) == of }.answer(BackendResult.Ok(ScriptedBackend.linked(of, "p", rights)))

    @Test
    fun `a copy older than 5 minutes is read again at the next tick, and not before`() {
        rights.refresh(lune)
        answerLast(lune, moderator)
        assertTrue(rights.moderates(lune))
        rights.tick()
        assertEquals(1, reads(lune))

        host.time += RightsCache.MAX_AGE_MS
        assertTrue(rights.moderates(lune), "exactly 5 minutes is still fresh")
        rights.tick()
        assertEquals(1, reads(lune))

        host.time += 1
        assertTrue(rights.moderates(lune), "the copy at hand answers until the new one comes")
        assertEquals(1, reads(lune), "never read from inside the command's check")
        rights.tick()
        assertEquals(2, reads(lune))
        rights.moderates(lune)
        rights.tick()
        assertEquals(2, reads(lune), "not twice while a read is on its way")

        answerLast(lune, player)
        assertFalse(rights.moderates(lune))
        assertEquals(listOf(lune, lune), host.commandRefreshes, "the tree follows each change of the moderate right")
        rights.tick()
        assertEquals(2, reads(lune))
    }

    @Test
    fun `a player with no copy is read at the next tick, unless they are offline`() {
        assertFalse(rights.moderates(camille))
        val stranger = UUID(9, 9)
        assertFalse(rights.moderates(stranger))
        rights.tick()
        assertEquals(1, reads(camille))
        assertEquals(0, reads(stranger))
    }

    @Test
    fun `a new state goes to the player only if they watch, and the tree only when moderate changes`() {
        rights.refresh(camille)
        answerLast(camille, player)
        assertTrue(host.sentTo(camille).isEmpty())
        assertTrue(host.commandRefreshes.isEmpty(), "the join's tree was built as for a player who doesn't moderate")

        watching += camille
        rights.refresh(camille)
        answerLast(camille, player.copy(announce = true))
        assertEquals(StatePayload(Availability.OK, ScriptedBackend.linked(camille, "p", player.copy(announce = true))), host.sentTo(camille).single())
        assertTrue(host.commandRefreshes.isEmpty(), "announce alone doesn't change the command")
    }

    @Test
    fun `a failed read keeps the copy, and a read for a player who left is not kept`() {
        rights.refresh(lune)
        answerLast(lune, moderator)
        rights.refresh(lune)
        backend.calls("player").last().answer(BackendResult.Unavailable("down"))
        assertTrue(rights.moderates(lune))
        rights.refresh(lune)
        backend.calls("player").last().fail(RuntimeException("boom"))
        assertTrue(rights.state(lune)!!.rights.moderate)

        rights.refresh(camille)
        rights.forget(camille)
        host.online -= camille
        answerLast(camille, moderator)
        assertNull(rights.state(camille))
        assertTrue(camille !in host.commandRefreshes)
    }

    @Test
    fun `a late answer is kept on the server thread, and the future completes there with it`() {
        host.deferred = true
        val done = rights.refresh(lune)
        Thread { answerLast(lune, moderator) }.apply { start() }.join()
        assertNull(rights.state(lune))
        assertFalse(done.isDone)
        host.runTasks()
        assertTrue(done.isDone)
        assertTrue(rights.moderates(lune))
    }

    @Test
    fun `forgetting a player drops what was marked stale for them`() {
        rights.moderates(camille)
        rights.forget(camille)
        rights.tick()
        assertEquals(0, reads(camille))
    }
}
