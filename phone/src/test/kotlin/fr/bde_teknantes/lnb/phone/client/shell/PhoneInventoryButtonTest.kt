package fr.bde_teknantes.lnb.phone.client.shell

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.shell.InventoryButtonLayout.Look
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The phone's button on the survival inventory (art direction 11, P-4, P-5): its place, its looks from the unread
 * count, its badge, and what it does to the phone.
 */
internal class PhoneInventoryButtonTest : AlertsTestBase() {
    // ---- Place and looks ----

    @ParameterizedTest(name = "inventory at ({0}, {1}): button at ({2}, {3})")
    @CsvSource(
        // 480 x 270 (11): guiLeft 152, guiTop 52, under the quest button at (130, 60).
        "152, 52, 130, 98",
        // The same window with the recipe book open: vanilla moves the inventory to guiLeft 229 and the button follows.
        "229, 52, 207, 98",
        // 320 x 240: guiLeft 72, guiTop 37 (the recipe book covers the inventory there and doesn't move it).
        "72, 37, 50, 83",
        // 1920 x 1080 at GUI scale 2: 960 x 540.
        "392, 187, 370, 233",
    )
    fun `the button sits 22 px left of the inventory and 46 px down, wherever the inventory is`(left: Int, top: Int, x: Int, y: Int) {
        assertEquals(Point(x, y), InventoryButtonLayout.position(left, top))
    }

    @Test
    fun `the open recipe book covers the button's left part where vanilla draws it`() {
        // 480 x 270: the book at x 80 to 226 next to the inventory moved to guiLeft 229, the button at x 207.
        val book = InventoryButtonLayout.recipeBookArea(480, 270)
        assertEquals(InventoryButtonLayout.Area(80, 52, 147, 166), book)
        val button = InventoryButtonLayout.position(229, 52)
        assertTrue(book.contains(button.x, button.y + 10))
        assertTrue(book.contains(226, button.y + 10))
        assertFalse(book.contains(227, button.y + 10))
        // Under 379 px the book is centred over the inventory.
        assertEquals(InventoryButtonLayout.Area(86, 37, 147, 166), InventoryButtonLayout.recipeBookArea(320, 240))
    }

    @Test
    fun `the button is 27 x 28 and its badge sits 3 px up and left of it, at (127, 95) in P-5_1`() {
        assertEquals(27, InventoryButtonLayout.WIDTH)
        assertEquals(28, InventoryButtonLayout.HEIGHT)
        assertEquals(Point(127, 95), InventoryButtonLayout.badgePosition(InventoryButtonLayout.position(152, 52)))
    }

    @Test
    fun `the four sprites follow the unread count and the pointer (11)`() {
        assertEquals(Look.NORMAL, InventoryButtonLayout.look(0, hovered = false))
        assertEquals(Look.HOVER, InventoryButtonLayout.look(0, hovered = true))
        assertEquals(Look.NEW, InventoryButtonLayout.look(1, hovered = false))
        assertEquals(Look.NEW_HOVER, InventoryButtonLayout.look(1, hovered = true))
        assertEquals(Look.NEW, InventoryButtonLayout.look(50, hovered = false))
    }

    @ParameterizedTest(name = "{0} unread: badge \"{1}\", {2} px wide")
    @CsvSource("1, 1, 9", "3, 3, 9", "9, 9, 9", "10, 9+, 15", "50, 9+, 15")
    fun `the badge shows the total unread count as the home badges do (6_1)`(unread: Int, text: String, width: Int) {
        assertEquals(text, HomeGrid.badgeText(unread))
        assertEquals(width, HomeGrid.badgeWidth(text))
    }

    @Test
    fun `no badge without news`() {
        assertNull(HomeGrid.badgeText(0))
    }

    @Test
    fun `the tooltip's second line is none without news, singular for one, plural with the count otherwise`() {
        assertNull(InventoryButtonLayout.unreadKey(0))
        assertEquals("gui.lnbphone.inventory.unread.one", InventoryButtonLayout.unreadKey(1))
        assertEquals("gui.lnbphone.inventory.unread", InventoryButtonLayout.unreadKey(2))
        assertEquals("gui.lnbphone.inventory.unread", InventoryButtonLayout.unreadKey(50))
    }

