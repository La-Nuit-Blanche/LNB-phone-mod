package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerState
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationPost
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A body from the site that isn't what `docs/feed-mod.md` says. Its message names the field, never a value. */
class SiteFormatException(message: String) : RuntimeException(message)

/**
 * The site's JSON (`docs/feed-mod.md` 3 and 4), read by hand rather than with Gson's reflection, so that a missing
 * field, a wrong type or a value past the payloads' bounds fails here (the call is then `Unavailable`) instead of
 * reaching a client, where a value past a bound would fail to encode and drop the player's connection.
 *
 * Pure: no game classes, so it is tested on plain strings.
 */
object SiteJson {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** A date as the site writes them: ISO 8601 in UTC, with milliseconds. */
    fun date(instant: Instant): String = DATE.format(instant.truncatedTo(ChronoUnit.MILLIS))

    fun parse(body: String): JsonElement = try {
        JsonParser.parseString(body)
    } catch (e: RuntimeException) {
        throw SiteFormatException("not JSON")
    }

    // ---- Shapes ----

    fun playerState(json: JsonElement): PlayerState {
        val o = json.obj("state")
        val rights = o.field("rights")
        val unread = o.field("unread")
        return PlayerState(
            linked = o.bool("linked"),
            banned = o.bool("banned"),
            writing = o.bool("writing"),
            me = o.objOrNull("me")?.let(::author),
            rights = Rights(rights.bool("write"), rights.bool("announce"), rights.bool("moderate")),
            unread = Unread(unread.bool("announcements"), unread.bool("global")),
        )
    }

    fun author(o: JsonObject) = Author(
        uuid = o.uuid("uuid"),
        username = o.string("username", Feed.MAX_USERNAME),
        realName = o.stringOrNull("realName", Feed.MAX_REAL_NAME_UNITS),
        announcer = o.bool("announcer"),
    )

    fun post(json: JsonElement): PostView {
        val o = json.obj("post")
        return PostView(
            id = o.uuid("id"),
            feed = o.feed("feed"),
            threadId = o.uuidOrNull("threadId"),
            replyTo = o.objOrNull("replyTo")?.let { ReplyRef(it.uuidOrNull("id"), it.bool("anonymous"), it.stringOrNull("name", Feed.MAX_REAL_NAME_UNITS)) },
            text = o.string("text", Feed.MAX_TEXT_UNITS),
            createdAt = o.instant("createdAt"),
            anonymous = o.bool("anonymous"),
            author = o.objOrNull("author")?.let(::author),
            likes = o.count("likes"),
            replies = o.count("replies"),
            pinnedAt = o.instantOrNull("pinnedAt"),
            liked = o.bool("liked"),
            mine = o.bool("mine"),
            canDelete = o.bool("canDelete"),
            canPin = o.bool("canPin"),
            unseenReplies = o.count("unseenReplies"),
            answersMe = o.bool("answersMe"),
        )
    }

    fun feedPage(json: JsonElement): FeedPage {
        val o = json.obj("page")
        return FeedPage(
            feed = o.feed("feed"),
            pinned = o.array("pinned", Feed.MAX_PINS).map(::post),
            posts = o.array("posts", Feed.MAX_PAGE).map(::post),
            next = o.stringOrNull("next", Feed.MAX_CURSOR),
        )
    }

    fun threadPage(json: JsonElement): ThreadPage {
        val o = json.obj("thread")
        return ThreadPage(post(o.get("post")), o.array("replies", Feed.MAX_PAGE).map(::post), o.stringOrNull("next", Feed.MAX_CURSOR))
    }

    /**
     * `POST /views`: each viewer's posts, **only for the viewers asked for**. A viewer the site names twice, or one
     * not asked for, makes the whole answer unusable: views must never go to someone they weren't made for.
     */
    fun views(json: JsonElement, asked: List<UUID>): Map<UUID, List<PostView>> {
        val wanted = asked.toSet()
        val result = LinkedHashMap<UUID, List<PostView>>()
        for (entry in json.obj("views").array("views", Feed.MAX_VIEWERS)) {
            val o = entry.obj("view")
            val viewer = o.uuid("viewer")
            if (viewer !in wanted || viewer in result) throw SiteFormatException("views: a viewer that wasn't asked for")
            result[viewer] = o.array("posts", Feed.MAX_VIEW_IDS).map(::post)
        }
        return result
    }

    fun created(json: JsonElement): Created {
        val o = json.obj("created")
        return Created(o.bool("replayed"), post(o.get("post")))
    }

    fun likeState(json: JsonElement): LikeState {
        val o = json.obj("like")
        return LikeState(o.uuid("id"), o.count("likes"), o.bool("liked"))
    }

    fun pinState(json: JsonElement): PinState {
        val o = json.obj("pins")
        return PinState(o.array("pinned", Feed.MAX_PINS).map(::pin), o.uuidOrNull("unpinned"))
    }

    fun pin(json: JsonElement): Pin {
        val o = json.obj("pin")
        return Pin(o.uuid("id"), o.instant("pinnedAt"))
    }

