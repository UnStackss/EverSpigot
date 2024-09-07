package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class FloatArgumentInfo implements ArgumentTypeInfo<FloatArgumentType, FloatArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(FloatArgumentInfo.Template properties, FriendlyByteBuf buf) {
        boolean bl = properties.min != -Float.MAX_VALUE;
        boolean bl2 = properties.max != Float.MAX_VALUE;
        buf.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buf.writeFloat(properties.min);
        }

        if (bl2) {
            buf.writeFloat(properties.max);
        }
    }

    @Override
    public FloatArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
        byte b = friendlyByteBuf.readByte();
        float f = ArgumentUtils.numberHasMin(b) ? friendlyByteBuf.readFloat() : -Float.MAX_VALUE;
        float g = ArgumentUtils.numberHasMax(b) ? friendlyByteBuf.readFloat() : Float.MAX_VALUE;
        return new FloatArgumentInfo.Template(f, g);
    }

    @Override
    public void serializeToJson(FloatArgumentInfo.Template properties, JsonObject json) {
        if (properties.min != -Float.MAX_VALUE) {
            json.addProperty("min", properties.min);
        }

        if (properties.max != Float.MAX_VALUE) {
            json.addProperty("max", properties.max);
        }
    }

    @Override
    public FloatArgumentInfo.Template unpack(FloatArgumentType argumentType) {
        return new FloatArgumentInfo.Template(argumentType.getMinimum(), argumentType.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<FloatArgumentType> {
        final float min;
        final float max;

        Template(final float min, final float max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public FloatArgumentType instantiate(CommandBuildContext commandBuildContext) {
            return FloatArgumentType.floatArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<FloatArgumentType, ?> type() {
            return FloatArgumentInfo.this;
        }
    }
}
