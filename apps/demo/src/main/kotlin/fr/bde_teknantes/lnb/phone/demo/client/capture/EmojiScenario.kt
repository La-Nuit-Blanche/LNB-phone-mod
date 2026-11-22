package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.demo.client.kit.EmojiPages
import fr.bde_teknantes.lnb.phone.demo.client.kit.KitHostScreen
import fr.bde_teknantes.lnb.phone.demo.client.notifications.BannerTestScreen

/**
 * Task 13: emoji in the apps and the banner at GUI scale 2, 3 and 4, in French: a text area with the caret after an
 * emoji, a field, a snackbar, a post card and rows; every kind of sequence in Regular and SemiBold; the banners. Then
 * about two hundred distinct emoji at GUI scale 9 for the glyph atlas. Logs how long the emoji face takes to open and
 * what the pre-warm costs with it.
 */
internal object EmojiScenario {
    fun script() = CaptureScript().apply {
        language("fr_fr")
        guiScale(2)
        // Cold: the fonts and the atlas dropped, then the emoji face opened alone, then the whole pre-warm.
        reloadResources()
        run {
            val start = System.nanoTime()
            val face = SmoothResources.emoji()
            val opened = System.nanoTime()
            face?.font?.ligatures?.size
            LnbPhone.LOGGER.info(
                "Capture emoji: face {} opened in {} us, ligatures read in {} us",
                if (face == null) "NOT" else "", (opened - start) / 1000, (System.nanoTime() - opened) / 1000,
            )
        }
        reloadResources()
        run { LnbPhone.LOGGER.info("Capture emoji: cold pre-warm, the emoji face opening in the background, in {} us", SmoothCanvas.prewarm() / 1000) }
        run {
            val start = System.nanoTime()
            SmoothResources.emoji()
            LnbPhone.LOGGER.info("Capture emoji: first emoji right after the pre-warm waited {} us for the face", (System.nanoTime() - start) / 1000)
        }
        // Alternating, three times each: the pre-warm with the face already open (what it cost before emoji), and
        // with the face opening in the background.
        repeat(3) {
            reloadResources()
            run { SmoothResources.emoji() }
            run { LnbPhone.LOGGER.info("Capture emoji: cold pre-warm, emoji face already open, in {} us", SmoothCanvas.prewarm() / 1000) }
            reloadResources()
            run { LnbPhone.LOGGER.info("Capture emoji: cold pre-warm, emoji face opening alongside, in {} us", SmoothCanvas.prewarm() / 1000) }
        }
        val pages = EmojiPages.pages
        for (scale in 2..4) {
            guiScale(scale)
            run { LnbPhone.LOGGER.info("Capture emoji: pre-warm for GUI {} in {} us", scale, SmoothCanvas.prewarm() / 1000) }
            screen { KitHostScreen(pages.subList(0, 2)) }
            waitTicks(16)
            shot("emoji_gui${scale}_1_inputs_post")
            screen { KitHostScreen(pages.subList(2, 4)) }
            waitTicks(16)
            shot("emoji_gui${scale}_2_sequences")
            screen { BannerTestScreen(BannerTestScreen.Mode.STATES) }
            waitTicks(20)
            shot("emoji_gui${scale}_3_banners")
        }
        forceGuiScale(9)
        screen { KitHostScreen(listOf(pages[4])) }
        waitTicks(20)
        shot("emoji_forced_gui9_many")
        screen { KitHostScreen(listOf(pages[2])) }
        waitTicks(20)
        shot("emoji_forced_gui9_sequences")
    }
}
