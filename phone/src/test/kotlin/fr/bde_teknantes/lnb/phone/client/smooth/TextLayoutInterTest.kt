package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Text layout on the bundled Inter itself, against the art direction's real slots and strings. */
class TextLayoutInterTest {
    private val regular = InterMetrics.REGULAR
    private val semibold = InterMetrics.SEMIBOLD

    private fun font(style: TextStyle) = if (style.weight == FontWeight.REGULAR) regular else semibold

    private fun width(text: String, style: TextStyle) = TextLayout.width(text, font(style), style.size)

    private fun wrap(text: String, style: TextStyle, maxWidth: Float) = TextLayout.wrap(text, font(style), style.size, maxWidth)

    private val styles = listOf(
        TextStyles.TITLE_L, TextStyles.TITLE_M, TextStyles.BODY, TextStyles.BODY_STRONG,
        TextStyles.LABEL, TextStyles.CAPTION, TextStyles.CAPTION_STRONG, TextStyles.MICRO,
    )

    @Test
    fun `metrics are the font's design units, as fontTools reads them`() {
        assertEquals(1984 / 2048f, regular.ascender)
        assertEquals(494 / 2048f, regular.descender)
        assertEquals(1150 / 2048f, regular.advance('a'.code))
        assertEquals(1770 / 2048f, regular.advance(0x2026))
        assertEquals(1176 / 2048f, semibold.advance('a'.code))
        assertEquals(1958 / 2048f, semibold.advance(0x2026))
    }

    /** Widths in font units as HarfBuzz 0.57 shapes them with only `kern` on (no ligatures). */
    private val harfBuzzWidths = listOf(
        Triple("Publier", 6730, 6949),
        Triple("Répondre", 9359, 9556),
        Triple("Quoi de neuf sur le serveur ?", 27943, 28394),
        Triple("Visible uniquement par les opérateurs", 36648, 37564),
        Triple("Aperçu des notifications", 23543, 24216),
        Triple("Écrire un post", 13425, 13686),
        Triple("Tout va bien, et toi ?", 19471, 19779),
    )

    @Test
    fun `strings measure as HarfBuzz lays them out`() {
        for ((text, regularUnits, semiboldUnits) in harfBuzzWidths) {
            assertEquals(regularUnits.toFloat(), regular.widthUnits(text), 0.05f, "Regular \"$text\"")
            assertEquals(semiboldUnits.toFloat(), semibold.widthUnits(text), 0.05f, "SemiBold \"$text\"")
        }
    }

    @Test
    fun `every style's font line fits its line box`() {
        for (style in styles) {
            val f = font(style)
            assertTrue((f.ascender + f.descender) * style.size <= style.lineHeight, "$style")
            val baseline = TextLayout.baseline(style, f)
            assertTrue(baseline >= f.ascender * style.size && baseline + f.descender * style.size <= style.lineHeight, "$style")
        }
    }

    @Test
    fun `fixed slots of the art direction hold their longer string`() {
        // Section 13.3: slot width in dp, longer string, style (8.4 and 9.x).
        val slots = listOf(
            Triple(128f - 10f, "Announcements", TextStyles.LABEL), // feed tab, with the unread dot
            Triple(88f, "Publier", TextStyles.LABEL),
            Triple(96f, "Répondre", TextStyles.LABEL),
            Triple(136f, "Écrire un post", TextStyles.LABEL),
            Triple(104f, "Réessayer", TextStyles.LABEL),
            Triple(88f, "Annuler", TextStyles.LABEL),
            Triple(104f, "Supprimer", TextStyles.LABEL),
            Triple(168f - 40f, "Supprimer", TextStyles.BODY), // menu item, label from x 40
            Triple(184f, "Publier anonymement", TextStyles.BODY_STRONG),
            Triple(184f, "Name hidden from players", TextStyles.CAPTION),
            Triple(184f, "Nom masqué aux joueurs", TextStyles.CAPTION),
            Triple(184f, "Aperçu des notifications", TextStyles.BODY_STRONG),
            Triple(164f, "New announcement", TextStyles.TITLE_M),
            Triple(164f, "Nouvelle annonce", TextStyles.TITLE_M),
            Triple(168f, "Posted anonymously by you", TextStyles.CAPTION),
            Triple(168f, "Publié anonymement par toi", TextStyles.CAPTION),
        )
        for ((slot, text, style) in slots) {
            val w = width(text, style)
            assertTrue(w <= slot, "\"$text\" is $w dp for a $slot dp slot")
            assertEquals(listOf(text), wrap(text, style, slot))
        }
    }

    @Test
    fun `texts the art direction wraps take the lines it allows`() {
        assertEquals(
            listOf("Visible uniquement par les", "opérateurs"),
            wrap("Visible uniquement par les opérateurs", TextStyles.CAPTION, 200f),
        )
        assertEquals(2, wrap("Server announcements will show up here.", TextStyles.BODY, 200f).size)
        assertTrue(wrap("Its replies will be deleted too. This can't be undone.", TextStyles.BODY, 200f).size <= 3)
    }

