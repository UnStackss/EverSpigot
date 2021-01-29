package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.EnchantingTable;
import org.bukkit.craftbukkit.util.CraftChatMessage;

public class CraftEnchantingTable extends CraftBlockEntityState<EnchantingTableBlockEntity> implements EnchantingTable {

    public CraftEnchantingTable(World world, EnchantingTableBlockEntity tileEntity) {
        super(world, tileEntity);
    }

    protected CraftEnchantingTable(CraftEnchantingTable state, Location location) {
        super(state, location);
    }

    // Paper start
    @Override
    public net.kyori.adventure.text.Component customName() {
        final EnchantingTableBlockEntity be = this.getSnapshot();
        return be.hasCustomName() ? io.papermc.paper.adventure.PaperAdventure.asAdventure(be.getCustomName()) : null;
    }

    @Override
    public void customName(final net.kyori.adventure.text.Component customName) {
        this.getSnapshot().setCustomName(customName != null ? io.papermc.paper.adventure.PaperAdventure.asVanilla(customName) : null);
    }
    // Paper end

    @Override
    public String getCustomName() {
        EnchantingTableBlockEntity enchant = this.getSnapshot();
        return enchant.hasCustomName() ? CraftChatMessage.fromComponent(enchant.getCustomName()) : null;
    }

    @Override
    public void setCustomName(String name) {
        this.getSnapshot().setCustomName(CraftChatMessage.fromStringOrNull(name));
    }

    @Override
    public void applyTo(EnchantingTableBlockEntity enchantingTable) {
        super.applyTo(enchantingTable);

        if (!this.getSnapshot().hasCustomName()) {
            enchantingTable.setCustomName(null);
        }
    }

    @Override
    public CraftEnchantingTable copy() {
        return new CraftEnchantingTable(this, null);
    }

    @Override
    public CraftEnchantingTable copy(Location location) {
        return new CraftEnchantingTable(this, location);
    }
}
