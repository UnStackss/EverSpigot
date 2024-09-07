package net.minecraft.world.level.gameevent;

import net.minecraft.core.Holder;
import net.minecraft.world.phys.Vec3;

public interface GameEventListenerRegistry {
    GameEventListenerRegistry NOOP = new GameEventListenerRegistry() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void register(GameEventListener listener) {
        }

        @Override
        public void unregister(GameEventListener listener) {
        }

        @Override
        public boolean visitInRangeListeners(Holder<GameEvent> event, Vec3 pos, GameEvent.Context emitter, GameEventListenerRegistry.ListenerVisitor callback) {
            return false;
        }
    };

    boolean isEmpty();

    void register(GameEventListener listener);

    void unregister(GameEventListener listener);

    boolean visitInRangeListeners(Holder<GameEvent> event, Vec3 pos, GameEvent.Context emitter, GameEventListenerRegistry.ListenerVisitor callback);

    @FunctionalInterface
    public interface ListenerVisitor {
        void visit(GameEventListener listener, Vec3 listenerPos);
    }
}
