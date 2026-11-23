package fr.bde_teknantes.lnb.phone.feed.network

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Bytes no honest side writes (`docs/feed-app.md` 5): a post view or a write's result with each field past its bound,
 * written by hand with looser codecs, must fail to decode rather than reach the game.
 */
class HostilePayloadTest {
    private val id = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")
    private val loose = ByteBufCodecs.stringUtf8(100_000)

    /** The fields of a post view, each as given, in the codec's order. Strings are written with no bound. */
    private data class RawPost(
        val feed: Int = FeedKind.GLOBAL.ordinal,
        val threadId: UUID? = null,
        val replyTo: Triple<UUID?, Boolean, String?>? = null,
        val text: String = "Salut",
        val author: Pair<String, String?>? = "Alex_Craft" to "Alexandre M.",
    )

    private fun ByteBuf.writeNullable(present: Boolean, write: ByteBuf.() -> Unit) {
        writeBoolean(present)
        if (present) write()
    }

    private fun ByteBuf.post(raw: RawPost) {
        UUIDUtil.STREAM_CODEC.encode(this, id)
        writeByte(raw.feed)
        writeNullable(raw.threadId != null) { UUIDUtil.STREAM_CODEC.encode(this, raw.threadId!!) }
        writeNullable(raw.replyTo != null) {
            val (target, anonymous, name) = raw.replyTo!!
            writeNullable(target != null) { UUIDUtil.STREAM_CODEC.encode(this, target!!) }
            writeBoolean(anonymous)
            writeNullable(name != null) { loose.encode(this, name!!) }
        }
        loose.encode(this, raw.text)
        ByteBufCodecs.VAR_LONG.encode(this, 1_764_000_000_000L)
        writeBoolean(raw.author == null)
        writeNullable(raw.author != null) {
            UUIDUtil.STREAM_CODEC.encode(this, id)
            loose.encode(this, raw.author!!.first)
            writeNullable(raw.author.second != null) { loose.encode(this, raw.author.second!!) }
            writeBoolean(false)
        }
        ByteBufCodecs.VAR_INT.encode(this, 12)
        ByteBufCodecs.VAR_INT.encode(this, 3)
        writeNullable(false) {}
        repeat(4) { writeBoolean(false) }
        ByteBufCodecs.VAR_INT.encode(this, 0)
        writeBoolean(false)
    }

    private fun bytes(write: ByteBuf.() -> Unit): ByteBuf = Unpooled.buffer().apply(write)

