package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.UnavailableBackend
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The site is called from a dedicated server only; the memory backend works anywhere. */
class BackendChoiceTest {
    @Test
    fun `the site backend runs on a dedicated server only, the memory backend anywhere`() {
        assertEquals(FeedServer.Choice.SITE, FeedServer.choose(FeedConfig.Kind.SITE, dedicated = true))
        assertEquals(FeedServer.Choice.NONE, FeedServer.choose(FeedConfig.Kind.SITE, dedicated = false))
        assertEquals(FeedServer.Choice.MEMORY, FeedServer.choose(FeedConfig.Kind.MEMORY, dedicated = false))
        assertEquals(FeedServer.Choice.MEMORY, FeedServer.choose(FeedConfig.Kind.MEMORY, dedicated = true))
    }

    @Test
    fun `without a site, Feed is unavailable to the player and calls nothing`() {
        val host = FakeHost()
        val player = UUID(1, 1)
        host.online += player
        val service = FeedService(UnavailableBackend("not a dedicated server"), host, RecordingSink())
        service.start()
        service.onJoin(player)
        service.handleOpen(player)
        service.handleRequest(player, LoadFeedPayload(1, FeedKind.GLOBAL, null))
        assertEquals(Availability.UNAVAILABLE, host.received<StatePayload>(player).single().available)
        assertEquals(Outcome.UNAVAILABLE, host.received<PagePayload>(player).single().outcome)
        assertTrue(host.commandRefreshes.isEmpty())
    }
}
