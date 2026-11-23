package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

class FallbackTest {
    @Test
    fun `takes the first source that loads and reports the broken ones above it`() {
        val failed = ArrayList<String>()
        val loaded = Fallback.firstLoading(listOf("server pack", "user pack", "phone jar"), { source ->
            if (source != "phone jar") throw IOException("broken $source")
            "font from $source"
        }) { source, e -> failed += "$source: ${e.message}" }
        assertEquals("font from phone jar", loaded)
        assertEquals(listOf("server pack: broken server pack", "user pack: broken user pack"), failed)
    }

    @Test
    fun `stops at the first that works`() {
        var tried = 0
        assertEquals(1, Fallback.firstLoading(listOf(1, 2, 3), { tried++; it }) { _, _ -> })
        assertEquals(1, tried)
    }

    @Test
    fun `null when nothing loads`() {
        assertNull(Fallback.firstLoading(listOf(1, 2), { throw IllegalStateException() }) { _, _ -> })
        assertNull(Fallback.firstLoading(emptyList<Int>(), { it }) { _, _ -> })
    }
}