    fun notifications(json: JsonElement): NotificationBatch {
        val o = json.obj("notifications")
        val items = o.array("items", Feed.MAX_NOTIFICATIONS).map { item ->
            val n = item.obj("notification")
            val p = n.field("post")
            QueuedNotification(
                id = n.uuid("id"),
                kind = QueuedNotification.Kind.fromWire(n.string("kind", 32)) ?: throw SiteFormatException("notification: unknown kind"),
                createdAt = n.instant("createdAt"),
                players = if (n.isNull("players")) null else n.array("players", Int.MAX_VALUE).map { it.uuidValue("players") },
                except = n.array("except", Int.MAX_VALUE).map { it.uuidValue("except") },
                post = NotificationPost(
                    id = p.uuid("id"),
                    feed = p.feed("feed"),
                    threadId = p.uuidOrNull("threadId"),
                    // Unbounded on the wire: the phone cuts a notification's text itself.
                    text = p.string("text", Int.MAX_VALUE),
                    anonymous = p.bool("anonymous"),
                    author = p.objOrNull("author")?.let(::author),
                ),
            )
        }
        return NotificationBatch(items, o.bool("more"))
    }

    fun announcerState(json: JsonElement): AnnouncerState {
        val o = json.obj("announcer")
        return AnnouncerState(o.uuid("uuid"), o.string("username", Feed.MAX_USERNAME), o.bool("announcer"), o.bool("viaRole"))
    }

    fun announcers(json: JsonElement): List<AnnouncerEntry> = json.obj("announcers").array("items", Int.MAX_VALUE).map { item ->
        val o = item.obj("announcer")
        AnnouncerEntry(
            o.stringOrNull("realName", Feed.MAX_REAL_NAME_UNITS),
            o.array("accounts", Int.MAX_VALUE).map { account ->
                val a = account.obj("account")
                AnnouncerEntry.Account(a.uuid("uuid"), a.string("username", Feed.MAX_USERNAME))
            },
        )
    }

    // ---- Stream events (feed-mod.md 8.1) ----

    fun removedItems(json: JsonElement): List<RemovedItem> = json.obj("deleted").array("items", Int.MAX_VALUE).map { item ->
        val o = item.obj("item")
        RemovedItem(o.uuid("id"), o.feed("feed"), o.uuidOrNull("threadId"))
    }

    fun countUpdates(json: JsonElement): List<CountUpdate> = json.obj("counts").array("items", Int.MAX_VALUE).map { item ->
        val o = item.obj("item")
        CountUpdate(o.uuid("id"), o.count("likes"), o.count("replies"))
    }

    fun pins(json: JsonElement): List<Pin> = json.obj("pins").array("pinned", Feed.MAX_PINS).map(::pin)

    fun uuids(json: JsonElement): List<UUID> = json.obj("rights").array("uuids", Int.MAX_VALUE).map { it.uuidValue("uuids") }

    // ---- Readers: each names the field it fails on, never the value ----

    /** This element as an object; [what] names it in the error. */
    private fun JsonElement?.obj(what: String): JsonObject =
        this as? JsonObject ?: throw SiteFormatException("$what: not an object")

    /** The object in field [name]. */
    private fun JsonObject.field(name: String): JsonObject = get(name).obj(name)

    private fun JsonObject.isNull(name: String) = get(name)?.isJsonNull ?: true

    private fun JsonObject.objOrNull(name: String): JsonObject? = if (isNull(name)) null else get(name).obj(name)

    private fun JsonObject.array(name: String, max: Int): JsonArray {
        val array = get(name) as? JsonArray ?: throw SiteFormatException("$name: not an array")
        if (array.size() > max) throw SiteFormatException("$name: more than $max")
        return array
    }

    private fun JsonObject.primitive(name: String) =
        get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: throw SiteFormatException("$name: missing")

    private fun JsonObject.bool(name: String): Boolean {
        val value = primitive(name)
        if (!value.isBoolean) throw SiteFormatException("$name: not a boolean")
        return value.asBoolean
    }

    private fun JsonObject.count(name: String): Int {
        val value = primitive(name)
        if (!value.isNumber) throw SiteFormatException("$name: not a number")
        val count = try {
            value.asBigDecimal.intValueExact()
        } catch (e: ArithmeticException) {
            throw SiteFormatException("$name: not a count")
        }
        if (count < 0) throw SiteFormatException("$name: negative")
        return count
    }

    private fun JsonObject.string(name: String, maxUnits: Int): String {
        val value = primitive(name)
        if (!value.isString) throw SiteFormatException("$name: not a string")
        val text = value.asString
        if (text.length > maxUnits) throw SiteFormatException("$name: longer than $maxUnits")
        return text
    }

    private fun JsonObject.stringOrNull(name: String, maxUnits: Int): String? = if (isNull(name)) null else string(name, maxUnits)

    private fun JsonObject.uuid(name: String): UUID = get(name).uuidValue(name)

    private fun JsonObject.uuidOrNull(name: String): UUID? = if (isNull(name)) null else uuid(name)

    private fun JsonElement?.uuidValue(name: String): UUID {
        val text = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: throw SiteFormatException("$name: not a UUID")
        return try {
            UUID.fromString(text)
        } catch (e: IllegalArgumentException) {
            throw SiteFormatException("$name: not a UUID")
        }
    }

    /** A date the payloads can carry: they hold epoch milliseconds in a long, which a far date overflows. */
    private fun JsonObject.instant(name: String): Instant {
        val instant = try {
            Instant.parse(string(name, 64)).truncatedTo(ChronoUnit.MILLIS)
        } catch (e: java.time.format.DateTimeParseException) {
            throw SiteFormatException("$name: not a date")
        }
        try {
            instant.toEpochMilli()
        } catch (e: ArithmeticException) {
            throw SiteFormatException("$name: out of range")
        }
        return instant
    }

    private fun JsonObject.instantOrNull(name: String): Instant? = if (isNull(name)) null else instant(name)

    private fun JsonObject.feed(name: String): FeedKind = FeedKind.fromWire(string(name, 32)) ?: throw SiteFormatException("$name: not a feed")
}
