package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShellCore.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every state the phone can be in when a notification arrives, crossed with the player's two settings, with what the
 * art direction says happens: what shows (peek, banner, nothing), whether the chime plays, whether the notification is
 * kept as a badge or read in place. Checked against the document and the accepted decisions, not against the code.
 *
 * Sources: 10.2 (peek; "Preview off": no peek, chime, badge waits), 10.3 (banner over any screen; not shown when the
 * target is on screen, "The chime plays in both cases"), 12 (preview "disables the peek only"), 15 (all sounds muted
 * with Sons off), P-8, P-10, P-11, P-13.3, P-14.3, P-14.5; and four decisions taken while the notifications were built,
 * recorded since in 10.2 and 10.3, which the rows cite by number: 1 (GUI scale 1), 3 (sliding open counts as open,
 * sliding closed the peek waits), 4 (another screen open: chime only) and 5 (a new notification swaps the peek's
 * content). A notification of an app this client lacks raises no alert at all (`NotificationAlerts`).
 *
 * A peek already showing with the preview then turned off was never decided: those rows are left out.
 */
internal class ShellAlertsTableTest : AlertsTestBase() {
    /** What the phone looks like when the notification arrives, and which notification arrives. */
    enum class Setup(val app: String = "feed", val target: String? = "thread:1", val scale: Double = 4.0) {
        CLOSED,
        CLOSED_CHAT,
        CLOSED_PAUSE_MENU,
        CLOSED_CONTAINER,
        CLOSED_DEATH_SCREEN,
        PEEKING_RISING,
        PEEKING_HOLDING,
        PEEKING_SINKING,
        OPENING_ON_HOME,
        OPENING_ONTO_THE_TARGET,
        OPEN_ON_HOME,
        OPEN_ON_THE_TARGET,
        OPEN_IN_THE_APP_ON_ANOTHER_TARGET,
        OPEN_IN_THE_APP_ON_ITS_ROOT,
        OPEN_ON_THE_TARGET_FOR_AN_UNTARGETED_NOTIFICATION(target = null),
        OPEN_IN_ANOTHER_APP,
        CLOSING,
        CLOSING_BACK_TO_THE_INVENTORY,
        GUI_SCALE_1_CLOSED(scale = 1.0),
        GUI_SCALE_1_OPEN_ON_HOME(scale = 1.0),
        GUI_SCALE_1_OPEN_ON_THE_TARGET(scale = 1.0),
        APP_NOT_INSTALLED_CLOSED(app = "gone"),
        APP_NOT_INSTALLED_OPEN_ON_HOME(app = "gone"),
    }

    enum class Shown {
        NOTHING,
        PEEK,

        /** Nothing yet: the peek rises once the device is away (decision 3). */
        PEEK_ONCE_AWAY,
        BANNER,
    }

    class Row(val setup: Setup, val preview: Boolean, val shown: Shown, val kept: Boolean, val source: String) {
        val installed get() = setup.app != "gone"
    }

    class Case(val row: Row, val sounds: Boolean) {
        override fun toString() = "${row.setup}, preview ${onOff(row.preview)}, sounds ${onOff(sounds)} -> " +
            "${row.shown}, chime ${row.installed && sounds}, ${if (row.kept) "kept" else "read"} (${row.source})"

        private fun onOff(b: Boolean) = if (b) "on" else "off"
    }

