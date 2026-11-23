package fr.bde_teknantes.lnb.phone.feed.capture

import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.feed.client.FeedClient
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.SampleContent
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * Feed's capture scenarios (`-Pcapture=<name>`), against the memory backend with the sample seed and its fixed clock.
 * The pointer rests outside the viewport (x -20 dp) for shots that shouldn't show a hover.
 */
object FeedScenarios {
    fun get(name: String): (FeedCapture.Script.() -> Unit)? = when (name) {
        "root" -> { { root() } }
        "guest" -> { { guest() } }
        "thread" -> { { with(WriteScenarios) { thread() } } }
        "compose" -> { { with(WriteScenarios) { compose() } } }
        else -> null
    }

    private const val AWAY_X = -20
    private const val GLOBAL_TAB_X = 192
    private const val ANNONCES_TAB_X = 64

    /** Post 1's heart, at the top of Global: three lines of text under a name and a handle. */
    private const val POST_1_HEART_Y = 44 + 104 + 14

    /** As Camille R. (`Cam_Roux`), the mockups' viewer: the root screen's main states. */
    private fun FeedCapture.Script.root() {
        language("fr_fr")
        guiScale(3)
        enterWorld("lnbphone_feed-capture")
        waitTicks(40)
        openPhone()
        openFeed()
        waitTicks(40)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("01_annonces")
        log("Annonces loaded")

        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("02_global")

        // F-5: scrolled down, two posts arrive: the pill, with its arrow.
        mouseTo(128, 200)
        scroll(-8.0)
        waitTicks(10)
        backend {
            it.createPost(UUID.randomUUID(), SampleContent.ALEX, FeedKind.GLOBAL, "Quelqu'un pour une partie de spleef ?", false)
            it.createPost(UUID.randomUUID(), SampleContent.MIKA, FeedKind.GLOBAL, "Je suis partant !", false)
        }
        waitTicks(20)
        shot("03_global_pill_arrow")
        log("two posts waiting")
        click(128, 66)
        waitTicks(3)
        shot("04_global_pill_scrolling")
        waitTicks(5)
        mouseTo(AWAY_X, 200)
        waitTicks(1)
        shot("05_global_new_posts_highlighted")

        // F-4: an announcement while on Global: its banner, then the Annonces dot; on Annonces, the pill without an
        // arrow, the pinned group pushing the new post below the fold.
        backend { it.createPost(UUID.randomUUID(), SampleContent.KAEL, FeedKind.ANNOUNCEMENTS, "Événement PvP dimanche à 15:00 à l'arène.", false) }
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(6)
        shot("06_global_banner")
        waitUntil { !CaptureHooks.bannerShown }
        waitTicks(4)
        shot("07_global_annonces_dot")
        click(ANNONCES_TAB_X, 22)
        waitTicks(20)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("08_annonces_pill_no_arrow")
        click(128, 66)
        waitTicks(4)
        mouseTo(AWAY_X, 200)
        waitTicks(1)
        shot("09_annonces_after_pill")

        // F-20.6: an announcement's banner, clicked, lands on it: scrolled into view under the pins, highlighted.
        click(GLOBAL_TAB_X, 22)
        waitTicks(10)
        backend { it.createPost(UUID.randomUUID(), SampleContent.KAEL, FeedKind.ANNOUNCEMENTS, "Le marché est fermé samedi matin pour travaux. Merci de votre patience.", false) }
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(6)
        click(128, 40)
        waitTicks(6)
        mouseTo(AWAY_X, 200)
        waitTicks(1)
        shot("10_landing_from_notification")

        // F-23: the connection drops over a loaded list.
        backend { it.setOffline(true) }
        waitTicks(30)
        shot("11_connection_lost")
        log("offline")

        // 9.12: opened with nothing loaded, the site away; then Feed switched off.
        reopenFresh()
        shot("12_unavailable")
        backend {
            it.setOffline(false)
            it.setDisabled(true)
        }
        waitTicks(10)
        reopenFresh()
        shot("13_disabled")

        // 9.11: writing switched off: no compose button; a heart says why.
        backend {
            it.setDisabled(false)
            it.setWriting(false)
        }
        waitTicks(10)
        reopenFresh()
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("14_writing_off_global")
        // The newest post on top is Mika's one-line post of F-5: its heart.
        click(128, 44 + 68 + 14)
        waitTicks(5)
        shot("15_writing_off_like")
        backend { it.setWriting(true) }
        waitTicks(10)

        // Sheet 5: English at GUI scale 2.
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        language("en_us")
        guiScale(2)
        run { FeedClient.session.clear() }
        openPhone()
        openFeed()
        waitTicks(40)
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("16_global_english_gui2")
        guiScale(4)
        waitTicks(10)
        shot("17_global_english_gui4")
    }

    /** As `Visiteur_42`, listed as a guest in the memory backend's config: F-25. */
    private fun FeedCapture.Script.guest() {
        language("fr_fr")
        guiScale(3)
        enterWorld("lnbphone_feed-capture-guest")
        waitTicks(40)
        openPhone()
        openFeed()
        waitTicks(40)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
        shot("guest_01_annonces")
        log("guest")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)
        mouseTo(128, POST_1_HEART_Y)
        waitTicks(2)
        shot("guest_02_global_hover_heart")
        click(128, POST_1_HEART_Y)
        waitTicks(5)
        shot("guest_03_like_refused")
        // F-25 steps 4 and 5: the thread's strip.
        waitTicks(70)
        with(WriteScenarios) { guestThread() }
    }

    /** Feed opened afresh with nothing held, as after joining the server. */
    private fun FeedCapture.Script.reopenFresh() {
        run { FeedClient.session.clear() }
        openFeed()
        waitTicks(40)
        mouseTo(AWAY_X, 200)
        waitTicks(2)
    }
}
