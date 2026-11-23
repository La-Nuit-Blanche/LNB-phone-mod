package fr.bde_teknantes.lnb.phone.feed.server

import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.network.ResetPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.Players.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.server.Players.KAEL
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * `/feed announcer` and the memory backend's `/feed dev` (`docs/feed-app.md` 7 and 3.3), run from the server console
 * against the memory backend. A player's run needs a `ServerPlayer`, which can't be made without a running server:
 * the player paths (the cached `moderate` in `requires`, the issuer sent to the site) are not covered here.
 */
class FeedCommandTest {
    private class Console : CommandSource {
        val messages = ArrayList<Component>()
        override fun sendSystemMessage(component: Component) {
            messages += component
        }
        override fun acceptsSuccess() = true
        override fun acceptsFailure() = true
        override fun shouldInformAdmins() = false
    }

    private val console = Console()
    private val clock = MutableClock()
    private val memory = MemoryBackend(clock, Players.directory(), guests = listOf("Visitor42"), rateLimits = false)
    private val host = FakeHost()

    private fun source(level: Int) =
        CommandSourceStack(console, Vec3.ZERO, Vec2.ZERO, null, level, "Server", Component.literal("Server"), null, null)

    private fun dispatcher(dev: Boolean) = CommandDispatcher<CommandSourceStack>().also { FeedCommand.register(it, devCommands = dev) }

    private fun setCurrent(service: FeedService?) {
        FeedServer::class.java.getDeclaredField("current").apply { isAccessible = true }.set(null, service)
    }

    private fun run(command: String, level: Int = 4, dev: Boolean = false): Int = dispatcher(dev).execute(command, source(level))

    /** The translation key of the last message, through the red style a failure is wrapped in. */
    private fun lastKey(): String {
        val message = console.messages.last()
        val translatable = (message.contents as? TranslatableContents) ?: message.siblings.single().contents as TranslatableContents
        return translatable.key
    }

    private fun lastArgs(): List<Any> {
        val message = console.messages.last()
        val translatable = (message.contents as? TranslatableContents) ?: message.siblings.single().contents as TranslatableContents
        return translatable.args.toList()
    }

    private fun announces(player: UUID) = (memory.player(player).join() as BackendResult.Ok).body.rights.announce

    private val service = FeedService(memory, host, RecordingSink()).apply { start() }

    init {
        setCurrent(service)
    }

    @AfterEach
    fun clear() = setCurrent(null)

    @Test
    fun `the console adds and removes an announcer by username, and says when it was already so`() {
        assertEquals(1, run("feed announcer add Cam_Roux"))
        assertEquals("command.lnbphone_feed.announcer.added", lastKey())
        assertEquals(listOf<Any>("Cam_Roux"), lastArgs())
        assertTrue(announces(CAMILLE))
        run("feed announcer add Cam_Roux")
        assertEquals("command.lnbphone_feed.announcer.already", lastKey())

        run("feed announcer remove Cam_Roux")
        assertEquals("command.lnbphone_feed.announcer.removed", lastKey())
        assertFalse(announces(CAMILLE))
        run("feed announcer remove Cam_Roux")
        assertEquals("command.lnbphone_feed.announcer.already_not", lastKey())
    }

    @Test
    fun `a moderator is an announcer by role, and can't lose it`() {
        run("feed announcer add Admin_Lune")
        assertEquals("command.lnbphone_feed.announcer.via_role", lastKey())
        run("feed announcer remove Admin_Lune")
        assertEquals("command.lnbphone_feed.announcer.implied_by_role", lastKey())
        assertEquals(listOf<Any>("Admin_Lune"), lastArgs())
    }

    @Test
    fun `a name with no LNB account, or that isn't a username, is refused`() {
        run("feed announcer add Visitor42")
        assertEquals("command.lnbphone_feed.announcer.not_linked", lastKey())
        assertEquals(0, run("feed announcer add Not-A-Name"))
        assertEquals("command.lnbphone_feed.announcer.invalid_name", lastKey())
        assertEquals(0, run("feed announcer add ${"a".repeat(17)}"))
        assertEquals("command.lnbphone_feed.announcer.invalid_name", lastKey())
    }

    @Test
    fun `the target is the offline UUID of the name typed, and the console names no issuer`() {
        val scripted = ScriptedBackend().apply { auto = { BackendResult.Unavailable("down") } }
        setCurrent(FeedService(scripted, host, RecordingSink()))
        run("feed announcer add Kael_Builder")
        val call = scripted.calls("setAnnouncer").single()
        assertEquals(UUIDUtil.createOfflinePlayerUUID("Kael_Builder"), call.arg<UUID>(0))
        assertEquals(KAEL, call.arg<UUID>(0))
        assertEquals(true, call.arg<Boolean>(1))
        assertEquals(null, call.arg<Issuer?>(2))
        assertEquals("command.lnbphone_feed.announcer.unavailable", lastKey())
    }

    @Test
    fun `each refusal of the site has its own answer`() {
        val cases = mapOf(
            "issuerNotAllowed" to "not_allowed", "notLinked" to "not_linked", "isGhost" to "not_linked",
            "impliedByRole" to "implied_by_role", "somethingNew" to "unavailable",
        )
        for ((problem, key) in cases) {
            val scripted = ScriptedBackend().apply {
                auto = { call -> if (call.name == "setAnnouncer") BackendResult.Refused(409, problem) else BackendResult.Unavailable("down") }
            }
            setCurrent(FeedService(scripted, host, RecordingSink()))
            run("feed announcer add Kael_Builder")
            assertEquals("command.lnbphone_feed.announcer.$key", lastKey(), problem)
        }
        setCurrent(FeedService(ScriptedBackend().apply { auto = { BackendResult.Disabled("feed") } }, host, RecordingSink()))
        run("feed announcer list")
        assertEquals("command.lnbphone_feed.announcer.unavailable", lastKey())
    }

