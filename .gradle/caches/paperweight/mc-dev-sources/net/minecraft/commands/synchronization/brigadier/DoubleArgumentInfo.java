package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class DoubleArgumentInfo implements ArgumentTypeInfo<DoubleArgumentType, DoubleArgumentInfo.Template> {
    @Override
    public void serializeToNetwork(DoubleArgumentInfo.Template properties, FriendlyByteBuf buf) {
        boolean bl = properties.min != -Double.MAX_VALUE;
        boolean bl2 = properties.max != Double.MAX_VALUE;
        buf.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buf.writeDouble(properties.min);
        }

        if (bl2) {
            buf.writeDouble(properties.max);
        }
    }

    @Override
    public DoubleArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
        byte b = friendlyByteBuf.readByte();
        double d = ArgumentUtils.numberHasMin(b) ? friendlyByteBuf.readDouble() : -Double.MAX_VALUE;
        double e = ArgumentUtils.numberHasMax(b) ? friendlyByteBuf.readDouble() : Double.MAX_VALUE;
        return new DoubleArgumentInfo.Template(d, e);
    }

    @Override
    public void serializeToJson(DoubleArgumentInfo.Template properties, JsonObject json) {
        if (properties.min != -Double.MAX_VALUE) {
            json.addProperty("min", properties.min);
        }

        if (properties.max != Double.MAX_VALUE) {
            json.addProperty("max", properties.max);
        }
    }

    @Override
    public DoubleArgumentInfo.Template unpack(DoubleArgumentType argumentType) {
        return new DoubleArgumentInfo.Template(argumentType.getMinimum(), argumentType.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<DoubleArgumentType> {
        final double min;
        final double max;

        Template(final double min, final double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public DoubleArgumentType instantiate(CommandBuildContext commandBuildContext) {
            return DoubleArgumentType.doubleArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<DoubleArgumentType, ?> type() {
            return DoubleArgumentInfo.this;
        }
    }
}
