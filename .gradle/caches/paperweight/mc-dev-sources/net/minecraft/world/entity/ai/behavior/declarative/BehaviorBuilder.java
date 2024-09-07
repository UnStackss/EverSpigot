package net.minecraft.world.entity.ai.behavior.declarative;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.datafixers.kinds.Const;
import com.mojang.datafixers.kinds.IdF;
import com.mojang.datafixers.kinds.K1;
import com.mojang.datafixers.kinds.OptionalBox;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Unit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class BehaviorBuilder<E extends LivingEntity, M> implements App<BehaviorBuilder.Mu<E>, M> {
    private final BehaviorBuilder.TriggerWithResult<E, M> trigger;

    public static <E extends LivingEntity, M> BehaviorBuilder<E, M> unbox(App<BehaviorBuilder.Mu<E>, M> app) {
        return (BehaviorBuilder<E, M>)app;
    }

    public static <E extends LivingEntity> BehaviorBuilder.Instance<E> instance() {
        return new BehaviorBuilder.Instance<>();
    }

    public static <E extends LivingEntity> OneShot<E> create(Function<BehaviorBuilder.Instance<E>, ? extends App<BehaviorBuilder.Mu<E>, Trigger<E>>> creator) {
        final BehaviorBuilder.TriggerWithResult<E, Trigger<E>> triggerWithResult = get((App<BehaviorBuilder.Mu<E>, Trigger<E>>)creator.apply(instance()));
        return new OneShot<E>() {
            @Override
            public boolean trigger(ServerLevel world, E entity, long time) {
                Trigger<E> trigger = triggerWithResult.tryTrigger(world, entity, time);
                return trigger != null && trigger.trigger(world, entity, time);
            }

            @Override
            public String debugString() {
                return "OneShot[" + triggerWithResult.debugString() + "]";
            }

            @Override
            public String toString() {
                return this.debugString();
            }
        };
    }

    public static <E extends LivingEntity> OneShot<E> sequence(Trigger<? super E> predicate, Trigger<? super E> task) {
        return create(context -> context.group(context.ifTriggered(predicate)).apply(context, unit -> task::trigger));
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate, OneShot<? super E> task) {
        return sequence(triggerIf(predicate), task);
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(Predicate<E> predicate) {
        return create(context -> context.point((world, entity, time) -> predicate.test(entity)));
    }

    public static <E extends LivingEntity> OneShot<E> triggerIf(BiPredicate<ServerLevel, E> predicate) {
        return create(context -> context.point((world, entity, time) -> predicate.test(world, entity)));
    }

    static <E extends LivingEntity, M> BehaviorBuilder.TriggerWithResult<E, M> get(App<BehaviorBuilder.Mu<E>, M> app) {
        return unbox(app).trigger;
    }

    BehaviorBuilder(BehaviorBuilder.TriggerWithResult<E, M> function) {
        this.trigger = function;
    }

    static <E extends LivingEntity, M> BehaviorBuilder<E, M> create(BehaviorBuilder.TriggerWithResult<E, M> function) {
        return new BehaviorBuilder<>(function);
    }

    static final class Constant<E extends LivingEntity, A> extends BehaviorBuilder<E, A> {
        Constant(A value) {
            this(value, () -> "C[" + value + "]");
        }

        Constant(A value, Supplier<String> nameSupplier) {
            super(new BehaviorBuilder.TriggerWithResult<E, A>() {
                @Override
                public A tryTrigger(ServerLevel world, E entity, long time) {
                    return value;
                }

                @Override
                public String debugString() {
                    return nameSupplier.get();
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    public static final class Instance<E extends LivingEntity> implements Applicative<BehaviorBuilder.Mu<E>, BehaviorBuilder.Instance.Mu<E>> {
        public <Value> Optional<Value> tryGet(MemoryAccessor<OptionalBox.Mu, Value> result) {
            return OptionalBox.unbox(result.value());
        }

        public <Value> Value get(MemoryAccessor<IdF.Mu, Value> result) {
            return IdF.get(result.value());
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<OptionalBox.Mu, Value>> registered(MemoryModuleType<Value> type) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Registered<>(type));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<IdF.Mu, Value>> present(MemoryModuleType<Value> type) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Present<>(type));
        }

        public <Value> BehaviorBuilder<E, MemoryAccessor<Const.Mu<Unit>, Value>> absent(MemoryModuleType<Value> type) {
            return new BehaviorBuilder.PureMemory<>(new MemoryCondition.Absent<>(type));
        }

        public BehaviorBuilder<E, Unit> ifTriggered(Trigger<? super E> runnable) {
            return new BehaviorBuilder.TriggerWrapper<>(runnable);
        }

        public <A> BehaviorBuilder<E, A> point(A object) {
            return new BehaviorBuilder.Constant<>(object);
        }

        public <A> BehaviorBuilder<E, A> point(Supplier<String> nameSupplier, A value) {
            return new BehaviorBuilder.Constant<>(value, nameSupplier);
        }

        public <A, R> Function<App<BehaviorBuilder.Mu<E>, A>, App<BehaviorBuilder.Mu<E>, R>> lift1(App<BehaviorBuilder.Mu<E>, Function<A, R>> app) {
            return app2 -> {
                final BehaviorBuilder.TriggerWithResult<E, A> triggerWithResult = BehaviorBuilder.get(app2);
                final BehaviorBuilder.TriggerWithResult<E, Function<A, R>> triggerWithResult2 = BehaviorBuilder.get(app);
                return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel world, E entity, long time) {
                        A object = (A)triggerWithResult.tryTrigger(world, entity, time);
                        if (object == null) {
                            return null;
                        } else {
                            Function<A, R> function = (Function<A, R>)triggerWithResult2.tryTrigger(world, entity, time);
                            return (R)(function == null ? null : function.apply(object));
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult2.debugString() + " * " + triggerWithResult.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                });
            };
        }

        public <T, R> BehaviorBuilder<E, R> map(Function<? super T, ? extends R> function, App<BehaviorBuilder.Mu<E>, T> app) {
            final BehaviorBuilder.TriggerWithResult<E, T> triggerWithResult = BehaviorBuilder.get(app);
            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel world, E entity, long time) {
                    T object = triggerWithResult.tryTrigger(world, entity, time);
                    return (R)(object == null ? null : function.apply(object));
                }

                @Override
                public String debugString() {
                    return triggerWithResult.debugString() + ".map[" + function + "]";
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }

        public <A, B, R> BehaviorBuilder<E, R> ap2(
            App<BehaviorBuilder.Mu<E>, BiFunction<A, B, R>> app, App<BehaviorBuilder.Mu<E>, A> app2, App<BehaviorBuilder.Mu<E>, B> app3
        ) {
            final BehaviorBuilder.TriggerWithResult<E, A> triggerWithResult = BehaviorBuilder.get(app2);
            final BehaviorBuilder.TriggerWithResult<E, B> triggerWithResult2 = BehaviorBuilder.get(app3);
            final BehaviorBuilder.TriggerWithResult<E, BiFunction<A, B, R>> triggerWithResult3 = BehaviorBuilder.get(app);
            return BehaviorBuilder.create(new BehaviorBuilder.TriggerWithResult<E, R>() {
                @Override
                public R tryTrigger(ServerLevel world, E entity, long time) {
                    A object = triggerWithResult.tryTrigger(world, entity, time);
                    if (object == null) {
                        return null;
                    } else {
                        B object2 = triggerWithResult2.tryTrigger(world, entity, time);
                        if (object2 == null) {
                            return null;
                        } else {
                            BiFunction<A, B, R> biFunction = triggerWithResult3.tryTrigger(world, entity, time);
                            return biFunction == null ? null : biFunction.apply(object, object2);
                        }
                    }
                }

                @Override
                public String debugString() {
                    return triggerWithResult3.debugString() + " * " + triggerWithResult.debugString() + " * " + triggerWithResult2.debugString();
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }

        public <T1, T2, T3, R> BehaviorBuilder<E, R> ap3(
            App<BehaviorBuilder.Mu<E>, Function3<T1, T2, T3, R>> app,
            App<BehaviorBuilder.Mu<E>, T1> app2,
            App<BehaviorBuilder.Mu<E>, T2> app3,
            App<BehaviorBuilder.Mu<E>, T3> app4
        ) {
            final BehaviorBuilder.TriggerWithResult<E, T1> triggerWithResult = BehaviorBuilder.get(app2);
            final BehaviorBuilder.TriggerWithResult<E, T2> triggerWithResult2 = BehaviorBuilder.get(app3);
            final BehaviorBuilder.TriggerWithResult<E, T3> triggerWithResult3 = BehaviorBuilder.get(app4);
            final BehaviorBuilder.TriggerWithResult<E, Function3<T1, T2, T3, R>> triggerWithResult4 = BehaviorBuilder.get(app);
            return BehaviorBuilder.create(
                new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel world, E entity, long time) {
                        T1 object = triggerWithResult.tryTrigger(world, entity, time);
                        if (object == null) {
                            return null;
                        } else {
                            T2 object2 = triggerWithResult2.tryTrigger(world, entity, time);
                            if (object2 == null) {
                                return null;
                            } else {
                                T3 object3 = triggerWithResult3.tryTrigger(world, entity, time);
                                if (object3 == null) {
                                    return null;
                                } else {
                                    Function3<T1, T2, T3, R> function3 = triggerWithResult4.tryTrigger(world, entity, time);
                                    return function3 == null ? null : function3.apply(object, object2, object3);
                                }
                            }
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult4.debugString()
                            + " * "
                            + triggerWithResult.debugString()
                            + " * "
                            + triggerWithResult2.debugString()
                            + " * "
                            + triggerWithResult3.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                }
            );
        }

        public <T1, T2, T3, T4, R> BehaviorBuilder<E, R> ap4(
            App<BehaviorBuilder.Mu<E>, Function4<T1, T2, T3, T4, R>> app,
            App<BehaviorBuilder.Mu<E>, T1> app2,
            App<BehaviorBuilder.Mu<E>, T2> app3,
            App<BehaviorBuilder.Mu<E>, T3> app4,
            App<BehaviorBuilder.Mu<E>, T4> app5
        ) {
            final BehaviorBuilder.TriggerWithResult<E, T1> triggerWithResult = BehaviorBuilder.get(app2);
            final BehaviorBuilder.TriggerWithResult<E, T2> triggerWithResult2 = BehaviorBuilder.get(app3);
            final BehaviorBuilder.TriggerWithResult<E, T3> triggerWithResult3 = BehaviorBuilder.get(app4);
            final BehaviorBuilder.TriggerWithResult<E, T4> triggerWithResult4 = BehaviorBuilder.get(app5);
            final BehaviorBuilder.TriggerWithResult<E, Function4<T1, T2, T3, T4, R>> triggerWithResult5 = BehaviorBuilder.get(app);
            return BehaviorBuilder.create(
                new BehaviorBuilder.TriggerWithResult<E, R>() {
                    @Override
                    public R tryTrigger(ServerLevel world, E entity, long time) {
                        T1 object = triggerWithResult.tryTrigger(world, entity, time);
                        if (object == null) {
                            return null;
                        } else {
                            T2 object2 = triggerWithResult2.tryTrigger(world, entity, time);
                            if (object2 == null) {
                                return null;
                            } else {
                                T3 object3 = triggerWithResult3.tryTrigger(world, entity, time);
                                if (object3 == null) {
                                    return null;
                                } else {
                                    T4 object4 = triggerWithResult4.tryTrigger(world, entity, time);
                                    if (object4 == null) {
                                        return null;
                                    } else {
                                        Function4<T1, T2, T3, T4, R> function4 = triggerWithResult5.tryTrigger(world, entity, time);
                                        return function4 == null ? null : function4.apply(object, object2, object3, object4);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public String debugString() {
                        return triggerWithResult5.debugString()
                            + " * "
                            + triggerWithResult.debugString()
                            + " * "
                            + triggerWithResult2.debugString()
                            + " * "
                            + triggerWithResult3.debugString()
                            + " * "
                            + triggerWithResult4.debugString();
                    }

                    @Override
                    public String toString() {
                        return this.debugString();
                    }
                }
            );
        }

        static final class Mu<E extends LivingEntity> implements Applicative.Mu {
            private Mu() {
            }
        }
    }

    public static final class Mu<E extends LivingEntity> implements K1 {
    }

    static final class PureMemory<E extends LivingEntity, F extends K1, Value> extends BehaviorBuilder<E, MemoryAccessor<F, Value>> {
        PureMemory(MemoryCondition<F, Value> query) {
            super(new BehaviorBuilder.TriggerWithResult<E, MemoryAccessor<F, Value>>() {
                @Override
                public MemoryAccessor<F, Value> tryTrigger(ServerLevel serverLevel, E livingEntity, long l) {
                    Brain<?> brain = livingEntity.getBrain();
                    Optional<Value> optional = brain.getMemoryInternal(query.memory());
                    return optional == null ? null : query.createAccessor(brain, optional);
                }

                @Override
                public String debugString() {
                    return "M[" + query + "]";
                }

                @Override
                public String toString() {
                    return this.debugString();
                }
            });
        }
    }

    interface TriggerWithResult<E extends LivingEntity, R> {
        @Nullable
        R tryTrigger(ServerLevel world, E entity, long time);

        String debugString();
    }

    static final class TriggerWrapper<E extends LivingEntity> extends BehaviorBuilder<E, Unit> {
        TriggerWrapper(Trigger<? super E> taskRunnable) {
            super(new BehaviorBuilder.TriggerWithResult<E, Unit>() {
                @Nullable
                @Override
                public Unit tryTrigger(ServerLevel serverLevel, E livingEntity, long l) {
                    return taskRunnable.trigger(serverLevel, livingEntity, l) ? Unit.INSTANCE : null;
                }

                @Override
                public String debugString() {
                    return "T[" + taskRunnable + "]";
                }
            });
        }
    }
}
