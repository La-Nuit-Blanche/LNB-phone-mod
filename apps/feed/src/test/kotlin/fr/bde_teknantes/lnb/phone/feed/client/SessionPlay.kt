package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals

/*
 * The server's side of a session, for the tests that play payloads in every order: each helper answers a request the
 * session really sent, and fails the test when it sent none (so an answer never lands on a stale request by mistake).
 */

/** Asks for [feed]'s first page (it must be due) and answers it. */
internal fun FeedFixtures.Recorder.firstPage(feed: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
    val request = sendsLoad(feed) { session.loadIfNeeded(feed) }
    session.onPage(PagePayload(request.requestId, Outcome.OK, feed, pinned, posts, next))
}

/** Asks for [feed]'s next page and answers it. */
internal fun FeedFixtures.Recorder.nextPage(feed: FeedKind, posts: List<PostView>, next: String? = null) {
    val request = sendsLoad(feed) { session.loadMore(feed) }
    session.onPage(PagePayload(request.requestId, Outcome.OK, feed, emptyList(), posts, next))
}

/** Runs [action], which must send one `load_feed` for [feed]; returns it. */
internal fun FeedFixtures.Recorder.sendsLoad(feed: FeedKind, action: () -> Unit): LoadFeedPayload {
    val before = all<LoadFeedPayload>().size
    action()
    assertEquals(before + 1, all<LoadFeedPayload>().size, "a load_feed was expected")
    return last<LoadFeedPayload>().also { assertEquals(feed, it.feed) }
}

/** Loads the thread of [main] (it must be due) and answers it with [replies]. */
internal fun FeedFixtures.Recorder.loadThread(main: PostView, replies: List<PostView>, next: String? = null) {
    val before = all<LoadThreadPayload>().size
    session.loadThreadIfNeeded(main.id)
    assertEquals(before + 1, all<LoadThreadPayload>().size, "a load_thread was expected")
    session.onThread(ThreadPayload(last<LoadThreadPayload>().requestId, Outcome.OK, main, replies, next))
}

/** A reply in the thread of [thread] answering [to] (a reply of it), or the main post when [to] is null. */
internal fun replyIn(
    thread: PostView,
    to: PostView? = null,
    minutesAgo: Long = 0,
    mine: Boolean = false,
    answersMe: Boolean = false,
): PostView = FeedFixtures.reply(thread, minutesAgo = minutesAgo, mine = mine, answersMe = answersMe)
    .copy(replyTo = to?.let { ReplyRef(it.id, it.anonymous, it.author?.displayName) })
