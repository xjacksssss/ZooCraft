package com.zoocraft.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What the Zoo Designation Tool marks (Phase 2 spec section 6). */
public enum DesignationMode implements StringRepresentable {
    GATE,
    SHOP,
    TOILET;

    public static final Codec<DesignationMode> CODEC = StringRepresentable.fromEnum(DesignationMode::values);
    public static final StreamCodec<ByteBuf, DesignationMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], DesignationMode::ordinal);

    /** The mode {@code direction} steps along, wrapping around. */
    public DesignationMode next(int direction) {
        return values()[Math.floorMod(ordinal() + direction, values().length)];
    }

    public Component displayName() {
        return Component.translatable("zoocraft.mode." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
