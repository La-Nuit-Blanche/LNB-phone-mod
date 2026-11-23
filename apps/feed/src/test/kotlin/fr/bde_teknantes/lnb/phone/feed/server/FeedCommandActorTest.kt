package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.server.FeedCommand.Actor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Who `/feed announcer` acts for. A `ServerPlayer` can't be made without a running server, so the decision is tested
 * on its inputs: where the command came from, what it runs as, and the permission level.
 */
class FeedCommandActorTest {
    @Test
    fun `a player who typed it acts for themselves`() {
        assertEquals(Actor.PLAYER, FeedCommand.actor(originIsPlayer = true, listed = true, runsAsOrigin = true, level4 = false))
        assertEquals(Actor.PLAYER, FeedCommand.actor(originIsPlayer = true, listed = true, runsAsOrigin = true, level4 = true))
    }

    @Test
    fun `execute as another entity never acts for a player, even from an operator of level 4`() {
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = true, listed = true, runsAsOrigin = false, level4 = false))
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = true, listed = true, runsAsOrigin = false, level4 = true))
    }

    @Test
    fun `command blocks and functions below level 4 get nothing, whatever they run as`() {
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = false, listed = false, runsAsOrigin = false, level4 = false))
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = false, listed = false, runsAsOrigin = true, level4 = false))
    }

    @Test
    fun `the console acts as the console, with no issuer, even through execute as a player`() {
        assertEquals(Actor.CONSOLE, FeedCommand.actor(originIsPlayer = false, listed = false, runsAsOrigin = true, level4 = true))
        assertEquals(Actor.CONSOLE, FeedCommand.actor(originIsPlayer = false, listed = false, runsAsOrigin = false, level4 = true))
    }

    @Test
    fun `a player the server's list doesn't hold, such as a mod's fake player with a moderator's UUID, gets nothing`() {
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = true, listed = false, runsAsOrigin = true, level4 = false))
        assertEquals(Actor.NONE, FeedCommand.actor(originIsPlayer = true, listed = false, runsAsOrigin = true, level4 = true))
    }
}
