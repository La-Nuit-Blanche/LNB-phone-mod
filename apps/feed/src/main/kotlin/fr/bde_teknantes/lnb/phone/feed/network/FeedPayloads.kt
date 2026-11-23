package fr.bde_teknantes.lnb.phone.feed.network

import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.Wire
import fr.bde_teknantes.lnb.phone.feed.model.Wire.list
import fr.bde_teknantes.lnb.phone.feed.model.Wire.nullable
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.time.Instant
import java.util.UUID

// Feed's payloads (`docs/feed-app.md` 5.2). Every field is bounded by its codec: a payload past a bound fails to
// decode, and the connection drops, which only a modified client or server can cause.

private fun <T : CustomPacketPayload> type(name: String) =
    CustomPacketPayload.Type<T>(ResourceLocation.fromNamespaceAndPath(Feed.MOD_ID, name))

private val CURSOR = Wire.string(Feed.MAX_CURSOR).nullable()
private val TEXT = Wire.string(Feed.MAX_TEXT_UNITS)
private val POST = PostView.STREAM_CODEC
private val OPTIONAL_POST = POST.nullable()
private val PINS = Pin.STREAM_CODEC.list(Feed.MAX_PINS)

/** A client's request, answered with the same [requestId]. */
sealed interface FeedRequest : CustomPacketPayload {
    val requestId: Int
}

// ---- Client to server ----

/** The player opened Feed: they become a watcher, and get their state again. */
data object OpenPayload : CustomPacketPayload {
    val TYPE = type<OpenPayload>("open")
    val STREAM_CODEC: StreamCodec<ByteBuf, OpenPayload> = StreamCodec.unit(OpenPayload)
    override fun type() = TYPE
}

/** A page of [feed]: the first without [before], the next with the previous page's `next`. */
data class LoadFeedPayload(override val requestId: Int, val feed: FeedKind, val before: String?) : FeedRequest {
    override fun type() = TYPE

    companion object {
        val TYPE = type<LoadFeedPayload>("load_feed")
        val STREAM_CODEC: StreamCodec<ByteBuf, LoadFeedPayload> = StreamCodec.composite(
            Wire.VAR_INT, LoadFeedPayload::requestId,
            FeedKind.STREAM_CODEC, LoadFeedPayload::feed,
            CURSOR, LoadFeedPayload::before,
            ::LoadFeedPayload,
        )
    }
}

/** A thread: the main post and a page of replies, the next page with the previous one's `next` as [after]. */
data class LoadThreadPayload(override val requestId: Int, val threadId: UUID, val after: String?) : FeedRequest {
    override fun type() = TYPE

    companion object {
        val TYPE = type<LoadThreadPayload>("load_thread")
        val STREAM_CODEC: StreamCodec<ByteBuf, LoadThreadPayload> = StreamCodec.composite(
            Wire.VAR_INT, LoadThreadPayload::requestId,
            Wire.UUID_CODEC, LoadThreadPayload::threadId,
            CURSOR, LoadThreadPayload::after,
            ::LoadThreadPayload,
        )
    }
}

/** A new main post. [key] is the draft's idempotency key (`feed-mod.md` 6). */
data class CreatePostPayload(
    override val requestId: Int,
    val key: UUID,
    val feed: FeedKind,
    val text: String,
    val anonymous: Boolean,
) : FeedRequest {
    override fun type() = TYPE

    // Never the text: it isn't to be logged.
    override fun toString() = "CreatePostPayload($requestId, $feed)"

    companion object {
        val TYPE = type<CreatePostPayload>("create_post")
        val STREAM_CODEC: StreamCodec<ByteBuf, CreatePostPayload> = StreamCodec.composite(
            Wire.VAR_INT, CreatePostPayload::requestId,
            Wire.UUID_CODEC, CreatePostPayload::key,
            FeedKind.STREAM_CODEC, CreatePostPayload::feed,
            TEXT, CreatePostPayload::text,
            Wire.BOOL, CreatePostPayload::anonymous,
            ::CreatePostPayload,
        )
    }
}

/** A reply in the thread of [threadId], answering [replyTo] (a reply of that thread) or the main post. */
data class CreateReplyPayload(
    override val requestId: Int,
    val key: UUID,
    val threadId: UUID,
    val replyTo: UUID?,
    val text: String,
    val anonymous: Boolean,
) : FeedRequest {
    override fun type() = TYPE

    override fun toString() = "CreateReplyPayload($requestId, $threadId)"

    companion object {
        val TYPE = type<CreateReplyPayload>("create_reply")
        val STREAM_CODEC: StreamCodec<ByteBuf, CreateReplyPayload> = StreamCodec.composite(
            Wire.VAR_INT, CreateReplyPayload::requestId,
            Wire.UUID_CODEC, CreateReplyPayload::key,
            Wire.UUID_CODEC, CreateReplyPayload::threadId,
            Wire.UUID_CODEC.nullable(), CreateReplyPayload::replyTo,
            TEXT, CreateReplyPayload::text,
            Wire.BOOL, CreateReplyPayload::anonymous,
            ::CreateReplyPayload,
        )
    }
}

