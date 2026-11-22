package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PostCardLayoutTest {
    /** Every character 5 dp; a line feed in the text splits lines, nothing else wraps. */
    private val fake = object : TextMeasurer {
        override fun measure(text: String, style: TextStyle) = text.length * 5f
        override fun truncate(text: String, style: TextStyle, maxWidth: Float) = text
        override fun wrap(text: String, style: TextStyle, maxWidth: Float) = text.split('\n')
        override fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int) = wrap(text, style, maxWidth).take(maxLines)
    }

    private val skin = { ResourceLocation.withDefaultNamespace("missing") }

    private fun post(author: Author, text: String = "one\ntwo", pinned: Boolean = false, canDelete: Boolean = false) =
        Post(1, author, { "2 min" }, text, pinned = pinned, canDelete = canDelete)

    @Test
    fun `heights match the art direction's examples`() {
        // 9.3: a 2-line post with a handle is 119 dp, without a handle 103 dp.
        assertEquals(119f, PostCardLayout(post(Author("Alex_Craft", "Alexandre Morel", skin)), fake).height)
        assertEquals(103f, PostCardLayout(post(Author("SteveBuilds", null, skin)), fake).height)
    }

    @Test
    fun `a pinned post moves down 18 dp`() {
        val l = PostCardLayout(post(Author("Admin_Lune", "Lune Garnier", skin, announcer = true), pinned = true), fake)
        assertEquals(28f, l.nameY)
        assertEquals(46f, l.handleY)
        assertEquals(137f, l.height)
    }

    @Test
    fun `the name leaves room for the badge and the time`() {
        val l = PostCardLayout(post(Author("Admin_Lune", "Lune Garnier", skin, announcer = true), canDelete = true), fake)
        // Time "2 min" is 25 dp, right-aligned to 214 with the more button: 214 - 25 - 8 - 20 - 56.
        assertEquals(105f, l.nameMaxWidth)
    }

    @Test
    fun `anonymous posts have no handle line`() {
        val anonymous = Post(2, Author("MikaPvP", "Mika Laurent", skin), { "5 min" }, "x", anonymous = true)
        assertNull(PostCardLayout(anonymous, fake).handleY)
    }
}