    @Test
    fun `the tooltip's strings are those of 13_2, in both languages`() {
        fun lang(name: String) = javaClass.getResourceAsStream("/assets/lnbphone/lang/$name.json")!!
            .reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        val en = lang("en_us")
        val fr = lang("fr_fr")
        assertEquals("LNB Phone", en[InventoryButtonLayout.NAME_KEY].asString)
        assertEquals("LNB Phone", fr[InventoryButtonLayout.NAME_KEY].asString)
        assertEquals("1 new notification", en[InventoryButtonLayout.UNREAD_ONE_KEY].asString)
        assertEquals("1 nouvelle notification", fr[InventoryButtonLayout.UNREAD_ONE_KEY].asString)
        assertEquals("3 new notifications", en[InventoryButtonLayout.UNREAD_KEY].asString.format(3))
        assertEquals("3 nouvelles notifications", fr[InventoryButtonLayout.UNREAD_KEY].asString.format(3))
    }

    @Test
    fun `the badge doesn't pulse when the inventory opens with news, only when the count rises while it is open`() {
        val pulse = BadgePulse()
        pulse.update(3, ticks = 100)
        assertFalse(pulse.lit(100))
        pulse.update(4, ticks = 110)
        assertEquals(110, pulse.start)
        // 1500 ms: light and normal every 250 ms, starting light (14).
        val lit = (110L until 145L).map { pulse.lit(it) }
        assertEquals(List(5) { true } + List(5) { false } + List(5) { true } + List(5) { false } + List(5) { true } + List(10) { false }, lit)
    }

    @Test
    fun `a count that falls or stays doesn't pulse, a new rise restarts the pulse`() {
        val pulse = BadgePulse()
        pulse.update(0, ticks = 0)
        pulse.update(2, ticks = 10)
        pulse.update(2, ticks = 12)
        assertEquals(10, pulse.start)
        pulse.update(1, ticks = 14)
        assertEquals(10, pulse.start)
        pulse.update(3, ticks = 20)
        assertEquals(20, pulse.start)
    }

    @Test
    fun `before any rise the badge shows its normal fill`() {
        val pulse = BadgePulse()
        for (ticks in listOf(0L, 1L, 1000L, Long.MAX_VALUE)) assertFalse(pulse.lit(ticks))
    }

    // ---- What a click does (11, P-4, P-5) ----

