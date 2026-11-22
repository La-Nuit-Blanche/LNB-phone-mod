package fr.bde_teknantes.lnb.phone.demo.guide

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.Phone
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.demo.ping.SendPingPayload
import fr.bde_teknantes.lnb.phone.demo.ping.client.PingClient
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.concurrent.CompletableFuture

// Code that docs/writing-an-app.md shows but Ping doesn't contain: the mistakes it warns about, and their fixes.
// Compiled with the tests so that the guide's samples stay true to the API (GuideSamplesTest); never run, and much of
// it is wrong on purpose.

// ---- State kept in a screen ----

// Wrong: the phone builds a new screen at every visit, so what was typed is gone the next time.
class ForgetfulNoteScreen : KitScreen() {
    private val field = add(TextField().at(12f, 56f))
}

// Right: the text lives in an object of the app, which outlives the screens; each new screen hands it to its field.
object NoteState {
    val draft = TextState("", false)
}

class NoteScreen : KitScreen() {
    private val field = add(TextField(NoteState.draft).at(12f, 56f))
}

// ---- Client classes in common code ----

// Wrong: the mod class runs on the dedicated server too, and PingClient is a client class.
class CarelessMod(modBus: IEventBus) {
    init {
        PingClient.init(modBus)
    }
}

// Wrong: payload handlers on the server side run on the dedicated server, where Minecraft (the client) doesn't exist.
fun carelessHandle(payload: SendPingPayload, context: IPayloadContext) {
    val name = Minecraft.getInstance().player?.name
}

// Right: on the server, the player who sent the payload comes from the context.
fun handle(payload: SendPingPayload, context: IPayloadContext) {
    val sender = context.player() as? ServerPlayer ?: return
}

// ---- Threads ----

class ProfileScreen(private val name: String) : KitScreen() {
    init {
        add(Label(name).apply { style = TextStyle.TITLE_M }.at(12f, 56f, 232f, 18f))
    }
}

class SearchScreen : KitScreen() {
    private fun fetchName(): String = "Cam_Roux"

    // Wrong: thenAccept runs on the pool's thread, and the phone throws when called from there.
    fun openCareless() {
        CompletableFuture.supplyAsync { fetchName() }.thenAccept { name ->
            phone.push(ProfileScreen(name))
        }
    }

    // Right: hop over to the client thread first. If the player has left the screen meanwhile, push does nothing.
    fun open() {
        CompletableFuture.supplyAsync { fetchName() }.thenAccept { name ->
            Minecraft.getInstance().execute { phone.push(ProfileScreen(name)) }
        }
    }
}

// ---- Interfaces the phone implements ----

// Wrong: only the phone implements Phone (and AppCanvas). A later phone adds a method to it and this class breaks.
class FakePhone : Phone {
    override val canGoBack = false
    override fun push(screen: AppScreen) {}
    override fun pop() {}
    override fun replace(screen: AppScreen) {}
    override fun home() {}
    override fun close() {}
}

// Right: TextStyle may get new values in a later phone, so a `when` over it keeps an `else`.
fun isHeading(style: TextStyle) = when (style) {
    TextStyle.TITLE_L, TextStyle.TITLE_M -> true
    else -> false
}
