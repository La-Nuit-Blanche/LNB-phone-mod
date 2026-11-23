package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.demo.client.DemoAppClient
import fr.bde_teknantes.lnb.phone.demo.client.kit.FeedScreen
import fr.bde_teknantes.lnb.phone.demo.client.kit.KitIndexScreen
import fr.bde_teknantes.lnb.phone.demo.client.kit.KitPages
import org.lwjgl.glfw.GLFW

/**
 * Task 5: the UI kit inside the real phone, in a world, driven through the game's own input path (the mouse, the
 * keys, the character callback), at GUI scale 2, 3 and 4, in French: the gallery with hover tints, a slide between
 * two screens held half way, the sample Feed scrolled, a menu, a dialog, Compose typed into (accents and a dead key
 * included), sent, and back on the feed with its snackbar. Home icons: Hello, UI kit, Feed (sample).
 */
internal object KitPhoneScenario {
    fun script() = CaptureScript().apply {
        enterWorld()
        language("fr_fr")
        run { CaptureHooks.setCentred(false) }
        for (scale in 2..4) scale(scale)
    }

    private fun CaptureScript.scale(s: Int) {
        val name = { step: String -> "kit_phone_gui${s}_$step" }
        guiScale(s)
        run {
            DemoAppClient.FEED_SESSION.tab = 0
            DemoAppClient.FEED_SESSION.scrolls.forEach { it.offset = 0f }
        }
        waitTicks(10)
        openPhone()
        // The phone reopens where it was put away: start from home.
        click { device(70, 218) }
        waitTicks(4)

        // The gallery's index, a row under the pointer.
        click { icon(1) }
        waitTicks(6)
        mouseToApp(128, 92)
        waitTicks(3)
        shot(name("01_index_row_hover"))

        // Forward to a page, caught half way through the slide.
        hold(0.5f)
        run { KitIndexScreen.current?.open(0) }
        waitTicks(3)
        shot(name("02_slide_half_way"))
        hold(null)
        waitTicks(5)
        mouseToApp(176, 230)
        waitTicks(3)
        shot(name("03_headers_icon_buttons"))
        page(s, 3, "04_buttons_secondary_text", 176, 230)
        page(s, KitPages.all.indexOfFirst { it.caption.startsWith("List rows") }, "05_rows", 128, 140)
        page(s, KitPages.all.indexOfFirst { it.caption.startsWith("Toggles") }, "06_toggles", 128, 300)

        // The sample Feed: Annonces scrolled with the wheel, a card under the pointer.
        click { device(70, 218) }
        waitTicks(4)
        click { icon(2) }
        waitTicks(8)
        mouseToApp(128, 200)
        scroll(-3.0)
        waitTicks(4)
        shot(name("07_annonces_scrolled_card_hover"))

        // Global: the more menu of Camille's post, then its dialog.
        click { app(192, 22) }
        waitTicks(6)
        run { FeedScreen.current?.openMenu(2) }
        waitTicks(3)
        mouseTo { menuItem() }
        waitTicks(3)
        shot(name("08_menu_item_hover"))
        mouseDown()
        mouseUp()
        waitTicks(6)
        mouseToApp(176, 224)
        waitTicks(3)
        shot(name("09_dialog_confirm_hover"))
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(4)

        // Compose from the FAB, typed through the keyboard: a dead key (^) then the ê it composes, and accents.
        click { app(220, 334) }
        waitTicks(8)
        type("Ça commence à l'ar")
        key(GLFW.GLFW_KEY_LEFT_BRACKET)
        type("ène, vous êtes prêts ?")
        waitTicks(3)
        shot(name("10_compose_typed"))
        click { app(200, 22) }
        waitTicks(3)
        shot(name("11_compose_sending"))
        waitTicks(25)
        mouseToApp(128, 200)
        waitTicks(2)
        shot(name("12_back_on_feed_snackbar"))
        closePhone()
    }

    /** Back to the index, then the gallery page [index], the pointer at ([x], [y]) dp. */
    private fun CaptureScript.page(s: Int, index: Int, step: String, x: Int, y: Int) {
        key(GLFW.GLFW_KEY_BACKSPACE)
        waitTicks(5)
        run { KitIndexScreen.current?.open(index) }
        waitTicks(6)
        mouseToApp(x, y)
        waitTicks(3)
        shot("kit_phone_gui${s}_$step")
    }

    private fun CaptureScript.click(position: () -> Pair<Int, Int>) {
        mouseTo(position)
        mouseDown()
        mouseUp()
    }

    private fun CaptureScript.mouseToApp(x: Int, y: Int) = mouseTo { app(x, y) }

    private fun CaptureScript.openPhone() {
        key(GLFW.GLFW_KEY_UP)
        waitUntil { CaptureHooks.isOpen }
        waitTicks(2)
    }

    private fun CaptureScript.closePhone() {
        key(GLFW.GLFW_KEY_ESCAPE)
        waitUntil { CaptureHooks.isClosed }
        waitTicks(2)
    }

    private fun CaptureScript.hold(progress: Float?) = run { CaptureHooks.holdAnimations(progress) }

    /** A device-local point in window GUI px. */
    private fun device(dx: Int, dy: Int): Pair<Int, Int> {
        val position = CaptureHooks.devicePosition() ?: error("The phone is away")
        return position[0] + dx to position[1] + dy
    }

    /** An app-local point ([x], [y]) dp in window GUI px (2 dp per GUI px). */
    private fun app(x: Int, y: Int): Pair<Int, Int> {
        val (vx, vy) = CaptureHooks.viewportOffset.let { it[0] to it[1] }
        return device(vx + x / 2, vy + y / 2)
    }

    /** The middle of home icon [slot]. */
    private fun icon(slot: Int): Pair<Int, Int> {
        val (vx, vy) = CaptureHooks.viewportOffset.let { it[0] to it[1] }
        return device(vx + 6 + 24 * (slot % 5) + 10, vy + 8 + 24 * (slot / 5) + 10)
    }

    /** The item of the menu opened on post 3 of Global (art direction 8.7 places the menu by its button). */
    private fun menuItem(): Pair<Int, Int> {
        val more = FeedScreen.current?.cards?.getOrNull(2)?.more ?: return app(128, 200)
        val top = more.toViewportY(0f)
        val below = more.toViewportY(more.height) + 4f
        val menuTop = if (below + 48f > 362f) top - 4f - 48f else below
        return app((more.toViewportX(more.width) - 60f).toInt(), (menuTop + 24f).toInt())
    }
}
