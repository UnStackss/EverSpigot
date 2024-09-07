package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(value, buf);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V value) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return value;
            }

            @Override
            public void encode(B buf, V value) {
                if (!value.equals(value)) {
                    throw new IllegalStateException("Can't encode '" + value + "', expected '" + value + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> function) {
        return function.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        return new StreamCodec<B, O>() {
            @Override
            public O decode(B buf) {
                return (O)to.apply(StreamCodec.this.decode(buf));
            }

            @Override
            public void encode(B buf, O value) {
                StreamCodec.this.encode(buf, (V)from.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> function) {
        return new StreamCodec<O, V>() {
            @Override
            public V decode(O byteBuf) {
                B object = (B)function.apply(byteBuf);
                return StreamCodec.this.decode(object);
            }

            @Override
            public void encode(O byteBuf, V object) {
                B object2 = (B)function.apply(byteBuf);
                StreamCodec.this.encode(object2, object);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(Function<? super U, ? extends V> type, Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codec) {
        return new StreamCodec<B, U>() {
            @Override
            public U decode(B buf) {
                V object = StreamCodec.this.decode(buf);
                StreamCodec<? super B, ? extends U> streamCodec = (StreamCodec<? super B, ? extends U>)codec.apply(object);
                return (U)streamCodec.decode(buf);
            }

            @Override
            public void encode(B buf, U value) {
                V object = (V)type.apply(value);
                StreamCodec<B, U> streamCodec = (StreamCodec<B, U>)codec.apply(object);
                StreamCodec.this.encode(buf, object);
                streamCodec.encode(buf, value);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec, Function<C, T1> from, Function<T1, C> to) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec.decode(buf);
                return to.apply(object);
            }

            @Override
            public void encode(B buf, C value) {
                codec.encode(buf, from.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1, Function<C, T1> from1, StreamCodec<? super B, T2> codec2, Function<C, T2> from2, BiFunction<T1, T2, C> to
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                return to.apply(object, object2);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, from1.apply(value));
                codec2.encode(buf, from2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> from1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> from2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> from3,
        Function3<T1, T2, T3, C> to
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                T3 object3 = codec3.decode(buf);
                return to.apply(object, object2, object3);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, from1.apply(value));
                codec2.encode(buf, from2.apply(value));
                codec3.encode(buf, from3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> from1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> from2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> from3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> from4,
        Function4<T1, T2, T3, T4, C> to
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                T3 object3 = codec3.decode(buf);
                T4 object4 = codec4.decode(buf);
                return to.apply(object, object2, object3, object4);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, from1.apply(value));
                codec2.encode(buf, from2.apply(value));
                codec3.encode(buf, from3.apply(value));
                codec4.encode(buf, from4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> from1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> from2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> from3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> from4,
        StreamCodec<? super B, T5> codec5,
        Function<C, T5> from5,
        Function5<T1, T2, T3, T4, T5, C> to
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                T3 object3 = codec3.decode(buf);
                T4 object4 = codec4.decode(buf);
                T5 object5 = codec5.decode(buf);
                return to.apply(object, object2, object3, object4, object5);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, from1.apply(value));
                codec2.encode(buf, from2.apply(value));
                codec3.encode(buf, from3.apply(value));
                codec4.encode(buf, from4.apply(value));
                codec5.encode(buf, from5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> codec1,
        Function<C, T1> from1,
        StreamCodec<? super B, T2> codec2,
        Function<C, T2> from2,
        StreamCodec<? super B, T3> codec3,
        Function<C, T3> from3,
        StreamCodec<? super B, T4> codec4,
        Function<C, T4> from4,
        StreamCodec<? super B, T5> codec5,
        Function<C, T5> from5,
        StreamCodec<? super B, T6> codec6,
        Function<C, T6> from6,
        Function6<T1, T2, T3, T4, T5, T6, C> to
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buf) {
                T1 object = codec1.decode(buf);
                T2 object2 = codec2.decode(buf);
                T3 object3 = codec3.decode(buf);
                T4 object4 = codec4.decode(buf);
                T5 object5 = codec5.decode(buf);
                T6 object6 = codec6.decode(buf);
                return to.apply(object, object2, object3, object4, object5, object6);
            }

            @Override
            public void encode(B buf, C value) {
                codec1.encode(buf, from1.apply(value));
                codec2.encode(buf, from2.apply(value));
                codec3.encode(buf, from3.apply(value));
                codec4.encode(buf, from4.apply(value));
                codec5.encode(buf, from5.apply(value));
                codec6.encode(buf, from6.apply(value));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(UnaryOperator<StreamCodec<B, T>> codecGetter) {
        return new StreamCodec<B, T>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> codecGetter.apply(this));

            @Override
            public T decode(B buf) {
                return this.inner.get().decode(buf);
            }

            @Override
            public void encode(B buf, T value) {
                this.inner.get().encode(buf, value);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return this;
    }

    @FunctionalInterface
    public interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}
