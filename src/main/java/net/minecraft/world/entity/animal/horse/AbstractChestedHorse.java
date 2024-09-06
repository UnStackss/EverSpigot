package net.minecraft.world.entity.animal.horse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public abstract class AbstractChestedHorse extends AbstractHorse {
    private static final EntityDataAccessor<Boolean> DATA_ID_CHEST = SynchedEntityData.defineId(AbstractChestedHorse.class, EntityDataSerializers.BOOLEAN);
    private final EntityDimensions babyDimensions;

    protected AbstractChestedHorse(EntityType<? extends AbstractChestedHorse> type, Level world) {
        super(type, world);
        this.canGallop = false;
        this.babyDimensions = type.getDimensions()
            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, type.getHeight() - 0.15625F, 0.0F))
            .scale(0.5F);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double)generateMaxHealth(random::nextInt));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_CHEST, false);
    }

    public static AttributeSupplier.Builder createBaseChestedHorseAttributes() {
        return createBaseHorseAttributes().add(Attributes.MOVEMENT_SPEED, 0.175F).add(Attributes.JUMP_STRENGTH, 0.5);
    }

    public boolean hasChest() {
        return this.entityData.get(DATA_ID_CHEST);
    }

    public void setChest(boolean hasChest) {
        this.entityData.set(DATA_ID_CHEST, hasChest);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? this.babyDimensions : super.getDefaultDimensions(pose);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (this.hasChest()) {
            if (!this.level().isClientSide) {
                this.spawnAtLocation(Blocks.CHEST);
            }

            this.setChest(false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("ChestedHorse", this.hasChest());
        if (this.hasChest()) {
            ListTag listTag = new ListTag();

            for (int i = 1; i < this.inventory.getContainerSize(); i++) {
                ItemStack itemStack = this.inventory.getItem(i);
                if (!itemStack.isEmpty()) {
                    CompoundTag compoundTag = new CompoundTag();
                    compoundTag.putByte("Slot", (byte)(i - 1));
                    listTag.add(itemStack.save(this.registryAccess(), compoundTag));
                }
            }

            nbt.put("Items", listTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setChest(nbt.getBoolean("ChestedHorse"));
        this.createInventory();
        if (this.hasChest()) {
            ListTag listTag = nbt.getList("Items", 10);

            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag compoundTag = listTag.getCompound(i);
                int j = compoundTag.getByte("Slot") & 255;
                if (j < this.inventory.getContainerSize() - 1) {
                    this.inventory.setItem(j + 1, ItemStack.parse(this.registryAccess(), compoundTag).orElse(ItemStack.EMPTY));
                }
            }
        }

        this.syncSaddleToClients();
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return mappedIndex == 499 ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return AbstractChestedHorse.this.hasChest() ? new ItemStack(Items.CHEST) : ItemStack.EMPTY;
            }

            @Override
            public boolean set(ItemStack stack) {
                if (stack.isEmpty()) {
                    if (AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(false);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else if (stack.is(Items.CHEST)) {
                    if (!AbstractChestedHorse.this.hasChest()) {
                        AbstractChestedHorse.this.setChest(true);
                        AbstractChestedHorse.this.createInventory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        } : super.getSlot(mappedIndex);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean bl = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !bl) {
            ItemStack itemStack = player.getItemInHand(hand);
            if (!itemStack.isEmpty()) {
                if (this.isFood(itemStack)) {
                    return this.fedFood(player, itemStack);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }

                if (!this.hasChest() && itemStack.is(Items.CHEST)) {
                    this.equipChest(player, itemStack);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void equipChest(Player player, ItemStack chest) {
        this.setChest(true);
        this.playChestEquipsSound();
        chest.consume(1, player);
        this.createInventory();
    }

    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? 5 : 0;
    }
}
