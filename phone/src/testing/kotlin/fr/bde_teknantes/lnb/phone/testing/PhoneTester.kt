package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.AppScreenFactory
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.api.client.ui.Motion
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.ui.TextEnvironment
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW

/**
 * A phone without the game, for an app's unit tests: it opens your app, shows its screens, and passes them frames,
 * ticks, the mouse, keys and typed text the way the phone does. Navigation is the phone's own (`push`, `pop`,
 * `replace`, `home`, `close` behave as in the game), so a test can click a row and check which screen came.
 *
 * **For apps built in this repository.** The `lnbphone.app` plugin puts it on an app's tests, and loads it with the
 * phone's classes, which it needs. It isn't published for mods built elsewhere, and nothing about it is promised to them
 * yet: that would take an artifact per phone version, loaded with the phone in the game's module layer.
 *
 * ```kotlin
 * class NotesScreenTest {
 *     private val tester = PhoneTester { NotesScreen() }
 *
 *     @AfterEach
 *     fun tearDown() = tester.close()
 *
 *     @Test
 *     fun `a click on a note opens it`() {
 *         tester.click(40f, 68f)                                    // the first row
 *         assertInstanceOf(NoteScreen::class.java, tester.screen)
 *         assertEquals(listOf(PhoneSound.TAP), tester.sounds)
 *         tester.key(GLFW.GLFW_KEY_BACKSPACE)                       // back, as on the phone
 *         assertInstanceOf(NotesScreen::class.java, tester.screen)
 *     }
 * }
 * ```
 *
 * What stands in for the game while a tester is open (until [close]):
 * - **There is no game**: `Minecraft.getInstance()` is null, so a screen that reads the game (the connection, the
 *   player, the world) throws under the tester. Write screens to be testable by taking what they show from your
 *   app's own objects (a session filled by your payload handlers, as Ping's `PingSession`), or as constructor
 *   arguments, and reading the game only in your app's client glue. A test then fills those objects itself.
 * - **Text** is measured as if every character were half its size wide (6.5 dp in body text), so layouts are known in
 *   advance. No language is loaded: `I18n.get(key)` returns the key, which is what screens then show.
 * - **Sounds** go to [sounds] instead of the speakers, and follow [soundsOn] as they follow the player's setting.
 * - **The clipboard** is [clipboard]; Shift is held for the mouse while [shiftHeld].
 * - **Time** moves only with [tick] and [advance] (50 ms per tick), as it moves with the game's ticks.
 * - **The keyboard** is a US layout: a key that types a character in the game sends it after the key, as the game
 *   does (letters, digits, space and plain punctuation; none while Ctrl or Alt is held), and a held key repeats both.
 *   [type] presses those keys; other characters arrive alone, as an input method sends them.
 *
 * Every action draws a frame afterwards, as the phone draws one per frame; [texts] and [shapes] are what it drew. A
 * frame that leaves a clip, an offset or an opacity pushed, or pops one it didn't push, fails the call. The keys the
 * phone itself acts on behave as in the game, on a first press the screen doesn't use: Esc puts the phone away; with
 * no text field focused, Backspace goes back and the Up arrow (the phone's key by default) puts the phone away. A key
 * held while the shown screen changes is dead for the new one until released, as in the game.
 *
 * If your app throws, the phone would close it and go home; the tester throws instead, from the call that made it
 * fail, so the test stops there with the cause.
 *
 * Client classes: use it in your tests only. The stand-ins are global, so one tester is open at a time: opening one
 * closes the one before. [close] it when the test ends: the app's screens are closed, as when the player leaves the
 * app, and the game's own fonts, sounds and clipboard come back.
 */