    private fun <T> decodes(codec: StreamCodec<ByteBuf, T>, buf: ByteBuf): T {
        val value = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "read to the end")
        return value
    }

    private fun refused(codec: StreamCodec<ByteBuf, *>, buf: ByteBuf) {
        assertThrows(Exception::class.java) { codec.decode(buf) }
    }

    @Test
    fun `the hand-written post reads back, so the cases below fail on their one bad field`() {
        val post = decodes(PostView.STREAM_CODEC, bytes { post(RawPost(replyTo = Triple(id, false, "Mika L."), threadId = id)) })
        assertEquals("Salut", post.text)
        assertEquals("Mika L.", post.replyTo?.name)
        assertEquals("Alexandre M.", post.author?.realName)
        val longest = decodes(PostView.STREAM_CODEC, bytes { post(RawPost(text = "👍".repeat(280), author = "a".repeat(16) to "😀".repeat(64))) })
        assertEquals(560, longest.text.length)
        assertNull(decodes(PostView.STREAM_CODEC, bytes { post(RawPost(author = null)) }).author)
    }

    @Test
    fun `a post view with any field past its bound fails to decode`() {
        val cases = listOf(
            RawPost(text = "a".repeat(561)),
            RawPost(text = "👍".repeat(281)),
            RawPost(feed = 2),
            RawPost(feed = 255),
            RawPost(author = "u".repeat(17) to null),
            RawPost(author = "Alex_Craft" to "r".repeat(129)),
            RawPost(replyTo = Triple(null, false, "n".repeat(129))),
        )
        for (case in cases) refused(PostView.STREAM_CODEC, bytes { post(case) })
        // Cut short anywhere: a present flag with nothing behind it.
        refused(PostView.STREAM_CODEC, bytes { UUIDUtil.STREAM_CODEC.encode(this, id); writeByte(0); writeBoolean(true) })
    }

    /** A write's result, written field by field: [fields] writes everything after the request ID and outcome. */
    private fun result(outcome: Int = Outcome.OK.ordinal, fields: ByteBuf.() -> Unit) = bytes {
        ByteBufCodecs.VAR_INT.encode(this, 5)
        writeByte(outcome)
        fields()
    }

    private fun ByteBuf.absent(count: Int) = repeat(count) { writeBoolean(false) }

    @Test
    fun `a write's result with any nullable field past its bound fails to decode`() {
        // The honest shape first: a pin's answer with three pins.
        val ok = result {
            absent(3)
            writeBoolean(true)
            ByteBufCodecs.VAR_INT.encode(this, 3)
            repeat(3) { UUIDUtil.STREAM_CODEC.encode(this, id); ByteBufCodecs.VAR_LONG.encode(this, 1L) }
            absent(3)
        }
        assertEquals(3, decodes(ResultPayload.STREAM_CODEC, ok).pinned?.size)

        refused(ResultPayload.STREAM_CODEC, result(outcome = Outcome.entries.size) { absent(7) })
        refused(ResultPayload.STREAM_CODEC, result { writeBoolean(true); post(RawPost(text = "a".repeat(561))); absent(6) })
        refused(ResultPayload.STREAM_CODEC, result { writeBoolean(true); post(RawPost(author = "Alex_Craft" to "r".repeat(129))); absent(6) })
        refused(
            ResultPayload.STREAM_CODEC,
            result {
                absent(3)
                writeBoolean(true)
                ByteBufCodecs.VAR_INT.encode(this, 4)
                repeat(4) { UUIDUtil.STREAM_CODEC.encode(this, id); ByteBufCodecs.VAR_LONG.encode(this, 1L) }
                absent(3)
            },
        )
        // A var int longer than five bytes, for likes.
        refused(ResultPayload.STREAM_CODEC, result { writeBoolean(false); writeBoolean(true); repeat(6) { writeByte(0x80) } })
        // Present flags with nothing behind them, for each field in turn.
        for (absentBefore in 0 until 7) refused(ResultPayload.STREAM_CODEC, result { absent(absentBefore); writeBoolean(true) })
    }

    @Test
    fun `server payloads past their bounds fail to decode`() {
        val state = bytes {
            writeByte(0)
            writeBoolean(true)
            repeat(3) { writeBoolean(true) }
            writeBoolean(true)
            UUIDUtil.STREAM_CODEC.encode(this, id)
            loose.encode(this, "Cam_Roux")
            writeBoolean(true)
            loose.encode(this, "r".repeat(129))
        }
        refused(StatePayload.STREAM_CODEC, state)
        refused(StatePayload.STREAM_CODEC, bytes { writeByte(3); writeBoolean(false) })

        val nextTooLong = bytes {
            ByteBufCodecs.VAR_INT.encode(this, 1)
            writeByte(Outcome.OK.ordinal)
            writeByte(FeedKind.GLOBAL.ordinal)
            ByteBufCodecs.VAR_INT.encode(this, 0)
            ByteBufCodecs.VAR_INT.encode(this, 0)
            writeBoolean(true)
            loose.encode(this, "c".repeat(129))
        }
        refused(PagePayload.STREAM_CODEC, nextTooLong)
        val fourPinned = bytes {
            ByteBufCodecs.VAR_INT.encode(this, 1)
            writeByte(Outcome.OK.ordinal)
            writeByte(FeedKind.ANNOUNCEMENTS.ordinal)
            ByteBufCodecs.VAR_INT.encode(this, 4)
            repeat(4) { post(RawPost()) }
        }
        refused(PagePayload.STREAM_CODEC, fourPinned)
        val replies = bytes {
            ByteBufCodecs.VAR_INT.encode(this, 1)
            writeByte(Outcome.OK.ordinal)
            writeBoolean(false)
            ByteBufCodecs.VAR_INT.encode(this, 51)
            repeat(51) { post(RawPost()) }
        }
        refused(ThreadPayload.STREAM_CODEC, replies)
    }

    @Test
    fun `client payloads past their bounds fail to decode`() {
        val reply = bytes {
            ByteBufCodecs.VAR_INT.encode(this, 1)
            UUIDUtil.STREAM_CODEC.encode(this, id)
            UUIDUtil.STREAM_CODEC.encode(this, id)
            writeBoolean(false)
            loose.encode(this, "a".repeat(561))
            writeBoolean(false)
        }
        refused(CreateReplyPayload.STREAM_CODEC, reply)
        val after = bytes {
            ByteBufCodecs.VAR_INT.encode(this, 1)
            UUIDUtil.STREAM_CODEC.encode(this, id)
            writeBoolean(true)
            loose.encode(this, "c".repeat(129))
        }
        refused(LoadThreadPayload.STREAM_CODEC, after)
        refused(SeenFeedPayload.STREAM_CODEC, bytes { writeByte(7); ByteBufCodecs.VAR_LONG.encode(this, 0L) })
        // A request ID is any var int: a negative one is a client's own business, not a failure.
        val negative = bytes { ByteBufCodecs.VAR_INT.encode(this, -1); UUIDUtil.STREAM_CODEC.encode(this, id) }
        assertEquals(DeletePayload(-1, id), decodes(DeletePayload.STREAM_CODEC, negative))
    }
}
