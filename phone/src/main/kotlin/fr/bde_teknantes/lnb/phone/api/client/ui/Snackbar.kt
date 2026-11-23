package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.client.ui.Placement

/**
 * The button of a snackbar, such as "Retry". A click runs [onClick] and hides the snackbar.
 */
class SnackbarAction(val label: String, val onClick: () -> Unit)

/**
 * The snackbar (art direction 8.7): 240 x 40 dp at (8, 322), sliding up in 150 ms, held 3 s (6 s with an action),
 * fading out in 150 ms. Shown with [KitScreen.showSnackbar]. Its timeline is fixed when it is created, so its state at
 * any time is a pure function of the clock.
 */
internal class Snackbar(
    private val text: String,
    private val action: SnackbarAction?,
    private val error: Boolean,
    private val shownAt: Float,
    private val slideIn: Boolean,
) : Widget() {
    private val actionButton = action?.let { a ->
        add(Button(a.label) {
            fadeFrom = now
            a.onClick()
        }.apply { kind = Button.Kind.TEXT })
    }
    private val inEnd = shownAt + if (slideIn) Motion.SHORT_MS else 0f
    private val holdEnd = inEnd + if (action != null) HOLD_WITH_ACTION_MS else HOLD_MS
    private var fadeFrom = holdEnd

    init {
        val box = Placement.SNACKBAR
        setBounds(box.x, box.y, box.w, box.h)
        actionButton?.atRight(box.w - 4f, 4f)
    }

    override val catchesPointer get() = true

    /** Whether it is on screen at [now]. */
    fun isShowing(now: Float) = now < fadeFrom + Motion.SHORT_MS

    /** Whether it is gone at [now], faded out. */
    fun isOver(now: Float) = !isShowing(now)

    /** How far the FAB should be lifted at [now], 0 to 1: up while the snackbar slides in, down while it fades out. */
    fun lift(now: Float): Float = when {
        now < fadeFrom -> Easing.OUT.ease(((now - shownAt) / Motion.SHORT_MS).coerceIn(0f, 1f)).let { if (slideIn) it else 1f }
        else -> 1f - Easing.OUT.ease(((now - fadeFrom) / Motion.SHORT_MS).coerceIn(0f, 1f))
    }

    override fun hitTest(localX: Float, localY: Float): Widget? = if (now < fadeFrom) super.hitTest(localX, localY) else null

    override fun update() {
        // Slides up from just below the viewport; the hit box follows.
        val box = Placement.SNACKBAR
        val t = if (slideIn) Easing.OUT.ease(((now - shownAt) / Motion.SHORT_MS).coerceIn(0f, 1f)) else 1f
        val y = Placement.VIEWPORT_H + (box.y - Placement.VIEWPORT_H) * t
        setBounds(box.x, y, box.w, box.h)
    }

    private fun alpha() = if (now < fadeFrom) 1f else 1f - ((now - fadeFrom) / Motion.SHORT_MS).coerceIn(0f, 1f)

    override fun render(canvas: AppCanvas) {
        canvas.pushAlpha(alpha())
        canvas.drawShadow(Shadow.SHEET, 0f, 0f, width, height)
        canvas.fillRoundRect(0f, 0f, width, height, Radius.SMALL, Colors.SURFACE_3)
        val textX = if (error) 38f else 14f
        if (error) canvas.drawIcon(Icon.ALERT, 14f, 12f, Colors.DANGER, 16f)
        // The text button's own 8 dp padding is the gap before its label (art direction 8.7): no more is added.
        val right = actionButton?.x ?: (width - 14f)
        canvas.drawTextTruncated(text, textX, 11f, right - textX, TextStyle.BODY, Colors.TEXT_1)
    }

    override fun renderOver(canvas: AppCanvas) {
        canvas.popAlpha()
    }

    /** What a snackbar is, with where it is in its timeline: handed from a screen to the next. */
    class Saved(val text: String, val action: SnackbarAction?, val error: Boolean, val elapsed: Float, val fadeAfter: Float)

    fun saved(now: Float) = Saved(text, action, error, now - shownAt, fadeFrom - shownAt)

    companion object {
        const val HOLD_MS = 3000f
        const val HOLD_WITH_ACTION_MS = 6000f

        /** The snackbar [saved] described, carrying on from where it was at [now]. */
        fun restore(saved: Saved, now: Float): Snackbar {
            val shownAt = now - saved.elapsed
            return Snackbar(saved.text, saved.action, saved.error, shownAt, slideIn = saved.elapsed < Motion.SHORT_MS).also {
                it.fadeFrom = shownAt + saved.fadeAfter
            }
        }
    }
}