    @Test
    fun `the list names the announcers, not the moderators`() {
        run("feed announcer list")
        assertEquals("command.lnbphone_feed.announcer.list.empty", lastKey())
        memory.setAnnouncer(KAEL, true, null)
        memory.setAnnouncer(CAMILLE, true, null)
        run("feed announcer list")
        assertEquals("command.lnbphone_feed.announcer.list", lastKey())
        assertEquals(listOf<Any>("Cam_Roux; Kael_Builder"), lastArgs())
    }

    @Test
    fun `without Feed running, the command says it can't be reached`() {
        setCurrent(null)
        assertEquals(0, run("feed announcer add Cam_Roux"))
        assertEquals("command.lnbphone_feed.announcer.unavailable", lastKey())
        assertEquals(0, run("feed announcer list"))
    }

    @Test
    fun `only level 4 may run it from outside a player, so command blocks and functions can't`() {
        assertThrows(CommandSyntaxException::class.java) { run("feed announcer add Cam_Roux", level = 2) }
        assertThrows(CommandSyntaxException::class.java) { run("feed announcer list", level = 3) }
        assertFalse(announces(CAMILLE))
    }

    @Test
    fun `the dev switches exist in memory mode only, for level 2, and switch the backend`() {
        assertEquals(1, run("feed dev offline true", level = 2, dev = true))
        assertTrue(memory.isOffline)
        assertEquals("command.lnbphone_feed.dev.set", lastKey())
        run("feed dev offline false", level = 2, dev = true)
        run("feed dev disabled true", level = 2, dev = true)
        assertTrue(memory.isDisabled)
        run("feed dev disabled false", level = 2, dev = true)
        run("feed dev writing false", level = 2, dev = true)
        assertFalse(memory.isWriting)

        assertThrows(CommandSyntaxException::class.java) { run("feed dev writing true", level = 1, dev = true) }
        assertThrows(CommandSyntaxException::class.java) { run("feed dev writing true", level = 4, dev = false) }
        // Registered for memory mode, yet the running backend is the site's: no switches either.
        setCurrent(FeedService(ScriptedBackend(), host, RecordingSink()))
        assertThrows(CommandSyntaxException::class.java) { run("feed dev writing true", level = 4, dev = true) }
        assertFalse(memory.isWriting)
    }

    @Test
    fun `each dev switch reaches the players with Feed open at once, writing by a new read of their state`() {
        host.online += CAMILLE
        host.online += KAEL
        service.handleOpen(CAMILLE)
        host.sent.clear()
        run("feed dev writing false", level = 2, dev = true)
        val states = host.sent.filter { it.second is StatePayload }
        // Camille, watching, has the new state; Kael, Feed closed, gets it when opening Feed.
        assertEquals(listOf(CAMILLE), states.map { it.first })
        val state = states.single().second as StatePayload
        assertEquals(Availability.OK, state.available)
        assertFalse(state.state!!.writing)
        host.sent.clear()
        run("feed dev writing true", level = 2, dev = true)
        assertTrue((host.sent.single { it.second is StatePayload }.second as StatePayload).state!!.writing)
        // `offline` and `disabled` go through the connection's events: the watchers hear of each at once.
        host.sent.clear()
        run("feed dev offline true", level = 2, dev = true)
        assertEquals(Availability.UNAVAILABLE, (host.sent.single().second as StatePayload).available)
        host.sent.clear()
        run("feed dev offline false", level = 2, dev = true)
        assertEquals(Availability.OK, (host.sent.single { it.second is StatePayload }.second as StatePayload).available)
        assertTrue(host.sent.any { it.second == ResetPayload })
        host.sent.clear()
        run("feed dev disabled true", level = 2, dev = true)
        assertEquals(Availability.DISABLED, (host.sent.single().second as StatePayload).available)
        run("feed dev disabled false", level = 2, dev = true)
        // Brigadier's booleans: `on` and `off` are not words it reads.
        assertThrows(CommandSyntaxException::class.java) { run("feed dev writing off", level = 2, dev = true) }
    }

    @Test
    fun `every answer the command gives has its words in English and French`() {
        val keys = listOf(
            "added", "removed", "already", "already_not", "via_role", "not_linked", "implied_by_role", "not_allowed",
            "unavailable", "invalid_name", "list", "list.empty",
        ).map { "command.lnbphone_feed.announcer.$it" } + "command.lnbphone_feed.dev.set"
        for (language in listOf("en_us", "fr_fr")) {
            val text = javaClass.getResourceAsStream("/assets/lnbphone_feed/lang/$language.json")!!.reader(Charsets.UTF_8).readText()
            val strings = JsonParser.parseString(text).asJsonObject
            for (key in keys) assertTrue(strings.has(key), "$key in $language")
            for (key in listOf("notif.lnbphone_feed.announcement", "notif.lnbphone_feed.reply", "notif.lnbphone_feed.text", "lnbphone_feed.post.anonymous")) {
                assertTrue(strings.has(key), "$key in $language")
            }
        }
    }
}
