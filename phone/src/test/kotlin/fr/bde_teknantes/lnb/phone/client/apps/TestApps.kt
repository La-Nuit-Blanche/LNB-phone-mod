package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.FakeCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.AppTargetScreens
import fr.bde_teknantes.lnb.phone.api.client.Phone
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

internal fun appId(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("test", path)

internal fun testApp(
    path: String,
    targets: ((String) -> List<AppScreen>)? = null,
    root: () -> AppScreen = { RecordingScreen(path, mutableListOf()) },
) = PhoneApp(
    appId(path),
    Component.literal(path),
    AppIcon.Glyph(TileColor.AZURE, appId("textures/gui/$path.png")),
    targets?.let { create -> AppTargetScreens { create(it) } },
) { root() }

internal object TestCanvas : FakeCanvas()

/** Writes each lifecycle call to [log] as `name:event`; [onEvent] lets a test act from inside a callback. */
internal open class RecordingScreen(
    private val name: String,
    private val log: MutableList<String>,
    private val onEvent: RecordingScreen.(String) -> Unit = {},
) : AppScreen() {
    val handle: Phone get() = phone

    private fun record(event: String) {
        log += "$name:$event"
        onEvent(event)
    }

    override fun onShown() = record("shown")
    override fun onHidden() = record("hidden")
    override fun onClosed() = record("closed")
    override fun tick() = record("tick")
    override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) = record("render")
    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        record("pressed")
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        record("released")
        return true
    }

    override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        record("dragged")
        return true
    }

    override fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float): Boolean {
        record("scrolled")
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        record("key")
        return true
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        record("char")
        return true
    }

    override fun toString() = name
}

/** Writes what the navigator tells its listeners to [log], each entry starting with `@` to tell it from screens. */
internal class RecordingListener(private val log: MutableList<String>) : NavigationListener {
    override fun onTransition(transition: Transition, outgoing: AppScreen?) {
        log += "@$transition from $outgoing"
    }

    override fun onAppOpened(app: PhoneApp) {
        log += "@opened ${app.id.path}"
    }

    override fun onPhoneOpened() {
        log += "@phone opened"
    }

    override fun onPhoneClosed() {
        log += "@phone closed"
    }

    override fun onAppFailed(app: PhoneApp, error: Throwable) {
        log += "@failed ${app.id.path}"
    }
}

/**
 * Checks the lifecycle promised to app authors (see [AppScreen]) for every screen in a [RecordingScreen] log: onShown
 * and onHidden alternate starting with onShown, a shown screen is hidden before it closes, and nothing comes after
 * onClosed. The log must go back to before the screens were given to the phone, and screen names must be unique.
 */
internal fun assertConsistentLifecycles(log: List<String>) {
    val lifecycle = setOf("shown", "hidden", "closed")
    val names = log.filter { !it.startsWith("@") }.map { it.substringBefore(':') }.distinct()
    for (name in names) {
        val events = log.filter { it.startsWith("$name:") }.map { it.substringAfter(':') }.filter { it in lifecycle }
        var shown = false
        var closed = false
        for (event in events) {
            check(!closed) { "$name got $event after onClosed: $events" }
            when (event) {
                "shown" -> check(!shown) { "$name shown twice in a row: $events" }.also { shown = true }
                "hidden" -> check(shown) { "$name hidden while not shown: $events" }.also { shown = false }
                "closed" -> check(!shown) { "$name closed while still shown: $events" }.also { closed = true }
            }
        }
    }
}
