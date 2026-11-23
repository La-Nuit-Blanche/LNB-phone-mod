package fr.bde_teknantes.lnb.phone.feed.model

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * The building blocks of Feed's stream codecs (`docs/feed-app.md` 5). Every string and list is bounded, so a payload
 * past a bound fails to decode (the connection then drops, which only a modified client or server can cause), and
 * the encoder refuses it as well.
 */
internal object Wire {
    val UUID_CODEC: StreamCodec<ByteBuf, UUID> = UUIDUtil.STREAM_CODEC
    val BOOL: StreamCodec<ByteBuf, Boolean> = ByteBufCodecs.BOOL
    val VAR_INT: StreamCodec<ByteBuf, Int> = ByteBufCodecs.VAR_INT

    /** A time as epoch milliseconds in a var long. */
    val TIME: StreamCodec<ByteBuf, Instant> = ByteBufCodecs.VAR_LONG.map(Instant::ofEpochMilli, Instant::toEpochMilli)

    /** A string of at most [maxUnits] UTF-16 units. */
    fun string(maxUnits: Int): StreamCodec<ByteBuf, String> = ByteBufCodecs.stringUtf8(maxUnits)

    /** An enum as one byte, by ordinal; an unknown ordinal fails to decode. */
    inline fun <reified E : Enum<E>> enumByte(): StreamCodec<ByteBuf, E> {
        val values = enumValues<E>()
        return StreamCodec.of(
            { buf, value -> buf.writeByte(value.ordinal) },
            { buf ->
                val ordinal = buf.readUnsignedByte().toInt()
                values.getOrNull(ordinal) ?: throw DecoderException("Unknown ${E::class.simpleName} $ordinal")
            },
        )
    }

    /** At most [max] elements. */
    fun <T> StreamCodec<ByteBuf, T>.list(max: Int): StreamCodec<ByteBuf, List<T>> = apply(ByteBufCodecs.list(max))

    /** A present flag, then the value (`ByteBufCodecs.optional`, read as a nullable value). */
    fun <T : Any> StreamCodec<ByteBuf, T>.nullable(): StreamCodec<ByteBuf, T?> =
        ByteBufCodecs.optional(this).map({ it.orElse(null) }, { Optional.ofNullable(it) })

    /** Writes [value] as [nullable] does, without handing a null to Minecraft's (non-null) codec methods. */
    fun <T : Any> writeNullable(buf: ByteBuf, codec: StreamCodec<ByteBuf, T>, value: T?) {
        buf.writeBoolean(value != null)
        if (value != null) codec.encode(buf, value)
    }

    fun <T : Any> readNullable(buf: ByteBuf, codec: StreamCodec<ByteBuf, T>): T? = if (buf.readBoolean()) codec.decode(buf) else null

    /** A codec written as two plain functions, for shapes with more fields than `StreamCodec.composite` takes. */
    fun <T : Any> codec(encode: (ByteBuf, T) -> Unit, decode: (ByteBuf) -> T): StreamCodec<ByteBuf, T> =
        StreamCodec.of({ buf, value -> encode(buf, value) }, { buf -> decode(buf) })
}
