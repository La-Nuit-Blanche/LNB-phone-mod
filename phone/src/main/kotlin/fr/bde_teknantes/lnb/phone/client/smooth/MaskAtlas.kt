package fr.bde_teknantes.lnb.phone.client.smooth

import net.minecraft.client.renderer.texture.DynamicTexture

/**
 * Textures holding everything the smooth layer draws from memory: glyphs, shape tiles and avatars, all at the
 * screen's own pixel size and drawn 1:1 with nearest filtering. White plus alpha for masks (tinted by the vertex
 * colour), real colours for avatars. One texture for all of it lets a whole frame of text and shapes go out in a few
 * draw calls.
 *
 * Regions are never freed one by one: when the pages are full, the owner clears the atlas and fills it again from
 * what is drawn next. Render thread only.
 */
class MaskAtlas(private val pageSize: Int = 1024, private val maxPages: Int = 4) : AutoCloseable {
    class Page internal constructor(val size: Int) {
        val texture = DynamicTexture(size, size, true)
        internal val packer = ShelfPacker(size, size)

        /** [upload] as a value, so queuing a quad does not allocate a lambda. */
        val uploader: () -> Unit = ::upload
        private var dirtyMinX = Int.MAX_VALUE
        private var dirtyMinY = Int.MAX_VALUE
        private var dirtyMaxX = -1
        private var dirtyMaxY = -1

        internal fun markDirty(x: Int, y: Int, w: Int, h: Int) {
            dirtyMinX = minOf(dirtyMinX, x)
            dirtyMinY = minOf(dirtyMinY, y)
            dirtyMaxX = maxOf(dirtyMaxX, x + w)
            dirtyMaxY = maxOf(dirtyMaxY, y + h)
        }

        /** Sends what changed since the last upload to the GPU. */
        fun upload() {
            if (dirtyMaxX < 0) return
            texture.bind()
            val w = dirtyMaxX - dirtyMinX
            val h = dirtyMaxY - dirtyMinY
            texture.pixels!!.upload(0, dirtyMinX, dirtyMinY, dirtyMinX, dirtyMinY, w, h, false, false)
            dirtyMinX = Int.MAX_VALUE
            dirtyMinY = Int.MAX_VALUE
            dirtyMaxX = -1
            dirtyMaxY = -1
        }
    }

    /** A rectangle of one page, in texels. */
    class Region(val page: Page, val x: Int, val y: Int, val width: Int, val height: Int)

    private val pages = ArrayList<Page>()

    /** Whether a [width] x [height] region fits on an empty page (with the packer's padding). */
    fun fits(width: Int, height: Int): Boolean = width in 1..pageSize - 2 && height in 1..pageSize - 2

    /** Room for a [width] x [height] region, or null when every page is full (then [clear] and try again). */
    fun allocate(width: Int, height: Int): Region? {
        if (!fits(width, height)) return null
        for (page in pages) page.packer.insert(width, height)?.let { (x, y) -> return Region(page, x, y, width, height) }
        if (pages.size >= maxPages) return null
        val page = Page(pageSize).also(pages::add)
        val (x, y) = page.packer.insert(width, height) ?: return null
        return Region(page, x, y, width, height)
    }

    /** Fills [region] with white at the given coverage ([coverage] is row by row, `region.width` wide). */
    fun writeMask(region: Region, coverage: ByteArray) {
        val image = region.page.texture.pixels!!
        for (row in 0 until region.height) {
            for (col in 0 until region.width) {
                val alpha = coverage[row * region.width + col].toInt() and 0xFF
                image.setPixelRGBA(region.x + col, region.y + row, (alpha shl 24) or 0xFFFFFF)
            }
        }
        region.page.markDirty(region.x, region.y, region.width, region.height)
    }

    /** Fills [region] with colours given as ARGB, row by row. */
    fun writeColors(region: Region, argb: IntArray) {
        val image = region.page.texture.pixels!!
        for (row in 0 until region.height) {
            for (col in 0 until region.width) {
                val c = argb[row * region.width + col]
                // NativeImage stores ABGR.
                val abgr = (c and 0xFF00FF00.toInt()) or ((c shr 16) and 0xFF) or ((c and 0xFF) shl 16)
                image.setPixelRGBA(region.x + col, region.y + row, abgr)
            }
        }
        region.page.markDirty(region.x, region.y, region.width, region.height)
    }

    fun clear() {
        pages.forEach { it.texture.close() }
        pages.clear()
    }

    override fun close() = clear()
}
