package net.minecraft.commands;

@FunctionalInterface
public interface CommandResultCallback {
    CommandResultCallback EMPTY = new CommandResultCallback() {
        @Override
        public void onResult(boolean successful, int returnValue) {
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    };

    void onResult(boolean successful, int returnValue);

    default void onSuccess(int successful) {
        this.onResult(true, successful);
    }

    default void onFailure() {
        this.onResult(false, 0);
    }

    static CommandResultCallback chain(CommandResultCallback a, CommandResultCallback b) {
        if (a == EMPTY) {
            return b;
        } else {
            return b == EMPTY ? a : (successful, returnValue) -> {
                a.onResult(successful, returnValue);
                b.onResult(successful, returnValue);
            };
        }
    }
}
