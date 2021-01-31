package io.papermc.paper.util;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Provides optimised access to POI data. All returned values will be identical to vanilla.
 */
public final class PoiAccess {

    protected static double clamp(final double val, final double min, final double max) {
        return (val < min ? min : (val > max ? max : val));
    }

    protected static double getSmallestDistanceSquared(final double boxMinX, final double boxMinY, final double boxMinZ,
                                                       final double boxMaxX, final double boxMaxY, final double boxMaxZ,

                                                       final double circleX, final double circleY, final double circleZ) {
        // is the circle center inside the box?
        if (circleX >= boxMinX && circleX <= boxMaxX && circleY >= boxMinY && circleY <= boxMaxY && circleZ >= boxMinZ && circleZ <= boxMaxZ) {
            return 0.0;
        }

        final double boxWidthX = (boxMaxX - boxMinX) / 2.0;
        final double boxWidthY = (boxMaxY - boxMinY) / 2.0;
        final double boxWidthZ = (boxMaxZ - boxMinZ) / 2.0;

        final double boxCenterX = (boxMinX + boxMaxX) / 2.0;
        final double boxCenterY = (boxMinY + boxMaxY) / 2.0;
        final double boxCenterZ = (boxMinZ + boxMaxZ) / 2.0;

        double centerDiffX = circleX - boxCenterX;
        double centerDiffY = circleY - boxCenterY;
        double centerDiffZ = circleZ - boxCenterZ;

        centerDiffX = circleX - (clamp(centerDiffX, -boxWidthX, boxWidthX) + boxCenterX);
        centerDiffY = circleY - (clamp(centerDiffY, -boxWidthY, boxWidthY) + boxCenterY);
        centerDiffZ = circleZ - (clamp(centerDiffZ, -boxWidthZ, boxWidthZ) + boxCenterZ);

        return (centerDiffX * centerDiffX) + (centerDiffY * centerDiffY) + (centerDiffZ * centerDiffZ);
    }


    // key is:
    //  upper 32 bits:
    //   upper 16 bits: max y section
    //   lower 16 bits: min y section
    //  lower 32 bits:
    //   upper 16 bits: section
    //   lower 16 bits: radius
    protected static long getKey(final int minSection, final int maxSection, final int section, final int radius) {
        return (
                (maxSection & 0xFFFFL) << (64 - 16)
                | (minSection & 0xFFFFL) << (64 - 32)
                | (section & 0xFFFFL) << (64 - 48)
                | (radius & 0xFFFFL) << (64 - 64)
                );
    }

    // only includes x/z axis
    // finds the closest poi data by distance.
    public static BlockPos findClosestPoiDataPosition(final PoiManager poiStorage,
                                                      final Predicate<Holder<PoiType>> villagePlaceType,
                                                      // position predicate must not modify chunk POI
                                                      final Predicate<BlockPos> positionPredicate,
                                                      final BlockPos sourcePosition,
                                                      final int range, // distance on x y z axis
                                                      final double maxDistanceSquared,
                                                      final PoiManager.Occupancy occupancy,
                                                      final boolean load) {
        final PoiRecord ret = findClosestPoiDataRecord(
                poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load
        );

        return ret == null ? null : ret.getPos();
    }

    // only includes x/z axis
    // finds the closest poi data by distance.
    public static Pair<Holder<PoiType>, BlockPos> findClosestPoiDataTypeAndPosition(final PoiManager poiStorage,
                                                                             final Predicate<Holder<PoiType>> villagePlaceType,
                                                                             // position predicate must not modify chunk POI
                                                                             final Predicate<BlockPos> positionPredicate,
                                                                             final BlockPos sourcePosition,
                                                                             final int range, // distance on x y z axis
                                                                             final double maxDistanceSquared,
                                                                             final PoiManager.Occupancy occupancy,
                                                                             final boolean load) {
        final PoiRecord ret = findClosestPoiDataRecord(
            poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load
        );

        return ret == null ? null : Pair.of(ret.getPoiType(), ret.getPos());
    }

