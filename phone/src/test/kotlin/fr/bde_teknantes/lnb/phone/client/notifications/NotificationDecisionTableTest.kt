package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.notifications.AlertVisual.BANNER
import fr.bde_teknantes.lnb.phone.client.notifications.AlertVisual.NONE
import fr.bde_teknantes.lnb.phone.client.notifications.AlertVisual.PEEK
import fr.bde_teknantes.lnb.phone.client.notifications.PhoneView.CLOSED
import fr.bde_teknantes.lnb.phone.client.notifications.PhoneView.HOME
import fr.bde_teknantes.lnb.phone.client.notifications.PhoneView.OTHER_APP
import fr.bde_teknantes.lnb.phone.client.notifications.PhoneView.SAME_APP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every input of [NotificationAlerts.decide], one row each, with the outcome the art direction gives (or a decision
 * the user accepted), checked row by row against the document rather than the code.
 *
 * Columns: app installed, phone view, target on screen, `Sons` setting, `Aperçu des notifications` setting, then the
 * expected visual, chime and keep (false: read at once, no badge left), and where that comes from. A row whose outcome
 * nothing decides has no expectation (`undecided`): it is listed so the gap shows, and not asserted.
 *
 * Sources: 10.2 (peek, "Preview off"), 10.3 (banner over the open phone, "Not shown when the target is already on
 * screen ... The chime plays in both cases"), 12 (preview "disables the peek only"), 15 ("All muted when the Settings
 * Sons toggle is off"), flows P-8, P-10, P-11, P-13, P-14, F-4 and F-20; decisions 4, 5 and 6 of the task brief.
 */
internal class NotificationDecisionTableTest {
    class Row(
        val installed: Boolean,
        val view: PhoneView,
        val onScreen: Boolean,
        val sounds: Boolean,
        val preview: Boolean,
        val expected: Alert?,
        val source: String,
    ) {
        override fun toString() =
            "${if (installed) "installed" else "not installed"}, $view, target ${if (onScreen) "on screen" else "elsewhere"}, " +
                "sounds ${onOff(sounds)}, preview ${onOff(preview)} -> ${expected ?: "undecided"} ($source)"

        private fun onOff(b: Boolean) = if (b) "on" else "off"
    }

    companion object {
        private const val Y = true
        private const val N = false

        private fun alert(visual: AlertVisual, chime: Boolean, keep: Boolean = true) = Alert(visual, chime, keep)

        private const val IMPOSSIBLE = "undecided: situation() never reports a target on screen unless the phone is open on its app"

        @JvmStatic
        val table: List<Row> = listOf(
            //  installed view       on screen sounds preview  expected                     source
            Row(Y, CLOSED, N, Y, Y, alert(PEEK, chime = true), "10.2, P-8.2"),
            Row(Y, CLOSED, N, Y, N, alert(NONE, chime = true), "10.2 preview off, P-14.3"),
            Row(Y, CLOSED, N, N, Y, alert(PEEK, chime = false), "10.2, 15, P-13.3"),
            Row(Y, CLOSED, N, N, N, alert(NONE, chime = false), "10.2 preview off: chime only if sounds on"),
            Row(Y, CLOSED, Y, Y, Y, null, IMPOSSIBLE),
            Row(Y, CLOSED, Y, Y, N, null, IMPOSSIBLE),
            Row(Y, CLOSED, Y, N, Y, null, IMPOSSIBLE),
            Row(Y, CLOSED, Y, N, N, null, IMPOSSIBLE),

            Row(Y, HOME, N, Y, Y, alert(BANNER, chime = true), "10.3, P-10.2, P-10.3 (badge under it)"),
            Row(Y, HOME, N, Y, N, alert(BANNER, chime = true), "12 preview: peek only, P-14.5"),
            Row(Y, HOME, N, N, Y, alert(BANNER, chime = false), "10.3, 15, P-13.3"),
            Row(Y, HOME, N, N, N, alert(BANNER, chime = false), "12, 15"),
            Row(Y, HOME, Y, Y, Y, null, IMPOSSIBLE),
            Row(Y, HOME, Y, Y, N, null, IMPOSSIBLE),
            Row(Y, HOME, Y, N, Y, null, IMPOSSIBLE),
            Row(Y, HOME, Y, N, N, null, IMPOSSIBLE),

            Row(Y, OTHER_APP, N, Y, Y, alert(BANNER, chime = true), "10.3 any app, P-11.2, P-11.4 (badge waits)"),
            Row(Y, OTHER_APP, N, Y, N, alert(BANNER, chime = true), "12 preview: peek only"),
            Row(Y, OTHER_APP, N, N, Y, alert(BANNER, chime = false), "15"),
            Row(Y, OTHER_APP, N, N, N, alert(BANNER, chime = false), "12, 15"),
            Row(Y, OTHER_APP, Y, Y, Y, null, IMPOSSIBLE),
            Row(Y, OTHER_APP, Y, Y, N, null, IMPOSSIBLE),
            Row(Y, OTHER_APP, Y, N, Y, null, IMPOSSIBLE),
            Row(Y, OTHER_APP, Y, N, N, null, IMPOSSIBLE),

            Row(Y, SAME_APP, N, Y, Y, alert(BANNER, chime = true), "10.3, F-20.2, F-4.4; badge stays: decision 5"),
            Row(Y, SAME_APP, N, Y, N, alert(BANNER, chime = true), "10.3, 12; decision 5"),
            Row(Y, SAME_APP, N, N, Y, alert(BANNER, chime = false), "10.3, 15; decision 5"),
            Row(Y, SAME_APP, N, N, N, alert(BANNER, chime = false), "10.3, 12, 15; decision 5"),
            Row(Y, SAME_APP, Y, Y, Y, alert(NONE, chime = true, keep = false), "10.3 not shown, chime in both cases; read: decision 4"),
            Row(Y, SAME_APP, Y, Y, N, alert(NONE, chime = true, keep = false), "10.3, 12; decision 4"),
            Row(Y, SAME_APP, Y, N, Y, alert(NONE, chime = false, keep = false), "10.3, 15; decision 4"),
            Row(Y, SAME_APP, Y, N, N, alert(NONE, chime = false, keep = false), "10.3, 12, 15; decision 4"),
        ) + notInstalled()

        /** Decision 6: an app this client doesn't have gets no alert, whatever the phone and the settings. */
        private fun notInstalled(): List<Row> = buildList {
            for (view in PhoneView.entries) for (onScreen in listOf(N, Y)) for (sounds in listOf(Y, N)) for (preview in listOf(Y, N)) {
                add(Row(N, view, onScreen, sounds, preview, Alert.QUIET, "decision 6 (the document is silent)"))
            }
        }

        @JvmStatic
        fun decided() = table.filter { it.expected != null }
    }

    @Test
    fun `the table has one row for every combination of inputs`() {
        val keys = table.map { listOf(it.installed, it.view, it.onScreen, it.sounds, it.preview) }
        assertEquals(keys.size, keys.toSet().size, "a combination appears twice")
        assertEquals(2 * PhoneView.entries.size * 2 * 2 * 2, keys.size)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decided")
    fun `each decided row gives the documented outcome`(row: Row) {
        val actual = NotificationAlerts.decide(row.installed, PhoneSituation(row.view, row.onScreen), AlertSettings(row.sounds, row.preview))
        assertEquals(row.expected, actual)
    }

    @Test
    fun `the undecided rows are exactly the impossible ones`() {
        val undecided = table.filter { it.expected == null }
        assertEquals(12, undecided.size)
        assertEquals(true, undecided.all { it.installed && it.onScreen && it.view != SAME_APP })
    }
}
