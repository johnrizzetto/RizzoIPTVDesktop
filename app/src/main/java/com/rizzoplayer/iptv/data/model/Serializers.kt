package com.rizzoplayer.iptv.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class IntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Int")
    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
    override fun deserialize(decoder: Decoder): Int {
        return decoder.decodeInt()
    }
}