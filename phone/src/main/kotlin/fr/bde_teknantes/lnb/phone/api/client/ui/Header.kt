package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * The app header (art direction 8.7): 44 dp at the top of the viewport, the title in large type, a back button on
 * every screen but the app's first, an optional button at the right, a divider at the bottom. Place it at (0, 0); it
 * takes the full width.
 *
 * ```kotlin
 * add(Header(tr("thread.title")).at(0f, 0f))                                     // back button on its own
 * add(Header(null, leading = IconButton(Icon.CLOSE) { phone.pop() },            // Compose: close, no title,
 *            trailing = Button(tr("compose.post"), fixedWidth = 88f) { send() }).at(0f, 0f))   // a primary button
 * ```
 *
 * @param title The title, cut with an ellipsis before the trailing button; null for none.
 * @param leading A button in the back button's place (32 dp at (6, 6)), such as a close button. When null, the back
 * button shows whenever there is a screen to go back to, and goes back.
 * @param trailing An [IconButton] (placed at (218, 6)) or a [Button] (right-aligned to x 244, at y 6), or null.
 * @param showBack Forces the back button on or off, when [leading] is null; null shows it when the phone can go back.
 * @param onBack What the back button does, when something must happen first (a composer collapses before the screen
 * goes back); null goes back with [Phone.pop][fr.bde_teknantes.lnb.phone.api.client.Phone.pop].
 */
class Header(
    title: String?,
    leading: Clickable? = null,
    trailing: Clickable? = null,
    private val showBack: Boolean? = null,
    private val onBack: (() -> Unit)? = null,
) : Widget() {
    var title: String? = title

    private val back: IconButton? = if (leading == null) add(IconButton(Icon.BACK) { onBack?.invoke() ?: screen?.goBack() }) else null

    /** The button at the left: the one given, or the back button. */
    val leading: Clickable? = leading?.let { add(it) } ?: back

    /** The button at the right, if any. */
    val trailing: Clickable? = trailing?.let { add(it) }

    override val preferredWidth get() = 256f
    override val preferredHeight get() = HEIGHT

    override fun layout() {
        leading?.at(6f, 6f)
        when (val t = trailing) {
            is IconButton -> t.at(218f, 6f)
            null -> {}
            else -> t.atRight(width - 12f, 6f)
        }
    }

    override fun update() {
        back?.visible = showBack ?: (screen?.canGoBack == true)
    }

    override fun render(canvas: AppCanvas) {
        canvas.fillRect(0f, 0f, width, height, Colors.BG)
        canvas.hairline(0f, width, height - 1f)
        val text = title ?: return
        val x = if (leading?.visible == true) 44f else 12f
        val right = trailing?.takeIf { it.visible }?.let { it.x - 12f } ?: (width - 12f)
        canvas.drawTextTruncated(text, x, 11f, right - x, TextStyle.TITLE_L, Colors.TEXT_1)
    }

    companion object {
        const val HEIGHT = 44f
    }
}
