package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import fr.bde_teknantes.lnb.phone.demo.ping.PingOutcome
import java.util.UUID

/**
 * What Ping keeps while the player stays connected. The phone builds new screens at every visit (from home, from a
 * notification), so anything a screen holds in its own fields is gone the next time; the screens read and write here
 * instead, and the payload handlers write here too. Cleared when the player disconnects. Client thread only.
 */
object PingSession {
    /** The most messages kept per conversation: pings keep coming, memory doesn't. */
    const val MAX_MESSAGES = 50

    /** One line of a conversation: sent by the player ([mine]) or received. */
    class Message(val text: String, val mine: Boolean)

    /** How far the list of players is scrolled. */
    val playersScroll = ScrollState()

    private val drafts = HashMap<UUID, TextState>()
    private val conversations = HashMap<UUID, ArrayDeque<Message>>()
    private val waiting = HashSet<UUID>()
    private val outcomes = HashMap<UUID, PingOutcome>()

    /** Goes up at every change of a conversation, so that screens know when to show them again. */
    var revision = 0
        private set

    /** The unsent text for [player]: the field shows it again when the player comes back. */
    fun draft(player: UUID): TextState = drafts.getOrPut(player) { TextState("", multiline = false, maxLength = Ping.MAX_LENGTH) }

    /** The pings exchanged with [player] since the player connected, oldest first. */
    fun conversation(player: UUID): List<Message> = conversations[player].orEmpty()

    /** Whether a ping to [player] waits for the server's answer. */
    fun isWaiting(player: UUID) = player in waiting

    /** A ping to [player] has left; its answer comes to [answered]. */
    fun sending(player: UUID) {
        waiting += player
    }

    /** The server's answer to a ping of [text] to [player]. Called by the payload handler. */
    fun answered(player: UUID, text: String, outcome: PingOutcome) {
        waiting -= player
        outcomes[player] = outcome
        if (outcome == PingOutcome.SENT) {
            add(player, Message(text, mine = true))
        } else {
            // Refused: the text comes back to the field, unless the player has started another one.
            val draft = draft(player)
            if (draft.text.isEmpty()) draft.text = text
        }
    }

    /** The last answer about a ping to [player] that no screen has shown yet, taken. */
    fun takeOutcome(player: UUID): PingOutcome? = outcomes.remove(player)

    /** [player] pinged this player with [text]. Called by the payload handler. */
    fun received(player: UUID, text: String) = add(player, Message(text, mine = false))

    private fun add(player: UUID, message: Message) {
        val messages = conversations.getOrPut(player) { ArrayDeque() }
        messages.addLast(message)
        if (messages.size > MAX_MESSAGES) messages.removeFirst()
        revision++
    }

    fun clear() {
        playersScroll.offset = 0f
        drafts.clear()
        conversations.clear()
        waiting.clear()
        outcomes.clear()
        revision++
    }
}
