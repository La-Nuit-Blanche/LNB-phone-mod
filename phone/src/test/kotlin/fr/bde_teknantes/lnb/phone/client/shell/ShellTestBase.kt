package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.Phone
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import net.minecraft.client.gui.screens.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.glfw.GLFW
import java.nio.file.Path

/**
 * The phone opened from the inventory, giving [inventory] itself back when it closes: in the game a fresh inventory is
 * made then; a test checks which screen comes back by instance.
 */
internal fun PhoneShellCore.openFromInventory(inventory: Screen) = openFromInventory { inventory }

/** The phone opened with [returnTo] to show when it closes. */
internal fun PhoneShellCore.open(returnTo: Screen) = open { returnTo }

/**
 * A phone shell on a [FakeShellHost]: a 480 x 270 GUI px window (1920 x 1080 at GUI scale 4, the flows' W frames),
 * corner placement, in a world, with the mouse captured by the game.
 */
internal abstract class ShellTestBase {
    @TempDir
    lateinit var dir: Path

    lateinit var host: FakeShellHost
    lateinit var shell: PhoneShellCore

    /** What the app screens heard, as `name:event`. */
    val log = ArrayList<String>()

    @BeforeEach
    fun createShell() {
        host = FakeShellHost(dir.resolve("lnbphone-client.json"))
        shell = PhoneShellCore(host)
        host.shell = shell
        shell.init()
    }

    fun register(vararg apps: PhoneApp) {
        apps.forEach { host.apps.register(it, "test") }
        host.apps.freeze()
    }

    /** [count] plain apps named `app0`, `app1`... */
    fun registerApps(count: Int) = register(*Array(count) { testApp("app$it") })

    fun tick(times: Int = 1) = repeat(times) { shell.tick() }

    /** The phone key tapped while the game has the mouse: the shell hears the press at once, from the input event. */
    fun pressPhoneKeyInGame() {
        host.pressKey(host.phoneKey)
    }

    /** Opens the phone with its key and waits until it stands open with its screen. */
    fun openFully() {
        pressPhoneKeyInGame()
        tick(OPEN_TICKS)
        assertEquals(PhoneShellCore.State.OPEN, shell.state)
    }

    fun closeFully() {
        tick(CLOSE_TICKS)
        assertEquals(PhoneShellCore.State.CLOSED, shell.state)
    }

    val pose: DevicePose? get() = shell.currentPose()

    /** A left click (press and release) at window GUI px ([x], [y]), through the phone's screen if it is open. */
    fun click(x: Double, y: Double, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        press(x, y, button)
        release(x, y, button)
    }

    fun press(x: Double, y: Double, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        if (host.isPhoneScreen(host.screen)) shell.mouseClicked(x, y, button)
    }

    fun release(x: Double, y: Double, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        if (host.isPhoneScreen(host.screen)) shell.mouseReleased(x, y, button)
    }

    /** Window GUI px of device-local ([dx], [dy]) where the device stands now. */
    fun device(dx: Double, dy: Double): Pair<Double, Double> {
        val at = pose!!
        return (at.x + dx) to (at.y + dy)
    }

    /** Window GUI px of the centre of the icon in home [slot]. */
    fun iconCentre(slot: Int): Pair<Double, Double> = device(
        Device.VIEWPORT_X + HomeGrid.iconX(slot) + 10.0,
        Device.VIEWPORT_Y + HomeGrid.iconY(slot) + 10.0,
    )

    fun clickIcon(slot: Int) = iconCentre(slot).let { (x, y) -> click(x, y) }

    fun clickHomeButton() = device(70.0, 218.0).let { (x, y) -> click(x, y) }

    val phoneScreenShown: Boolean get() = host.isPhoneScreen(host.screen)

    companion object {
        const val OPEN_TICKS = 5
        const val CLOSE_TICKS = 4
        const val ESC = GLFW.GLFW_KEY_ESCAPE
        const val BACKSPACE = GLFW.GLFW_KEY_BACKSPACE
        const val UP = GLFW.GLFW_KEY_UP
    }
}

/**
 * An app screen whose answers a test sets: which keys it keeps, whether a text field has the focus. Logs what it
 * hears to [log] as `name:event`.
 */
internal open class ScriptedScreen(private val name: String, private val log: MutableList<String>) : AppScreen() {
    val handle: Phone get() = phone

    /** Keys [keyPressed] returns true for. */
    var keeps: Set<Int> = emptySet()
    var focus = false

    /** Runs inside [keyPressed], before it answers (to move the focus, navigate...). */
    var onKey: (Int) -> Unit = {}
    var onTick: () -> Unit = {}
    var onShownAction: () -> Unit = {}

    override val hasTextFocus: Boolean get() = focus

    override fun onShown() {
        log += "$name:shown"
        onShownAction()
    }

    override fun onHidden() {
        log += "$name:hidden"
    }

    override fun onClosed() {
        log += "$name:closed"
    }

    override fun tick() {
        onTick()
    }

    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {}

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        log += "$name:key $keyCode"
        onKey(keyCode)
        return keyCode in keeps
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        log += "$name:char $codePoint"
        return true
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        log += "$name:pressed $x,$y,$button"
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        log += "$name:released $x,$y,$button"
        return true
    }

    override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        log += "$name:dragged $x,$y,$button,$dragX,$dragY"
        return true
    }

    override fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float): Boolean {
        log += "$name:scrolled $scrollY"
        return true
    }

    override fun toString() = name
}
