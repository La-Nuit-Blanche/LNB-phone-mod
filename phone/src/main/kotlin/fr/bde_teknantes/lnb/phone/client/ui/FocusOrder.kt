package fr.bde_teknantes.lnb.phone.client.ui

/** Tab and Shift+Tab: which element of a screen's focus order gets the focus next. Plain logic, so it can be tested. */
internal object FocusOrder {
    /**
     * The element after [current] in [order] (before it when [backwards]), wrapping around at the ends. With nothing
     * focused (or a [current] no longer in the order), the first element (the last one when going backwards). Null
     * when there is nothing to focus.
     */
    fun <T> next(order: List<T>, current: T?, backwards: Boolean): T? {
        if (order.isEmpty()) return null
        val index = if (current == null) -1 else order.indexOf(current)
        if (index < 0) return if (backwards) order.last() else order.first()
        val step = if (backwards) -1 else 1
        return order[(index + step + order.size) % order.size]
    }
}