class PhoneTester private constructor(
    private val app: PhoneApp,
    target: String?,
    // Only tells this constructor from the public (app, target) one, which has the same JVM signature.
    @Suppress("unused") marker: Unit,
) : AutoCloseable {
    /** Opens [app] on its first screen, as from the home screen. */
    constructor(app: PhoneApp) : this(app, null, Unit)

    /** Opens [app] at [target], as a notification carrying it does ([PhoneApp]'s `targetScreens`). */
    constructor(app: PhoneApp, target: String) : this(app, target, Unit)

    /** Opens an app whose first screen [rootScreen] creates: `PhoneTester { NotesScreen() }`. */
    constructor(rootScreen: AppScreenFactory) : this(testApp(rootScreen), null, Unit)

    private val registry = AppRegistry()
    private val navigator = PhoneNavigator(registry)
    private val canvas = TestCanvas()
    private val heldKeys = HashSet<Int>()
    private val heard = ArrayList<PhoneSound>()
    private var failure: Throwable? = null
    private var closed = false

    /** Where the pointer is, in viewport dp; outside the viewport until the first move. */
    var mouseX = -1f
        private set
    var mouseY = -1f
        private set

    /** The player's Sounds setting, as this phone pretends it is: on unless a test turns it off. */
    var soundsOn = true

    /** The clipboard text fields copy to and paste from. */
    var clipboard = ""

    /** Whether Shift is held, for a click that extends a text selection. Keys take Shift in their modifiers. */
    var shiftHeld = false

    private val environment = object : TextEnvironment {
        override var clipboard: String
            get() = this@PhoneTester.clipboard
            set(value) {
                this@PhoneTester.clipboard = value
            }
        override val shiftDown get() = shiftHeld
        override val mac get() = false
    }

    init {
        active?.close()
        active = this
        TextMetrics.fonts = { MonospaceFont }
        PhoneSounds.output = { heard += it.forTests() }
        PhoneSounds.setting = { soundsOn }
        TextEnvironment.current = environment
        try {
            registry.register(app, app.id.namespace)
            registry.freeze()
            navigator.addListener(object : NavigationListener {
                override fun onAppFailed(app: PhoneApp, error: Throwable) {
                    if (failure == null) failure = error
                }
            })
            act {
                navigator.open()
                navigator.openApp(app, target)
            }
        } catch (e: Throwable) {
            // A tester that couldn't open leaves the game's stand-ins as it found them.
            close()
            throw e
        }
    }

    // ---- What the phone shows ----

    /** The screen the player sees, or null once the app is left (`home`) or the phone put away. */
    val screen: AppScreen? get() = navigator.shownScreen

    /** The app's screens, its first one first; empty once the app is left. */
    val screens: List<AppScreen> get() = navigator.screens

    /** Whether the app is still open (not left for the home screen with `phone.home()`). */
    val isAppOpen: Boolean get() = navigator.currentApp != null

    /** Whether the phone is out, as opposed to put away (Esc, `phone.close()`). */
    val isPhoneOpen: Boolean get() = navigator.isOpen

    /** The text drawn in the last frame, in viewport dp. */
    val texts: List<DrawnText> get() = canvas.texts

    /** The filled shapes drawn in the last frame, in viewport dp. */
    val shapes: List<DrawnShape> get() = canvas.shapes

    /** Whether the last frame drew [text] as one line, exactly. */
    fun shows(text: String): Boolean = canvas.texts.any { it.text == text }

    /** The sounds played since the tester opened or since [clearSounds], in order. */
    val sounds: List<PhoneSound> get() = heard

    /** Forgets the sounds heard so far. */
    fun clearSounds() = heard.clear()

    // ---- Time ----

    /** Draws a frame with the pointer where it is. */
    fun frame() = frame(mouseX, mouseY)

    /** Draws a frame with the pointer at ([x], [y]), viewport dp: what moving the mouse there shows. */
    fun frame(x: Float, y: Float) = act {
        mouseX = x
        mouseY = y
    }

    /** One game tick (50 ms). */
    fun tick() = tick(1)

    /** [times] game ticks (50 ms each). */
    fun tick(times: Int) = act {
        repeat(times) {
            navigator.tick()
            PhoneSounds.tick()
        }
    }

    /** Lets [milliseconds] pass, in whole ticks of 50 ms. */
    fun advance(milliseconds: Int) = tick((milliseconds / Motion.TICK_MS).toInt())

    // ---- Mouse ----

    /** Moves the pointer to ([x], [y]), viewport dp. */
    fun moveTo(x: Float, y: Float) = frame(x, y)

    /** Presses the left button at ([x], [y]). Returns whether the screen used it. */
    fun press(x: Float, y: Float): Boolean = press(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)

    /** Presses [button] (0 left, 1 right, 2 middle) at ([x], [y]). Returns whether the screen used it. */
    fun press(x: Float, y: Float, button: Int): Boolean {
        frame(x, y)
        return act { navigator.mousePressed(x, y, button) }
    }

    /** Releases the left button where the pointer is. */
    fun release(): Boolean = release(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)

    /** Releases the left button at ([x], [y]). */
    fun release(x: Float, y: Float): Boolean = release(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)

    /** Releases [button] at ([x], [y]). */
    fun release(x: Float, y: Float, button: Int): Boolean = act {
        mouseX = x
        mouseY = y
        navigator.mouseReleased(x, y, button)
    }

    /** Moves the pointer to ([x], [y]) with the left button held. */
    fun drag(x: Float, y: Float): Boolean = act {
        val dx = x - mouseX
        val dy = y - mouseY
        mouseX = x
        mouseY = y
        navigator.mouseDragged(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, dx, dy)
    }

    /** A left click at ([x], [y]): press, then release. */
    fun click(x: Float, y: Float) {
        press(x, y)
        release(x, y)
    }

    /** Turns the wheel [notches] at ([x], [y]): positive towards the top of a list. Returns whether it was used. */
    fun scroll(x: Float, y: Float, notches: Float): Boolean {
        frame(x, y)
        return act { navigator.mouseScrolled(x, y, 0f, notches) }
    }

    // ---- Keyboard ----

    /**
     * Presses and releases [keyCode] (GLFW's codes, `GLFW.GLFW_KEY_ENTER`), with the character it types if any.
     * Returns whether the screen used the key.
     */
    fun key(keyCode: Int): Boolean = key(keyCode, 0)

    /** [key] with [modifiers] held (`GLFW.GLFW_MOD_CONTROL`...). */
    fun key(keyCode: Int, modifiers: Int): Boolean {
        val used = pressKey(keyCode, modifiers)
        releaseKey(keyCode)
        return used
    }

    /** Holds [keyCode] down: the press, then [repeats] repeats (each with its character), then the release. */
    fun holdKey(keyCode: Int, repeats: Int) {
        repeat(repeats + 1) { pressKey(keyCode, 0) }
        releaseKey(keyCode)
    }

    /** Presses [keyCode]; pressed again before [releaseKey], it repeats, as a held key does. */
    fun pressKey(keyCode: Int): Boolean = pressKey(keyCode, 0)

    /** [pressKey] with [modifiers] held. */
    fun pressKey(keyCode: Int, modifiers: Int): Boolean = act {
        val repeat = !heldKeys.add(keyCode)
        // As the phone does: the focus is read before the screen sees the key, which may move it.
        val textFocus = navigator.hasTextFocus
        val used = navigator.keyPressed(keyCode, 0, modifiers, repeat)
        if (!used && !repeat) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> navigator.close()
                PHONE_KEY -> if (!textFocus) navigator.close()
                GLFW.GLFW_KEY_BACKSPACE -> if (!textFocus) navigator.back()
            }
        }
        // The game sends the character a key types after the key itself.
        charOf(keyCode, modifiers)?.let { navigator.charTyped(it, modifiers) }
        used
    }

    /** Releases [keyCode]. */
    fun releaseKey(keyCode: Int) {
        heldKeys.remove(keyCode)
        navigator.keyUp(keyCode)
    }

    /**
     * Types [text] as a player would: through the keys that type its characters (with Shift for capitals; Enter for a
     * line feed, Tab for a tab), and characters no key types alone, as an input method sends them.
     */
    fun type(text: String) {
        for (c in text) {
            val key = keyOf(c)
            if (key != null) {
                key(key.first, if (key.second) GLFW.GLFW_MOD_SHIFT else 0)
            } else {
                act { navigator.charTyped(c, 0) }
            }
        }
    }

    // ---- The phone ----

    /** Puts the phone away, as Esc does: the app keeps its screens. */
    fun closePhone() = act { navigator.close() }

    /** Brings the phone out again, on the screen it was put away on; keys still held are dead for it until released. */
    fun openPhone() = act {
        navigator.keysHeldElsewhere(heldKeys)
        navigator.open()
    }

    /**
     * Ends the test: the app's screens are closed (`onHidden`, `onClosed`) as when the player leaves the app, and the
     * game's own fonts, sounds and clipboard come back. Call it when the test ends.
     */
    override fun close() {
        if (closed) return
        closed = true
        try {
            navigator.reset()
        } finally {
            if (active === this) {
                active = null
                TextMetrics.fonts = { SmoothResources.font(it) }
                PhoneSounds.output = PhoneSounds.gameOutput
                PhoneSounds.setting = PhoneSounds.playerSetting
                TextEnvironment.current = TextEnvironment.Game
            }
        }
    }

    // Runs one action, draws the frame that follows it, and throws what the app threw meanwhile.
    private inline fun <T> act(action: () -> T): T {
        check(!closed) { "This PhoneTester is closed" }
        val result = action()
        navigator.shownScreen?.let { shown ->
            canvas.clear()
            navigator.render(shown, canvas, mouseX, mouseY, 0f)
        }
        failure?.let {
            failure = null
            throw IllegalStateException("${app.id} threw: the phone would have closed it", it)
        }
        canvas.checkBalanced()
        return result
    }

    private companion object {
        /** The phone's key as the game binds it by default. */
        const val PHONE_KEY = GLFW.GLFW_KEY_UP

        /** The tester whose stand-ins are in place. */
        var active: PhoneTester? = null

        // A US layout: the characters keys type without Shift.
        val PLAIN: Map<Int, Char> = buildMap {
            for (k in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z) put(k, 'a' + (k - GLFW.GLFW_KEY_A))
            for (k in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9) put(k, '0' + (k - GLFW.GLFW_KEY_0))
            put(GLFW.GLFW_KEY_SPACE, ' ')
            put(GLFW.GLFW_KEY_APOSTROPHE, '\'')
            put(GLFW.GLFW_KEY_COMMA, ',')
            put(GLFW.GLFW_KEY_MINUS, '-')
            put(GLFW.GLFW_KEY_PERIOD, '.')
            put(GLFW.GLFW_KEY_SLASH, '/')
            put(GLFW.GLFW_KEY_SEMICOLON, ';')
            put(GLFW.GLFW_KEY_EQUAL, '=')
            put(GLFW.GLFW_KEY_LEFT_BRACKET, '[')
            put(GLFW.GLFW_KEY_BACKSLASH, '\\')
            put(GLFW.GLFW_KEY_RIGHT_BRACKET, ']')
            put(GLFW.GLFW_KEY_GRAVE_ACCENT, '`')
        }
        // The same keys with Shift.
        val SHIFTED: Map<Int, Char> = buildMap {
            for ((k, c) in PLAIN) if (c.isLetter()) put(k, c.uppercaseChar())
            ")!@#$%^&*(".forEachIndexed { i, c -> put(GLFW.GLFW_KEY_0 + i, c) }
            put(GLFW.GLFW_KEY_SPACE, ' ')
            put(GLFW.GLFW_KEY_APOSTROPHE, '"')
            put(GLFW.GLFW_KEY_COMMA, '<')
            put(GLFW.GLFW_KEY_MINUS, '_')
            put(GLFW.GLFW_KEY_PERIOD, '>')
            put(GLFW.GLFW_KEY_SLASH, '?')
            put(GLFW.GLFW_KEY_SEMICOLON, ':')
            put(GLFW.GLFW_KEY_EQUAL, '+')
            put(GLFW.GLFW_KEY_LEFT_BRACKET, '{')
            put(GLFW.GLFW_KEY_BACKSLASH, '|')
            put(GLFW.GLFW_KEY_RIGHT_BRACKET, '}')
            put(GLFW.GLFW_KEY_GRAVE_ACCENT, '~')
        }

        // How to type a character: its key, and whether with Shift.
        val KEYS: Map<Char, Pair<Int, Boolean>> = SHIFTED.entries.associate { (k, c) -> c to (k to true) } +
            PLAIN.entries.associate { (k, c) -> c to (k to false) } +
            mapOf('\n' to (GLFW.GLFW_KEY_ENTER to false), '\t' to (GLFW.GLFW_KEY_TAB to false))

        /** The character [keyCode] types with [modifiers], or null: none for keys that don't type, nor with Ctrl or Alt. */
        fun charOf(keyCode: Int, modifiers: Int): Char? {
            if (modifiers and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_SUPER) != 0) return null
            return if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) SHIFTED[keyCode] else PLAIN[keyCode]
        }

        /** The key that types [c], and whether with Shift; null when no key of the layout types it alone. */
        fun keyOf(c: Char): Pair<Int, Boolean>? = KEYS[c]

        fun testApp(rootScreen: AppScreenFactory) = PhoneApp(
            ResourceLocation.fromNamespaceAndPath("phone_tester", "app"),
            Component.literal("App"),
            AppIcon.Glyph(TileColor.GRAPHITE, ResourceLocation.fromNamespaceAndPath("phone_tester", "icon.png")),
            rootScreen = rootScreen,
        )

        fun PhoneSounds.Sound.forTests(): PhoneSound = when (this) {
            PhoneSounds.Sound.OPEN -> PhoneSound.OPEN
            PhoneSounds.Sound.CLOSE -> PhoneSound.CLOSE
            PhoneSounds.Sound.TAP -> PhoneSound.TAP
            PhoneSounds.Sound.LIKE -> PhoneSound.LIKE
            PhoneSounds.Sound.SENT -> PhoneSound.SENT
            PhoneSounds.Sound.ERROR -> PhoneSound.ERROR
            PhoneSounds.Sound.NOTIFICATION, PhoneSounds.Sound.NOTIFICATION_SECOND -> PhoneSound.NOTIFICATION
        }
    }
}
