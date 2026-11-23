package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import java.util.UUID

/**
 * A request the client sent and waits an answer for, by its `requestId` (`docs/feed-app.md` 8): what the answer is
 * applied to. An answer whose ID isn't waiting (late, after a disconnection, or forged) is dropped.
 */
internal sealed interface PendingRequest {
    /**
     * `load_feed`: the first page when [before] is null and not [more], otherwise the page after [before]. [generation]
     * is the session's count of `reset`s when it was sent: an answer from before the last one is out of date.
     */
    data class LoadFeed(val feed: FeedKind, val more: Boolean, val before: String?, val generation: Int = 0) : PendingRequest

    /** `load_thread`: the first page of replies, or the page after [after]; [generation] as for [LoadFeed]. */
    data class LoadThread(val threadId: UUID, val more: Boolean, val after: String?, val generation: Int = 0) : PendingRequest

    data class Like(val postId: UUID) : PendingRequest

    /** `create_post` from the Compose draft, sent with [key]. */
    data class CreatePost(val key: UUID) : PendingRequest

    /** `create_reply` from the reply draft of [threadId], sent with [key]. */
    data class CreateReply(val threadId: UUID, val key: UUID) : PendingRequest

    data class Pin(val postId: UUID, val pinned: Boolean) : PendingRequest

    /** `delete` of [postId]; [reply] read when it was sent, as the post may be gone by its answer. */
    data class Delete(val postId: UUID, val reply: Boolean = false) : PendingRequest
}

/** Hands out request IDs: increasing, never reused within a session. */
internal class RequestIds {
    private var last = 0

    fun next(): Int {
        last = if (last == Int.MAX_VALUE) 1 else last + 1
        return last
    }
}
