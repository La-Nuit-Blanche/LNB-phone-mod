package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Easing
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.Interaction
import fr.bde_teknantes.lnb.phone.api.client.ui.KitDrawing
import fr.bde_teknantes.lnb.phone.api.client.ui.LabeledToggle
import fr.bde_teknantes.lnb.phone.api.client.ui.Motion
import fr.bde_teknantes.lnb.phone.api.client.ui.Radius
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Tween
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.api.client.ui.atRight
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.FeedWriting
import fr.bde_teknantes.lnb.phone.feed.client.ReplyDraft
import fr.bde_teknantes.lnb.phone.feed.client.ThreadLayout

/**
 * The reply composer docked at the bottom of a thread (art direction 9.7), on the thread's [draft]:
 *
 * - **collapsed**, 64 dp: a field-like box with `Répondre à {name}…`, which expands when clicked;
 * - **expanded**, 200 dp: `En réponse à {name}`, the text area with its `n/280` counter, the `Anonyme` toggle and the
 *   `Répondre` button, disabled while the text can't be sent ([FeedWriting.canSend]).
 *
 * It moves between the two in 150 ms (ease-out opening, ease-in closing) and places itself at the bottom of the
 * viewport, so place it nowhere; the screen reads [height] for the list's spacer and what shows of the list. While the
 * draft is sending, the button loads, the text area is in its sending state and the toggle is disabled.
 *
 * @param targetName the display name the composer answers now, null for `Anonyme`.
 * @param onSend the button, or Ctrl+Enter, with a text that can be sent.
 */
