package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * Deciding when a skin read back from the GPU is the real one, apart from GL so it can be tested.
 *
 * A downloaded skin's texture is registered first, then shows Steve's skin (its placeholder), and only later the real
 * pixels. Minecraft makes a loaded skin's face layer opaque, so a face with any transparent pixel is not loaded yet;
 * a face exactly equal to Steve's, for a skin that is not Steve's, is most likely the placeholder. After
 * [PLACEHOLDER_PATIENCE_NANOS] it is accepted anyway: some players do wear Steve's face.
 */
internal object SkinReadiness {
    const val PLACEHOLDER_PATIENCE_NANOS = 10_000_000_000L

    /**
     * Whether [face] (64 ARGB pixels, or null when the texture is not skin-sized) can be cached. [placeholderFace] is
     * Steve's, or null when the skin is Steve's own or Steve's could not be read; [waitedNanos] is the time since the
     * skin was first asked for.
     */
    fun isReady(face: IntArray?, placeholderFace: IntArray?, waitedNanos: Long): Boolean {
        if (face == null || face.size != 64) return false
        if (face.any { it ushr 24 != 0xFF }) return false
        val looksLikePlaceholder = placeholderFace != null && face.contentEquals(placeholderFace)
        return !looksLikePlaceholder || waitedNanos >= PLACEHOLDER_PATIENCE_NANOS
    }

    /** The hat layer over the face layer, pixel by pixel (ARGB). The face's alpha is kept, so an unloaded face shows. */
    fun composite(base: IntArray, hat: IntArray): IntArray = IntArray(base.size) { i ->
        val top = hat[i]
        val bottom = base[i]
        val a = top ushr 24
        fun channel(shift: Int) = (((top shr shift) and 0xFF) * a + ((bottom shr shift) and 0xFF) * (255 - a) + 127) / 255
        (bottom and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
