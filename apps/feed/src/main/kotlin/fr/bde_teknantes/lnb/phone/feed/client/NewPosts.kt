package fr.bde_teknantes.lnb.phone.feed.client

/** Where a new post's place lies against the visible part of its list (art direction 9.5). */
enum class Place {
    /** It would be fully visible once inserted: it goes in at once, highlighted. */
    VISIBLE,

    /** Above the visible area (the list is scrolled past it): it waits, and the pill has the arrow. */
    ABOVE,

    /** Below the visible area, or only partly visible at its bottom (Annonces under the pins): it waits, no arrow. */
    BELOW,
}

/** The rule of new posts while a feed is open (art direction 9.5), on numbers alone. */
object NewPosts {
    /** Half a dp of slack: a list scrolled to 0.3 dp is at its top. */
    private const val SLACK = 0.5f

    /**
     * Where a post [height] dp tall, inserted at [top] dp from the start of a list scrolled by [offset] and [viewport]
     * dp tall, would stand.
     */
    fun place(top: Float, height: Float, offset: Float, viewport: Float): Place = when {
        top < offset - SLACK -> Place.ABOVE
        top + height <= offset + viewport + SLACK -> Place.VISIBLE
        else -> Place.BELOW
    }

    /** The offset that makes the rows [top]..[top]+[height] fully visible with the least scrolling. */
    fun revealOffset(top: Float, height: Float, offset: Float, viewport: Float): Float = when {
        top < offset -> top
        top + height > offset + viewport -> top + height - viewport
        else -> offset
    }
}