    companion object {
        private const val ON = true
        private const val OFF = false
        private const val KEPT = true
        private const val READ = false

        @JvmStatic
        val table: List<Row> = listOf(
            Row(Setup.CLOSED, ON, Shown.PEEK, KEPT, "10.2, P-8"),
            Row(Setup.CLOSED, OFF, Shown.NOTHING, KEPT, "10.2 preview off, P-14.3"),
            Row(Setup.CLOSED_CHAT, ON, Shown.NOTHING, KEPT, "decision 4"),
            Row(Setup.CLOSED_CHAT, OFF, Shown.NOTHING, KEPT, "decision 4, 10.2"),
            Row(Setup.CLOSED_PAUSE_MENU, ON, Shown.NOTHING, KEPT, "decision 4"),
            Row(Setup.CLOSED_PAUSE_MENU, OFF, Shown.NOTHING, KEPT, "decision 4, 10.2"),
            Row(Setup.CLOSED_CONTAINER, ON, Shown.NOTHING, KEPT, "decision 4"),
            Row(Setup.CLOSED_CONTAINER, OFF, Shown.NOTHING, KEPT, "decision 4, 10.2"),
            Row(Setup.CLOSED_DEATH_SCREEN, ON, Shown.NOTHING, KEPT, "decision 4"),
            Row(Setup.CLOSED_DEATH_SCREEN, OFF, Shown.NOTHING, KEPT, "decision 4, 10.2"),
            Row(Setup.PEEKING_RISING, ON, Shown.PEEK, KEPT, "10.2 queue, decision 5"),
            Row(Setup.PEEKING_HOLDING, ON, Shown.PEEK, KEPT, "10.2 queue, decision 5"),
            Row(Setup.PEEKING_SINKING, ON, Shown.PEEK, KEPT, "10.2 queue, decision 5"),
            Row(Setup.OPENING_ON_HOME, ON, Shown.BANNER, KEPT, "decision 3, 10.3"),
            Row(Setup.OPENING_ON_HOME, OFF, Shown.BANNER, KEPT, "decision 3, 12, P-14.5"),
            Row(Setup.OPENING_ONTO_THE_TARGET, ON, Shown.NOTHING, READ, "decision 3, 10.3 target on screen"),
            Row(Setup.OPENING_ONTO_THE_TARGET, OFF, Shown.NOTHING, READ, "decision 3, 10.3, 12"),
            Row(Setup.OPEN_ON_HOME, ON, Shown.BANNER, KEPT, "10.3, P-10"),
            Row(Setup.OPEN_ON_HOME, OFF, Shown.BANNER, KEPT, "12, P-14.5"),
            Row(Setup.OPEN_ON_THE_TARGET, ON, Shown.NOTHING, READ, "10.3 not shown when the target is on screen"),
            Row(Setup.OPEN_ON_THE_TARGET, OFF, Shown.NOTHING, READ, "10.3, 12"),
            Row(Setup.OPEN_IN_THE_APP_ON_ANOTHER_TARGET, ON, Shown.BANNER, KEPT, "10.3"),
            Row(Setup.OPEN_IN_THE_APP_ON_ANOTHER_TARGET, OFF, Shown.BANNER, KEPT, "10.3, 12"),
            Row(Setup.OPEN_IN_THE_APP_ON_ITS_ROOT, ON, Shown.BANNER, KEPT, "10.3"),
            Row(Setup.OPEN_IN_THE_APP_ON_ITS_ROOT, OFF, Shown.BANNER, KEPT, "10.3, 12"),
            Row(Setup.OPEN_ON_THE_TARGET_FOR_AN_UNTARGETED_NOTIFICATION, ON, Shown.BANNER, KEPT, "10.3; no target is never on screen"),
            Row(Setup.OPEN_ON_THE_TARGET_FOR_AN_UNTARGETED_NOTIFICATION, OFF, Shown.BANNER, KEPT, "10.3, 12"),
            Row(Setup.OPEN_IN_ANOTHER_APP, ON, Shown.BANNER, KEPT, "10.3, P-11"),
            Row(Setup.OPEN_IN_ANOTHER_APP, OFF, Shown.BANNER, KEPT, "12, P-11"),
            Row(Setup.CLOSING, ON, Shown.PEEK_ONCE_AWAY, KEPT, "decision 3"),
            Row(Setup.CLOSING, OFF, Shown.NOTHING, KEPT, "decision 3, 10.2 preview off"),
            Row(Setup.CLOSING_BACK_TO_THE_INVENTORY, ON, Shown.NOTHING, KEPT, "decision 4: the inventory is open"),
            Row(Setup.CLOSING_BACK_TO_THE_INVENTORY, OFF, Shown.NOTHING, KEPT, "decision 4"),
            Row(Setup.GUI_SCALE_1_CLOSED, ON, Shown.NOTHING, KEPT, "decision 1"),
            Row(Setup.GUI_SCALE_1_CLOSED, OFF, Shown.NOTHING, KEPT, "decision 1"),
            Row(Setup.GUI_SCALE_1_OPEN_ON_HOME, ON, Shown.NOTHING, KEPT, "decision 1"),
            Row(Setup.GUI_SCALE_1_OPEN_ON_HOME, OFF, Shown.NOTHING, KEPT, "decision 1"),
            Row(Setup.GUI_SCALE_1_OPEN_ON_THE_TARGET, ON, Shown.NOTHING, KEPT, "decision 1: not read where it can't be seen"),
            Row(Setup.GUI_SCALE_1_OPEN_ON_THE_TARGET, OFF, Shown.NOTHING, KEPT, "decision 1"),
            Row(Setup.APP_NOT_INSTALLED_CLOSED, ON, Shown.NOTHING, KEPT, "no alert for an app this client lacks"),
            Row(Setup.APP_NOT_INSTALLED_CLOSED, OFF, Shown.NOTHING, KEPT, "app not installed"),
            Row(Setup.APP_NOT_INSTALLED_OPEN_ON_HOME, ON, Shown.NOTHING, KEPT, "app not installed"),
            Row(Setup.APP_NOT_INSTALLED_OPEN_ON_HOME, OFF, Shown.NOTHING, KEPT, "app not installed"),
        )

        @JvmStatic
        fun cases(): List<Case> = table.flatMap { row -> listOf(Case(row, sounds = true), Case(row, sounds = false)) }
    }

