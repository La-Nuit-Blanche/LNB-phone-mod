package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * A player's face in a circle (art direction 8.7), the one pixelated element in apps: the 8 x 8 skin face with its hat,
 * in sharp pixels. 16, 32 or 48 dp only ([size]), the sizes that scale the face by whole pixels at every GUI scale.
 * Anonymous until told whose face to show: a grey disc with a person icon.
 *
 * ```kotlin
 * Avatar().apply { showPlayer(post.authorId, post.authorName) }
 * ```
 */
class Avatar : Widget() {
    /** The allowed sizes, and the person icon each uses when anonymous. Complete for good: no `else` needed. */
    enum class Size(val dp: Float, internal val iconDp: Float) {
        SMALL(16f, 12f),
        MEDIUM(32f, 20f),
        LARGE(48f, 28f),
    }

    var size: Size = Size.MEDIUM

    private var playerId: UUID? = null
    private var playerName = ""
    private var texture: ResourceLocation? = null

    /** Shows the face of the player [id] named [name], found as [PlayerSkins] does, the default face meanwhile. */
    fun showPlayer(id: UUID, name: String) {
        playerId = id
        playerName = name
        texture = null
    }

    /** Shows the face of a skin texture you already have (a skin of your own mod). */
    fun showSkin(texture: ResourceLocation) {
        playerId = null
        this.texture = texture
    }

    /** Shows the anonymous avatar. */
    fun showAnonymous() {
        playerId = null
        texture = null
    }

    override val preferredWidth get() = size.dp
    override val preferredHeight get() = size.dp

    override fun render(canvas: AppCanvas) {
        val id = playerId
        if (id != null) paintPlayer(canvas, 0f, 0f, size, id, playerName) else paintSkin(canvas, 0f, 0f, size, texture)
    }

    companion object {
        /** Draws the face of the player [id] named [name] at ([x], [y]), for widgets that draw many (a list of posts). */
        fun paintPlayer(canvas: AppCanvas, x: Float, y: Float, size: Size, id: UUID, name: String) =
            canvas.drawPlayerFace(PlayerSkins.skin(id, name), x, y, size.dp)

        /** Draws the face of [skin] at ([x], [y]), or the anonymous avatar when it is null. */
        fun paintSkin(canvas: AppCanvas, x: Float, y: Float, size: Size, skin: ResourceLocation?) {
            if (skin != null) {
                canvas.drawPlayerFace(skin, x, y, size.dp)
            } else {
                canvas.fillCircle(x, y, size.dp, Colors.ANON_BG)
                val inset = (size.dp - size.iconDp) / 2f
                canvas.drawIcon(Icon.PERSON, x + inset, y + inset, Colors.TEXT_2, size.iconDp)
            }
        }
    }
}

/**
 * A red count badge (art direction 8.7): 16 dp tall, at least 16 wide, the count in small white type; `99+` above 99.
 * Hidden at 0.
 */
class CountBadge : Widget() {
    var count: Int = 0
        set(value) {
            field = value
            if (width > 0f) setBounds(x, y, preferredWidth, preferredHeight)
        }

    private val text get() = if (count > 99) "99+" else count.toString()

    override val preferredWidth get() = maxOf(16f, TextMetrics.measure(text, TextStyle.MICRO) + 8f)
    override val preferredHeight get() = 16f

    override fun render(canvas: AppCanvas) {
        if (count <= 0) return
        canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, Colors.BADGE)
        canvas.drawTextCentered(text, width / 2f, 1f, TextStyle.MICRO, Colors.ON_ACCENT)
    }
}

/** A plain red dot (8 dp): something new, without a count. */
class DotBadge : Widget() {
    override val preferredWidth get() = 8f
    override val preferredHeight get() = 8f

    override fun render(canvas: AppCanvas) = canvas.fillCircle(0f, 0f, 8f, Colors.BADGE)
}

/**
 * The announcer badge (art direction 8.7): a 16 dp violet disc with a megaphone, after the name of someone who may
 * post announcements. Give it a [tooltip] explaining it ("Can post announcements"). No text, so it reads the same in
 * every language.
 */
class AnnouncerBadge : Widget() {
    override val preferredWidth get() = 16f
    override val preferredHeight get() = 16f

    override fun render(canvas: AppCanvas) = paint(canvas, 0f, 0f)

    companion object {
        /** Draws the badge at ([x], [y]) without a widget. */
        fun paint(canvas: AppCanvas, x: Float, y: Float) {
            canvas.fillCircle(x, y, 16f, Colors.ANNOUNCER)
            canvas.drawIcon(Icon.MEGAPHONE_FILLED, x + 2f, y + 2f, Colors.ON_ANNOUNCER, 12f)
        }
    }
}