    // only includes x/z axis
    // finds the closest poi data by distance. if multiple match the same distance, then they all are returned.
    public static void findClosestPoiDataPositions(final PoiManager poiStorage,
                                                   final Predicate<Holder<PoiType>> villagePlaceType,
                                                   // position predicate must not modify chunk POI
                                                   final Predicate<BlockPos> positionPredicate,
                                                   final BlockPos sourcePosition,
                                                   final int range, // distance on x y z axis
                                                   final double maxDistanceSquared,
                                                   final PoiManager.Occupancy occupancy,
                                                   final boolean load,
                                                   final Set<BlockPos> ret) {
        final Set<BlockPos> positions = new HashSet<>();
        // pos predicate is last thing that runs before adding to ret.
        final Predicate<BlockPos> newPredicate = (final BlockPos pos) -> {
            if (positionPredicate != null && !positionPredicate.test(pos)) {
                return false;
            }
            return positions.add(pos.immutable());
        };

        final List<PoiRecord> toConvert = new ArrayList<>();
        findClosestPoiDataRecords(
                poiStorage, villagePlaceType, newPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load, toConvert
        );

        for (final PoiRecord record : toConvert) {
            ret.add(record.getPos());
        }
    }

    // only includes x/z axis
    // finds the closest poi data by distance.
    public static PoiRecord findClosestPoiDataRecord(final PoiManager poiStorage,
                                                     final Predicate<Holder<PoiType>> villagePlaceType,
                                                     // position predicate must not modify chunk POI
                                                     final Predicate<BlockPos> positionPredicate,
                                                     final BlockPos sourcePosition,
                                                     final int range, // distance on x y z axis
                                                     final double maxDistanceSquared,
                                                     final PoiManager.Occupancy occupancy,
                                                     final boolean load) {
        final List<PoiRecord> ret = new ArrayList<>();
        findClosestPoiDataRecords(
            poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load, ret
        );
        return ret.isEmpty() ? null : ret.get(0);
    }

    // only includes x/z axis
    // finds the closest poi data by distance.
    public static PoiRecord findClosestPoiDataRecord(final PoiManager poiStorage,
                                                     final Predicate<Holder<PoiType>> villagePlaceType,
                                                     // position predicate must not modify chunk POI
                                                     final BiPredicate<Holder<PoiType>, BlockPos> predicate,
                                                     final BlockPos sourcePosition,
                                                     final int range, // distance on x y z axis
                                                     final double maxDistanceSquared,
                                                     final PoiManager.Occupancy occupancy,
                                                     final boolean load) {
        final List<PoiRecord> ret = new ArrayList<>();
        findClosestPoiDataRecords(
                poiStorage, villagePlaceType, predicate, sourcePosition, range, maxDistanceSquared, occupancy, load, ret
        );
        return ret.isEmpty() ? null : ret.get(0);
    }

    // only includes x/z axis
    // finds the closest poi data by distance. if multiple match the same distance, then they all are returned.
    public static void findClosestPoiDataRecords(final PoiManager poiStorage,
                                                 final Predicate<Holder<PoiType>> villagePlaceType,
                                                 // position predicate must not modify chunk POI
                                                 final Predicate<BlockPos> positionPredicate,
                                                 final BlockPos sourcePosition,
                                                 final int range, // distance on x y z axis
                                                 final double maxDistanceSquared,
                                                 final PoiManager.Occupancy occupancy,
                                                 final boolean load,
                                                 final List<PoiRecord> ret) {
        final BiPredicate<Holder<PoiType>, BlockPos> predicate = positionPredicate != null ? (type, pos) -> positionPredicate.test(pos) : null;
        findClosestPoiDataRecords(poiStorage, villagePlaceType, predicate, sourcePosition, range, maxDistanceSquared, occupancy, load, ret);
    }

