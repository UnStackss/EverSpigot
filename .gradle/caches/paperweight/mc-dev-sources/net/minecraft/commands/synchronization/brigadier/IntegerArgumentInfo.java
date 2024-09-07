package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class IntegerArgumentInfo implements ArgumentTypeInfo<IntegerArgumentType, IntegerArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(IntegerArgumentInfo.Template properties, FriendlyByteBuf buf) {
        boolean bl = properties.min != Integer.MIN_VALUE;
        boolean bl2 = properties.max != Integer.MAX_VALUE;
        buf.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buf.writeInt(properties.min);
        }

        if (bl2) {
            buf.writeInt(properties.max);
        }
    }

    @Override
    public IntegerArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
        byte b = friendlyByteBuf.readByte();
        int i = ArgumentUtils.numberHasMin(b) ? friendlyByteBuf.readInt() : Integer.MIN_VALUE;
        int j = ArgumentUtils.numberHasMax(b) ? friendlyByteBuf.readInt() : Integer.MAX_VALUE;
        return new IntegerArgumentInfo.Template(i, j);
    }

    @Override
    public void serializeToJson(IntegerArgumentInfo.Template properties, JsonObject json) {
        if (properties.min != Integer.MIN_VALUE) {
            json.addProperty("min", properties.min);
        }

        if (properties.max != Integer.MAX_VALUE) {
            json.addProperty("max", properties.max);
        }
    }

    @Override
    public IntegerArgumentInfo.Template unpack(IntegerArgumentType argumentType) {
        return new IntegerArgumentInfo.Template(argumentType.getMinimum(), argumentType.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<IntegerArgumentType> {
        final int min;
        final int max;

        Template(final int min, final int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public IntegerArgumentType instantiate(CommandBuildContext commandBuildContext) {
            return IntegerArgumentType.integer(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<IntegerArgumentType, ?> type() {
            return IntegerArgumentInfo.this;
        }
    }
}
