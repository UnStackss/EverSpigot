package net.minecraft.world.level.block.entity.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public enum VaultState implements StringRepresentable {
    INACTIVE("inactive", VaultState.LightLevel.HALF_LIT) {
        @Override
        protected void onEnter(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
            sharedData.setDisplayItem(ItemStack.EMPTY);
            world.levelEvent(3016, pos, ominous ? 1 : 0);
        }
    },
    ACTIVE("active", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
            if (!sharedData.hasDisplayItem()) {
                VaultBlockEntity.Server.cycleDisplayItemFromLootTable(world, this, config, sharedData, pos);
            }

            world.levelEvent(3015, pos, ominous ? 1 : 0);
        }
    },
    UNLOCKING("unlocking", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
            world.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS);
        }
    },
    EJECTING("ejecting", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
            world.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS);
        }

        @Override
        protected void onExit(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData) {
            world.playSound(null, pos, SoundEvents.VAULT_CLOSE_SHUTTER, SoundSource.BLOCKS);
        }
    };

    private static final int UPDATE_CONNECTED_PLAYERS_TICK_RATE = 20;
    private static final int DELAY_BETWEEN_EJECTIONS_TICKS = 20;
    private static final int DELAY_AFTER_LAST_EJECTION_TICKS = 20;
    private static final int DELAY_BEFORE_FIRST_EJECTION_TICKS = 20;
    private final String stateName;
    private final VaultState.LightLevel lightLevel;

    VaultState(final String id, final VaultState.LightLevel light) {
        this.stateName = id;
        this.lightLevel = light;
    }

    @Override
    public String getSerializedName() {
        return this.stateName;
    }

    public int lightLevel() {
        return this.lightLevel.value;
    }

    public VaultState tickAndGetNext(ServerLevel world, BlockPos pos, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData) {
        return switch (this) {
            case INACTIVE -> updateStateForConnectedPlayers(world, pos, config, serverData, sharedData, config.activationRange());
            case ACTIVE -> updateStateForConnectedPlayers(world, pos, config, serverData, sharedData, config.deactivationRange());
            case UNLOCKING -> {
                serverData.pauseStateUpdatingUntil(world.getGameTime() + 20L);
                yield EJECTING;
            }
            case EJECTING -> {
                if (serverData.getItemsToEject().isEmpty()) {
                    serverData.markEjectionFinished();
                    yield updateStateForConnectedPlayers(world, pos, config, serverData, sharedData, config.deactivationRange());
                } else {
                    float f = serverData.ejectionProgress();
                    this.ejectResultItem(world, pos, serverData.popNextItemToEject(), f);
                    sharedData.setDisplayItem(serverData.getNextItemToEject());
                    boolean bl = serverData.getItemsToEject().isEmpty();
                    int i = bl ? 20 : 20;
                    serverData.pauseStateUpdatingUntil(world.getGameTime() + (long)i);
                    yield EJECTING;
                }
            }
        };
    }

    private static VaultState updateStateForConnectedPlayers(
        ServerLevel world, BlockPos pos, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, double radius
    ) {
        sharedData.updateConnectedPlayersWithinRange(world, pos, serverData, config, radius);
        serverData.pauseStateUpdatingUntil(world.getGameTime() + 20L);
        return sharedData.hasConnectedPlayers() ? ACTIVE : INACTIVE;
    }

    public void onTransition(ServerLevel world, BlockPos pos, VaultState newState, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
        this.onExit(world, pos, config, sharedData);
        newState.onEnter(world, pos, config, sharedData, ominous);
    }

    protected void onEnter(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData, boolean ominous) {
    }

    protected void onExit(ServerLevel world, BlockPos pos, VaultConfig config, VaultSharedData sharedData) {
    }

    private void ejectResultItem(ServerLevel world, BlockPos pos, ItemStack stack, float pitchModifier) {
        DefaultDispenseItemBehavior.spawnItem(world, stack, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2));
        world.levelEvent(3017, pos, 0);
        world.playSound(null, pos, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F + 0.4F * pitchModifier);
    }

    static enum LightLevel {
        HALF_LIT(6),
        LIT(12);

        final int value;

        private LightLevel(final int luminance) {
            this.value = luminance;
        }
    }
}
