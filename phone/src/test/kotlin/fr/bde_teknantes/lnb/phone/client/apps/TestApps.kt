package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
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

internal object TestCanvas : AppCanvas {
    override val width = AppCanvas.WIDTH
    override val height = AppCanvas.HEIGHT
}

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

    override fun toString() = name
}
