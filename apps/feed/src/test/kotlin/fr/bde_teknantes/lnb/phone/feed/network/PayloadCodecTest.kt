package fr.bde_teknantes.lnb.phone.feed.network

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Every payload round-trips, and one past a bound fails to decode (and to encode). */
class PayloadCodecTest {
    private val id = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")
    private val other = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")
    private val time = Instant.parse("2026-11-23T20:14:05.123Z")
    private val author = Author(other, "Alex_Craft", "Alexandre M.", announcer = true)

    private fun post(text: String = "Qui est chaud pour une virée dans le Nether ce soir ? 👍", realName: String? = "Alexandre M.") = PostView(
        id = id, feed = FeedKind.GLOBAL, threadId = other, replyTo = ReplyRef(null, true, null), text = text, createdAt = time,
        anonymous = false, author = author.copy(realName = realName), likes = 12, replies = 3, pinnedAt = time, liked = true, mine = false,
        canDelete = true, canPin = false, unseenReplies = 2, answersMe = true,
    )

    private val bare = PostView(
        id = id, feed = FeedKind.ANNOUNCEMENTS, threadId = null, replyTo = null, text = "x", createdAt = time, anonymous = true,
        author = null, likes = 0, replies = 0, pinnedAt = null, liked = false, mine = false, canDelete = false, canPin = false,
        unseenReplies = 0, answersMe = false,
    )

    private val state = PlayerState(true, false, true, author, Rights(true, true, false), Unread(true, false))

    private fun <T : Any> roundTrip(codec: StreamCodec<ByteBuf, T>, value: T) {
        val buf = Unpooled.buffer()
        codec.encode(buf, value)
        assertEquals(value, codec.decode(buf))
        assertEquals(0, buf.readableBytes(), "everything read back for $value")
    }

    @Test
    fun `client payloads round-trip`() {
        roundTrip(OpenPayload.STREAM_CODEC, OpenPayload)
        roundTrip(LoadFeedPayload.STREAM_CODEC, LoadFeedPayload(7, FeedKind.ANNOUNCEMENTS, null))
        roundTrip(LoadFeedPayload.STREAM_CODEC, LoadFeedPayload(Int.MAX_VALUE, FeedKind.GLOBAL, "c".repeat(128)))
        roundTrip(LoadThreadPayload.STREAM_CODEC, LoadThreadPayload(8, id, "1764000000000:$other"))
        roundTrip(CreatePostPayload.STREAM_CODEC, CreatePostPayload(9, other, FeedKind.GLOBAL, "👍".repeat(280), true))
        roundTrip(CreateReplyPayload.STREAM_CODEC, CreateReplyPayload(10, other, id, null, "Moi !", false))
        roundTrip(CreateReplyPayload.STREAM_CODEC, CreateReplyPayload(10, other, id, other, "Moi !", true))
        roundTrip(LikePayload.STREAM_CODEC, LikePayload(11, id, true))
        roundTrip(PinPayload.STREAM_CODEC, PinPayload(12, id, true, true))
        roundTrip(DeletePayload.STREAM_CODEC, DeletePayload(13, id))
        roundTrip(SeenFeedPayload.STREAM_CODEC, SeenFeedPayload(FeedKind.GLOBAL, time))
        roundTrip(SeenThreadPayload.STREAM_CODEC, SeenThreadPayload(id))
    }

