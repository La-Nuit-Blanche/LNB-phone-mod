package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import fr.bde_teknantes.lnb.phone.demo.ping.PingTarget
import fr.bde_teknantes.lnb.phone.demo.ping.client.PingSession
import fr.bde_teknantes.lnb.phone.demo.ping.SendPingPayload
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * Task 10: Ping, the example app, in a world and inside the real phone, driven through the game's input: its icon on
 * the home screen, the list of players, a ping typed and sent to the player themselves (the server's answer adds it to
 * the conversation, and the ping received shows there too); then pings arriving with the phone closed (the peek, and
 * Up landing on the conversation) and open on home (the banner, the badge, a click on the banner); then the refusals:
 * a second ping within the cooldown, and a ping to a player who isn't online. At GUI scale 3, the screens again at 2
 * and 4, and in French at 3.
 *
 * Pings that must arrive while the player is elsewhere are sent as the screen sends them, from a step of the script.
 * The player who isn't online is reached through the navigator (internal to the phone, so by reflection), opening
 * Ping at a target that names them, as one of their old notifications would.
 */
internal object PingScenario {
    fun script() = CaptureScript().apply {
        enterWorld()
        language("en_us")
        run {
            CaptureHooks.resetSettings()
            CaptureHooks.setCentred(false)
        }
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

        // The player's own row: a ping to oneself is how Ping is tried alone. It lands on the screen it points to,
        // so no banner, and it is read at once.
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
        log("after a ping to oneself on the conversation")

        // Refused: a second ping within the cooldown. The error, and the text back in the field.
        type("again")
        key(GLFW.GLFW_KEY_ENTER)
        waitTicks(4)
        shot(name("06_refused_too_fast"))
        log("after a ping within the cooldown")

        // With the phone closed: the peek, then Up during it lands on the conversation.
        closePhone()
        waitTicks(30)
        ping("Are you there?")
        waitUntil { CaptureHooks.isPeeking }
        waitTicks(8)
        shot(name("07_peek"))
        log("during the peek")
        key(GLFW.GLFW_KEY_UP)
        waitUntil { CaptureHooks.isOpen }
        waitTicks(6)
        shot(name("08_opened_from_peek"))
        log("after opening the peek")

        // With the phone open on home: the banner, then the badge it leaves, then a click on a banner.
        click { device(70, 218) }
        waitTicks(30)
        ping("Still there?")
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(6)
        shot(name("09_banner_on_home"))
        waitUntil { !CaptureHooks.bannerShown }
        waitTicks(4)
        mouseTo { icon(slotOf(Ping.APP_ID)) }
        waitTicks(2)
        shot(name("10_badge"))
        log("after the banner went")
        mouseTo { device(70, 140) }
        waitTicks(30)
        ping("Hello?")
        waitUntil { CaptureHooks.bannerShown }
        waitTicks(6)
        click { device(6 + 64, 23 + 21) }
        waitTicks(8)
        shot(name("11_opened_from_banner"))
        click { device(70, 218) }
        waitTicks(4)
        shot(name("12_home_badge_cleared"))
        log("back home after the banner")

        // Refused: a player who isn't online. The error names them; the text comes back to the field.
        run { openApp(Ping.APP_ID, PingTarget(GHOST, "Ghost").toString()) }
        waitTicks(8)
        type("Where are you")
        key(GLFW.GLFW_KEY_ENTER)
        waitTicks(4)
        shot(name("13_refused_offline"))
        run { LnbPhone.LOGGER.info("Capture ping: after a ping to Ghost, draft '{}'", PingSession.draft(GHOST).text) }
        click { app(22, 22) }
        waitTicks(8)
        shot(name("14_back_to_players"))
        closePhone()
    }

    /** Pings the player themselves as the conversation screen does, from wherever the script stands. */
    private fun CaptureScript.ping(text: String) = run {
        PacketDistributor.sendToServer(SendPingPayload(Minecraft.getInstance().player!!.uuid, text))
    }

    private fun CaptureScript.log(moment: String) = run {
        val me = Minecraft.getInstance().player!!.uuid
        LnbPhone.LOGGER.info(
            "Capture ping: {}: unread {}, conversation {}, draft '{}', peek {}, banner {}",
            moment, CaptureHooks.unreadCount(Ping.APP_ID), PingSession.conversation(me).map { (if (it.mine) "> " else "< ") + it.text },
            PingSession.draft(me).text, CaptureHooks.isPeeking, CaptureHooks.bannerShown,
        )
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

    /** A player who never joined. */
    private val GHOST: UUID = UUID.fromString("00000000-0000-4000-8000-000000000000")
}
