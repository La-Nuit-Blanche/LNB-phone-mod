package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import java.util.UUID

/**
 * How an answer of the site becomes a [BackendResult] (`docs/feed-app.md` 3.2), and what is told to Warden's
 * `ApiHealth`. Pure, so it is tested without a site.
 */
internal object SiteAnswers {
    /** A classified answer, and the [ApiResult] that `ApiHealth` records for it. */
    data class Classified<T>(val result: BackendResult<T>, val health: ApiResult<*>)

    /**
     * - 2xx: `Ok` with [decode] of the body (an unparsable body is `Unavailable`, an answer with a problem for ApiHealth);
     * - 503 `disabled`: `Disabled` with `data.flag`;
     * - 401 and other 5xx: `Unavailable`;
     * - anything else: `Refused`, with `data.problem`, `data.oldest.id` (`pinLimit`) and `Retry-After` (`rateLimited`).
     *
     * A switched-off feature is not an outage: it is recorded as an answer, so that operators aren't told the site
     * is down (and up again at the next call of another mod) while Feed is merely off.
     */
    fun <T> classify(status: Int, body: String?, retryAfter: String?, decode: (String) -> T): Classified<T> {
        if (status in 200..299) {
            return try {
                Classified(BackendResult.Ok(decode(body.orEmpty())), ApiResult.Ok(Unit))
            } catch (e: RuntimeException) {
                val reason = (e as? SiteFormatException)?.message ?: e.javaClass.simpleName
                // The site answered: told to ApiHealth as an answer with a problem, not as an outage, which would flip
                // the "API unreachable" state Warden shares with LNB's other mods.
                Classified(BackendResult.Unavailable("unexpected answer ($reason)"), ApiResult.Http(status, MALFORMED))
            }
        }
        val data = errorData(body)
        val problem = data?.text("problem")
        return when {
            status == 503 && problem == "disabled" -> Classified(BackendResult.Disabled(data.text("flag")), ApiResult.Ok(Unit))
            status == 401 || status >= 500 -> Classified(BackendResult.Unavailable("HTTP $status"), ApiResult.Http(status, problem))
            else -> {
                val oldest = (data?.get("oldest") as? JsonObject)?.text("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val seconds = retryAfter?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
                Classified(BackendResult.Refused(status, problem, oldest, seconds), ApiResult.Http(status, problem))
            }
        }
    }

    /** The problem ApiHealth is told for a body Feed couldn't read. */
    const val MALFORMED = "feedMalformedBody"

    /** No answer at all: a timeout, a refused connection, a malformed URL. */
    fun <T> network(cause: Throwable): Classified<T> =
        Classified(BackendResult.Unavailable(cause.javaClass.simpleName), ApiResult.Network(cause))

    private fun errorData(body: String?): JsonObject? = try {
        (SiteJson.parse(body ?: return null) as? JsonObject)?.get("data") as? JsonObject
    } catch (e: RuntimeException) {
        null
    }

    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
