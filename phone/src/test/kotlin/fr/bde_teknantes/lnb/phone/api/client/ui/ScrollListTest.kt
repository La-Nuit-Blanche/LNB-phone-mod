package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A [ScrollList] on a [KitScreen], 326 dp tall under a 44 dp header, as Feed lays it out. */
class ScrollListTest {
    private lateinit var screen: TestScreen

    /** A row of a fixed (changeable) height that counts its clicks. */
    private class Row(var h: Float) : Clickable() {
        var clicks = 0

        init {
            onClick = { clicks++ }
        }

        override fun heightFor(width: Float) = h
    }

    private val top = 44f
    private val viewport = 326f

    private fun list(state: ScrollState = ScrollState()) = screen.add(ScrollList(state).at(0f, top, 256f, viewport))

    private fun ScrollList.rows(count: Int, height: Float = 50f) = (0 until count).map { add(Row(height)) }

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `rows of different heights stack in order, hidden ones taking no room`() {
        val list = list()
        val a = list.add(Row(50f))
        val hidden = list.add(Row(500f)).apply { visible = false }
        val b = list.add(Row(100f))
        val c = list.add(Row(30f))
        screen.frame()
        assertEquals(listOf(0f, 50f, 150f), listOf(a.y, b.y, c.y))
        assertEquals(180f, list.contentHeight)
        assertEquals(256f, b.width)
        assertFalse(hidden.isShowing)
    }

    @Test
    fun `content shorter than the list doesn't scroll and leaves the wheel to others`() {
        val list = list()
        list.rows(3)
        screen.frame()
        assertFalse(screen.wheel(128f, 200f, -1f))
        assertEquals(0f, list.offset)
        assertEquals(0f, list.maxOffset)
    }

    @Test
    fun `the wheel scrolls 36 dp per notch and stops at both ends`() {
        val list = list()
        list.rows(20)
        screen.frame()
        assertEquals(1000f - viewport, list.maxOffset)
        assertTrue(screen.wheel(128f, 200f, -1f))
        assertEquals(36f, list.offset)
        screen.wheel(128f, 200f, 5f)
        assertEquals(0f, list.offset)
        assertTrue(list.isAtTop)
        screen.wheel(128f, 200f, -100f)
        assertEquals(674f, list.offset)
        // At the end the list still keeps the wheel, so the screen behind doesn't scroll.
        assertTrue(screen.wheel(128f, 200f, -1f))
        assertEquals(674f, list.offset)
    }

    @Test
    fun `a click in a scrolled list reaches the row under the pointer`() {
        val list = list()
        val rows = list.rows(20)
        screen.frame()
        list.scrollTo(100f)
        screen.frame()
        // y 54 on screen is y 10 in the list, 110 in the content: the third row.
        screen.click(128f, top + 10f)
        assertEquals(listOf(0, 0, 1, 0), rows.take(4).map { it.clicks })
        assertEquals(top + 50f * 2 - 100f, rows[2].toViewportY(0f))
    }

    @Test
    fun `rows scrolled out above the list can't be clicked through the header`() {
        val list = list()
        val rows = list.rows(20)
        screen.frame()
        list.scrollTo(100f)
        screen.frame()
        screen.click(128f, top - 10f)
        assertTrue(rows.all { it.clicks == 0 })
    }

    @Test
    fun `dragging the thumb scrolls in proportion and clicks no row`() {
        val list = list()
        val rows = list.rows(20)
        screen.frame()
        screen.press(252f, top + 10f)
        screen.drag(252f, top + 60f)
        val travel = viewport - viewport * viewport / 1000f
        assertEquals(50f * 674f / travel, list.offset, 0.01f)
        screen.drag(252f, top + 1000f)
        assertEquals(674f, list.offset)
        screen.drag(252f, top - 500f)
        assertEquals(0f, list.offset)
        screen.release()
        assertTrue(rows.all { it.clicks == 0 })
    }

    @Test
    fun `scrolling to the top animates over 250 ms, easing out`() {
        val list = list()
        list.rows(20)
        screen.frame()
        list.scrollTo(600f)
        list.scrollToTop()
        screen.frame()
        assertEquals(600f, list.offset)
        screen.advance(100f)
        // 0.4 of the way in time, 0.64 of the way in distance.
        assertEquals(216f, list.offset, 0.01f)
        screen.advance(150f)
        assertEquals(0f, list.offset)
        assertTrue(list.isAtTop)
    }

