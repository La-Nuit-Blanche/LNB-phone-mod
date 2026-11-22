package fr.bde_teknantes.lnb.phone.notifications

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import fr.bde_teknantes.lnb.phone.api.AppTarget
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.Optional

/** How a [PhoneNotification] is saved with the world and sent over the network. Common code. */
object NotificationCodecs {
    /** Saved form (NBT through registry ops, since components may refer to registries). */
    val CODEC: Codec<PhoneNotification> = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.LONG.fieldOf("id").forGetter(PhoneNotification::id),
            ResourceLocation.CODEC.fieldOf("app").forGetter(PhoneNotification::appId),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(PhoneNotification::title),
            ComponentSerialization.CODEC.fieldOf("text").forGetter(PhoneNotification::text),
            Codec.string(0, AppTarget.MAX_LENGTH).optionalFieldOf("target").forGetter { Optional.ofNullable(it.target) },
            Codec.LONG.fieldOf("time").forGetter(PhoneNotification::time),
            Codec.BOOL.optionalFieldOf("delivered", false).forGetter(PhoneNotification::delivered),
        ).apply(instance) { id, app, title, text, target, time, delivered ->
            PhoneNotification(id, app, title, text, target.orElse(null), time, delivered)
        }
    }

    /**
     * An app id on the wire, at most [NotificationLimits.MAX_APP_ID_LENGTH] characters. A longer or malformed one
     * fails decoding, which disconnects the sender: only a modified client sends one.
     */
    val APP_ID: StreamCodec<ByteBuf, ResourceLocation> = ByteBufCodecs.stringUtf8(NotificationLimits.MAX_APP_ID_LENGTH).map(
        { ResourceLocation.tryParse(it) ?: throw DecoderException("Malformed app id") },
        ResourceLocation::toString,
    )

    val TARGET: StreamCodec<ByteBuf, Optional<String>> = ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(AppTarget.MAX_LENGTH))

    val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PhoneNotification> = StreamCodec.of(
        { buf, n ->
            buf.writeVarLong(n.id)
            APP_ID.encode(buf, n.appId)
            ComponentSerialization.STREAM_CODEC.encode(buf, n.title)
            ComponentSerialization.STREAM_CODEC.encode(buf, n.text)
            TARGET.encode(buf, Optional.ofNullable(n.target))
            buf.writeLong(n.time)
            buf.writeBoolean(n.delivered)
        },
        { buf ->
            PhoneNotification(
                id = buf.readVarLong(),
                appId = APP_ID.decode(buf),
                title = ComponentSerialization.STREAM_CODEC.decode(buf),
                text = ComponentSerialization.STREAM_CODEC.decode(buf),
                target = TARGET.decode(buf).orElse(null),
                time = buf.readLong(),
                delivered = buf.readBoolean(),
            )
        },
    )
}
