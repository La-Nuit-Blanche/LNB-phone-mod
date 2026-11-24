package fr.bde_teknantes.lnb.phone.feed.client

/** List bookkeeping the feed and the thread share, on plain values so that tests reach it. */
object Rows {
    /**
     * The rows to show: [desired], with the rows of [kept] that are in [current] and not in [desired] (cells folding
     * away after a delete) put back where they stood, after the row that preceded them in [current], or first when
     * nothing kept precedes them.
     */
    fun <T> keepInPlace(desired: List<T>, current: List<T>, kept: Set<T>): List<T> {
        if (kept.isEmpty()) return desired
        val out = desired.toMutableList()
        val wanted = desired.toHashSet()
        var previous: T? = null
        for (row in current) {
            if (row in kept && row !in wanted) {
                val at = if (previous == null) 0 else out.indexOf(previous) + 1
                out.add(at, row)
                previous = row
            } else if (row in out) {
                previous = row
            }
        }
        return out
    }
}
