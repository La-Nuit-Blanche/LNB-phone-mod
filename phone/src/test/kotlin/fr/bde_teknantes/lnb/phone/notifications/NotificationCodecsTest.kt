package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.api.AppTarget
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.Utf8String
import net.minecraft.network.VarInt
import net.minecraft.network.VarLong
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The saved and wire forms of notifications and of every payload, including what a modified client could send. */
class NotificationCodecsTest {
    private val ops = RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE)
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")

    @Suppress("DEPRECATION") // the constructor a test can use without a connection
    private fun buffer() = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)

    private fun <B : ByteBuf, T> roundTrip(codec: StreamCodec<in B, T>, value: T, buf: B): T {
        codec.encode(buf, value)
        val decoded = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "bytes left after decoding $value")
        return decoded
    }

    private fun full(id: Long = 42) = PhoneNotification(
        id = id,
        appId = feed,
        title = Component.translatable("notif.test.reply"),
        text = Component.translatable("notif.test.text", "Mika Laurent", "Je l'ai vu près du moulin, côté est !"),
        target = "thread:8",
        time = 1_763_800_000_000L,
        delivered = true,
    )

    // ---- Notifications ----

    @Test
    fun `a notification round-trips every field on the wire`() {
        for (n in listOf(
            full(),
            full().copy(target = null, delivered = false),
            full().copy(id = Long.MAX_VALUE, time = Long.MIN_VALUE),
            full().copy(id = 0, time = 0, title = Component.empty(), text = Component.empty()),
            full().copy(target = "t".repeat(AppTarget.MAX_LENGTH)),
            full().copy(target = "é".repeat(AppTarget.MAX_LENGTH)),
        )) {
            assertEquals(n, roundTrip(NotificationCodecs.STREAM_CODEC, n, buffer()))
        }
    }

    @Test
    fun `a notification round-trips every field in its saved form`() {
        for (n in listOf(full(), full().copy(target = null, delivered = false), full().copy(id = Long.MIN_VALUE))) {
            val saved = NotificationCodecs.CODEC.encodeStart(ops, n).getOrThrow()
            assertEquals(n, NotificationCodecs.CODEC.parse(ops, saved).getOrThrow())
        }
    }

    @Test
    fun `a target over the limit can't be sent or saved`() {
        val long = full().copy(target = "t".repeat(AppTarget.MAX_LENGTH + 1))
        assertThrows(EncoderException::class.java) { NotificationCodecs.STREAM_CODEC.encode(buffer(), long) }
        assertTrue(NotificationCodecs.CODEC.encodeStart(ops, long).error().isPresent)
    }

    @Test
    fun `a truncated notification fails to decode`() {
        val buf = buffer()
        NotificationCodecs.STREAM_CODEC.encode(buf, full())
        val bytes = ByteArray(buf.readableBytes()).also(buf::readBytes)
        for (length in listOf(0, 1, bytes.size / 2, bytes.size - 1)) {
            val cut = buffer().apply { writeBytes(bytes, 0, length) }
            assertThrows(RuntimeException::class.java, { NotificationCodecs.STREAM_CODEC.decode(cut) }, "cut at $length")
        }
    }

    // ---- App ids ----

    @Test
    fun `app ids round-trip up to 128 characters`() {
        val longest = ResourceLocation.fromNamespaceAndPath("a", "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH - 2))
        assertEquals(longest, roundTrip(NotificationCodecs.APP_ID, longest, Unpooled.buffer()))
        assertEquals(feed, roundTrip(NotificationCodecs.APP_ID, feed, Unpooled.buffer()))
    }

    @Test
    fun `an over-long app id fails decoding`() {
        val buf = Unpooled.buffer()
        Utf8String.write(buf, "a:" + "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH), 32767)
        assertThrows(DecoderException::class.java) { NotificationCodecs.APP_ID.decode(buf) }
    }

    @Test
    fun `an over-long app id can't be encoded either`() {
        val long = ResourceLocation.fromNamespaceAndPath("a", "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH))
        assertThrows(EncoderException::class.java) { NotificationCodecs.APP_ID.encode(Unpooled.buffer(), long) }
    }

    @Test
    fun `a malformed app id fails decoding`() {
        for (bad in listOf("Upper:Case", "two::colons", "space here", "a:b c", "é:x")) {
            val buf = Unpooled.buffer()
            Utf8String.write(buf, bad, 32767)
            assertThrows(DecoderException::class.java, { NotificationCodecs.APP_ID.decode(buf) }, bad)
        }
    }

    @Test
    fun `a string length larger than the bytes sent fails decoding`() {
        val buf = Unpooled.buffer()
        VarInt.write(buf, 100)
        buf.writeBytes("test:feed".toByteArray())
        assertThrows(DecoderException::class.java) { NotificationCodecs.APP_ID.decode(buf) }
        val negative = Unpooled.buffer()
        VarInt.write(negative, -1)
        assertThrows(DecoderException::class.java) { NotificationCodecs.APP_ID.decode(negative) }
    }

    // ---- Client to server ----

    @Test
    fun `clear-app requests round-trip`() {
        for (upTo in listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val payload = ClearAppNotificationsPayload(feed, upTo)
            assertEquals(payload, roundTrip(ClearAppNotificationsPayload.STREAM_CODEC, payload, Unpooled.buffer()))
        }
    }

    @Test
    fun `clear-app requests hold an app id and an id, and name no player`() {
        // The record's fields are all a client can say: the server takes the player from the connection.
        val buf = Unpooled.buffer()
        ClearAppNotificationsPayload.STREAM_CODEC.encode(buf, ClearAppNotificationsPayload(feed, 5))
        assertEquals("test:feed", Utf8String.read(buf, 128))
        assertEquals(5L, VarLong.read(buf))
        assertEquals(0, buf.readableBytes())
    }

    @Test
    fun `a malformed clear-app request fails decoding`() {
        val badId = Unpooled.buffer().also { Utf8String.write(it, "NOT VALID", 32767); VarLong.write(it, 1) }
        assertThrows(DecoderException::class.java) { ClearAppNotificationsPayload.STREAM_CODEC.decode(badId) }
        val noId = Unpooled.buffer().also { Utf8String.write(it, "test:feed", 32767) }
        assertThrows(RuntimeException::class.java) { ClearAppNotificationsPayload.STREAM_CODEC.decode(noId) }
        val tooLong = Unpooled.buffer().also { Utf8String.write(it, "a:" + "p".repeat(500), 32767); VarLong.write(it, 1) }
        assertThrows(DecoderException::class.java) { ClearAppNotificationsPayload.STREAM_CODEC.decode(tooLong) }
    }

    @Test
    fun `dismiss requests round-trip and fail when cut`() {
        for (id in listOf(0L, 7L, -7L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val payload = DismissNotificationPayload(id)
            assertEquals(payload, roundTrip(DismissNotificationPayload.STREAM_CODEC, payload, Unpooled.buffer()))
        }
        assertThrows(RuntimeException::class.java) { DismissNotificationPayload.STREAM_CODEC.decode(Unpooled.buffer()) }
        // A var-long that never ends.
        val endless = Unpooled.buffer().apply { repeat(11) { writeByte(0x80) } }
        assertThrows(RuntimeException::class.java) { DismissNotificationPayload.STREAM_CODEC.decode(endless) }
    }

    // ---- Server to client ----

    @Test
    fun `cleared notices round-trip with and without a target`() {
        for (payload in listOf(
            NotificationsClearedPayload(feed, null),
            NotificationsClearedPayload(feed, "thread:1"),
            NotificationsClearedPayload(feed, "t".repeat(AppTarget.MAX_LENGTH)),
        )) {
            assertEquals(payload, roundTrip(NotificationsClearedPayload.STREAM_CODEC, payload, Unpooled.buffer()))
        }
        assertThrows(EncoderException::class.java) {
            NotificationsClearedPayload.STREAM_CODEC.encode(Unpooled.buffer(), NotificationsClearedPayload(feed, "t".repeat(AppTarget.MAX_LENGTH + 1)))
        }
    }

    @Test
    fun `an added notice round-trips`() {
        val payload = NotificationAddedPayload(full())
        assertEquals(payload, roundTrip(NotificationAddedPayload.STREAM_CODEC, payload, buffer()))
    }

    @Test
    fun `a sync round-trips a full list, in order`() {
        val list = (1L..NotificationList.CAPACITY.toLong()).map { full(it).copy(delivered = it % 2 == 0L) }
        val payload = NotificationsSyncPayload(list)
        assertEquals(payload, roundTrip(NotificationsSyncPayload.STREAM_CODEC, payload, buffer()))
        assertEquals(NotificationsSyncPayload(emptyList()), roundTrip(NotificationsSyncPayload.STREAM_CODEC, NotificationsSyncPayload(emptyList()), buffer()))
    }

    @Test
    fun `a sync of more than the capacity is refused both ways`() {
        val tooMany = (1L..NotificationList.CAPACITY.toLong() + 1).map { full(it) }
        assertThrows(EncoderException::class.java) { NotificationsSyncPayload.STREAM_CODEC.encode(buffer(), NotificationsSyncPayload(tooMany)) }
        val buf = buffer()
        VarInt.write(buf, NotificationList.CAPACITY + 1)
        assertThrows(DecoderException::class.java) { NotificationsSyncPayload.STREAM_CODEC.decode(buf) }
    }

    @Test
    fun `payload types are distinct and in the phone's namespace`() {
        val types = listOf(
            NotificationsSyncPayload.TYPE, NotificationAddedPayload.TYPE, NotificationsClearedPayload.TYPE,
            ClearAppNotificationsPayload.TYPE, DismissNotificationPayload.TYPE,
        ).map { it.id() }
        assertEquals(types.size, types.toSet().size)
        assertTrue(types.all { it.namespace == "lnbphone" })
        assertFalse(types.any { it.path.isEmpty() })
    }
}