    @Test
    fun `without news the phone opens on the screen it was put away on, and the inventory comes back (P-4)`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        // The inventory closes at once and the phone slides up from the window's edge.
        assertNull(host.screen)
        assertEquals(State.OPENING, shell.state)
        assertEquals(DevicePose(334, 270, 0f), pose)
        tick(OPEN_TICKS)
        assertEquals("mail", host.navigator.currentScreen.toString())
        // A click outside (the app keeps Esc).
        click(10.0, 10.0)
        assertSame(inventory, host.screen)
    }

    @Test
    fun `with news in any app the phone opens on home, whichever app was left open (P-5)`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("feed"))
        shell.close()
        closeFully()
        badges("feed" to 2)
        tick()
        shell.openFromInventory(host.openOtherScreen("inventory"))
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `badges of zero are no news`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        badges("feed" to 0, "mail" to 0)
        tick()
        shell.openFromInventory(host.openOtherScreen("inventory"))
        assertEquals("mail", host.navigator.currentScreen.toString())
    }

    @Test
    fun `the inventory comes back however the phone is closed`() {
        registerAll()
        val closers = listOf<Pair<String, () -> Unit>>(
            "Esc" to { host.pressKey(ESC) },
            "the phone key" to { host.pressKey(UP) },
            "a click outside" to { click(10.0, 10.0) },
            "an app closing the phone" to {
                host.navigator.close()
                host.runDeferred()
            },
        )
        for ((how, close) in closers) {
            val inventory = host.openOtherScreen("inventory")
            shell.openFromInventory(inventory)
            tick(OPEN_TICKS)
            close()
            assertSame(inventory, host.screen, how)
            tick(CLOSE_TICKS)
            assertEquals(State.CLOSED, shell.state, how)
            host.setScreen(null)
        }
    }

    @Test
    fun `closed while still rising, the phone gives the inventory back too`() {
        registerAll()
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        tick(2)
        pressPhoneKeyInGame()
        assertEquals(State.CLOSING, shell.state)
        assertSame(inventory, host.screen)
    }

    @Test
    fun `the next ordinary open and close doesn't bring the inventory back`() {
        registerAll()
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        tick(OPEN_TICKS)
        host.pressKey(ESC)
        host.setScreen(null)
        tick(CLOSE_TICKS)
        openFully()
        host.pressKey(ESC)
        assertNull(host.screen)
    }

    @Test
    fun `another screen taking over the rise is left alone, and the inventory doesn't come back over it`() {
        registerAll()
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        tick(2)
        val chat = host.openOtherScreen("chat")
        tick()
        assertEquals(State.CLOSING, shell.state)
        tick(CLOSE_TICKS)
        assertSame(chat, host.screen)
    }

    @Test
    fun `clicked while the phone slides down, it turns the device around where it is and remembers the inventory`() {
        registerAll()
        openFully()
        shell.close()
        tick(2)
        val sliding = pose
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        assertEquals(State.OPENING, shell.state)
        assertEquals(sliding, pose)
        tick(OPEN_TICKS)
        host.pressKey(ESC)
        assertSame(inventory, host.screen)
    }

    @Test
    fun `nothing happens when the phone is already rising or open, or out of a world`() {
        registerAll()
        pressPhoneKeyInGame()
        tick(2)
        val rising = pose
        shell.openFromInventory(TestScreen("inventory"))
        assertEquals(State.OPENING, shell.state)
        assertEquals(rising, pose)
        tick(OPEN_TICKS)
        shell.openFromInventory(TestScreen("inventory"))
        assertEquals(State.OPEN, shell.state)
        // Closing still goes back to the game: the second inventory wasn't remembered.
        host.pressKey(ESC)
        assertNull(host.screen)

        closeFully()
        host.inWorld = false
        val inventory = host.openOtherScreen("inventory")
        shell.openFromInventory(inventory)
        assertEquals(State.CLOSED, shell.state)
        assertSame(inventory, host.screen)
    }

    @Test
    fun `the inventory is closed as its own close does, and a fresh one comes back each time`() {
        registerAll()
        val first = host.openOtherScreen("inventory")
        var made = 0
        shell.openFromInventory { TestScreen("inventory ${++made}") }
        assertEquals(listOf(first), host.screensClosed)
        tick(OPEN_TICKS)
        host.pressKey(ESC)
        assertEquals("inventory 1", host.screen.toString())
        assertTrue(host.screen !== first)
    }

    @Test
    fun `no inventory comes back once the player is gone`() {
        registerAll()
        host.openOtherScreen("inventory")
        shell.openFromInventory { null }
        tick(OPEN_TICKS)
        host.pressKey(ESC)
        assertNull(host.screen)
    }

    @Test
    fun `news counts from the tick it arrives, before the badges refresh`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("mail"))
        shell.close()
        closeFully()
        badges("feed" to 1)
        // No tick between the arrival and the click.
        shell.openFromInventory(host.openOtherScreen("inventory"))
        assertTrue(host.navigator.isHome)
    }

    @Test
    fun `with news, a phone put away deeper than an app's root keeps its screen and shows the newest as the banner`() {
        registerAll()
        openFully()
        host.navigator.openApp(appId("feed"), "thread:3")
        host.navigator.currentScreen!!.let { (it as TargetScreen).handle.push(TargetScreen("compose")) }
        shell.close()
        closeFully()
        val newest = notification("mail", null)
        host.unreadList = listOf(notification("feed"), newest)
        badges("mail" to 1)
        shell.openFromInventory(host.openOtherScreen("inventory"))
        assertEquals("compose", host.navigator.currentScreen.toString())
        assertSame(newest, alerts.banner?.notification)
        // The banner is the news already counted: no chime.
        assertEquals(0, chimes)
    }

    @Test
    fun `the button opens the phone with its own sound only`() {
        registerAll()
        shell.openFromInventory(host.openOtherScreen("inventory"))
        assertEquals(listOf(ShellSound.OPEN), host.sounds)
    }
}