    @Test
    fun `every setup has a row for each preview setting, except a peek already showing`() {
        for (setup in Setup.entries) {
            val previews = table.filter { it.setup == setup }.map { it.preview }.sorted()
            val expected = if (setup.name.startsWith("PEEKING")) listOf(ON) else listOf(OFF, ON)
            assertEquals(expected, previews, "$setup")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `each arrival does what the art direction says`(case: Case) {
        val row = case.row
        val setup = row.setup
        registerAll()
        host.settings.update { it.copy(sounds = case.sounds, preview = row.preview) }
        host.guiScale = setup.scale
        prepare(setup)
        host.sounds.clear()
        host.dismissed.clear()

        val n = notify(setup.app, setup.target)

        assertEquals(if (row.installed && case.sounds) 1 else 0, chimes, "chime")
        assertEquals(if (row.kept) emptyList() else listOf(n), host.dismissed, "kept or read")
        when (row.shown) {
            Shown.NOTHING -> {
                assertNull(alerts.banner, "banner")
                assertNull(alerts.peek, "peek")
                // Not later either, once a closing phone is away.
                tick(CLOSE_TICKS + 1)
                assertNull(alerts.peek, "peek later")
            }
            Shown.PEEK -> {
                assertSame(n, alerts.peek?.notification, "peek")
                assertNull(alerts.banner, "banner")
            }
            Shown.PEEK_ONCE_AWAY -> {
                assertNull(alerts.peek, "peek while the device slides down")
                closeFully()
                tick()
                assertSame(n, alerts.peek?.notification, "peek once away")
            }
            Shown.BANNER -> {
                assertSame(n, alerts.banner?.notification, "banner")
                assertNull(alerts.peek, "peek")
            }
        }
    }

    private fun prepare(setup: Setup) {
        when (setup) {
            Setup.CLOSED, Setup.GUI_SCALE_1_CLOSED, Setup.APP_NOT_INSTALLED_CLOSED -> {}
            Setup.CLOSED_CHAT -> host.openOtherScreen("chat")
            Setup.CLOSED_PAUSE_MENU -> host.pressKey(ESC).also { assertEquals(FakeShellHost.PAUSE, host.screen.toString()) }
            Setup.CLOSED_CONTAINER -> host.openOtherScreen("chest")
            Setup.CLOSED_DEATH_SCREEN -> host.openOtherScreen("death")
            Setup.PEEKING_RISING -> peeking(2)
            Setup.PEEKING_HOLDING -> peeking(40)
            Setup.PEEKING_SINKING -> peeking(PEEK_LIFE_TICKS - 2)
            Setup.OPENING_ON_HOME -> {
                pressPhoneKeyInGame()
                tick(2)
                assertEquals(State.OPENING, shell.state)
            }
            Setup.OPENING_ONTO_THE_TARGET -> {
                openFully()
                host.navigator.openApp(appId("feed"), "thread:1")
                shell.close()
                closeFully()
                pressPhoneKeyInGame()
                tick(2)
                assertEquals(State.OPENING, shell.state)
            }
            Setup.OPEN_ON_HOME, Setup.GUI_SCALE_1_OPEN_ON_HOME, Setup.APP_NOT_INSTALLED_OPEN_ON_HOME -> openFully()
            Setup.OPEN_ON_THE_TARGET, Setup.GUI_SCALE_1_OPEN_ON_THE_TARGET, Setup.OPEN_ON_THE_TARGET_FOR_AN_UNTARGETED_NOTIFICATION -> {
                openFully()
                host.navigator.openApp(appId("feed"), "thread:1")
            }
            Setup.OPEN_IN_THE_APP_ON_ANOTHER_TARGET -> {
                openFully()
                host.navigator.openApp(appId("feed"), "thread:2")
            }
            Setup.OPEN_IN_THE_APP_ON_ITS_ROOT -> {
                openFully()
                host.navigator.openApp(appId("feed"))
            }
            Setup.OPEN_IN_ANOTHER_APP -> {
                openFully()
                host.navigator.openApp(appId("mail"))
            }
            Setup.CLOSING -> {
                openFully()
                host.pressKey(ESC)
                assertEquals(State.CLOSING, shell.state)
            }
            Setup.CLOSING_BACK_TO_THE_INVENTORY -> {
                val inventory = host.openOtherScreen("inventory")
                shell.openFromInventory(inventory)
                tick(OPEN_TICKS)
                host.pressKey(ESC)
                assertEquals(State.CLOSING, shell.state)
                assertSame(inventory, host.screen)
            }
        }
    }

    // Another notification (for another app) peeking, [ticks] into its life.
    private fun peeking(ticks: Int) {
        notify("mail", null)
        tick(ticks)
        check(alerts.peek != null && peekAt() != null)
    }
}
