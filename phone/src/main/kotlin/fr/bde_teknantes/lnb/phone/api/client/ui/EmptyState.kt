package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * What a list shows when it has nothing (art direction 8.7), centred in the widget's area: a 72 dp disc with a 40 dp
 * icon, a title, a text, and optionally a button. Give it the content area (under the header or tabs).
 *
 * The same block makes the error state of a list that can't load: an icon such as [Icon.GLOBE], a title and text
 * saying so, and a [Button.Kind.SECONDARY] retry button of 104 dp, set [loading][Button.loading] while it retries.
 *
 * ```kotlin
 * add(EmptyState(Icon.INBOX, I18n.get("feed.empty.global.title")).apply {
 *     text = I18n.get("feed.empty.global.text")
 *     button = Button(I18n.get("feed.empty.global.button")) { openCompose() }.apply { fixedWidth = 136f }
 * }.at(0f, 44f, 256f, 326f))
 * ```
 *
 * @param icon Drawn at 40 dp: use an icon that has that size.
 * @param title Up to 2 lines of 200 dp.
 */
class EmptyState(var icon: Icon, var title: String) : Widget() {
    /** Up to 2 lines of 200 dp under the title; empty for none. */
    var text: String = ""

    /** The button under the text, if any. */
    var button: Button? = null
        set(value) {
            field?.let(::remove)
            field = value?.let { add(it) }
        }

    override val preferredWidth get() = 256f

    private fun titleLines() = TextMetrics.wrap(title, TextStyle.TITLE_M, MAX_WIDTH, 2)

    private fun textLines() = if (text.isEmpty()) emptyList() else TextMetrics.wrap(text, TextStyle.BODY, MAX_WIDTH, 2)

    /** Height of the block: disc, 16, title, 4, text, then 16 and the button. */
    private fun blockHeight(titles: Int, texts: Int): Float =
        DISC + 16f + titles * TextStyle.TITLE_M.lineHeight +
            (if (texts > 0) 4f + texts * TextStyle.BODY.lineHeight else 0f) +
            (if (button != null) 16f + Button.HEIGHT else 0f)

    private fun top(titles: Int, texts: Int) = evenFloor((height - blockHeight(titles, texts)) / 2f)

    // The texts may change after placement (an error state's message): place the button again every frame.
    override fun update() = layout()

    override fun layout() {
        val b = button ?: return
        val titles = titleLines().size
        val texts = textLines().size
        val y = top(titles, texts) + blockHeight(titles, texts) - Button.HEIGHT
        b.at(evenFloor((width - b.preferredWidth) / 2f), y)
    }

    override fun render(canvas: AppCanvas) {
        val titles = titleLines()
        val texts = textLines()
        var y = top(titles.size, texts.size)
        val discX = (width - DISC) / 2f
        canvas.fillCircle(discX, y, DISC, Colors.SURFACE)
        canvas.drawIcon(icon, discX + 16f, y + 16f, Colors.TEXT_3, 40f)
        y += DISC + 16f
        for (line in titles) {
            canvas.drawTextCentered(line, width / 2f, y, TextStyle.TITLE_M, Colors.TEXT_1)
            y += TextStyle.TITLE_M.lineHeight
        }
        if (texts.isNotEmpty()) y += 4f
        for (line in texts) {
            canvas.drawTextCentered(line, width / 2f, y, TextStyle.BODY, Colors.TEXT_2)
            y += TextStyle.BODY.lineHeight
        }
    }

    private companion object {
        const val DISC = 72f
        const val MAX_WIDTH = 200f

        /** Down to an even dp, so the disc and the button stay on whole GUI pixels. */
        fun evenFloor(v: Float) = Math.floor((v / 2f).toDouble()).toFloat() * 2f
    }
}
