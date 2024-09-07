package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;

public class UnconfiguredPipelineHandler {
    public static <T extends PacketListener> UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundProtocol(ProtocolInfo<T> newState) {
        return setupInboundHandler(new PacketDecoder<>(newState));
    }

    private static UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundHandler(ChannelInboundHandler newDecoder) {
        return context -> {
            context.pipeline().replace(context.name(), "decoder", newDecoder);
            context.channel().config().setAutoRead(true);
        };
    }

    public static <T extends PacketListener> UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundProtocol(ProtocolInfo<T> newState) {
        return setupOutboundHandler(new PacketEncoder<>(newState));
    }

    private static UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundHandler(ChannelOutboundHandler newEncoder) {
        return context -> context.pipeline().replace(context.name(), "encoder", newEncoder);
    }

    public static class Inbound extends ChannelDuplexHandler {
        public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) {
            if (!(object instanceof ByteBuf) && !(object instanceof Packet)) {
                channelHandlerContext.fireChannelRead(object);
            } else {
                ReferenceCountUtil.release(object);
                throw new DecoderException("Pipeline has no inbound protocol configured, can't process packet " + object);
            }
        }

        public void write(ChannelHandlerContext channelHandlerContext, Object object, ChannelPromise channelPromise) throws Exception {
            if (object instanceof UnconfiguredPipelineHandler.InboundConfigurationTask inboundConfigurationTask) {
                try {
                    inboundConfigurationTask.run(channelHandlerContext);
                } finally {
                    ReferenceCountUtil.release(object);
                }

                channelPromise.setSuccess();
            } else {
                channelHandlerContext.write(object, channelPromise);
            }
        }
    }

    @FunctionalInterface
    public interface InboundConfigurationTask {
        void run(ChannelHandlerContext context);

        default UnconfiguredPipelineHandler.InboundConfigurationTask andThen(UnconfiguredPipelineHandler.InboundConfigurationTask inboundConfigurationTask) {
            return context -> {
                this.run(context);
                inboundConfigurationTask.run(context);
            };
        }
    }

    public static class Outbound extends ChannelOutboundHandlerAdapter {
        public void write(ChannelHandlerContext channelHandlerContext, Object object, ChannelPromise channelPromise) throws Exception {
            if (object instanceof Packet) {
                ReferenceCountUtil.release(object);
                throw new EncoderException("Pipeline has no outbound protocol configured, can't process packet " + object);
            } else {
                if (object instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask outboundConfigurationTask) {
                    try {
                        outboundConfigurationTask.run(channelHandlerContext);
                    } finally {
                        ReferenceCountUtil.release(object);
                    }

                    channelPromise.setSuccess();
                } else {
                    channelHandlerContext.write(object, channelPromise);
                }
            }
        }
    }

    @FunctionalInterface
    public interface OutboundConfigurationTask {
        void run(ChannelHandlerContext context);

        default UnconfiguredPipelineHandler.OutboundConfigurationTask andThen(UnconfiguredPipelineHandler.OutboundConfigurationTask outboundConfigurationTask) {
            return context -> {
                this.run(context);
                outboundConfigurationTask.run(context);
            };
        }
    }
}
