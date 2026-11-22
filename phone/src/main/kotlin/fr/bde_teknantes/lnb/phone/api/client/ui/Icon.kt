package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.ui.SmoothTextures
import net.minecraft.resources.ResourceLocation

/**
 * A line icon: white on transparent, tinted when drawn, exported once per GUI scale so it is drawn pixel for pixel.
 * Draw one with [AppCanvas.drawIcon][fr.bde_teknantes.lnb.phone.api.client.AppCanvas.drawIcon].
 *
 * Each icon exists at a few sizes ([sizes], in dp) only. Draw it at one of them: any other size is scaled from the
 * nearest larger one and comes out softer.
 *
 * The phone's set is in the companion object. An app can add its own icons drawn the same way: put white PNGs at
 * `assets/<namespace>/textures/gui/smooth/<density>/icon<size>/<name>.png`, where `<density>` is the pixels per dp of a
 * GUI scale (half of it): `1x`, `1_5x`, `2x`, `2_5x` ... up to `4_5x` for GUI scale 2 to 9, so a 16 dp icon is 16,
 * 24, 32, 40 ... pixels square. A missing density falls back to the nearest one there is, scaled. Give
 * each PNG a `.png.mcmeta` containing `{"texture": {"blur": true, "clamp": true}}`. Then declare it:
 * `val STAR = Icon("mymod", "star", 16)`.
 *
 * @property namespace The mod the textures belong to.
 * @property name The file name, without `.png`.
 * @property sizes The sizes it was exported at, in dp.
 * @property mirrored Whether it is drawn flipped left to right ([mirrored] returns such a copy).
 */
class Icon(val namespace: String, val name: String, vararg sizes: Int, val mirrored: Boolean = false) {
    val sizes: List<Int> = sizes.sorted()

    init {
        require(this.sizes.isNotEmpty() && this.sizes.all { it > 0 }) { "An icon needs at least one positive size" }
    }

    /** The same icon flipped left to right: a chevron pointing right from the "back" one. */
    fun mirrored(): Icon = Icon(namespace, name, *sizes.toIntArray(), mirrored = !mirrored)

    /** The size the texture is taken from to draw the icon at [size] dp: the same, or the nearest larger, or the largest. */
    fun sourceSize(size: Float): Int = sizes.firstOrNull { it >= size - 1e-3f } ?: sizes.last()

    /** The texture for [size] dp at GUI scale [guiScale], or the nearest density exported. */
    internal fun texture(size: Int, guiScale: Int): ResourceLocation = SmoothTextures.find(namespace, "icon$size/$name.png", guiScale)

    override fun equals(other: Any?) =
        other is Icon && other.namespace == namespace && other.name == name && other.mirrored == mirrored && other.sizes == sizes

    override fun hashCode() = (namespace.hashCode() * 31 + name.hashCode()) * 31 + mirrored.hashCode()

    override fun toString() = "Icon($namespace:$name${if (mirrored) ", mirrored" else ""})"

    /** The phone's icons (art direction 8.6). The comment of each gives its sizes. */
    companion object {
        private fun phone(name: String, vararg sizes: Int) = Icon(LnbPhone.ID, name, *sizes)

        /** Chevron pointing left, for back buttons. 20 dp. */
        val BACK = phone("back", 20)

        /** Chevron pointing right, for list rows that open something. 20 dp. */
        val CHEVRON = BACK.mirrored()

        /** An X, to close a screen or dismiss. 20 dp. */
        val CLOSE = phone("close", 20)

        /** Pen nib over a line: write something new. 24 dp (the floating action button). */
        val COMPOSE = phone("compose", 24)

        /** Heart outline. 16 dp. */
        val HEART = phone("heart", 16)

        /** Filled heart, for the active state. 16 dp. */
        val HEART_FILLED = phone("heart_filled", 16)

        /** Speech bubble. 16 dp. */
        val REPLY = phone("reply", 16)

        /** Pushpin outline. 16 dp. */
        val PIN = phone("pin", 16)

        /** Filled pushpin, for the active state. 16 dp. */
        val PIN_FILLED = phone("pin_filled", 16)

        /** Padlock, for private notes. 16 dp. */
        val LOCK = phone("lock", 16)

        /** Three horizontal dots: more options. 16 dp. */
        val MORE = phone("more", 16)

        /** Bin, for deleting. 16 dp. */
        val TRASH = phone("trash", 16)

        /** Circle with an exclamation mark: an error. 16 dp. */
        val ALERT = phone("alert", 16)

        /** Arrow pointing up. 16 dp. */
        val ARROW_UP = phone("arrow_up", 16)

        /** Head and shoulders: someone anonymous. 12, 20 and 28 dp (the anonymous avatar at 16, 32 and 48 dp). */
        val PERSON = phone("person", 12, 20, 28)

        /** Globe. 20 and 40 dp (40 for empty and error states). */
        val GLOBE = phone("globe", 20, 40)

        /** Megaphone outline. 20 and 40 dp. */
        val MEGAPHONE = phone("megaphone", 20, 40)

        /** Solid megaphone, inside the announcer badge. 12 dp. */
        val MEGAPHONE_FILLED = phone("megaphone_filled", 12)

        /** Inbox tray. 20 and 40 dp. */
        val INBOX = phone("inbox", 20, 40)

        /** Every icon of the phone's set, for galleries. */
        val ALL: List<Icon> get() = listOf(
            BACK, CHEVRON, CLOSE, COMPOSE, HEART, HEART_FILLED, REPLY, PIN, PIN_FILLED, LOCK, MORE, TRASH, ALERT,
            ARROW_UP, PERSON, GLOBE, MEGAPHONE, MEGAPHONE_FILLED, INBOX,
        )

        /** The spinner's texture (a quarter arc on a faint ring) at GUI scale [guiScale]. */
        internal fun spinnerTexture(guiScale: Int): ResourceLocation = SmoothTextures.find(LnbPhone.ID, "spinner.png", guiScale)
    }
}
