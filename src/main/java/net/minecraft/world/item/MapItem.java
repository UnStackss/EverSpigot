package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapItem extends ComplexItem {
    public static final int IMAGE_WIDTH = 128;
    public static final int IMAGE_HEIGHT = 128;

    public MapItem(Item.Properties settings) {
        super(settings);
    }

    public static ItemStack create(Level world, int x, int z, byte scale, boolean showIcons, boolean unlimitedTracking) {
        ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
        MapId mapId = createNewSavedData(world, x, z, scale, showIcons, unlimitedTracking, world.dimension());
        itemStack.set(DataComponents.MAP_ID, mapId);
        return itemStack;
    }

    @Nullable
    public static MapItemSavedData getSavedData(@Nullable MapId id, Level world) {
        return id == null ? null : world.getMapData(id);
    }

    @Nullable
    public static MapItemSavedData getSavedData(ItemStack map, Level world) {
        MapId mapId = map.get(DataComponents.MAP_ID);
        return getSavedData(mapId, world);
    }

    public static MapId createNewSavedData(Level world, int x, int z, int scale, boolean showIcons, boolean unlimitedTracking, ResourceKey<Level> dimension) {
        MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh((double)x, (double)z, (byte)scale, showIcons, unlimitedTracking, dimension);
        MapId mapId = world.getFreeMapId();
        world.setMapData(mapId, mapItemSavedData);
        return mapId;
    }

    public void update(Level world, Entity entity, MapItemSavedData state) {
        if (world.dimension() == state.dimension && entity instanceof Player) {
            int i = 1 << state.scale;
            int j = state.centerX;
            int k = state.centerZ;
            int l = Mth.floor(entity.getX() - (double)j) / i + 64;
            int m = Mth.floor(entity.getZ() - (double)k) / i + 64;
            int n = 128 / i;
            if (world.dimensionType().hasCeiling()) {
                n /= 2;
            }

            MapItemSavedData.HoldingPlayer holdingPlayer = state.getHoldingPlayer((Player)entity);
            holdingPlayer.step++;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
            boolean bl = false;

            for (int o = l - n + 1; o < l + n; o++) {
                if ((o & 15) == (holdingPlayer.step & 15) || bl) {
                    bl = false;
                    double d = 0.0;

                    for (int p = m - n - 1; p < m + n; p++) {
                        if (o >= 0 && p >= -1 && o < 128 && p < 128) {
                            int q = Mth.square(o - l) + Mth.square(p - m);
                            boolean bl2 = q > (n - 2) * (n - 2);
                            int r = (j / i + o - 64) * i;
                            int s = (k / i + p - 64) * i;
                            Multiset<MapColor> multiset = LinkedHashMultiset.create();
                            LevelChunk levelChunk = world.getChunk(SectionPos.blockToSectionCoord(r), SectionPos.blockToSectionCoord(s));
                            if (!levelChunk.isEmpty()) {
                                int t = 0;
                                double e = 0.0;
                                if (world.dimensionType().hasCeiling()) {
                                    int u = r + s * 231871;
                                    u = u * u * 31287121 + u * 11;
                                    if ((u >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(world, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.defaultBlockState().getMapColor(world, BlockPos.ZERO), 100);
                                    }

                                    e = 100.0;
                                } else {
                                    for (int v = 0; v < i; v++) {
                                        for (int w = 0; w < i; w++) {
                                            mutableBlockPos.set(r + v, 0, s + w);
                                            int x = levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos.getX(), mutableBlockPos.getZ()) + 1;
                                            BlockState blockState3;
                                            if (x <= world.getMinBuildHeight() + 1) {
                                                blockState3 = Blocks.BEDROCK.defaultBlockState();
                                            } else {
                                                do {
                                                    mutableBlockPos.setY(--x);
                                                    blockState3 = levelChunk.getBlockState(mutableBlockPos);
                                                } while (blockState3.getMapColor(world, mutableBlockPos) == MapColor.NONE && x > world.getMinBuildHeight());

                                                if (x > world.getMinBuildHeight() && !blockState3.getFluidState().isEmpty()) {
                                                    int y = x - 1;
                                                    mutableBlockPos2.set(mutableBlockPos);

                                                    BlockState blockState2;
                                                    do {
                                                        mutableBlockPos2.setY(y--);
                                                        blockState2 = levelChunk.getBlockState(mutableBlockPos2);
                                                        t++;
                                                    } while (y > world.getMinBuildHeight() && !blockState2.getFluidState().isEmpty());

                                                    blockState3 = this.getCorrectStateForFluidBlock(world, blockState3, mutableBlockPos);
                                                }
                                            }

                                            state.checkBanners(world, mutableBlockPos.getX(), mutableBlockPos.getZ());
                                            e += (double)x / (double)(i * i);
                                            multiset.add(blockState3.getMapColor(world, mutableBlockPos));
                                        }
                                    }
                                }

                                t /= i * i;
                                MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                                MapColor.Brightness brightness;
                                if (mapColor == MapColor.WATER) {
                                    double f = (double)t * 0.1 + (double)(o + p & 1) * 0.2;
                                    if (f < 0.5) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (f > 0.9) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                } else {
                                    double g = (e - d) * 4.0 / (double)(i + 4) + ((double)(o + p & 1) - 0.5) * 0.4;
                                    if (g > 0.6) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (g < -0.6) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                }

                                d = e;
                                if (p >= 0 && q < n * n && (!bl2 || (o + p & 1) != 0)) {
                                    bl |= state.updateColor(o, p, mapColor.getPackedId(brightness));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState getCorrectStateForFluidBlock(Level world, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(world, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    private static boolean isBiomeWatery(boolean[] biomes, int x, int z) {
        return biomes[z * 128 + x];
    }

    public static void renderBiomePreviewMap(ServerLevel world, ItemStack map) {
        MapItemSavedData mapItemSavedData = getSavedData(map, world);
        if (mapItemSavedData != null) {
            if (world.dimension() == mapItemSavedData.dimension) {
                int i = 1 << mapItemSavedData.scale;
                int j = mapItemSavedData.centerX;
                int k = mapItemSavedData.centerZ;
                boolean[] bls = new boolean[16384];
                int l = j / i - 64;
                int m = k / i - 64;
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int n = 0; n < 128; n++) {
                    for (int o = 0; o < 128; o++) {
                        Holder<Biome> holder = world.getBiome(mutableBlockPos.set((l + o) * i, 0, (m + n) * i));
                        bls[n * 128 + o] = holder.is(BiomeTags.WATER_ON_MAP_OUTLINES);
                    }
                }

                for (int p = 1; p < 127; p++) {
                    for (int q = 1; q < 127; q++) {
                        int r = 0;

                        for (int s = -1; s < 2; s++) {
                            for (int t = -1; t < 2; t++) {
                                if ((s != 0 || t != 0) && isBiomeWatery(bls, p + s, q + t)) {
                                    r++;
                                }
                            }
                        }

                        MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                        MapColor mapColor = MapColor.NONE;
                        if (isBiomeWatery(bls, p, q)) {
                            mapColor = MapColor.COLOR_ORANGE;
                            if (r > 7 && q % 2 == 0) {
                                switch ((p + (int)(Mth.sin((float)q + 0.0F) * 7.0F)) / 8 % 5) {
                                    case 0:
                                    case 4:
                                        brightness = MapColor.Brightness.LOW;
                                        break;
                                    case 1:
                                    case 3:
                                        brightness = MapColor.Brightness.NORMAL;
                                        break;
                                    case 2:
                                        brightness = MapColor.Brightness.HIGH;
                                }
                            } else if (r > 7) {
                                mapColor = MapColor.NONE;
                            } else if (r > 5) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else if (r > 3) {
                                brightness = MapColor.Brightness.LOW;
                            } else if (r > 1) {
                                brightness = MapColor.Brightness.LOW;
                            }
                        } else if (r > 0) {
                            mapColor = MapColor.COLOR_BROWN;
                            if (r > 3) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else {
                                brightness = MapColor.Brightness.LOWEST;
                            }
                        }

                        if (mapColor != MapColor.NONE) {
                            mapItemSavedData.setColor(p, q, mapColor.getPackedId(brightness));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (!world.isClientSide) {
            MapItemSavedData mapItemSavedData = getSavedData(stack, world);
            if (mapItemSavedData != null) {
                if (entity instanceof Player player) {
                    mapItemSavedData.tickCarriedBy(player, stack);
                }

                if (!mapItemSavedData.locked && (selected || entity instanceof Player && ((Player)entity).getOffhandItem() == stack)) {
                    this.update(world, entity, mapItemSavedData);
                }
            }
        }
    }

    @Nullable
    @Override
    public Packet<?> getUpdatePacket(ItemStack stack, Level world, Player player) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = getSavedData(mapId, world);
        return mapItemSavedData != null ? mapItemSavedData.getUpdatePacket(mapId, player) : null;
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level world) {
        MapPostProcessing mapPostProcessing = stack.remove(DataComponents.MAP_POST_PROCESSING);
        if (mapPostProcessing != null) {
            switch (mapPostProcessing) {
                case LOCK:
                    lockMap(world, stack);
                    break;
                case SCALE:
                    scaleMap(stack, world);
            }
        }
    }

    private static void scaleMap(ItemStack map, Level world) {
        MapItemSavedData mapItemSavedData = getSavedData(map, world);
        if (mapItemSavedData != null) {
            MapId mapId = world.getFreeMapId();
            world.setMapData(mapId, mapItemSavedData.scaled());
            map.set(DataComponents.MAP_ID, mapId);
        }
    }

    public static void lockMap(Level world, ItemStack stack) {
        MapItemSavedData mapItemSavedData = getSavedData(stack, world);
        if (mapItemSavedData != null) {
            MapId mapId = world.getFreeMapId();
            MapItemSavedData mapItemSavedData2 = mapItemSavedData.locked();
            world.setMapData(mapId, mapItemSavedData2);
            stack.set(DataComponents.MAP_ID, mapId);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = mapId != null ? context.mapData(mapId) : null;
        MapPostProcessing mapPostProcessing = stack.get(DataComponents.MAP_POST_PROCESSING);
        if (mapItemSavedData != null && (mapItemSavedData.locked || mapPostProcessing == MapPostProcessing.LOCK)) {
            tooltip.add(Component.translatable("filled_map.locked", mapId.id()).withStyle(ChatFormatting.GRAY));
        }

        if (type.isAdvanced()) {
            if (mapItemSavedData != null) {
                if (mapPostProcessing == null) {
                    tooltip.add(getTooltipForId(mapId));
                }

                int i = mapPostProcessing == MapPostProcessing.SCALE ? 1 : 0;
                int j = Math.min(mapItemSavedData.scale + i, 4);
                tooltip.add(Component.translatable("filled_map.scale", 1 << j).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("filled_map.level", j, 4).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static Component getTooltipForId(MapId id) {
        return Component.translatable("filled_map.id", id.id()).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(BlockTags.BANNERS)) {
            if (!context.getLevel().isClientSide) {
                MapItemSavedData mapItemSavedData = getSavedData(context.getItemInHand(), context.getLevel());
                if (mapItemSavedData != null && !mapItemSavedData.toggleBanner(context.getLevel(), context.getClickedPos())) {
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        } else {
            return super.useOn(context);
        }
    }
}
