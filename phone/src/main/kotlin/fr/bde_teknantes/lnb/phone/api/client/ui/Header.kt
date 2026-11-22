package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * The app header (art direction 8.7): 44 dp at the top of the viewport, the title in large type, a back button on
 * every screen but the app's first, an optional button at the right, a divider at the bottom. Place it at (0, 0); it
 * takes the full width.
 *
 * ```kotlin
 * add(Header(I18n.get("feed.thread.title")).at(0f, 0f))                            // back button on its own
 * add(Header(null).apply {                                                          // Compose: close, no title,
 *     leading = IconButton(Icon.CLOSE) { phone.pop() }                              // and a primary button
 *     trailing = Button(I18n.get("feed.compose.post")) { send() }.apply { fixedWidth = 88f }
 * }.at(0f, 0f))
 * ```
 *
 * The back button shows by itself whenever there is a screen to go back to, and calls the screen's [KitScreen.onBack]
 * before going back, as Backspace does.
 *
 * @param title The title, cut with an ellipsis before the trailing button; null for none.
 */
class Header(var title: String?) : Widget() {
    private val back = add(IconButton(Icon.BACK) { screen?.goBack() })

    /**
     * A button in the back button's place (32 dp at (6, 6)), such as a close button. When null, the back button shows
     * whenever there is a screen to go back to.
     */
    var leading: Clickable? = null
        set(value) {
            field?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    /** An [IconButton] (placed at (218, 6)) or a [Button] (right-aligned to x 244, at y 6), or null. */
    var trailing: Clickable? = null
        set(value) {
            field?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    /** Forces the back button on or off, when there is no [leading] button; null shows it when the phone can go back. */
    var showBack: Boolean? = null

    override val preferredWidth get() = 256f
    override val preferredHeight get() = HEIGHT

    override fun layout() {
        back.at(6f, 6f)
        leading?.at(6f, 6f)
        when (val t = trailing) {
            is IconButton -> t.at(218f, 6f)
            null -> {}
            else -> t.atRight(width - 12f, 6f)
        }
    }

    override fun update() {
        back.visible = leading == null && (showBack ?: (screen?.canGoBack == true))
        // A trailing button whose label changed keeps its right edge.
        layout()
    }

    override fun render(canvas: AppCanvas) {
        canvas.fillRect(0f, 0f, width, height, Colors.BG)
        canvas.hairline(0f, width, height - 1f)
        val text = title ?: return
        val start = leading ?: back.takeIf { it.visible }
        val x = if (start?.visible == true) 44f else 12f
        val right = trailing?.takeIf { it.visible }?.let { it.x - 12f } ?: (width - 12f)
        canvas.drawTextTruncated(text, x, 11f, right - x, TextStyle.TITLE_L, Colors.TEXT_1)
    }

    companion object {
        val HEIGHT = 44f
    }
}
