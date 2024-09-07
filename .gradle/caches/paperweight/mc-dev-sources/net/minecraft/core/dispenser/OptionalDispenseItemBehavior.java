package net.minecraft.core.dispenser;

public abstract class OptionalDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private boolean success = true;

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    protected void playSound(BlockSource pointer) {
        pointer.level().levelEvent(this.isSuccess() ? 1000 : 1001, pointer.pos(), 0);
    }
}
