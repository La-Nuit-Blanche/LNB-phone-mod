package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.testing.PhoneTester

/*
 * Helpers for playing Feed's screens: the test font draws every character 6.5 dp wide, so string IDs used as text are
 * often cut in their slots (a dialog's buttons, a snackbar beside its action); these find them by what they start
 * with, and the dialog's buttons by their shape.
 */

/** Whether a line starting with [start] was drawn. */
internal fun PhoneTester.showsStart(start: String) = texts.any { it.text.startsWith(start) }

/** The dialog's confirm button: the filled 104 x 32 dp pill at the card's bottom right (art direction 8.7). */
internal fun PhoneTester.confirmDialog() {
    val button = shapes.last { it.width == 104f && it.height == 32f }
    click(button.x + 52f, button.y + 16f)
}

/** The dialog's cancel button: 88 dp, 8 dp left of the confirm button. */
internal fun PhoneTester.cancelDialog() {
    val button = shapes.last { it.width == 104f && it.height == 32f }
    click(button.x - 8f - 44f, button.y + 16f)
}

/** Clicks the middle of the first line drawn that starts with [start]. */
internal fun PhoneTester.clickStart(start: String) {
    val drawn = texts.first { it.text.startsWith(start) }
    click(drawn.x + 10f, drawn.y + 6f)
}
