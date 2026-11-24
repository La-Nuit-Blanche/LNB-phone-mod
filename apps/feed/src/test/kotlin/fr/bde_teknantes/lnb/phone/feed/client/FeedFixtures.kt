package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.time.Instant
import java.util.UUID

/** Posts, players and a session that records what it sends, for the client's tests. */
object FeedFixtures {
    val NOW: Instant = Instant.parse("2026-11-12T13:34:00Z")

    val ALEX = Author(UUID.nameUUIDFromBytes("Alex_Craft".toByteArray()), "Alex_Craft", "Alexandre M.", announcer = false)
    val STEVE = Author(UUID.nameUUIDFromBytes("SteveBuilds".toByteArray()), "SteveBuilds", null, announcer = false)
    val LUNE = Author(UUID.nameUUIDFromBytes("Admin_Lune".toByteArray()), "Admin_Lune", "Lune G.", announcer = true)
    val CAMILLE = Author(UUID.nameUUIDFromBytes("Cam_Roux".toByteArray()), "Cam_Roux", "Camille R.", announcer = false)

    /** A player with an account and the right to write. */
    val PLAYER = PlayerState(linked = true, banned = false, writing = true, me = CAMILLE, rights = Rights(write = true, announce = false, moderate = false), unread = Unread.NONE)
    val ANNOUNCER = PLAYER.copy(rights = Rights(write = true, announce = true, moderate = false))
    val GUEST = PlayerState(linked = false, banned = false, writing = true, me = null, rights = Rights.NONE, unread = Unread.NONE)

    private var counter = 0L

    fun id(): UUID = UUID(0x1000L, ++counter)

    /** A main post of [feed], [minutesAgo] before [NOW]. */
    fun post(
        feed: FeedKind = FeedKind.GLOBAL,
        minutesAgo: Long = 1,
        author: Author? = ALEX,
        text: String = "Des diamants à la mine du spawn.",
        id: UUID = id(),
        anonymous: Boolean = false,
        mine: Boolean = false,
        pinnedAt: Instant? = null,
        likes: Int = 0,
        liked: Boolean = false,
        replies: Int = 0,
        canDelete: Boolean = mine,
        canPin: Boolean = false,
        unseenReplies: Int = 0,
    ) = PostView(
        id = id, feed = feed, threadId = null, replyTo = null, text = text, createdAt = NOW.minusSeconds(minutesAgo * 60),
        anonymous = anonymous, author = author, likes = likes, replies = replies, pinnedAt = pinnedAt, liked = liked,
        mine = mine, canDelete = canDelete, canPin = canPin, unseenReplies = unseenReplies, answersMe = false,
    )

    /** A reply in the thread of [thread]. */
    fun reply(thread: PostView, minutesAgo: Long = 0, author: Author? = STEVE, mine: Boolean = false, answersMe: Boolean = false) = PostView(
        id = id(), feed = thread.feed, threadId = thread.id, replyTo = null, text = "J'arrive !", createdAt = NOW.minusSeconds(minutesAgo * 60),
        anonymous = false, author = author, likes = 0, replies = 0, pinnedAt = null, liked = false, mine = mine,
        canDelete = mine, canPin = false, unseenReplies = 0, answersMe = answersMe,
    )

    /** A session whose requests land in [sent]. */
    class Recorder {
        val sent = ArrayList<CustomPacketPayload>()
        val session = FeedSession(send = { sent += it }, clock = { NOW })

        inline fun <reified T : CustomPacketPayload> last(): T = sent.filterIsInstance<T>().last()

        inline fun <reified T : CustomPacketPayload> all(): List<T> = sent.filterIsInstance<T>()

        /** The state answer to `open`. */
        fun state(state: PlayerState = PLAYER, available: Availability = Availability.OK) = session.onState(StatePayload(available, state))

        /** Loads [feed]'s first page and answers it with [posts] (and [pinned]). */
        fun page(feed: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
            session.loadIfNeeded(feed)
            val request = last<LoadFeedPayload>()
            session.onPage(PagePayload(request.requestId, Outcome.OK, feed, pinned, posts, next))
        }
    }
}
