package fr.bde_teknantes.lnb.phone.feed.model

import fr.bde_teknantes.lnb.phone.feed.model.Wire.nullable
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

/** What a player may do now (`feed-mod.md` 4.1 and 10). Reading needs no right. */
data class Rights(val write: Boolean, val announce: Boolean, val moderate: Boolean) {
    companion object {
        val NONE = Rights(write = false, announce = false, moderate = false)

        val STREAM_CODEC: StreamCodec<ByteBuf, Rights> = StreamCodec.composite(
            Wire.BOOL, Rights::write,
            Wire.BOOL, Rights::announce,
            Wire.BOOL, Rights::moderate,
            ::Rights,
        )
    }
}

/** The tab dots: whether each feed holds a main post the player hasn't seen. */
data class Unread(val announcements: Boolean, val global: Boolean) {
    operator fun get(feed: FeedKind) = when (feed) {
        FeedKind.ANNOUNCEMENTS -> announcements
        FeedKind.GLOBAL -> global
    }

    companion object {
        val NONE = Unread(announcements = false, global = false)

        val STREAM_CODEC: StreamCodec<ByteBuf, Unread> = StreamCodec.composite(
            Wire.BOOL, Unread::announcements,
            Wire.BOOL, Unread::global,
            ::Unread,
        )
    }
}

/**
 * A player's state on the site (`feed-mod.md` 4.1): whether their Minecraft account is linked to an LNB account,
 * banned, whether writing is on (`feed.writing`), the author block they would post as ([me]), their [rights] and the
 * tab dots. A guest is `linked: false`, `me: null`, no rights, no dots.
 */
data class PlayerState(
    val linked: Boolean,
    val banned: Boolean,
    val writing: Boolean,
    val me: Author?,
    val rights: Rights,
    val unread: Unread,
) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, PlayerState> = StreamCodec.composite(
            Wire.BOOL, PlayerState::linked,
            Wire.BOOL, PlayerState::banned,
            Wire.BOOL, PlayerState::writing,
            Author.STREAM_CODEC.nullable(), PlayerState::me,
            Rights.STREAM_CODEC, PlayerState::rights,
            Unread.STREAM_CODEC, PlayerState::unread,
            ::PlayerState,
        )
    }
}

/**
 * Whether the server can reach Feed on the site, as the client is told with each `state`. Travels by ordinal:
 * reordering, inserting or removing a value needs a bump of `FeedNetwork.PROTOCOL_VERSION`.
 */
enum class Availability {
    OK,

    /** The site doesn't answer, or answers wrongly: "Feed unavailable", or "Connection lost" over what is loaded. */
    UNAVAILABLE,

    /** Feed is switched off on the site (503 `disabled`): "Feed is switched off". */
    DISABLED;

    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, Availability> = Wire.enumByte()
    }
}

/**
 * What became of a client's request (`docs/feed-app.md` 5.3). Travels by ordinal: reordering, inserting or removing a
 * value needs a bump of `FeedNetwork.PROTOCOL_VERSION`.
 */
enum class Outcome {
    OK,
    UNAVAILABLE,
    DISABLED,
    RATE_LIMITED,
    NOT_LINKED,
    BANNED,
    NOT_ALLOWED,
    NOT_FOUND,
    PIN_LIMIT,
    NOT_PINNABLE,
    INVALID;

    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, Outcome> = Wire.enumByte()
    }
}
