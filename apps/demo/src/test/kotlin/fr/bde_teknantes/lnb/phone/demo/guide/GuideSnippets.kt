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
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
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

// ---- Player text as a translation key ----

// Wrong: the player's text is used as the key. A player who writes "death.attack.lava" makes the notification read as
// that string of the game, in every recipient's language; any key of any mod works the same.
fun carelessNotificationText(sender: ServerPlayer, text: String): Component = Component.translatable(text)

// Right: the key is yours, and the player's words go in as arguments, shown as they are.
fun notificationText(sender: ServerPlayer, text: String): Component =
    Component.translatable("notif.lnbphone_demo.ping.text", sender.gameProfile.name, text)

// ---- A target taken for a permission ----

/** A request to delete a post, for the example below. */
data class DeletePostPayload(val post: Long) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<DeletePostPayload>(ResourceLocation.fromNamespaceAndPath("lnbphone_demo", "delete_post"))
    }
}

// Wrong: the screen acts because a notification led there. Any server-side code can notify your app's id with any
// target, so landing on this screen proves nothing about what the player may do.
class CarelessDeleteScreen(private val post: Long) : KitScreen() {
    override fun onScreenShown() {
        PacketDistributor.sendToServer(DeletePostPayload(post))
    }
}

// ---- What a client sends ----

// Wrong: STRING_UTF8 reads up to 32767 characters, whatever the app allows, and the handler takes it as it comes.
val CARELESS_TEXT_CODEC: StreamCodec<ByteBuf, String> = ByteBufCodecs.STRING_UTF8

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
