package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.FakeCanvas
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.api.client.ui.KitFakes
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettings
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
import fr.bde_teknantes.lnb.phone.client.settings.SettingsChoices
import fr.bde_teknantes.lnb.phone.client.settings.SettingsScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/**
 * The Settings screen inside the phone (flow P-12): the player's choice reaches the shell through the settings store,
 * and the open device glides. The app is built as SettingsApp builds it, on the fake host's store.
 */
internal class SettingsInShellTest : ShellTestBase() {
    private val settingsApp
        get() = PhoneApp(
            AppRegistry.SETTINGS_ID,
            Component.literal("Settings"),
            AppIcon.Glyph(TileColor.GRAPHITE, ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/sprites/icon/settings.png")),
        ) { SettingsScreen(SettingsChoices(host.settings)) }

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        host.apps.register(testApp("feed"), "test")
        host.apps.register(settingsApp, LnbPhone.ID)
        host.apps.freeze()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    /** Opens the phone and Settings, the last icon, and lets the screen slide in. */
    private fun openSettings(): SettingsScreen {
        openFully()
        clickIcon(1)
        tick(10)
        return assertInstanceOf(SettingsScreen::class.java, host.navigator.currentScreen)
    }

    private fun SettingsScreen.position() = widgets.filterIsInstance<SegmentedControl>().single()

    private fun glide(): List<DevicePose> = (1..5).map {
        tick()
        pose!!
    }

    @Test
    fun `clicking Centre where P-12 clicks it glides the phone to the centre, and Coin glides it back`() {
        val screen = openSettings()
        assertEquals(DevicePose(334, 36, 0f), pose)

        // P-12 step 4: the click at window GUI px (433, 117), Centre's place in the corner.
        click(433.0, 117.0)
        assertEquals(Placement.CENTRE, host.settings.current.placement)
        assertEquals(Placement.CENTRE, PhoneSettingsStore(dir.resolve("lnbphone-client.json")) { "player" }.current.placement)
        assertEquals(listOf(275, 229, 196, 177, 170), glide().map { it.x })
        assertEquals(DevicePose(170, 21, 1f), pose)
        assertEquals(1, screen.position().selected)
        assertEquals(PhoneShellCore.State.OPEN, shell.state)

        // P-12 step 6: Coin at (211, 102) in the centred phone.
        click(211.0, 102.0)
        assertEquals(PhoneSettings(), host.settings.current)
        assertEquals(listOf(DevicePose(334, 36, 0f)), glide().takeLast(1))
        assertEquals(0, screen.position().selected)
    }

    @Test
    fun `the keyboard reaches the same choice, Tab Tab Enter, through the phone`() {
        val screen = openSettings()
        // The shell's tests draw no app screen, and the header hides its back button (no screen to go back to) while
        // drawing: one frame, as the game draws before the player can press anything, or Tab stops on it first.
        screen.render(FakeCanvas(), -1f, -1f, 0f)
        host.pressKey(GLFW.GLFW_KEY_TAB)
        host.pressKey(GLFW.GLFW_KEY_TAB)
        host.pressKey(GLFW.GLFW_KEY_ENTER)
        assertEquals(Placement.CENTRE, host.settings.current.placement)
        assertEquals(DevicePose(170, 21, 1f), glide().last())
        assertEquals(1, screen.position().selected)
        assertTrue(phoneScreenShown)
    }

    @Test
    fun `sounds and preview switched in the phone are saved, and leave the device where it is`() {
        openSettings()
        // The rows' toggles, at P-13's and P-14's cursor (226, 169) and (226, 218), from the corner pose.
        click(334.0 + 6 + 113, 36.0 + 23 + 84.5)
        click(334.0 + 6 + 113, 36.0 + 23 + 109)
        assertEquals(PhoneSettings(sounds = false, preview = false), host.settings.current)
        tick(5)
        assertEquals(DevicePose(334, 36, 0f), pose)
    }

    @Test
    fun `put away mid-glide and opened again, the phone rises to the centre on the same Settings screen`() {
        val screen = openSettings()
        click(433.0, 117.0)
        // Put away mid-glide and opened again: the phone rises straight to the centre and Settings still says Centre.
        tick(2)
        host.pressKey(ESC)
        closeFully()
        openFully()
        assertEquals(DevicePose(170, 21, 1f), pose)
        assertEquals(1, screen.position().selected)
    }
}