class ReplyComposer(
    private val draft: ReplyDraft,
    private val strings: FeedStrings,
    private val targetName: () -> String?,
    private val onSend: () -> Unit,
) : Widget() {
    private val size = Tween(ThreadLayout.COLLAPSED)

    /** Whether the composer is expanded, or expanding. */
    var expanded = false
        private set

    private val field = add(CollapsedField().apply { onClick = { expand(focus = true) } })
    val area: TextArea = add(TextArea(draft.text).apply {
        limit = Feed.MAX_TEXT
        onSubmit = { if (button.isEnabled && !draft.sending) send() }
    })
    val anonymous: LabeledToggle = add(LabeledToggle(strings.get("reply.anonymous")).apply {
        toggleFirst = true
        toggle.onChange = { draft.anonymous = it }
    })
    val button: Button = add(Button(strings.get("reply.send")) { send() }.apply { fixedWidth = SEND_WIDTH })

    init {
        showContent()
    }

    /** The composer's height now (it animates). */
    override val preferredHeight get() = size.value(now)
    override val preferredWidth get() = 256f

    /** The height it is going to: 64 or 200 dp. */
    val targetHeight: Float get() = size.target

    /** Opens the composer (150 ms, ease-out); with [focus], the text area takes the keyboard. */
    fun expand(focus: Boolean) {
        if (!expanded) {
            expanded = true
            animate(ThreadLayout.EXPANDED, Easing.OUT)
            showContent()
        }
        if (focus) screen?.focus(area)
    }

    /** Closes the composer (150 ms, ease-in); the draft stays. */
    fun collapse() {
        if (!expanded) return
        expanded = false
        screen?.let { if (contains(it.focused)) it.focus(null) }
        animate(ThreadLayout.COLLAPSED, Easing.IN)
        showContent()
    }

    private fun animate(to: Float, easing: Easing) {
        if (shouldAnimate) size.animateTo(to, now, Motion.SHORT_MS, easing) else size.snapTo(to)
    }

    private fun showContent() {
        field.visible = !expanded
        area.visible = expanded
        anonymous.visible = expanded
        button.visible = expanded
    }

    private fun send() {
        if (!FeedWriting.canSend(draft.text.text) || draft.sending) return
        onSend()
    }

    override fun update() {
        val h = size.value(now)
        setBounds(0f, ThreadLayout.dockTop(h), 256f, h)
        // The draft's state, as the session holds it.
        button.loading = draft.sending
        button.enabled = draft.sending || FeedWriting.canSend(draft.text.text)
        area.sending = draft.sending
        anonymous.enabled = !draft.sending
        anonymous.toggle.checked = draft.anonymous
        field.placeholder = strings.get("reply.placeholder", FeedWriting.nameOrAnonymous(targetName(), strings))
        field.draftText = draft.text.text
    }

    override fun layout() {
        field.at(12f, 12f, 232f, 40f)
        area.at(12f, 32f, 232f, 112f)
        anonymous.at(12f, 156f, ANONYMOUS_WIDTH, 32f)
        button.atRight(244f, 156f)
    }

    override val catchesPointer get() = true

    override fun render(canvas: AppCanvas) {
        canvas.fillRect(0f, 0f, width, height, Colors.BG)
        canvas.hairline(0f, width, 0f)
        if (expanded) {
            val line = strings.get("post.reply_to", FeedWriting.nameOrAnonymous(targetName(), strings))
            canvas.drawTextTruncated(line, 12f, 12f, 232f, TextStyle.CAPTION, Colors.TEXT_2)
        }
    }

    // Only what fits in the composer's height shows while it opens or closes.
    override fun renderChildren(canvas: AppCanvas) {
        canvas.pushClip(0f, 0f, width, height)
        super.renderChildren(canvas)
        canvas.popClip()
    }

    /**
     * The collapsed composer's field (art direction 9.7): a text field's look (fill, border, its hover), with the
     * placeholder, or the start of a draft left in it, cut with `…` before the field's right padding. A click (or
     * Enter, Space) expands the composer: nothing is typed here.
     */
    class CollapsedField : Clickable() {
        var placeholder = ""
        var draftText = ""

        override fun render(canvas: AppCanvas) {
            val hot = interaction == Interaction.HOVER || interaction == Interaction.PRESSED
            canvas.fillRoundRect(0f, 0f, width, height, Radius.SMALL, Colors.FIELD)
            canvas.strokeRoundRect(0f, 0f, width, height, Radius.SMALL, 1f, if (hot) Colors.STROKE_HOVER else Colors.STROKE)
            if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.SMALL)
            val y = (height - TextStyle.BODY.lineHeight) / 2f
            val firstLine = draftText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            if (firstLine.isNullOrEmpty()) {
                canvas.drawTextTruncated(placeholder, PADDING, y, width - 2 * PADDING, TextStyle.BODY, Colors.TEXT_3)
            } else {
                canvas.drawTextTruncated(firstLine, PADDING, y, width - 2 * PADDING, TextStyle.BODY, Colors.TEXT_1)
            }
        }

        private companion object {
            const val PADDING = 12f
        }
    }

    companion object {
        /** `Répondre` / `Reply`, sized for the longer (art direction 13.3). */
        const val SEND_WIDTH = 96f

        /** The toggle and its label, clear of the button. */
        const val ANONYMOUS_WIDTH = 120f
    }
}

/**
 * What takes the reply composer's place for a guest, a banned player, or while writing is off (art direction 9.11): a
 * 40 dp strip with a lock and why. Not clickable: no hover, no press, no focus; it only keeps clicks from reaching the
 * list under it.
 */
class ReadOnlyStrip(var text: String) : Widget() {
    override val preferredWidth get() = 256f
    override val preferredHeight get() = ThreadLayout.STRIP
    override val catchesPointer get() = true

    override fun update() {
        setBounds(0f, ThreadLayout.dockTop(ThreadLayout.STRIP), 256f, ThreadLayout.STRIP)
    }

    override fun render(canvas: AppCanvas) {
        canvas.fillRect(0f, 0f, width, height, Colors.BG)
        canvas.hairline(0f, width, 0f)
        canvas.drawIcon(Icon.LOCK, 12f, 12f, Colors.TEXT_3, 16f)
        canvas.drawTextTruncated(text, 36f, 12f, 244f - 36f, TextStyle.CAPTION, Colors.TEXT_3)
    }
}
