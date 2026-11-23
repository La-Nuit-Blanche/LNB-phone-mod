package fr.bde_teknantes.lnb.phone.feed.server.backend.memory

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import net.minecraft.core.UUIDUtil
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The art direction's sample content (`design/art-direction.md` 18.3): its authors, posts 1 to 10, the replies of the
 * threads of posts 1, 5 and 8, likes and pins, for screenshots and for trying the app (`memory.seed = "sample"`).
 *
 * Times are placed before [now], at the art direction's distances ("2 min", "1 h", "26 oct."...). [FIXED_NOW] is the
 * moment the art direction draws (post 1, two minutes old, is "14:32 · 12 nov.", Paris time): the screenshot harness
 * runs with a clock fixed there, so that the dates match the mockups.
 *
 * The viewer of the mockups is Camille R. (`Cam_Roux`): post 8 is hers, she liked post 1, and she hasn't opened post
 * 8's thread since R6 and R7 arrived. Playing as `Cam_Roux` shows the sample as drawn. Counts the art direction gives
 * without rows (post 2's 31 likes, post 9's 12 replies) are numbers kept beside the rows.
 */
object SampleContent {
    /** 12 November 2026, 14:34 in Paris. */
    val FIXED_NOW: Instant = Instant.parse("2026-11-12T13:34:00Z")

    val ALEX: UUID = offline("Alex_Craft")
    val MIKA: UUID = offline("MikaPvP")
    val CAMILLE: UUID = offline("Cam_Roux")
    val STEVE: UUID = offline("SteveBuilds")
    val LUNE: UUID = offline("Admin_Lune")
    val KAEL: UUID = offline("Kael_Builder")

    /** The IDs of the seeded posts, by the art direction's numbers (`"1"`, `"R3"`...). */
    class Seeded(val ids: Map<String, UUID>) {
        operator fun get(name: String): UUID = ids.getValue(name)
    }

    fun seed(backend: MemoryBackend, now: Instant): Seeded {
        backend.addSampleAuthor(ALEX, "Alex_Craft", "Alexandre M.")
        backend.addSampleAuthor(MIKA, "MikaPvP", "Mika L.")
        backend.addSampleAuthor(CAMILLE, "Cam_Roux", "Camille R.")
        backend.addSampleAuthor(STEVE, "SteveBuilds", null)
        backend.addSampleAuthor(LUNE, "Admin_Lune", "Lune G.", moderator = true)
        backend.addSampleAuthor(KAEL, "Kael_Builder", null, announcer = true)

        fun ago(minutes: Long): Instant = now.minus(Duration.ofMinutes(minutes))
        val day = 24 * 60L
        val ids = LinkedHashMap<String, UUID>()
        val global = FeedKind.GLOBAL
        val announcements = FeedKind.ANNOUNCEMENTS

        // Oldest first, so that the counter's IDs follow the dates.
        ids["9"] = backend.insert(
            LUNE, announcements, "Bienvenue sur le serveur ! Le guide des débutants est au spawn, derrière la fontaine.",
            createdAt = ago(17 * day + 4 * 60), pinnedAt = ago(15 * day), baseLikes = 88, baseReplies = 12,
        )
        // Drawn as "2 j", pinned "2 nov.": a pin can't come before its post, so it comes a minute after.
        ids["5"] = backend.insert(
            LUNE, announcements, "Règlement mis à jour : lisez /rules avant vendredi. Le grief dans la zone du marché vaut désormais 3 jours de ban.",
            createdAt = ago(2 * day), pinnedAt = ago(2 * day - 1), baseLikes = 54,
        )
        ids["7"] = backend.insert(
            KAEL, announcements, "Maintenance cette nuit de 02:00 à 02:30, le serveur redémarrera deux fois.",
            createdAt = ago(3 * day), baseLikes = 17,
        )
        ids["6"] = backend.insert(
            LUNE, announcements, "La saison 2 commence samedi à 18:00. L'End sera réinitialisé à 17:00.",
            createdAt = ago(day), baseLikes = 40, baseReplies = 2,
        )
        ids["R8"] = backend.insert(MIKA, announcements, "Lu, merci pour la précision !", createdAt = ago(day - 30), threadId = ids["5"], baseLikes = 3)
        ids["R9"] = backend.insert(ALEX, announcements, "Trois jours de ban, ça calme tout le monde.", createdAt = ago(day - 60), threadId = ids["5"], baseLikes = 1)
        ids["4"] = backend.insert(MIKA, global, "gg à tous à l'arène", createdAt = ago(3 * 60))
        ids["3"] = backend.insert(
            STEVE, global,
            "J'ouvre ma boutique ce soir à 21:00 près de la porte nord. Kits redstone, livres enchantés et quelques teintures rares. Venez tôt, le stock est petit !",
            createdAt = ago(60), baseLikes = 8,
        )
        ids["8"] = backend.insert(
            CAMILLE, global, "Quelqu'un a vu mon loup ? Il s'appelle Biscuit et il porte un collier bleu.",
            createdAt = ago(20), baseLikes = 4,
        )
        ids["R6"] = backend.insert(ALEX, global, "Il traînait vers la ferme à moutons tout à l'heure.", createdAt = ago(12), threadId = ids["8"], baseLikes = 1)
        ids["R7"] = backend.insert(STEVE, global, "Un loup avec un collier bleu ? J'en ai croisé un au marché.", createdAt = ago(6), threadId = ids["8"])
        ids["2"] = backend.insert(
            MIKA, global,
            "Merci à celui qui a laissé tourner la ferme à creepers près du marché, j'ai de la poudre à canon pour un mois.",
            createdAt = ago(5), anonymous = true, baseLikes = 31, baseReplies = 4,
        )
        ids["1"] = backend.insert(
            ALEX, global, "Des diamants à la mine du spawn, couche -54. Premier arrivé, premier servi !",
            createdAt = ago(2), baseLikes = 11,
        )
        backend.insertLike(CAMILLE, ids.getValue("1"))
        ids["R1"] = backend.insert(MIKA, global, "J'arrive !", createdAt = ago(1), threadId = ids["1"], baseLikes = 1)
        ids["R2"] = backend.insert(
            STEVE, global, "Il en reste encore ?",
            createdAt = now.minusSeconds(40), threadId = ids["1"], anonymous = true,
        )
        ids["R3"] = backend.insert(
            ALEX, global, "Oui, deux filons côté est.",
            createdAt = now.minusSeconds(20), threadId = ids["1"], replyTo = ids["R2"], baseLikes = 2,
        )
        ids["10"] = backend.insert(
            KAEL, announcements, "Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn.",
            createdAt = now.minusSeconds(10),
        )
        return Seeded(ids)
    }

    private fun offline(name: String): UUID = UUIDUtil.createOfflinePlayerUUID(name)
}
