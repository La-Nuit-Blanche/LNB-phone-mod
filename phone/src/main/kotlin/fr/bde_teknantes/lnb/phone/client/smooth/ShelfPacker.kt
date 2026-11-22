package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * Packs rectangles into a [width] x [height] page in rows ("shelves"). Glyphs and shape tiles are small and of
 * similar heights, so shelves waste little room, and nothing is ever removed from a page: the atlas is cleared as a
 * whole instead.
 *
 * Every rectangle keeps [padding] empty pixels to its right and below it (and the page keeps them on its top and left
 * edge), so sampling at a rectangle's border never reads a neighbour.
 */
class ShelfPacker(val width: Int, val height: Int, private val padding: Int = 1) {
    private class Shelf(val y: Int, val height: Int, var x: Int)

    private val shelves = ArrayList<Shelf>()
    private var nextShelfY = padding

    /** Top-left corner of a free [w] x [h] area as `x to y`, or null when the page is full. */
    fun insert(w: Int, h: Int): Pair<Int, Int>? {
        require(w > 0 && h > 0) { "Size must be positive, was ${w}x$h" }
        if (w + 2 * padding > width) return null
        // The tightest shelf that holds the rectangle without wasting more than half its own height.
        var best: Shelf? = null
        for (shelf in shelves) {
            if (shelf.height < h || shelf.height > h * 2 || shelf.x + w + padding > width) continue
            if (best == null || shelf.height < best.height) best = shelf
        }
        val shelf = best ?: openShelf(h) ?: return null
        val x = shelf.x
        shelf.x += w + padding
        return x to shelf.y
    }

    private fun openShelf(h: Int): Shelf? {
        if (nextShelfY + h + padding > height) return null
        return Shelf(nextShelfY, h, padding).also {
            shelves.add(it)
            nextShelfY += h + padding
        }
    }
}
