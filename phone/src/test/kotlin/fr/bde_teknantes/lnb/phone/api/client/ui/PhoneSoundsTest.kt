package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds.Sound
import fr.bde_teknantes.lnb.phone.client.shell.MinecraftShellHost
import fr.bde_teknantes.lnb.phone.client.shell.ShellSound
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Art direction 15: every sound of the phone follows the Sounds setting, read at the moment it plays. */
class PhoneSoundsTest {
    private val played = ArrayList<Sound>()
    private var on = true

    @BeforeEach
    fun setUp() {
        PhoneSounds.output = { played += it }
        PhoneSounds.setting = { on }
    }

    @AfterEach
    fun tearDown() {
        // A second chime left waiting must not play in another test.
        repeat(3) { PhoneSounds.tick() }
        PhoneSounds.output = PhoneSounds.gameOutput
        PhoneSounds.setting = PhoneSounds.playerSetting
    }

    /** Every way the phone and its apps make a sound, the shell's included. */
    private fun playEverything() {
        PhoneSounds.tap()
        PhoneSounds.like()
        PhoneSounds.sent()
        PhoneSounds.error()
        PhoneSounds.open()
        PhoneSounds.close()
        PhoneSounds.notification()
        repeat(3) { PhoneSounds.tick() }
        for (sound in ShellSound.entries) MinecraftShellHost.play(sound)
        repeat(3) { PhoneSounds.tick() }
    }

    @Test
    fun `with sounds on, each sound plays, the notification as two chimes`() {
        playEverything()
        assertEquals(
            listOf(
                Sound.TAP, Sound.LIKE, Sound.SENT, Sound.ERROR, Sound.OPEN, Sound.CLOSE, Sound.NOTIFICATION, Sound.NOTIFICATION_SECOND,
                Sound.OPEN, Sound.CLOSE, Sound.TAP, Sound.NOTIFICATION, Sound.NOTIFICATION_SECOND,
            ),
            played,
        )
    }

    @Test
    fun `with sounds off, nothing plays at all`() {
        on = false
        playEverything()
        assertEquals(emptyList<Sound>(), played)
    }

    @Test
    fun `sounds turned off between the two chimes silence the second`() {
        PhoneSounds.notification()
        on = false
        repeat(3) { PhoneSounds.tick() }
        assertEquals(listOf(Sound.NOTIFICATION), played)
    }

    @Test
    fun `with sounds off, the kit's widgets and snackbars are silent too`() {
        KitFakes.install()
        try {
            PhoneSounds.output = { played += it }
            PhoneSounds.setting = { on }
            on = false
            val screen = TestScreen()
            screen.add(Button("OK") {}.at(12f, 12f))
            screen.add(Button("Non").apply { enabled = false }.at(12f, 60f))
            screen.click(30f, 20f)
            screen.click(30f, 70f)
            screen.showErrorSnackbar("Action impossible")
            assertEquals(emptyList<Sound>(), played)
            on = true
            screen.click(30f, 20f)
            screen.click(30f, 70f)
            screen.showErrorSnackbar("Action impossible")
            assertEquals(listOf(Sound.TAP, Sound.ERROR, Sound.ERROR), played)
        } finally {
            KitFakes.uninstall()
        }
    }
}