    private val post = "Ce soir à 21 h, grande soirée au spawn : concours de constructions, quiz « spécial " +
        "redstone » et feu d'artifice ! Inscrivez-vous auprès de @Mika_Laurent avant 20 h ; places limitées. " +
        "Les gagnants repartent avec des diamants, évidemment. Vous venez ? Répondez ici, et partagez !"

    @Test
    fun `a full post wraps in the card's body column`() {
        assertTrue(post.length <= 280)
        // Post card (9.3): body text at x 56..244, 188 dp.
        val lines = wrap(post, TextStyles.BODY, 188f)
        assertEquals(post, lines.joinToString(" "))
        for ((i, line) in lines.withIndex()) {
            assertTrue(width(line, TextStyles.BODY) <= 188f, "\"$line\" overflows")
            if (i > 0) assertTrue(line.first() !in "!?:;»", "\"$line\" starts with a mark")
            assertTrue(!line.endsWith("«"), "\"$line\" ends with «")
        }
        // "About 27 characters per line" (9.3).
        val perLine = post.length / lines.size.toFloat()
        assertTrue(perLine in 24f..34f, "$perLine characters per line")
    }

    private val sentences = listOf(
        "Bienvenue sur le serveur, amusez-vous bien !",
        "Qui vient miner ce soir ? Rendez-vous à la base.",
        "Attention : redémarrage prévu à 18 h ; sauvegardez vos coffres.",
        "Elle a dit « on se retrouve au portail » puis elle a disparu…",
        "Énorme merci à l'équipe de modération pour cette saison !",
        "Événement spécial : chasse au trésor dans l'End, récompenses à la clé.",
        "Ça marche ! Je m'occupe des fours et de la ferme à blé.",
        "Anticonstitutionnellement, les creepers n'ont pas voté.",
    )

    @Test
    fun `French prose wraps without overflow or stray marks at every width`() {
        val random = Random(11)
        repeat(400) {
            val text = (1..random.nextInt(1, 5)).joinToString(" ") { sentences[random.nextInt(sentences.size)] }
            val style = styles[random.nextInt(styles.size)]
            val maxWidth = random.nextInt(120, 245).toFloat()
            val lines = wrap(text, style, maxWidth)
            for ((i, line) in lines.withIndex()) {
                assertTrue(width(line, style) <= maxWidth + 1e-3f, "\"$line\" in $maxWidth dp")
                if (i > 0) assertTrue(line.first() !in "!?:;» ", "\"$line\" begins badly")
                if (i < lines.size - 1) assertTrue(!line.endsWith("«") && !line.endsWith(" "), "\"$line\" ends badly")
            }
            // A word wider than the line ("Anticonstitutionnellement" in title-l) is cut by character.
            val unitsFit = TextLayout.words(text).all { width(it, style) <= maxWidth }
            if (unitsFit) assertEquals(text, lines.joinToString(" ")) else assertEquals(text.replace(" ", ""), lines.joinToString("").replace(" ", ""))
        }
    }

    @Test
    fun `truncated names never overflow their slot`() {
        val names = listOf(
            "Alexandre Morel", "Marie-Éléonore de La Tour d'Auvergne", "Ωmega_Builder", "Mika Laurent @MikaPvP",
            "Auteur : Mika Laurent @MikaPvP", "Wwwwwwwwwwwwwwwwwwwwwwwwwww", "Ça va être épique", "Té, Vo, r. et Yo",
        )
        for (style in listOf(TextStyles.TITLE_M, TextStyles.CAPTION, TextStyles.BODY)) {
            for (name in names) {
                for (slot in 0..200 step 3) {
                    val cut = TextLayout.truncate(name, font(style), style.size, slot.toFloat())
                    assertTrue(width(cut, style) <= slot + 1e-3f, "\"$cut\" in $slot dp")
                    assertTrue(cut == name || cut.isEmpty() || (cut.endsWith("…") && name.startsWith(cut.dropLast(1))))
                }
            }
        }
    }

    @Test
    fun `layout gives the same lines whatever the size in pixels`() {
        // Widths are linear in the size: the same text at a style's size and at twice it breaks at the same words when
        // the width doubles too. Lines therefore never depend on how many pixels a dp is.
        for (sentence in sentences) {
            for (maxWidth in listOf(100f, 150f, 188f)) {
                val dp = wrap(sentence, TextStyles.BODY, maxWidth)
                for (k in listOf(1f, 1.5f, 2f)) {
                    assertEquals(dp, TextLayout.wrap(sentence, regular, 13f * k, maxWidth * k), "\"$sentence\" x $k")
                }
            }
        }
    }
}
