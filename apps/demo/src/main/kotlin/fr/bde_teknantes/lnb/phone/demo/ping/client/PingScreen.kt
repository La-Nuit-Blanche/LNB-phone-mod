package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.Spacer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.api.client.ui.atRight
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import fr.bde_teknantes.lnb.phone.demo.ping.PingOutcome
import fr.bde_teknantes.lnb.phone.demo.ping.PingTarget
import fr.bde_teknantes.lnb.phone.demo.ping.SendPingPayload
import net.minecraft.client.resources.language.I18n
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * Writing to one player: a field for the ping, the send button, and the pings exchanged with them this session (theirs
 * on the left, the player's on the right). Everything that lasts lives in [PingSession]: the draft, the conversation,
 * whether a ping waits for the server's answer. The screen shows it, and catches up with it at every tick.
 */
class PingScreen(private val player: UUID, private val name: String) : KitScreen() {
    private val draft = PingSession.draft(player)
    private val field = TextField(draft)
    private val send = Button(I18n.get("lnbphone_demo.ping.send")) { send() }
    private val conversation = ScrollList()
    private var shownRevision = -1

    init {
        add(Header(name).at(0f, 0f))
        add(Avatar().apply {
            size = Avatar.Size.LARGE
            showPlayer(player, name)
        }.at(12f, 56f))
        add(Label(I18n.get("lnbphone_demo.ping.hint")).apply {
            style = TextStyle.CAPTION
            color = Colors.TEXT_2
            maxLines = 2
        }.at(72f, 64f, 172f, 32f))

        add(field.apply {
            placeholder = I18n.get("lnbphone_demo.ping.placeholder")
            onChange = { updateSend() }
            onSubmit = { send() }
        }.at(12f, 120f))
        // Wide enough for the longer of "Send" and "Envoyer".
        add(send.apply { fixedWidth = 88f }.atRight(244f, 168f))
        updateSend()

        add(Label(I18n.get("lnbphone_demo.ping.conversation")).apply {
            style = TextStyle.CAPTION_STRONG
            color = Colors.TEXT_2
        }.at(12f, 220f, 232f, 16f))
        add(conversation.at(12f, 240f, 232f, 130f))
        showConversation()
    }

    override fun onScreenShown() {
        // An answer that came while the player was elsewhere is old news.
        PingSession.takeOutcome(player)
        // Ready to type: the field takes the keys, so Backspace erases instead of going back.
        field.requestFocus()
    }

    override fun onTick() {
        when (PingSession.takeOutcome(player)) {
            PingOutcome.SENT -> {
                PhoneSounds.sent()
                showSnackbar(I18n.get("lnbphone_demo.ping.sent"))
            }
            PingOutcome.OFFLINE -> showErrorSnackbar(I18n.get("lnbphone_demo.ping.refused.offline", name))
            PingOutcome.TOO_FAST -> showErrorSnackbar(I18n.get("lnbphone_demo.ping.refused.too_fast"))
            null -> {}
        }
        showConversation()
    }

    // A ping from this player lands in the conversation on screen: the phone needs no banner over it.
    override fun showsTarget(target: String) = PingTarget.parse(target)?.player == player

    private fun updateSend() {
        send.enabled = TextLimits.canSend(draft.text, Ping.MAX_LENGTH)
    }

    private fun send() {
        val text = draft.text.trim()
        if (!TextLimits.canSend(text, Ping.MAX_LENGTH) || PingSession.isWaiting(player)) return
        PacketDistributor.sendToServer(SendPingPayload(player, text))
        PingSession.sending(player)
        // The server's answer adds the ping to the conversation, or puts the text back if it was refused.
        draft.clear()
    }

    /** Rebuilds the conversation when the session's has changed since it was last shown. */
    private fun showConversation() {
        if (shownRevision == PingSession.revision) return
        shownRevision = PingSession.revision
        conversation.removeAll()
        val messages = PingSession.conversation(player)
        if (messages.isEmpty()) conversation.add(MessageRow(I18n.get("lnbphone_demo.ping.conversation.empty")))
        for (message in messages) {
            conversation.add(Label(message.text).apply {
                maxLines = 3
                if (message.mine) {
                    align = Label.Align.END
                    color = Colors.TEXT_2
                }
            })
            conversation.add(Spacer(8f))
        }
        conversation.scrollTo(conversation.maxOffset)
    }
}
