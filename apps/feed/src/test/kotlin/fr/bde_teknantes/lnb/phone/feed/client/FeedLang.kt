package fr.bde_teknantes.lnb.phone.feed.client

import com.google.gson.JsonParser
import java.io.InputStreamReader

/**
 * Feed's strings as the game shows them: the mod's own language files, formatted as Minecraft's `I18n.get` does
 * (`String.format` over the value). A key missing from the file fails the test rather than showing the key.
 */
object FeedLang {
    val FRENCH: FeedStrings = load("fr_fr")
    val ENGLISH: FeedStrings = load("en_us")

    private fun load(language: String): FeedStrings {
        val path = "/assets/lnbphone_feed/lang/$language.json"
        val stream = FeedLang::class.java.getResourceAsStream(path) ?: error("$path isn't on the test classpath")
        val values = InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        return FeedStrings { id, args ->
            val value = values.get("lnbphone_feed.$id")?.asString ?: error("$language has no lnbphone_feed.$id")
            String.format(value, *args)
        }
    }
}