    @Test
    fun `the wheel stops an animated scroll where it is`() {
        val list = list()
        list.rows(20)
        screen.frame()
        list.scrollTo(600f)
        list.scrollToTop()
        screen.advance(100f)
        screen.wheel(128f, 200f, -1f)
        assertEquals(252f, list.offset, 0.01f)
        screen.advance(200f)
        assertEquals(252f, list.offset, 0.01f)
    }

    @Test
    fun `revealing a row scrolls the least needed`() {
        val list = list()
        val rows = list.rows(20)
        screen.frame()
        list.reveal(rows[10])
        assertEquals(550f - viewport, list.offset)
        list.reveal(rows[9])
        assertEquals(550f - viewport, list.offset)
        list.reveal(rows[1])
        assertEquals(50f, list.offset)
    }

    @Test
    fun `content that shrinks while scrolled brings the offset back in range`() {
        val list = list()
        val rows = list.rows(20)
        screen.frame()
        list.scrollTo(674f)
        rows.takeLast(10).forEach(list::remove)
        screen.frame()
        assertEquals(500f - viewport, list.offset)
        rows.take(10).forEach { it.h = 10f }
        screen.frame()
        assertEquals(0f, list.offset)
    }

    @Test
    fun `a row that grows pushes the rows after it down`() {
        val list = list()
        val rows = list.rows(3)
        screen.frame()
        rows[0].h = 120f
        screen.frame()
        assertEquals(120f, rows[1].y)
        assertEquals(220f, list.contentHeight)
    }

    @Test
    fun `onScroll hears each new offset once`() {
        val list = list()
        list.rows(20)
        val heard = ArrayList<Float>()
        list.onScroll = { heard += it }
        screen.frame()
        screen.wheel(128f, 200f, -1f)
        screen.wheel(128f, 200f, 1f)
        screen.wheel(128f, 200f, 1f)
        assertEquals(listOf(36f, 0f), heard)
    }

    @Test
    fun `a position kept in a ScrollState survives the screen being built again`() {
        val kept = ScrollState(500f)
        val list = list(kept)
        list.rows(20)
        screen.frame()
        assertEquals(500f, list.offset)
        assertEquals(500f, kept.offset)
    }

    @Test
    fun `the bar shows while the pointer is over the list, then fades 800 ms after the last scroll`() {
        val list = list()
        list.rows(20)
        screen.frame()
        fun barDrawn() = screen.canvas.fills.any { it == Colors.SCROLL_THUMB || it == Colors.SCROLL_THUMB_HOVER }
        screen.moveTo(10f, 10f)
        assertFalse(barDrawn(), "never scrolled, pointer outside: no bar")
        screen.moveTo(128f, 200f)
        assertTrue(barDrawn(), "pointer over the list: the bar shows")
        screen.wheel(128f, 200f, -1f)
        screen.moveTo(10f, 10f)
        assertTrue(barDrawn())
        screen.advance(800f)
        assertTrue(barDrawn())
        screen.advance(200f)
        assertFalse(barDrawn())
    }

    @Test
    fun `the thumb lights up under the pointer`() {
        val list = list()
        list.rows(20)
        screen.moveTo(252f, top + 10f)
        assertTrue(screen.canvas.fills.contains(Colors.SCROLL_THUMB_HOVER))
        screen.moveTo(100f, top + 10f)
        assertTrue(screen.canvas.fills.contains(Colors.SCROLL_THUMB))
    }

    @Test
    fun `a text area that can't scroll lets the list scroll`() {
        val list = list()
        list.add(TextArea(TextState("court")).apply { limit = 280 })
        list.rows(20)
        screen.frame()
        assertTrue(screen.wheel(100f, top + 20f, -1f))
        assertEquals(36f, list.offset)
    }

    @Test
    fun `scrolling to a target out of range clamps it`() {
        val list = list()
        list.rows(20)
        screen.frame()
        list.scrollTo(5000f)
        assertEquals(674f, list.offset)
        list.scrollTo(-10f)
        assertEquals(0f, list.offset)
    }
}
