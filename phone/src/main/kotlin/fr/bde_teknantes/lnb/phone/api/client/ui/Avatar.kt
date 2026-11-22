package fr.bde_teknantes.lnb.phone.api.client.ui

import com.mojang.authlib.GameProfile
import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/**
 * A player's face in a circle (art direction 8.7), the one pixelated element in apps: the 8 x 8 skin face with its hat,
 * in sharp pixels. 16, 32 or 48 dp only, the sizes that scale the face by whole pixels at every GUI scale. Without a
 * skin, the anonymous avatar: a grey disc with a person icon.
 *
 * @param skin The skin texture: [skinOf] gives a player's from their profile. Null for the anonymous avatar.
 */
class Avatar(var skin: ResourceLocation?, val size: Size = Size.MEDIUM) : Widget() {
    /** The allowed sizes, and the person icon each uses when anonymous. */
    enum class Size(val dp: Float, internal val iconDp: Float) {
        SMALL(16f, 12f),
        MEDIUM(32f, 20f),
        LARGE(48f, 28f),
    }

    override val preferredWidth get() = size.dp
    override val preferredHeight get() = size.dp

    override fun render(canvas: AppCanvas) = paint(canvas, 0f, 0f, skin, size)

    companion object {
        /** Draws an avatar at ([x], [y]) without a widget, for widgets that draw many (a list of posts). */
        fun paint(canvas: AppCanvas, x: Float, y: Float, skin: ResourceLocation?, size: Size = Size.MEDIUM) {
            if (skin != null) {
                canvas.drawPlayerFace(skin, x, y, size.dp)
            } else {
                canvas.fillCircle(x, y, size.dp, Colors.ANON_BG)
                val inset = (size.dp - size.iconDp) / 2f
                canvas.drawIcon(Icon.PERSON, x + inset, y + inset, Colors.TEXT_2, size.iconDp)
            }
        }

        /**
         * The skin of [profile]: the player's own once it has downloaded (a few moments the first time), Minecraft's
         * default skin until then. Client thread.
         */
        fun skinOf(profile: GameProfile): ResourceLocation = Minecraft.getInstance().skinManager.getInsecureSkin(profile).texture()
    }
}

/**
 * A red count badge (art direction 8.7): 16 dp tall, at least 16 wide, the count in small white type; `99+` above 99.
 * Hidden at 0.
 */
class CountBadge(count: Int = 0) : Widget() {
    var count: Int = count
        set(value) {
            field = value
            setBounds(x, y, preferredWidth, preferredHeight)
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
 * post announcements. [tooltip] explains it ("Can post announcements"). No text, so it reads the same in every
 * language.
 */
class AnnouncerBadge(tooltip: String? = null) : Widget() {
    init {
        this.tooltip = tooltip
    }

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
