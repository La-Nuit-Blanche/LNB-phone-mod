package fr.bde_teknantes.lnb.phone.client.smooth

/** Loading from a list of sources, best first, where any one may be broken (a resource pack replacing a file). */
internal object Fallback {
    /**
     * The first of [candidates] that [load] opens without throwing, or null when none does. [failed] hears about
     * each one that threw, in order.
     */
    fun <T, R : Any> firstLoading(candidates: List<T>, load: (T) -> R, failed: (T, Exception) -> Unit): R? {
        for (candidate in candidates) {
            try {
                return load(candidate)
            } catch (e: Exception) {
                failed(candidate, e)
            }
        }
        return null
    }
}
