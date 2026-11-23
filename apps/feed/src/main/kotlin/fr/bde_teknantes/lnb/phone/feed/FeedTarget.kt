package fr.bde_teknantes.lnb.phone.feed

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import java.util.UUID

/**
 * Where a Feed notification leads (`docs/feed-app.md` 6.2). The server writes it; the client reads it back when the
 * player opens the notification, maybe days later and with a newer Feed, so [parse] answers null for anything it
 * doesn't recognise rather than throwing (the app then opens on Announcements).
 *
 * - `post:<postId>`: an announcement, on the Announcements tab.
 * - `reply:<feed>:<threadId>:<replyId>`: a reply, in its thread over the tab of its feed.
 *
 * At most [MAX_LENGTH] characters, far under the phone's limit.
 */
sealed interface FeedTarget {
    data class Post(val postId: UUID) : FeedTarget {
        override fun toString() = "$POST$postId"
    }

    data class Reply(val feed: FeedKind, val threadId: UUID, val replyId: UUID) : FeedTarget {
        override fun toString() = "$REPLY${feed.wire}:$threadId:$replyId"
    }

    companion object {
        /** `reply:announcements:` and two UUIDs with their separator. */
        const val MAX_LENGTH = 93

        private const val POST = "post:"
        private const val REPLY = "reply:"

        fun parse(target: String?): FeedTarget? {
            if (target == null || target.length > MAX_LENGTH) return null
            return when {
                target.startsWith(POST) -> uuid(target.substring(POST.length))?.let(::Post)
                target.startsWith(REPLY) -> {
                    val parts = target.substring(REPLY.length).split(':')
                    if (parts.size != 3) return null
                    val feed = FeedKind.fromWire(parts[0]) ?: return null
                    val thread = uuid(parts[1]) ?: return null
                    val reply = uuid(parts[2]) ?: return null
                    Reply(feed, thread, reply)
                }
                else -> null
            }
        }

        /** Only the canonical form: `UUID.fromString` alone also takes short or unpadded groups. */
        private fun uuid(text: String): UUID? {
            if (text.length != 36) return null
            val uuid = try {
                UUID.fromString(text)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return uuid.takeIf { it.toString() == text.lowercase() }
        }
    }
}
