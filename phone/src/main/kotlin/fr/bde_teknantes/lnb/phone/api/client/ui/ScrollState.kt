package fr.bde_teknantes.lnb.phone.api.client.ui

/**
 * How far a [ScrollList] is scrolled, in dp from the top. A list creates its own unless you give it one: give it one
 * kept outside the screen when the position must survive the screen (the feed the player comes back to after a
 * thread opened from a notification). Plain data, no game classes.
 */
class ScrollState(offset: Float = 0f) {
    /** Distance scrolled from the top, in dp; the list keeps it between 0 and its maximum. */
    var offset: Float = offset.coerceAtLeast(0f)
}
