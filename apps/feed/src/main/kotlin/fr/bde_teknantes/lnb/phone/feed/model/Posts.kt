package fr.bde_teknantes.lnb.phone.feed.model

import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Wire.nullable
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import java.time.Instant
import java.util.UUID

/**
 * One of the two feeds. [wire] is how the site and the targets write it.
 * Travels by ordinal: reordering, inserting or removing a value needs a bump of `FeedNetwork.PROTOCOL_VERSION`.
 */
enum class FeedKind(val wire: String) {
    ANNOUNCEMENTS("announcements"),
    GLOBAL("global");

    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, FeedKind> = Wire.enumByte()

        fun fromWire(text: String?): FeedKind? = entries.firstOrNull { it.wire == text }
    }
}

/**
 * Who wrote a post, as the site shows it (`feed-mod.md` 3): the Minecraft account it was posted from, the author's
 * real name when they show it (first name and initial), and whether they can post announcements now (the badge).
 */
data class Author(val uuid: UUID, val username: String, val realName: String?, val announcer: Boolean) {
    /** `realName ?: username`. */
    val displayName: String get() = realName ?: username

    /** `@username`, only with a real name: otherwise the username is already the display name. */
    val handle: String? get() = if (realName != null) "@$username" else null

    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, Author> = StreamCodec.composite(
            Wire.UUID_CODEC, Author::uuid,
            Wire.string(Feed.MAX_USERNAME), Author::username,
            Wire.string(Feed.MAX_REAL_NAME_UNITS).nullable(), Author::realName,
            Wire.BOOL, Author::announcer,
            ::Author,
        )
    }
}

/**
 * The reply a reply answers (`feed-mod.md` 3): [id] (null once purged), whether it was [anonymous], and the display
 * [name] of who wrote it (null when anonymous).
 */
data class ReplyRef(val id: UUID?, val anonymous: Boolean, val name: String?) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, ReplyRef> = StreamCodec.composite(
            Wire.UUID_CODEC.nullable(), ReplyRef::id,
            Wire.BOOL, ReplyRef::anonymous,
            Wire.string(Feed.MAX_REAL_NAME_UNITS).nullable(), ReplyRef::name,
            ::ReplyRef,
        )
    }
}

/**
 * A main post or a reply, computed by the site **for one viewer** (`feed-mod.md` 3). A view is only ever sent to the
 * client of the viewer it was made for: [author] of an anonymous post is filled for its author and for moderators.
 */
data class PostView(
    val id: UUID,
    val feed: FeedKind,
    /** Null on a main post; the main post on a reply. */
    val threadId: UUID?,
    val replyTo: ReplyRef?,
    val text: String,
    val createdAt: Instant,
    val anonymous: Boolean,
    val author: Author?,
    val likes: Int,
    /** On a main post, the replies in its thread; on a reply, the replies that answer it. */
    val replies: Int,
    val pinnedAt: Instant?,
    val liked: Boolean,
    val mine: Boolean,
    val canDelete: Boolean,
    val canPin: Boolean,
    val unseenReplies: Int,
    val answersMe: Boolean,
) {
    val isReply: Boolean get() = threadId != null

    /** What the client shows in place of the text, on purpose: never the text itself, which isn't to be logged. */
    override fun toString() = "PostView($id, $feed, thread=$threadId)"

    companion object {
        private val TEXT = Wire.string(Feed.MAX_TEXT_UNITS)

        val STREAM_CODEC: StreamCodec<ByteBuf, PostView> = Wire.codec(
            { buf, post ->
                Wire.UUID_CODEC.encode(buf, post.id)
                FeedKind.STREAM_CODEC.encode(buf, post.feed)
                Wire.writeNullable(buf, Wire.UUID_CODEC, post.threadId)
                Wire.writeNullable(buf, ReplyRef.STREAM_CODEC, post.replyTo)
                TEXT.encode(buf, post.text)
                Wire.TIME.encode(buf, post.createdAt)
                Wire.BOOL.encode(buf, post.anonymous)
                Wire.writeNullable(buf, Author.STREAM_CODEC, post.author)
                Wire.VAR_INT.encode(buf, post.likes)
                Wire.VAR_INT.encode(buf, post.replies)
                Wire.writeNullable(buf, Wire.TIME, post.pinnedAt)
                Wire.BOOL.encode(buf, post.liked)
                Wire.BOOL.encode(buf, post.mine)
                Wire.BOOL.encode(buf, post.canDelete)
                Wire.BOOL.encode(buf, post.canPin)
                Wire.VAR_INT.encode(buf, post.unseenReplies)
                Wire.BOOL.encode(buf, post.answersMe)
            },
            { buf ->
                PostView(
                    id = Wire.UUID_CODEC.decode(buf),
                    feed = FeedKind.STREAM_CODEC.decode(buf),
                    threadId = Wire.readNullable(buf, Wire.UUID_CODEC),
                    replyTo = Wire.readNullable(buf, ReplyRef.STREAM_CODEC),
                    text = TEXT.decode(buf),
                    createdAt = Wire.TIME.decode(buf),
                    anonymous = Wire.BOOL.decode(buf),
                    author = Wire.readNullable(buf, Author.STREAM_CODEC),
                    likes = Wire.VAR_INT.decode(buf),
                    replies = Wire.VAR_INT.decode(buf),
                    pinnedAt = Wire.readNullable(buf, Wire.TIME),
                    liked = Wire.BOOL.decode(buf),
                    mine = Wire.BOOL.decode(buf),
                    canDelete = Wire.BOOL.decode(buf),
                    canPin = Wire.BOOL.decode(buf),
                    unseenReplies = Wire.VAR_INT.decode(buf),
                    answersMe = Wire.BOOL.decode(buf),
                )
            },
        )
    }
}

/** A pinned announcement and when it was pinned. */
data class Pin(val id: UUID, val pinnedAt: Instant) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, Pin> = StreamCodec.composite(
            Wire.UUID_CODEC, Pin::id,
            Wire.TIME, Pin::pinnedAt,
            ::Pin,
        )
    }
}

/** A post's or reply's new counts. */
data class CountUpdate(val id: UUID, val likes: Int, val replies: Int) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, CountUpdate> = StreamCodec.composite(
            Wire.UUID_CODEC, CountUpdate::id,
            Wire.VAR_INT, CountUpdate::likes,
            Wire.VAR_INT, CountUpdate::replies,
            ::CountUpdate,
        )
    }
}

/** A post or reply that went (deleted, or with its thread or its account). */
data class RemovedItem(val id: UUID, val feed: FeedKind, val threadId: UUID?) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, RemovedItem> = StreamCodec.composite(
            Wire.UUID_CODEC, RemovedItem::id,
            FeedKind.STREAM_CODEC, RemovedItem::feed,
            Wire.UUID_CODEC.nullable(), RemovedItem::threadId,
            ::RemovedItem,
        )
    }
}
