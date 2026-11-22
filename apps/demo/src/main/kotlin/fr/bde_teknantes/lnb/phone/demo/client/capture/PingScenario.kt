package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.demo.client.notifications.LiveBannerScreen
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import fr.bde_teknantes.lnb.phone.demo.ping.SendPingPayload
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Task 10: Ping, the example app, in a world and inside the real phone, driven through the game's input: its icon on
 * the home screen, the list of players, a ping typed and sent to the player themselves, the notification the server
 * sends back (read from the client's store), the app opened at that notification's target with the draft and history
 * kept while its screens were built anew, then back to the list. At GUI scale 3, the screens again at 2 and 4, and in
 * French at 3.
 *
 * Opening a notification from its banner is task 7's; here the app is opened at the stored notification's target the
 * way the banner does it, through the navigator, which is internal to the phone and so reached by reflection.
 */
internal object PingScenario {
    fun script() = CaptureScript().apply {
        enterWorld()
        language("en_us")
        run { CaptureHooks.setCentred(false) }
        guiScale(3)
        flow()
        for (scale in listOf(2, 4)) screens("gui$scale") { guiScale(scale) }
        screens("gui3_fr") {
            language("fr_fr")
            guiScale(3)
        }
        language("en_us")
    }

    private fun CaptureScript.flow() {
        val name = { step: String -> "ping_gui3_$step" }
        waitTicks(10)
        openPhone()
        click { device(70, 218) }
        waitTicks(4)
        mouseTo { icon(slotOf(Ping.APP_ID)) }
        waitTicks(3)
        shot(name("01_home_hover"))
        mouseDown()
        mouseUp()
        waitTicks(8)
        mouseToApp(128, 72)
        waitTicks(3)
        shot(name("02_players"))

        // The player's own row: a ping to oneself is how Ping is tried alone.
        mouseDown()
        mouseUp()
        waitTicks(8)
        shot(name("03_ping_focused"))
        type("Meet me at the mill")
        waitTicks(3)
        shot(name("04_typed"))
        key(GLFW.GLFW_KEY_ENTER)
        waitTicks(4)
        shot(name("05_sent"))
        waitTicks(30)
        run { LnbPhone.LOGGER.info("Capture ping: after sending, client store has {}", LiveBannerScreen.summary()) }

        // A draft left behind, and home. A second ping arrives there: sent as the screen sends it, but from home, so
        // that the phone doesn't count it as read in place (the first one landed on the very screen it points to).
        type("see you soon")
        click { device(70, 218) }
        waitTicks(4)
        run { PacketDistributor.sendToServer(SendPingPayload(Minecraft.getInstance().player!!.uuid, "Are you there?")) }
        waitTicks(20)
        shot(name("06_home_after_ping"))

        // That notification opened at its target: Ping lands on the sender's screen, built anew with the draft and
        // the history kept in the session, over the list.
        run {
            val newest = LiveBannerScreen.read().first.lastOrNull { it.appId == Ping.APP_ID }
            LnbPhone.LOGGER.info("Capture ping: opening the newest ping notification, {}", newest)
            openApp(Ping.APP_ID, newest?.target ?: error("No ping notification reached the client"))
        }
        waitTicks(8)
        shot(name("07_opened_at_target"))
        run { LnbPhone.LOGGER.info("Capture ping: after opening Ping, client store has {}", LiveBannerScreen.summary()) }
        click { app(22, 22) }
        waitTicks(8)
        shot(name("08_back_to_players"))
        closePhone()
    }

    /** The list and the ping screen (with its draft and history) at the GUI scale or language [setUp] sets. */
    private fun CaptureScript.screens(suffix: String, setUp: CaptureScript.() -> Unit) {
        setUp()
        waitTicks(10)
        openPhone()
        click { device(70, 218) }
        waitTicks(4)
        click { icon(slotOf(Ping.APP_ID)) }
        waitTicks(8)
        mouseToApp(128, 72)
        waitTicks(3)
        shot("ping_${suffix}_players")
        mouseDown()
        mouseUp()
        waitTicks(8)
        shot("ping_${suffix}_ping")
        closePhone()
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

    // The registry and the navigator are internal to the phone: development code reaches them by reflection.

    /** Where the app [id] sits on the home screen, in home order. */
    @Suppress("UNCHECKED_CAST")
    private fun slotOf(id: ResourceLocation): Int {
        val registry = LnbPhoneClient::class.java.getDeclaredField("apps").apply { isAccessible = true }.get(LnbPhoneClient)
        val apps = registry.javaClass.getMethod("getApps").invoke(registry) as List<PhoneApp>
        return apps.indexOfFirst { it.id == id }.also { check(it >= 0) { "$id isn't on the phone" } }
    }

    /** Opens the app [id] at [target], as opening one of its notifications does. */
    private fun openApp(id: ResourceLocation, target: String) {
        val navigator = LnbPhoneClient::class.java.getDeclaredField("navigator").apply { isAccessible = true }.get(LnbPhoneClient)
        val opened = navigator.javaClass.getMethod("openApp", ResourceLocation::class.java, String::class.java).invoke(navigator, id, target)
        check(opened == true) { "The phone refused to open $id at $target" }
    }
}
