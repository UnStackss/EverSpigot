package net.minecraft.world.level.dimension.end;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public enum DragonRespawnAnimation {

    START {
        @Override
        public void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos) {
            BlockPos blockposition1 = new BlockPos(0, 128, 0);
            Iterator iterator = crystals.iterator();

            while (iterator.hasNext()) {
                EndCrystal entityendercrystal = (EndCrystal) iterator.next();

                entityendercrystal.setBeamTarget(blockposition1);
            }

            fight.setRespawnStage(PREPARING_TO_SUMMON_PILLARS); // CraftBukkit - decompile error
        }
    },
    PREPARING_TO_SUMMON_PILLARS {
        @Override
        public void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos) {
            if (tick < 100) {
                if (tick == 0 || tick == 50 || tick == 51 || tick == 52 || tick >= 95) {
                    world.levelEvent(3001, new BlockPos(0, 128, 0), 0);
                }
            } else {
                fight.setRespawnStage(SUMMONING_PILLARS); // CraftBukkit - decompile error
            }

        }
    },
    SUMMONING_PILLARS {
        @Override
        public void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos) {
            boolean flag = true;
            boolean flag1 = tick % 40 == 0;
            boolean flag2 = tick % 40 == 39;

            if (flag1 || flag2) {
                List<SpikeFeature.EndSpike> list1 = SpikeFeature.getSpikesForLevel(world);
                int j = tick / 40;

                if (j < list1.size()) {
                    SpikeFeature.EndSpike worldgenender_spike = (SpikeFeature.EndSpike) list1.get(j);

                    if (flag1) {
                        Iterator iterator = crystals.iterator();

                        while (iterator.hasNext()) {
                            EndCrystal entityendercrystal = (EndCrystal) iterator.next();

                            entityendercrystal.setBeamTarget(new BlockPos(worldgenender_spike.getCenterX(), worldgenender_spike.getHeight() + 1, worldgenender_spike.getCenterZ()));
                        }
                    } else {
                        boolean flag3 = true;
                        Iterator iterator1 = BlockPos.betweenClosed(new BlockPos(worldgenender_spike.getCenterX() - 10, worldgenender_spike.getHeight() - 10, worldgenender_spike.getCenterZ() - 10), new BlockPos(worldgenender_spike.getCenterX() + 10, worldgenender_spike.getHeight() + 10, worldgenender_spike.getCenterZ() + 10)).iterator();

                        while (iterator1.hasNext()) {
                            BlockPos blockposition1 = (BlockPos) iterator1.next();

                            world.removeBlock(blockposition1, false);
                        }

                        world.explode((Entity) null, (double) ((float) worldgenender_spike.getCenterX() + 0.5F), (double) worldgenender_spike.getHeight(), (double) ((float) worldgenender_spike.getCenterZ() + 0.5F), 5.0F, Level.ExplosionInteraction.BLOCK);
                        SpikeConfiguration worldgenfeatureendspikeconfiguration = new SpikeConfiguration(true, ImmutableList.of(worldgenender_spike), new BlockPos(0, 128, 0));

                        Feature.END_SPIKE.place(worldgenfeatureendspikeconfiguration, world, world.getChunkSource().getGenerator(), RandomSource.create(), new BlockPos(worldgenender_spike.getCenterX(), 45, worldgenender_spike.getCenterZ()));
                    }
                } else if (flag1) {
                    fight.setRespawnStage(SUMMONING_DRAGON); // CraftBukkit - decompile error
                }
            }

        }
    },
    SUMMONING_DRAGON {
        @Override
        public void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos) {
            Iterator iterator;
            EndCrystal entityendercrystal;

            if (tick >= 100) {
                fight.setRespawnStage(END); // CraftBukkit - decompile error
                fight.resetSpikeCrystals();
                iterator = crystals.iterator();

                while (iterator.hasNext()) {
                    entityendercrystal = (EndCrystal) iterator.next();
                    entityendercrystal.setBeamTarget((BlockPos) null);
                    world.explode(entityendercrystal, entityendercrystal.getX(), entityendercrystal.getY(), entityendercrystal.getZ(), 6.0F, Level.ExplosionInteraction.NONE);
                    entityendercrystal.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
                }
            } else if (tick >= 80) {
                world.levelEvent(3001, new BlockPos(0, 128, 0), 0);
            } else if (tick == 0) {
                iterator = crystals.iterator();

                while (iterator.hasNext()) {
                    entityendercrystal = (EndCrystal) iterator.next();
                    entityendercrystal.setBeamTarget(new BlockPos(0, 128, 0));
                }
            } else if (tick < 5) {
                world.levelEvent(3001, new BlockPos(0, 128, 0), 0);
            }

        }
    },
    END {
        @Override
        public void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos) {}
    };

    DragonRespawnAnimation() {}

    public abstract void tick(ServerLevel world, EndDragonFight fight, List<EndCrystal> crystals, int tick, BlockPos pos);
}