data class LikePayload(override val requestId: Int, val postId: UUID, val liked: Boolean) : FeedRequest {
    override fun type() = TYPE

    companion object {
        val TYPE = type<LikePayload>("like")
        val STREAM_CODEC: StreamCodec<ByteBuf, LikePayload> = StreamCodec.composite(
            Wire.VAR_INT, LikePayload::requestId,
            Wire.UUID_CODEC, LikePayload::postId,
            Wire.BOOL, LikePayload::liked,
            ::LikePayload,
        )
    }
}

/** Pins or unpins an announcement; [replace] confirms the "Replace a pin?" dialog. */
data class PinPayload(override val requestId: Int, val postId: UUID, val pinned: Boolean, val replace: Boolean) : FeedRequest {
    override fun type() = TYPE

    companion object {
        val TYPE = type<PinPayload>("pin")
        val STREAM_CODEC: StreamCodec<ByteBuf, PinPayload> = StreamCodec.composite(
            Wire.VAR_INT, PinPayload::requestId,
            Wire.UUID_CODEC, PinPayload::postId,
            Wire.BOOL, PinPayload::pinned,
            Wire.BOOL, PinPayload::replace,
            ::PinPayload,
        )
    }
}

data class DeletePayload(override val requestId: Int, val postId: UUID) : FeedRequest {
    override fun type() = TYPE

    companion object {
        val TYPE = type<DeletePayload>("delete")
        val STREAM_CODEC: StreamCodec<ByteBuf, DeletePayload> = StreamCodec.composite(
            Wire.VAR_INT, DeletePayload::requestId,
            Wire.UUID_CODEC, DeletePayload::postId,
            ::DeletePayload,
        )
    }
}

/** The player viewed [feed] up to the post created at [upTo]. No answer. */
data class SeenFeedPayload(val feed: FeedKind, val upTo: Instant) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<SeenFeedPayload>("seen_feed")
        val STREAM_CODEC: StreamCodec<ByteBuf, SeenFeedPayload> = StreamCodec.composite(
            FeedKind.STREAM_CODEC, SeenFeedPayload::feed,
            Wire.TIME, SeenFeedPayload::upTo,
            ::SeenFeedPayload,
        )
    }
}

/** The player opened the thread of [threadId] and was shown its first page. No answer. */
data class SeenThreadPayload(val threadId: UUID) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<SeenThreadPayload>("seen_thread")
        val STREAM_CODEC: StreamCodec<ByteBuf, SeenThreadPayload> = StreamCodec.composite(
            Wire.UUID_CODEC, SeenThreadPayload::threadId,
            ::SeenThreadPayload,
        )
    }
}

// ---- Server to client ----

/** Whether Feed is reachable, and the player's state (kept from before when the site is away). */
data class StatePayload(val available: Availability, val state: PlayerState?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<StatePayload>("state")
        val STREAM_CODEC: StreamCodec<ByteBuf, StatePayload> = StreamCodec.composite(
            Availability.STREAM_CODEC, StatePayload::available,
            PlayerState.STREAM_CODEC.nullable(), StatePayload::state,
            ::StatePayload,
        )
    }
}

/** The answer to `load_feed`: the posts when [outcome] is `OK`, empty otherwise. */
data class PagePayload(
    val requestId: Int,
    val outcome: Outcome,
    val feed: FeedKind,
    val pinned: List<PostView>,
    val posts: List<PostView>,
    val next: String?,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<PagePayload>("page")
        val STREAM_CODEC: StreamCodec<ByteBuf, PagePayload> = StreamCodec.composite(
            Wire.VAR_INT, PagePayload::requestId,
            Outcome.STREAM_CODEC, PagePayload::outcome,
            FeedKind.STREAM_CODEC, PagePayload::feed,
            POST.list(Feed.MAX_PINS), PagePayload::pinned,
            POST.list(Feed.MAX_PAGE), PagePayload::posts,
            CURSOR, PagePayload::next,
            ::PagePayload,
        )

        fun failed(requestId: Int, outcome: Outcome, feed: FeedKind) = PagePayload(requestId, outcome, feed, emptyList(), emptyList(), null)
    }
}

