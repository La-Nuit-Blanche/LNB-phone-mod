package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.client.ui.Placement

/**
 * A confirmation dialog (art direction 8.7): a card over a darkened viewport with a title, a text, a cancel button and
 * a confirm button. Show it with [KitScreen.showDialog].
 *
 * Confirming puts the confirm button in its loading state and calls [onConfirm] with the dialog; call [close] when the
 * action is over (the server answered, whatever it said), and the dialog fades out. Call it at once for an action
 * that has nothing to wait for (without [onConfirm], confirming just closes). While it loads, the dialog can't be
 * cancelled. Cancel, Esc and a click on the dark area call [onCancel] and close it.
 *
 * ```kotlin
 * showDialog(Dialog(tr("dialog.delete_post.title"), tr("dialog.cancel"), tr("dialog.delete.confirm")).apply {
 *     text = tr("dialog.delete_post.text")
 *     danger = true
 *     onConfirm = { dialog -> server.delete(post) { ok -> dialog.close(); if (!ok) showErrorSnackbar(tr("snackbar.action_failed")) } }
 * })
 * ```
 *
 * @param title Up to 2 lines.
 */
class Dialog(val title: String, cancelLabel: String, confirmLabel: String) : Widget(), Overlay {
    private val card = add(Card())
    private val cancelButton = card.add(Button(cancelLabel) { cancel() }.apply {
        kind = Button.Kind.SECONDARY
        fixedWidth = Placement.DIALOG_CANCEL_WIDTH
    })
    private val confirmButton = card.add(Button(confirmLabel) { confirm() }.apply { fixedWidth = Placement.DIALOG_CONFIRM_WIDTH })

    /** Up to 3 lines under the title; empty for none. */
    var text: String = ""

    /** A red confirm button, for what can't be undone. */
    var danger = false
        set(value) {
            field = value
            confirmButton.kind = if (value) Button.Kind.DANGER else Button.Kind.PRIMARY
        }

    /** What confirming does; it must [close] the dialog when done. Null closes it at once. */
    var onConfirm: ((Dialog) -> Unit)? = null

    /** What cancelling does, before the dialog closes. */
    var onCancel: (() -> Unit)? = null

    private var openedAt = 0f
    private var closedAt = Float.POSITIVE_INFINITY

    /** Whether the dialog is open: not yet closed (it may still be fading in). */
    val isOpen: Boolean get() = closedAt == Float.POSITIVE_INFINITY

    /** Whether the confirm button is waiting for [close]. */
    val isLoading: Boolean get() = confirmButton.loading

    internal fun opened(now: Float) {
        openedAt = now
        closedAt = Float.POSITIVE_INFINITY
        confirmButton.loading = false
        cancelButton.enabled = true
    }

    /** Whether the fade-out is over at [now]: the screen then takes the dialog away. */
    internal fun isGone(now: Float) = now >= closedAt + Motion.QUICK_MS

    /** Closes the dialog (fading out over 100 ms). */
    fun close() {
        if (!isOpen) return
        closedAt = now
        screen?.let { if (contains(it.focused)) it.focus(null) }
    }

    /** What cancel, Esc and the dark area do: [onCancel], then [close]. Ignored while loading. */
    fun cancel() {
        if (!isOpen || confirmButton.loading) return
        close()
        onCancel?.invoke()
    }

    /** What the confirm button does: its loading state, then [onConfirm]. Ignored while loading or closed. */
    fun confirm() {
        if (!isOpen || confirmButton.loading) return
        val action = onConfirm ?: return close()
        confirmButton.loading = true
        cancelButton.enabled = false
        action(this)
    }

    override val catchesPointer get() = true

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        // The dark area around the card. A press in the first 150 ms is the end of a double click that opened the
        // dialog (on a menu item), not a wish to cancel it.
        if (now - openedAt >= Motion.SHORT_MS) cancel()
        return true
    }

    override fun mouseScrolled(x: Float, y: Float, notches: Float): Boolean = true

    // Until it is gone, the dialog keeps the pointer: a second click while it fades out must not reach what is behind.
    override fun hitTest(localX: Float, localY: Float): Widget? {
        if (isOpen) return super.hitTest(localX, localY)
        return if (localX >= 0f && localY >= 0f && localX < width && localY < height) this else null
    }

    override fun update() {
        val inner = Placement.DIALOG_WIDTH - 2 * Placement.DIALOG_PADDING
        val titleLines = TextMetrics.wrap(title, TextStyle.TITLE_M, inner, Placement.DIALOG_MAX_TITLE_LINES)
        val textLines = if (text.isEmpty()) emptyList() else TextMetrics.wrap(text, TextStyle.BODY, inner, Placement.DIALOG_MAX_TEXT_LINES)
        val h = Placement.dialogHeight(titleLines.size, textLines.size)
        card.titleLines = titleLines
        card.textLines = textLines
        card.setBounds(Placement.DIALOG_X, Placement.dialogTop(h), Placement.DIALOG_WIDTH, h)
        val buttonsY = h - Placement.DIALOG_PADDING - 32f
        val right = Placement.DIALOG_WIDTH - Placement.DIALOG_PADDING
        confirmButton.atRight(right, buttonsY)
        cancelButton.atRight(right - Placement.DIALOG_CONFIRM_WIDTH - 8f, buttonsY)
    }

    override fun layout() = update()

    private fun alpha(): Float = if (isOpen) {
        ((now - openedAt) / Motion.SHORT_MS).coerceIn(0f, 1f)
    } else {
        1f - ((now - closedAt) / Motion.QUICK_MS).coerceIn(0f, 1f)
    }

    override fun render(canvas: AppCanvas) {
        canvas.pushAlpha(alpha())
        canvas.fillRect(0f, 0f, width, height, Colors.SCRIM)
    }

    override fun renderOver(canvas: AppCanvas) {
        canvas.popAlpha()
    }

    private class Card : Widget() {
        var titleLines: List<String> = emptyList()
        var textLines: List<String> = emptyList()

        override val catchesPointer get() = true

        // A press on the card (its text, its padding) stops here: unused, it would reach the scrim and cancel.
        override fun mousePressed(x: Float, y: Float, button: Int): Boolean = true

        override fun render(canvas: AppCanvas) {
            canvas.drawShadow(Shadow.SHEET, 0f, 0f, width, height)
            canvas.fillRoundRect(0f, 0f, width, height, Radius.LARGE, Colors.SURFACE_2)
            val pad = Placement.DIALOG_PADDING
            var y = pad
            for (line in titleLines) {
                canvas.drawText(line, pad, y, TextStyle.TITLE_M, Colors.TEXT_1)
                y += TextStyle.TITLE_M.lineHeight
            }
            if (textLines.isNotEmpty()) y += 8f
            for (line in textLines) {
                canvas.drawText(line, pad, y, TextStyle.BODY, Colors.TEXT_2)
                y += TextStyle.BODY.lineHeight
            }
        }
    }
}
