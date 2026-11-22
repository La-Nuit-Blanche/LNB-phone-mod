package fr.bde_teknantes.lnb.phone.client.ui

/** Scrolling arithmetic, apart from drawing so it can be tested. All values in dp. */
internal object ScrollMath {
    /** Art direction 2.3: one wheel notch scrolls 36 dp. */
    const val NOTCH_DP = 36f
    const val THUMB_MIN = 24f
    const val IDLE_MS = 800f
    const val FADE_MS = 200f

    /** The largest offset: the content's end at the viewport's bottom, or 0 when it all fits. */
    fun maxOffset(contentHeight: Float, viewportHeight: Float): Float = (contentHeight - viewportHeight).coerceAtLeast(0f)

    fun clamp(offset: Float, contentHeight: Float, viewportHeight: Float): Float =
        offset.coerceIn(0f, maxOffset(contentHeight, viewportHeight))

    /** The offset after turning the wheel [notches] (positive towards the top). */
    fun wheel(offset: Float, notches: Float, contentHeight: Float, viewportHeight: Float): Float =
        clamp(offset - notches * NOTCH_DP, contentHeight, viewportHeight)

    /** Length of the scroll bar's thumb along a track as tall as the viewport. */
    fun thumbLength(contentHeight: Float, viewportHeight: Float): Float {
        if (contentHeight <= viewportHeight || contentHeight <= 0f) return viewportHeight
        return (viewportHeight * viewportHeight / contentHeight).coerceIn(THUMB_MIN, viewportHeight)
    }

    /** Top of the thumb in the track, for [offset]. */
    fun thumbTop(offset: Float, contentHeight: Float, viewportHeight: Float): Float {
        val max = maxOffset(contentHeight, viewportHeight)
        if (max <= 0f) return 0f
        val travel = viewportHeight - thumbLength(contentHeight, viewportHeight)
        return travel * (offset / max).coerceIn(0f, 1f)
    }

    /** The offset after the thumb is dragged by [deltaY] dp from where it was at [startOffset]. */
    fun dragThumb(startOffset: Float, deltaY: Float, contentHeight: Float, viewportHeight: Float): Float {
        val max = maxOffset(contentHeight, viewportHeight)
        val travel = viewportHeight - thumbLength(contentHeight, viewportHeight)
        if (max <= 0f || travel <= 0f) return 0f
        return (startOffset + deltaY * max / travel).coerceIn(0f, max)
    }

    /**
     * Opacity of the scroll bar (0 to 1) at [now], given when the list last scrolled and whether it is held (pointer
     * over the list, or thumb dragged): shown, then fading over 200 ms after 800 ms idle.
     */
    fun barAlpha(now: Float, lastActivity: Float, held: Boolean): Float {
        if (held) return 1f
        val idle = now - lastActivity
        return when {
            idle <= IDLE_MS -> 1f
            idle >= IDLE_MS + FADE_MS -> 0f
            else -> 1f - (idle - IDLE_MS) / FADE_MS
        }
    }

    /**
     * The offset that brings the band [top]..[bottom] (in content coordinates) fully into view with the least
     * movement; unchanged when it is already visible. A band taller than the viewport shows its top.
     */
    fun reveal(offset: Float, top: Float, bottom: Float, viewportHeight: Float, contentHeight: Float): Float {
        val target = when {
            top < offset -> top
            bottom > offset + viewportHeight -> minOf(top, bottom - viewportHeight)
            else -> offset
        }
        return clamp(target, contentHeight, viewportHeight)
    }
}
