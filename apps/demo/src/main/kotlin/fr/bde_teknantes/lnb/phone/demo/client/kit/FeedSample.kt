package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.demo.client.smooth.SampleSkins
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation

/** A string of the post sample, in the demo's language files (art direction 13.2, under `lnbphone_demo.feed.`). */
fun tr(key: String, vararg args: Any): String = I18n.get("lnbphone_demo.feed.$key", *args)

/** Someone who writes posts, as the Feed app knows them from LNB Warden (art direction 9.2). */
class Author(val username: String, val realName: String?, val skin: () -> ResourceLocation, val announcer: Boolean = false) {
    /** The bold name: the real name, or the username without one. */
    val displayName get() = realName ?: username

    /** `@username`, only when there is a real name (the same word is never printed twice). */
    val handle get() = realName?.let { "@$username" }
}

/** A post as the viewer sees it: what the server would send, plus what depends on the viewer. */
class Post(
    val id: Int,
    val author: Author,
    val time: () -> String,
    val text: String,
    var replies: Int = 0,
    var likes: Int = 0,
    var liked: Boolean = false,
    val anonymous: Boolean = false,
    var pinned: Boolean = false,
    /** The private line under the header (9.3), with its tooltip; null for none. */
    val privateLine: String? = null,
    val privateTooltip: String? = null,
    /** Whether the viewer may delete it: the "more" button shows. */
    val canDelete: Boolean = false,
    /** Whether the viewer may pin it: announcements, for announcers and operators. */
    val canPin: Boolean = false,
    /** The viewer's own post with replies they haven't seen. */
    val unseenReplies: Boolean = false,
    val replyTo: String? = null,
)

/** The sample content of art direction 18.3, viewed by Camille Roux, a regular player, unless told otherwise. */
object FeedSample {
    val ALEX = Author("Alex_Craft", "Alexandre Morel", { SampleSkins.ALEX })
    val MIKA = Author("MikaPvP", "Mika Laurent", { SampleSkins.MIKA })
    val STEVE = Author("SteveBuilds", null, { SampleSkins.STEVE })
    val LUNE = Author("Admin_Lune", "Lune Garnier", { SampleSkins.LUNE }, announcer = true)
    val KAEL = Author("Kael_Builder", null, { SampleSkins.KAEL }, announcer = true)
    val CAMILLE = Author("Cam_Roux", "Camille Roux", { SampleSkins.CAMILLE })

    const val TEXT_C1 = "Qui est chaud pour une virée dans le Nether ce soir ? Rendez-vous au portail du spawn à 21:00. " +
        "Prenez des potions de résistance au feu, des blocs pour faire des ponts et de quoi manger, on risque d'y passer " +
        "un bon moment avant de trouver une forteresse."

    /** [TEXT_C1], cut or repeated to [count] characters (the counts of flow F-11). */
    fun textOf(count: Int): String {
        val sb = StringBuilder()
        while (sb.length < count) sb.append(TEXT_C1).append(' ')
        return sb.substring(0, count)
    }

    fun global(viewerIsOperator: Boolean = false) = listOf(
        Post(1, ALEX, { tr("time.minutes", 2) }, "Des diamants à la mine du spawn, couche -54. Premier arrivé, premier servi !", 3, 12, liked = true),
        Post(
            2, MIKA, { tr("time.minutes", 5) },
            "Merci à celui qui a laissé tourner la ferme à creepers près du marché, j'ai de la poudre à canon pour un mois.",
            4, 31, anonymous = true,
            privateLine = if (viewerIsOperator) tr("post.op_author", "Mika Laurent", "@MikaPvP") else null,
            privateTooltip = if (viewerIsOperator) tr("post.op_author.tooltip") else null,
            canDelete = viewerIsOperator,
        ),
        Post(
            8, CAMILLE, { tr("time.minutes", 20) }, "Quelqu'un a vu mon loup ? Il s'appelle Biscuit et il porte un collier bleu.",
            2, 4, canDelete = true, unseenReplies = true,
        ),
        Post(
            3, STEVE, { tr("time.hours", 1) },
            "J'ouvre ma boutique ce soir à 21:00 près de la porte nord. Kits redstone, livres enchantés et quelques teintures rares. Venez tôt, le stock est petit !",
            0, 8,
        ),
        Post(4, MIKA, { tr("time.hours", 3) }, "gg à tous à l'arène", 0, 0),
    )

    fun announcements(viewerCanPin: Boolean = false) = listOf(
        Post(
            5, LUNE, { tr("time.days", 2) },
            "Règlement mis à jour : lisez /rules avant vendredi. Le grief dans la zone du marché vaut désormais 3 jours de ban.",
            6, 54, pinned = true, canPin = viewerCanPin,
        ),
        Post(
            9, LUNE, { tr("time.date.oct26") }, "Bienvenue sur le serveur ! Le guide des débutants est au spawn, derrière la fontaine.",
            12, 88, pinned = true, canPin = viewerCanPin,
        ),
        Post(6, LUNE, { tr("time.days", 1) }, "La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00.", 2, 40, canPin = viewerCanPin),
        Post(7, KAEL, { tr("time.days", 3) }, "Maintenance cette nuit de 02:00 à 02:30, le serveur redémarrera deux fois.", 0, 17, canPin = viewerCanPin),
    )
}