/** The answer to `load_thread`: the main post (`null` unless `OK`) and a page of replies. */
data class ThreadPayload(
    val requestId: Int,
    val outcome: Outcome,
    val post: PostView?,
    val replies: List<PostView>,
    val next: String?,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<ThreadPayload>("thread")
        val STREAM_CODEC: StreamCodec<ByteBuf, ThreadPayload> = StreamCodec.composite(
            Wire.VAR_INT, ThreadPayload::requestId,
            Outcome.STREAM_CODEC, ThreadPayload::outcome,
            OPTIONAL_POST, ThreadPayload::post,
            POST.list(Feed.MAX_PAGE), ThreadPayload::replies,
            CURSOR, ThreadPayload::next,
            ::ThreadPayload,
        )

        fun failed(requestId: Int, outcome: Outcome) = ThreadPayload(requestId, outcome, null, emptyList(), null)
    }
}

/**
 * The answer to a write: [post] for `create_post` and `create_reply`; [likes] and [liked] for `like`; [pinned] and
 * [unpinned] for `pin`, and [oldestPin] with `PIN_LIMIT`; [retryAfter] in seconds with `RATE_LIMITED`.
 */
data class ResultPayload(
    val requestId: Int,
    val outcome: Outcome,
    val post: PostView? = null,
    val likes: Int? = null,
    val liked: Boolean? = null,
    val pinned: List<Pin>? = null,
    val unpinned: UUID? = null,
    val oldestPin: UUID? = null,
    val retryAfter: Int? = null,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<ResultPayload>("result")

        val STREAM_CODEC: StreamCodec<ByteBuf, ResultPayload> = Wire.codec(
            { buf, result ->
                Wire.VAR_INT.encode(buf, result.requestId)
                Outcome.STREAM_CODEC.encode(buf, result.outcome)
                Wire.writeNullable(buf, POST, result.post)
                Wire.writeNullable(buf, Wire.VAR_INT, result.likes)
                Wire.writeNullable(buf, Wire.BOOL, result.liked)
                Wire.writeNullable(buf, PINS, result.pinned)
                Wire.writeNullable(buf, Wire.UUID_CODEC, result.unpinned)
                Wire.writeNullable(buf, Wire.UUID_CODEC, result.oldestPin)
                Wire.writeNullable(buf, Wire.VAR_INT, result.retryAfter)
            },
            { buf ->
                ResultPayload(
                    requestId = Wire.VAR_INT.decode(buf),
                    outcome = Outcome.STREAM_CODEC.decode(buf),
                    post = Wire.readNullable(buf, POST),
                    likes = Wire.readNullable(buf, Wire.VAR_INT),
                    liked = Wire.readNullable(buf, Wire.BOOL),
                    pinned = Wire.readNullable(buf, PINS),
                    unpinned = Wire.readNullable(buf, Wire.UUID_CODEC),
                    oldestPin = Wire.readNullable(buf, Wire.UUID_CODEC),
                    retryAfter = Wire.readNullable(buf, Wire.VAR_INT),
                )
            },
        )
    }
}

/** New posts or replies, each the receiver's own view. */
data class LivePayload(val posts: List<PostView>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val MAX = 20
        val TYPE = type<LivePayload>("live")
        val STREAM_CODEC: StreamCodec<ByteBuf, LivePayload> = POST.list(MAX).map(::LivePayload, LivePayload::posts)
    }
}

data class RemovedPayload(val items: List<RemovedItem>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val MAX = 512
        val TYPE = type<RemovedPayload>("removed")
        val STREAM_CODEC: StreamCodec<ByteBuf, RemovedPayload> = RemovedItem.STREAM_CODEC.list(MAX).map(::RemovedPayload, RemovedPayload::items)
    }
}

data class CountsPayload(val items: List<CountUpdate>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val MAX = 256
        val TYPE = type<CountsPayload>("counts")
        val STREAM_CODEC: StreamCodec<ByteBuf, CountsPayload> = CountUpdate.STREAM_CODEC.list(MAX).map(::CountsPayload, CountsPayload::items)
    }
}

/** The whole pinned group, most recent pin first. */
data class PinsPayload(val pinned: List<Pin>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<PinsPayload>("pins")
        val STREAM_CODEC: StreamCodec<ByteBuf, PinsPayload> = PINS.map(::PinsPayload, PinsPayload::pinned)
    }
}

/** The stream came back: what the client holds may be stale and is reloaded where shown. */
data object ResetPayload : CustomPacketPayload {
    val TYPE = type<ResetPayload>("reset")
    val STREAM_CODEC: StreamCodec<ByteBuf, ResetPayload> = StreamCodec.unit(ResetPayload)
    override fun type() = TYPE
}