    public static void findClosestPoiDataRecords(final PoiManager poiStorage,
                                                 final Predicate<Holder<PoiType>> villagePlaceType,
                                                 // position predicate must not modify chunk POI
                                                 final BiPredicate<Holder<PoiType>, BlockPos> predicate,
                                                 final BlockPos sourcePosition,
                                                 final int range, // distance on x y z axis
                                                 final double maxDistanceSquared,
                                                 final PoiManager.Occupancy occupancy,
                                                 final boolean load,
                                                 final List<PoiRecord> ret) {
        final Predicate<? super PoiRecord> occupancyFilter = occupancy.getTest();

        final List<PoiRecord> closestRecords = new ArrayList<>();
        double closestDistanceSquared = maxDistanceSquared;

        final int lowerX = Mth.floor(sourcePosition.getX() - range) >> 4;
        final int lowerY = WorldUtil.getMinSection(poiStorage.moonrise$getWorld());
        final int lowerZ = Mth.floor(sourcePosition.getZ() - range) >> 4;
        final int upperX = Mth.floor(sourcePosition.getX() + range) >> 4;
        final int upperY = WorldUtil.getMaxSection(poiStorage.moonrise$getWorld());
        final int upperZ = Mth.floor(sourcePosition.getZ() + range) >> 4;

        final int centerX = sourcePosition.getX() >> 4;
        final int centerY = Mth.clamp(sourcePosition.getY() >> 4, lowerY, upperY);
        final int centerZ = sourcePosition.getZ() >> 4;
        final long centerKey = CoordinateUtils.getChunkSectionKey(centerX, centerY, centerZ);

        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(centerKey);
        queue.enqueue(centerKey);

        while (!queue.isEmpty()) {
            final long key = queue.dequeueLong();
            final int sectionX = CoordinateUtils.getChunkSectionX(key);
            final int sectionY = CoordinateUtils.getChunkSectionY(key);
            final int sectionZ = CoordinateUtils.getChunkSectionZ(key);

            if (sectionX < lowerX || sectionX > upperX || sectionY < lowerY || sectionY > upperY || sectionZ < lowerZ || sectionZ > upperZ) {
                // out of bound chunk
                continue;
            }

            final double sectionDistanceSquared = getSmallestDistanceSquared(
                    (sectionX << 4) + 0.5,
                    (sectionY << 4) + 0.5,
                    (sectionZ << 4) + 0.5,
                    (sectionX << 4) + 15.5,
                    (sectionY << 4) + 15.5,
                    (sectionZ << 4) + 15.5,
                    (double)sourcePosition.getX(), (double)sourcePosition.getY(), (double)sourcePosition.getZ()
            );
            if (sectionDistanceSquared > closestDistanceSquared) {
                continue;
            }

            // queue all neighbours
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        // -1 and 1 have the 1st bit set. so just add up the first bits, and it will tell us how many
                        // values are set. we only care about cardinal neighbours, so, we only care if one value is set
                        if ((dx & 1) + (dy & 1) + (dz & 1) != 1) {
                            continue;
                        }

                        final int neighbourX = sectionX + dx;
                        final int neighbourY = sectionY + dy;
                        final int neighbourZ = sectionZ + dz;

                        final long neighbourKey = CoordinateUtils.getChunkSectionKey(neighbourX, neighbourY, neighbourZ);
                        if (seen.add(neighbourKey)) {
                            queue.enqueue(neighbourKey);
                        }
                    }
                }
            }

            final Optional<PoiSection> poiSectionOptional = load ? poiStorage.getOrLoad(key) : poiStorage.get(key);

            if (poiSectionOptional == null || !poiSectionOptional.isPresent()) {
                continue;
            }

            final PoiSection poiSection = poiSectionOptional.get();

            final Map<Holder<PoiType>, Set<PoiRecord>> sectionData = poiSection.getData();
            if (sectionData.isEmpty()) {
                continue;
            }

            // now we search the section data
            for (final Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : sectionData.entrySet()) {
                if (!villagePlaceType.test(entry.getKey())) {
                    // filter out by poi type
                    continue;
                }

                // now we can look at the poi data
                for (final PoiRecord poiData : entry.getValue()) {
                    if (!occupancyFilter.test(poiData)) {
                        // filter by occupancy
                        continue;
                    }

                    final BlockPos poiPosition = poiData.getPos();

                    if (Math.abs(poiPosition.getX() - sourcePosition.getX()) > range
                            || Math.abs(poiPosition.getZ() - sourcePosition.getZ()) > range) {
                        // out of range for square radius
                        continue;
                    }

                    // it's important that it's poiPosition.distSqr(source) : the value actually is different IF the values are swapped!
                    final double dataRange = poiPosition.distSqr(sourcePosition);

                    if (dataRange > closestDistanceSquared) {
                        // out of range for distance check
                        continue;
                    }

                    if (predicate != null && !predicate.test(poiData.getPoiType(), poiPosition)) {
                        // filter by position
                        continue;
                    }

                    if (dataRange < closestDistanceSquared) {
                        closestRecords.clear();
                        closestDistanceSquared = dataRange;
                    }
                    closestRecords.add(poiData);
                }
            }
        }

        // uh oh! we might have multiple records that match the distance sorting!
        // we need to re-order our results by the way vanilla would have iterated over them.
        closestRecords.sort((record1, record2) -> {
            // vanilla iterates the same way we do for data inside sections, so we know the ordering inside a section
            // is fine and should be preserved (this sort is stable so we're good there)
            // but they iterate sections by x then by z (like the following)
            // for (int x = -dx; x <= dx; ++x)
            //     for (int z = -dz; z <= dz; ++z)
            //  ....
            // so we need to reorder such that records with lower chunk z, then lower chunk x come first
            final BlockPos pos1 = record1.getPos();
            final BlockPos pos2 = record2.getPos();

            final int cx1 = pos1.getX() >> 4;
            final int cz1 = pos1.getZ() >> 4;

            final int cx2 = pos2.getX() >> 4;
            final int cz2 = pos2.getZ() >> 4;

            if (cz2 != cz1) {
                // want smaller z
                return Integer.compare(cz1, cz2);
            }

            if (cx2 != cx1) {
                // want smaller x
                return Integer.compare(cx1, cx2);
            }

            // same chunk
            // once vanilla has the chunk, it will iterate from all of the chunk sections starting from smaller y
            // so now we just compare section y, wanting smaller y

            return Integer.compare(pos1.getY() >> 4, pos2.getY() >> 4);
        });

        // now we match perfectly what vanilla would have outputted, without having to search the whole radius (hopefully).
        ret.addAll(closestRecords);
    }

    // finds the closest poi entry pos.
    public static BlockPos findNearestPoiPosition(final PoiManager poiStorage,
                                                  final Predicate<Holder<PoiType>> villagePlaceType,
                                                  // position predicate must not modify chunk POI
                                                  final Predicate<BlockPos> positionPredicate,
                                                  final BlockPos sourcePosition,
                                                  final int range, // distance on x y z axis
                                                  final double maxDistanceSquared,
                                                  final PoiManager.Occupancy occupancy,
                                                  final boolean load) {
        final PoiRecord ret = findNearestPoiRecord(
                poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load
        );
        return ret == null ? null : ret.getPos();
    }

    // finds the closest `max` poi entry positions.
    public static void findNearestPoiPositions(final PoiManager poiStorage,
                                               final Predicate<Holder<PoiType>> villagePlaceType,
                                               // position predicate must not modify chunk POI
                                               final Predicate<BlockPos> positionPredicate,
                                               final BlockPos sourcePosition,
                                               final int range, // distance on x y z axis
                                               final double maxDistanceSquared,
                                               final PoiManager.Occupancy occupancy,
                                               final boolean load,
                                               final int max,
                                               final List<Pair<Holder<PoiType>, BlockPos>> ret) {
        final Set<BlockPos> positions = new HashSet<>();
        // pos predicate is last thing that runs before adding to ret.
        final Predicate<BlockPos> newPredicate = (final BlockPos pos) -> {
            if (positionPredicate != null && !positionPredicate.test(pos)) {
                return false;
            }
            return positions.add(pos.immutable());
        };

        final List<PoiRecord> toConvert = new ArrayList<>();
        findNearestPoiRecords(
                poiStorage, villagePlaceType, newPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load, max, toConvert
        );

        for (final PoiRecord record : toConvert) {
            ret.add(Pair.of(record.getPoiType(), record.getPos()));
        }
    }

    // finds the closest poi entry.
    public static PoiRecord findNearestPoiRecord(final PoiManager poiStorage,
                                                 final Predicate<Holder<PoiType>> villagePlaceType,
                                                 // position predicate must not modify chunk POI
                                                 final Predicate<BlockPos> positionPredicate,
                                                 final BlockPos sourcePosition,
                                                 final int range, // distance on x y z axis
                                                 final double maxDistanceSquared,
                                                 final PoiManager.Occupancy occupancy,
                                                 final boolean load) {
        final List<PoiRecord> ret = new ArrayList<>();
        findNearestPoiRecords(
                poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, maxDistanceSquared, occupancy, load,
                1, ret
        );
        return ret.isEmpty() ? null : ret.get(0);
    }

    // finds the closest `max` poi entries.
    public static void findNearestPoiRecords(final PoiManager poiStorage,
                                             final Predicate<Holder<PoiType>> villagePlaceType,
                                             // position predicate must not modify chunk POI
                                             final Predicate<BlockPos> positionPredicate,
                                             final BlockPos sourcePosition,
                                             final int range, // distance on x y z axis
                                             final double maxDistanceSquared,
                                             final PoiManager.Occupancy occupancy,
                                             final boolean load,
                                             final int max,
                                             final List<PoiRecord> ret) {
        final Predicate<? super PoiRecord> occupancyFilter = occupancy.getTest();

        final Double2ObjectRBTreeMap<List<PoiRecord>> closestRecords = new Double2ObjectRBTreeMap<>();
        int totalRecords = 0;
        double furthestDistanceSquared = maxDistanceSquared;

        final int lowerX = Mth.floor(sourcePosition.getX() - range) >> 4;
        final int lowerY = WorldUtil.getMinSection(poiStorage.moonrise$getWorld());
        final int lowerZ = Mth.floor(sourcePosition.getZ() - range) >> 4;
        final int upperX = Mth.floor(sourcePosition.getX() + range) >> 4;
        final int upperY = WorldUtil.getMaxSection(poiStorage.moonrise$getWorld());
        final int upperZ = Mth.floor(sourcePosition.getZ() + range) >> 4;

        final int centerX = sourcePosition.getX() >> 4;
        final int centerY = Mth.clamp(sourcePosition.getY() >> 4, lowerY, upperY);
        final int centerZ = sourcePosition.getZ() >> 4;
        final long centerKey = CoordinateUtils.getChunkSectionKey(centerX, centerY, centerZ);

        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(centerKey);
        queue.enqueue(centerKey);

        while (!queue.isEmpty()) {
            final long key = queue.dequeueLong();
            final int sectionX = CoordinateUtils.getChunkSectionX(key);
            final int sectionY = CoordinateUtils.getChunkSectionY(key);
            final int sectionZ = CoordinateUtils.getChunkSectionZ(key);

            if (sectionX < lowerX || sectionX > upperX || sectionY < lowerY || sectionY > upperY || sectionZ < lowerZ || sectionZ > upperZ) {
                // out of bound chunk
                continue;
            }

            final double sectionDistanceSquared = getSmallestDistanceSquared(
                    (sectionX << 4) + 0.5,
                    (sectionY << 4) + 0.5,
                    (sectionZ << 4) + 0.5,
                    (sectionX << 4) + 15.5,
                    (sectionY << 4) + 15.5,
                    (sectionZ << 4) + 15.5,
                    (double) sourcePosition.getX(), (double) sourcePosition.getY(), (double) sourcePosition.getZ()
            );

            if (sectionDistanceSquared > (totalRecords >= max ? furthestDistanceSquared : maxDistanceSquared)) {
                continue;
            }

            // queue all neighbours
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        // -1 and 1 have the 1st bit set. so just add up the first bits, and it will tell us how many
                        // values are set. we only care about cardinal neighbours, so, we only care if one value is set
                        if ((dx & 1) + (dy & 1) + (dz & 1) != 1) {
                            continue;
                        }

                        final int neighbourX = sectionX + dx;
                        final int neighbourY = sectionY + dy;
                        final int neighbourZ = sectionZ + dz;

                        final long neighbourKey = CoordinateUtils.getChunkSectionKey(neighbourX, neighbourY, neighbourZ);
                        if (seen.add(neighbourKey)) {
                            queue.enqueue(neighbourKey);
                        }
                    }
                }
            }

            final Optional<PoiSection> poiSectionOptional = load ? poiStorage.getOrLoad(key) : poiStorage.get(key);

            if (poiSectionOptional == null || !poiSectionOptional.isPresent()) {
                continue;
            }

            final PoiSection poiSection = poiSectionOptional.get();

            final Map<Holder<PoiType>, Set<PoiRecord>> sectionData = poiSection.getData();
            if (sectionData.isEmpty()) {
                continue;
            }

            // now we search the section data
            for (final Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : sectionData.entrySet()) {
                if (!villagePlaceType.test(entry.getKey())) {
                    // filter out by poi type
                    continue;
                }

                // now we can look at the poi data
                for (final PoiRecord poiData : entry.getValue()) {
                    if (!occupancyFilter.test(poiData)) {
                        // filter by occupancy
                        continue;
                    }

                    final BlockPos poiPosition = poiData.getPos();

                    if (Math.abs(poiPosition.getX() - sourcePosition.getX()) > range
                            || Math.abs(poiPosition.getZ() - sourcePosition.getZ()) > range) {
                        // out of range for square radius
                        continue;
                    }

                    // it's important that it's poiPosition.distSqr(source) : the value actually is different IF the values are swapped!
                    final double dataRange = poiPosition.distSqr(sourcePosition);

                    if (dataRange > maxDistanceSquared) {
                        // out of range for distance check
                        continue;
                    }

                    if (dataRange > furthestDistanceSquared && totalRecords >= max) {
                        // out of range for distance check
                        continue;
                    }

                    if (positionPredicate != null && !positionPredicate.test(poiPosition)) {
                        // filter by position
                        continue;
                    }

                    if (dataRange > furthestDistanceSquared) {
                        // we know totalRecords < max, so this entry is now our furthest
                        furthestDistanceSquared = dataRange;
                    }

                    closestRecords.computeIfAbsent(dataRange, (final double unused) -> {
                        return new ArrayList<>();
                    }).add(poiData);

                    if (++totalRecords >= max) {
                        if (closestRecords.size() >= 2) {
                            int entriesInClosest = 0;
                            final Iterator<Double2ObjectMap.Entry<List<PoiRecord>>> iterator = closestRecords.double2ObjectEntrySet().iterator();
                            double nextFurthestDistanceSquared = 0.0;

                            for (int i = 0, len = closestRecords.size() - 1; i < len; ++i) {
                                final Double2ObjectMap.Entry<List<PoiRecord>> recordEntry = iterator.next();
                                entriesInClosest += recordEntry.getValue().size();
                                nextFurthestDistanceSquared = recordEntry.getDoubleKey();
                            }

                            if (entriesInClosest >= max) {
                                // the last set of entries at range wont even be considered for sure... nuke em
                                final Double2ObjectMap.Entry<List<PoiRecord>> recordEntry = iterator.next();
                                totalRecords -= recordEntry.getValue().size();
                                iterator.remove();

                                furthestDistanceSquared = nextFurthestDistanceSquared;
                            }
                        }
                    }
                }
            }
        }

        final List<PoiRecord> closestRecordsUnsorted = new ArrayList<>();

        // we're done here, so now just flatten the map and sort it.

        for (final List<PoiRecord> records : closestRecords.values()) {
            closestRecordsUnsorted.addAll(records);
        }

        // uh oh! we might have multiple records that match the distance sorting!
        // we need to re-order our results by the way vanilla would have iterated over them.
        closestRecordsUnsorted.sort((record1, record2) -> {
            // vanilla iterates the same way we do for data inside sections, so we know the ordering inside a section
            // is fine and should be preserved (this sort is stable so we're good there)
            // but they iterate sections by x then by z (like the following)
            // for (int x = -dx; x <= dx; ++x)
            //     for (int z = -dz; z <= dz; ++z)
            //  ....
            // so we need to reorder such that records with lower chunk z, then lower chunk x come first
            final BlockPos pos1 = record1.getPos();
            final BlockPos pos2 = record2.getPos();

            final int cx1 = pos1.getX() >> 4;
            final int cz1 = pos1.getZ() >> 4;

            final int cx2 = pos2.getX() >> 4;
            final int cz2 = pos2.getZ() >> 4;

            if (cz2 != cz1) {
                // want smaller z
                return Integer.compare(cz1, cz2);
            }

            if (cx2 != cx1) {
                // want smaller x
                return Integer.compare(cx1, cx2);
            }

            // same chunk
            // once vanilla has the chunk, it will iterate from all of the chunk sections starting from smaller y
            // so now we just compare section y, wanting smaller section y

            return Integer.compare(pos1.getY() >> 4, pos2.getY() >> 4);
        });

        // trim out any entries exceeding our maximum
        for (int i = closestRecordsUnsorted.size() - 1; i >= max; --i) {
            closestRecordsUnsorted.remove(i);
        }

        // now we match perfectly what vanilla would have outputted, without having to search the whole radius (hopefully).
        ret.addAll(closestRecordsUnsorted);
    }

    public static BlockPos findAnyPoiPosition(final PoiManager poiStorage,
                                              final Predicate<Holder<PoiType>> villagePlaceType,
                                              final Predicate<BlockPos> positionPredicate,
                                              final BlockPos sourcePosition,
                                              final int range, // distance on x y z axis
                                              final PoiManager.Occupancy occupancy,
                                              final boolean load) {
        final PoiRecord ret = findAnyPoiRecord(
                poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, occupancy, load
        );

        return ret == null ? null : ret.getPos();
    }

    public static void findAnyPoiPositions(final PoiManager poiStorage,
                                           final Predicate<Holder<PoiType>> villagePlaceType,
                                           final Predicate<BlockPos> positionPredicate,
                                           final BlockPos sourcePosition,
                                           final int range, // distance on x y z axis
                                           final PoiManager.Occupancy occupancy,
                                           final boolean load,
                                           final int max,
                                           final List<Pair<Holder<PoiType>, BlockPos>> ret) {
        final Set<BlockPos> positions = new HashSet<>();
        // pos predicate is last thing that runs before adding to ret.
        final Predicate<BlockPos> newPredicate = (final BlockPos pos) -> {
            if (positionPredicate != null && !positionPredicate.test(pos)) {
                return false;
            }
            return positions.add(pos.immutable());
        };

        final List<PoiRecord> toConvert = new ArrayList<>();
        findAnyPoiRecords(
                poiStorage, villagePlaceType, newPredicate, sourcePosition, range, occupancy, load, max, toConvert
        );

        for (final PoiRecord record : toConvert) {
            ret.add(Pair.of(record.getPoiType(), record.getPos()));
        }
    }

    public static PoiRecord findAnyPoiRecord(final PoiManager poiStorage,
                                             final Predicate<Holder<PoiType>> villagePlaceType,
                                             final Predicate<BlockPos> positionPredicate,
                                             final BlockPos sourcePosition,
                                             final int range, // distance on x y z axis
                                             final PoiManager.Occupancy occupancy,
                                             final boolean load) {
        final List<PoiRecord> ret = new ArrayList<>();
        findAnyPoiRecords(poiStorage, villagePlaceType, positionPredicate, sourcePosition, range, occupancy, load, 1, ret);
        return ret.isEmpty() ? null : ret.get(0);
    }

    public static void findAnyPoiRecords(final PoiManager poiStorage,
                                         final Predicate<Holder<PoiType>> villagePlaceType,
                                         final Predicate<BlockPos> positionPredicate,
                                         final BlockPos sourcePosition,
                                         final int range, // distance on x y z axis
                                         final PoiManager.Occupancy occupancy,
                                         final boolean load,
                                         final int max,
                                         final List<PoiRecord> ret) {
        // the biggest issue with the original mojang implementation is that they chain so many streams together
        // the amount of streams chained just rolls performance, even if nothing is iterated over
        final Predicate<? super PoiRecord> occupancyFilter = occupancy.getTest();
        final double rangeSquared = range * range;

        int added = 0;

        // First up, we need to iterate the chunks
        // all the values here are in chunk sections
        final int lowerX = Mth.floor(sourcePosition.getX() - range) >> 4;
        final int lowerY = Math.max(WorldUtil.getMinSection(poiStorage.moonrise$getWorld()), Mth.floor(sourcePosition.getY() - range) >> 4);
        final int lowerZ = Mth.floor(sourcePosition.getZ() - range) >> 4;
        final int upperX = Mth.floor(sourcePosition.getX() + range) >> 4;
        final int upperY = Math.min(WorldUtil.getMaxSection(poiStorage.moonrise$getWorld()), Mth.floor(sourcePosition.getY() + range) >> 4);
        final int upperZ = Mth.floor(sourcePosition.getZ() + range) >> 4;

        // Vanilla iterates by x until max is reached then increases z
        // vanilla also searches by increasing Y section value
        for (int currZ = lowerZ; currZ <= upperZ; ++currZ) {
            for (int currX = lowerX; currX <= upperX; ++currX) {
                for (int currY = lowerY; currY <= upperY; ++currY) { // vanilla searches the entire chunk because they're actually stupid. just search the sections we need
                    final Optional<PoiSection> poiSectionOptional = load ? poiStorage.getOrLoad(CoordinateUtils.getChunkSectionKey(currX, currY, currZ)) :
                            poiStorage.get(CoordinateUtils.getChunkSectionKey(currX, currY, currZ));
                    final PoiSection poiSection = poiSectionOptional == null ? null : poiSectionOptional.orElse(null);
                    if (poiSection == null) {
                        continue;
                    }

                    final Map<Holder<PoiType>, Set<PoiRecord>> sectionData = poiSection.getData();
                    if (sectionData.isEmpty()) {
                        continue;
                    }

                    // now we search the section data
                    for (final Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : sectionData.entrySet()) {
                        if (!villagePlaceType.test(entry.getKey())) {
                            // filter out by poi type
                            continue;
                        }

                        // now we can look at the poi data
                        for (final PoiRecord poiData : entry.getValue()) {
                            if (!occupancyFilter.test(poiData)) {
                                // filter by occupancy
                                continue;
                            }

                            final BlockPos poiPosition = poiData.getPos();

                            if (Math.abs(poiPosition.getX() - sourcePosition.getX()) > range
                                    || Math.abs(poiPosition.getZ() - sourcePosition.getZ()) > range) {
                                // out of range for square radius
                                continue;
                            }

                            if (poiPosition.distSqr(sourcePosition) > rangeSquared) {
                                // out of range for distance check
                                continue;
                            }

                            if (positionPredicate != null && !positionPredicate.test(poiPosition)) {
                                // filter by position
                                continue;
                            }

                            // found one!
                            ret.add(poiData);
                            if (++added >= max) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private PoiAccess() {
        throw new RuntimeException();
    }
}
