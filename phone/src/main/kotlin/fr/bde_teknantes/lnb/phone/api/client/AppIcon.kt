package fr.bde_teknantes.lnb.phone.api.client

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * An app's icon: a coloured tile from the phone's palette with a 16 x 16 picture on it. The phone draws the tile, its
 * frame and its shading itself, so that icons from different mods look like one family; you only choose the colour
 * and the picture.
 *
 * The icon is pixel art, drawn at 20 x 20 GUI pixels on the home screen and on notification banners, never resized.
 */
sealed class AppIcon(
    /** The tile behind the picture. */
    val tile: TileColor,
) {
    /**
     * A 16 x 16 picture of your own, drawn at (2, 2) on the tile with a 1 px shade under it added by the phone.
     *
     * How to draw it:
     * - a 16 x 16 PNG with hard pixels: each pixel fully opaque or fully transparent;
     * - the main shape inside the 12 x 12 area from (2, 2) to (13, 13); small parts (a tail, a beak) may reach the edge;
     * - at most 4 colours, from: white `#FFFFFF` (the main shape), light `#D6DCE6`, ink `#1B1E23`, the tile's
     *   [TileColor.lo], and one accent among `#F28A2E`, `#F2B33A`, `#5BD34A`, `#E5483C` and `#3A8DF0`;
     * - no shadow: the phone adds it.
     *
     * @property texture The PNG, for instance `ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/notes.png")`
     * for `assets/mymod/textures/gui/notes.png`.
     */
    class Glyph(tile: TileColor, val texture: ResourceLocation) : AppIcon(tile) {
        override fun toString() = "AppIcon.Glyph($tile, $texture)"
    }

    /**
     * A Minecraft item drawn on the tile in place of a picture (16 x 16 at (2, 2), with no added shade). Handy for a
     * first version; a [Glyph] fits the other icons better.
     */
    class Item(tile: TileColor, stack: ItemStack) : AppIcon(tile) {
        /** A copy of the stack given, so that later changes to the original don't reach the icon. */
        val stack: ItemStack = stack.copy()

        override fun toString() = "AppIcon.Item($tile, $stack)"
    }
}

/**
 * The 8 tile colours an app icon can use. Each tile has a highlight row ([hi]), a body ([base]) and a shade row and
 * auto-shade ([lo]). Colours are opaque ARGB.
 */
enum class TileColor(val hi: Int, val base: Int, val lo: Int) {
    AZURE(0xFF7CC0FF.toInt(), 0xFF3A8DF0.toInt(), 0xFF1F5FB0.toInt()),
    TEAL(0xFF7FE3E0.toInt(), 0xFF1FA3A0.toInt(), 0xFF12706E.toInt()),
    GREEN(0xFF9BEA7A.toInt(), 0xFF4DB33C.toInt(), 0xFF2D7A22.toInt()),
    ORANGE(0xFFFFC48A.toInt(), 0xFFE57A24.toInt(), 0xFFA65116.toInt()),
    RED(0xFFFF8F86.toInt(), 0xFFDB4237.toInt(), 0xFF9A2720.toInt()),
    PINK(0xFFFFB0DA.toInt(), 0xFFE0609F.toInt(), 0xFFA03A70.toInt()),
    VIOLET(0xFFC7A8FF.toInt(), 0xFF8457E6.toInt(), 0xFF56339E.toInt()),
    GRAPHITE(0xFF5C6372.toInt(), 0xFF3A404B.toInt(), 0xFF23272E.toInt()),
}