    @Test
    fun `server payloads round-trip`() {
        roundTrip(StatePayload.STREAM_CODEC, StatePayload(Availability.OK, state))
        roundTrip(StatePayload.STREAM_CODEC, StatePayload(Availability.DISABLED, null))
        roundTrip(StatePayload.STREAM_CODEC, StatePayload(Availability.UNAVAILABLE, PlayerState(false, false, false, null, Rights.NONE, Unread.NONE)))
        roundTrip(PagePayload.STREAM_CODEC, PagePayload(1, Outcome.OK, FeedKind.ANNOUNCEMENTS, List(3) { bare }, List(50) { post() }, "next"))
        roundTrip(PagePayload.STREAM_CODEC, PagePayload.failed(2, Outcome.RATE_LIMITED, FeedKind.GLOBAL))
        roundTrip(ThreadPayload.STREAM_CODEC, ThreadPayload(3, Outcome.OK, bare, List(50) { post() }, null))
        roundTrip(ThreadPayload.STREAM_CODEC, ThreadPayload.failed(4, Outcome.NOT_FOUND))
        roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(5, Outcome.OK, post = post()))
        roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(6, Outcome.OK, likes = 13, liked = true))
        roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(7, Outcome.OK, pinned = List(3) { Pin(id, time) }, unpinned = other))
        roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(8, Outcome.PIN_LIMIT, oldestPin = other))
        roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(9, Outcome.RATE_LIMITED, retryAfter = 40))
        roundTrip(LivePayload.STREAM_CODEC, LivePayload(List(20) { post() }))
        roundTrip(RemovedPayload.STREAM_CODEC, RemovedPayload(List(512) { RemovedItem(id, FeedKind.GLOBAL, if (it % 2 == 0) null else other) }))
        roundTrip(CountsPayload.STREAM_CODEC, CountsPayload(List(256) { CountUpdate(id, it, it * 1000) }))
        roundTrip(PinsPayload.STREAM_CODEC, PinsPayload(List(3) { Pin(id, time) }))
        roundTrip(ResetPayload.STREAM_CODEC, ResetPayload)
        for (outcome in Outcome.entries) roundTrip(ResultPayload.STREAM_CODEC, ResultPayload(0, outcome))
    }

    @Test
    fun `the longest fields fit`() {
        roundTrip(PostView.STREAM_CODEC, post(text = "👍".repeat(280), realName = "😀".repeat(64)))
        // A page of 50 posts at their largest, with 3 pinned, stays far under the 1 MiB a payload may hold.
        val big = post(text = "👍".repeat(280), realName = "😀".repeat(64))
        val buf = Unpooled.buffer()
        PagePayload.STREAM_CODEC.encode(buf, PagePayload(1, Outcome.OK, FeedKind.ANNOUNCEMENTS, List(3) { big }, List(50) { big }, "c".repeat(128)))
        assertTrue(buf.readableBytes() < 200_000, "${buf.readableBytes()} bytes")
    }

    @Test
    fun `past a bound, encoding fails`() {
        assertThrows(Exception::class.java) { roundTrip(PostView.STREAM_CODEC, post(text = "a".repeat(561))) }
        assertThrows(Exception::class.java) { roundTrip(LivePayload.STREAM_CODEC, LivePayload(List(21) { bare })) }
        assertThrows(Exception::class.java) { roundTrip(PinsPayload.STREAM_CODEC, PinsPayload(List(4) { Pin(id, time) })) }
        assertThrows(Exception::class.java) { roundTrip(PagePayload.STREAM_CODEC, PagePayload(1, Outcome.OK, FeedKind.GLOBAL, emptyList(), List(51) { bare }, null)) }
        assertThrows(Exception::class.java) { roundTrip(RemovedPayload.STREAM_CODEC, RemovedPayload(List(513) { RemovedItem(id, FeedKind.GLOBAL, null) })) }
        assertThrows(Exception::class.java) { roundTrip(CountsPayload.STREAM_CODEC, CountsPayload(List(257) { CountUpdate(id, 0, 0) })) }
        assertThrows(Exception::class.java) { roundTrip(LoadFeedPayload.STREAM_CODEC, LoadFeedPayload(1, FeedKind.GLOBAL, "c".repeat(129))) }
        assertThrows(Exception::class.java) { roundTrip(PostView.STREAM_CODEC, post().copy(author = author.copy(username = "u".repeat(17)))) }
    }

    @Test
    fun `past a bound, what a modified client sends fails to decode`() {
        // create_post with a text of 561 units, written with a looser codec.
        val text = Unpooled.buffer().also {
            ByteBufCodecs.VAR_INT.encode(it, 1)
            UUIDUtil.STREAM_CODEC.encode(it, id)
            it.writeByte(FeedKind.GLOBAL.ordinal)
            ByteBufCodecs.stringUtf8(10_000).encode(it, "a".repeat(561))
            it.writeBoolean(false)
        }
        assertThrows(Exception::class.java) { CreatePostPayload.STREAM_CODEC.decode(text) }

        val cursor = Unpooled.buffer().also {
            ByteBufCodecs.VAR_INT.encode(it, 1)
            it.writeByte(FeedKind.GLOBAL.ordinal)
            it.writeBoolean(true)
            ByteBufCodecs.stringUtf8(10_000).encode(it, "c".repeat(129))
        }
        assertThrows(Exception::class.java) { LoadFeedPayload.STREAM_CODEC.decode(cursor) }

        val feed = Unpooled.buffer().also {
            ByteBufCodecs.VAR_INT.encode(it, 1)
            it.writeByte(2)
            it.writeBoolean(false)
        }
        assertThrows(Exception::class.java) { LoadFeedPayload.STREAM_CODEC.decode(feed) }

        val live = Unpooled.buffer().also { PostView.STREAM_CODEC.apply(ByteBufCodecs.list(100)).encode(it, List(21) { bare }) }
        assertThrows(Exception::class.java) { LivePayload.STREAM_CODEC.decode(live) }
    }
}
