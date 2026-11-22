package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import java.util.UUID

/**
 * What Ping keeps while the player stays connected. The phone builds new screens at every visit (from home, from a
 * notification), so anything a screen holds in its own fields is gone the next time; the screens read and write here
 * instead. Cleared when the player disconnects. Client thread only.
 */
object PingSession {
    /** One line of a conversation: sent by the player ([mine]) or received. */
    class Message(val text: String, val mine: Boolean)

    /** How far the list of players is scrolled. */
    val playersScroll = ScrollState()

    private val drafts = HashMap<UUID, TextState>()
    private val conversations = HashMap<UUID, MutableList<Message>>()

    /** The unsent text for [player]: the field shows it again when the player comes back. */
    fun draft(player: UUID): TextState = drafts.getOrPut(player) { TextState("", false, Ping.MAX_LENGTH) }

    /** The pings exchanged with [player] since the player connected, oldest first. */
    fun conversation(player: UUID): List<Message> = conversations[player].orEmpty()

    fun sent(player: UUID, text: String) {
        conversations.getOrPut(player) { ArrayList() } += Message(text, mine = true)
    }

    /** Called by the payload handler, on the client thread. */
    fun received(player: UUID, text: String) {
        conversations.getOrPut(player) { ArrayList() } += Message(text, mine = false)
    }

    fun clear() {
        playersScroll.offset = 0f
        drafts.clear()
        conversations.clear()
    }
}
