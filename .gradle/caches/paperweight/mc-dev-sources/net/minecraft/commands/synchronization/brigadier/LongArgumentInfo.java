package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class LongArgumentInfo implements ArgumentTypeInfo<LongArgumentType, LongArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(LongArgumentInfo.Template properties, FriendlyByteBuf buf) {
        boolean bl = properties.min != Long.MIN_VALUE;
        boolean bl2 = properties.max != Long.MAX_VALUE;
        buf.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buf.writeLong(properties.min);
        }

        if (bl2) {
            buf.writeLong(properties.max);
        }
    }

    @Override
    public LongArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
        byte b = friendlyByteBuf.readByte();
        long l = ArgumentUtils.numberHasMin(b) ? friendlyByteBuf.readLong() : Long.MIN_VALUE;
        long m = ArgumentUtils.numberHasMax(b) ? friendlyByteBuf.readLong() : Long.MAX_VALUE;
        return new LongArgumentInfo.Template(l, m);
    }

    @Override
    public void serializeToJson(LongArgumentInfo.Template properties, JsonObject json) {
        if (properties.min != Long.MIN_VALUE) {
            json.addProperty("min", properties.min);
        }

        if (properties.max != Long.MAX_VALUE) {
            json.addProperty("max", properties.max);
        }
    }

    @Override
    public LongArgumentInfo.Template unpack(LongArgumentType argumentType) {
        return new LongArgumentInfo.Template(argumentType.getMinimum(), argumentType.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<LongArgumentType> {
        final long min;
        final long max;

        Template(final long min, final long max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public LongArgumentType instantiate(CommandBuildContext commandBuildContext) {
            return LongArgumentType.longArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<LongArgumentType, ?> type() {
            return LongArgumentInfo.this;
        }
    }
}
