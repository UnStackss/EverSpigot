package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class WoodlandMansionPieces {
    public static void generateMansion(
        StructureTemplateManager manager, BlockPos pos, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, RandomSource random
    ) {
        WoodlandMansionPieces.MansionGrid mansionGrid = new WoodlandMansionPieces.MansionGrid(random);
        WoodlandMansionPieces.MansionPiecePlacer mansionPiecePlacer = new WoodlandMansionPieces.MansionPiecePlacer(manager, random);
        mansionPiecePlacer.createMansion(pos, rotation, pieces, mansionGrid);
    }

    static class FirstFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource random) {
            return "1x1_a" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean staircase) {
            return "1x2_a" + (random.nextInt(9) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean staircase) {
            return "1x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            return "1x2_s" + (random.nextInt(2) + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            return "2x2_a" + (random.nextInt(4) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    abstract static class FloorRoomCollection {
        public abstract String get1x1(RandomSource random);

        public abstract String get1x1Secret(RandomSource random);

        public abstract String get1x2SideEntrance(RandomSource random, boolean staircase);

        public abstract String get1x2FrontEntrance(RandomSource random, boolean staircase);

        public abstract String get1x2Secret(RandomSource random);

        public abstract String get2x2(RandomSource random);

        public abstract String get2x2Secret(RandomSource random);
    }

    static class MansionGrid {
        private static final int DEFAULT_SIZE = 11;
        private static final int CLEAR = 0;
        private static final int CORRIDOR = 1;
        private static final int ROOM = 2;
        private static final int START_ROOM = 3;
        private static final int TEST_ROOM = 4;
        private static final int BLOCKED = 5;
        private static final int ROOM_1x1 = 65536;
        private static final int ROOM_1x2 = 131072;
        private static final int ROOM_2x2 = 262144;
        private static final int ROOM_ORIGIN_FLAG = 1048576;
        private static final int ROOM_DOOR_FLAG = 2097152;
        private static final int ROOM_STAIRS_FLAG = 4194304;
        private static final int ROOM_CORRIDOR_FLAG = 8388608;
        private static final int ROOM_TYPE_MASK = 983040;
        private static final int ROOM_ID_MASK = 65535;
        private final RandomSource random;
        final WoodlandMansionPieces.SimpleGrid baseGrid;
        final WoodlandMansionPieces.SimpleGrid thirdFloorGrid;
        final WoodlandMansionPieces.SimpleGrid[] floorRooms;
        final int entranceX;
        final int entranceY;

        public MansionGrid(RandomSource random) {
            this.random = random;
            int i = 11;
            this.entranceX = 7;
            this.entranceY = 4;
            this.baseGrid = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.baseGrid.set(this.entranceX, this.entranceY, this.entranceX + 1, this.entranceY + 1, 3);
            this.baseGrid.set(this.entranceX - 1, this.entranceY, this.entranceX - 1, this.entranceY + 1, 2);
            this.baseGrid.set(this.entranceX + 2, this.entranceY - 2, this.entranceX + 3, this.entranceY + 3, 5);
            this.baseGrid.set(this.entranceX + 1, this.entranceY - 2, this.entranceX + 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX + 1, this.entranceY + 2, this.entranceX + 1, this.entranceY + 3, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY + 2, 1);
            this.baseGrid.set(0, 0, 11, 1, 5);
            this.baseGrid.set(0, 9, 11, 11, 5);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY - 2, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY + 3, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY - 1, Direction.WEST, 3);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY + 2, Direction.WEST, 3);

            while (this.cleanEdges(this.baseGrid)) {
            }

            this.floorRooms = new WoodlandMansionPieces.SimpleGrid[3];
            this.floorRooms[0] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[1] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[2] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.identifyRooms(this.baseGrid, this.floorRooms[0]);
            this.identifyRooms(this.baseGrid, this.floorRooms[1]);
            this.floorRooms[0].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.floorRooms[1].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.thirdFloorGrid = new WoodlandMansionPieces.SimpleGrid(this.baseGrid.width, this.baseGrid.height, 5);
            this.setupThirdFloor();
            this.identifyRooms(this.thirdFloorGrid, this.floorRooms[2]);
        }

        public static boolean isHouse(WoodlandMansionPieces.SimpleGrid layout, int i, int j) {
            int k = layout.get(i, j);
            return k == 1 || k == 2 || k == 3 || k == 4;
        }

        public boolean isRoomId(WoodlandMansionPieces.SimpleGrid layout, int i, int j, int floor, int roomId) {
            return (this.floorRooms[floor].get(i, j) & 65535) == roomId;
        }

        @Nullable
        public Direction get1x2RoomDirection(WoodlandMansionPieces.SimpleGrid layout, int i, int j, int floor, int roomId) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isRoomId(layout, i + direction.getStepX(), j + direction.getStepZ(), floor, roomId)) {
                    return direction;
                }
            }

            return null;
        }

        private void recursiveCorridor(WoodlandMansionPieces.SimpleGrid layout, int i, int j, Direction direction, int length) {
            if (length > 0) {
                layout.set(i, j, 1);
                layout.setif(i + direction.getStepX(), j + direction.getStepZ(), 0, 1);

                for (int k = 0; k < 8; k++) {
                    Direction direction2 = Direction.from2DDataValue(this.random.nextInt(4));
                    if (direction2 != direction.getOpposite() && (direction2 != Direction.EAST || !this.random.nextBoolean())) {
                        int l = i + direction.getStepX();
                        int m = j + direction.getStepZ();
                        if (layout.get(l + direction2.getStepX(), m + direction2.getStepZ()) == 0
                            && layout.get(l + direction2.getStepX() * 2, m + direction2.getStepZ() * 2) == 0) {
                            this.recursiveCorridor(
                                layout,
                                i + direction.getStepX() + direction2.getStepX(),
                                j + direction.getStepZ() + direction2.getStepZ(),
                                direction2,
                                length - 1
                            );
                            break;
                        }
                    }
                }

                Direction direction3 = direction.getClockWise();
                Direction direction4 = direction.getCounterClockWise();
                layout.setif(i + direction3.getStepX(), j + direction3.getStepZ(), 0, 2);
                layout.setif(i + direction4.getStepX(), j + direction4.getStepZ(), 0, 2);
                layout.setif(i + direction.getStepX() + direction3.getStepX(), j + direction.getStepZ() + direction3.getStepZ(), 0, 2);
                layout.setif(i + direction.getStepX() + direction4.getStepX(), j + direction.getStepZ() + direction4.getStepZ(), 0, 2);
                layout.setif(i + direction.getStepX() * 2, j + direction.getStepZ() * 2, 0, 2);
                layout.setif(i + direction3.getStepX() * 2, j + direction3.getStepZ() * 2, 0, 2);
                layout.setif(i + direction4.getStepX() * 2, j + direction4.getStepZ() * 2, 0, 2);
            }
        }

        private boolean cleanEdges(WoodlandMansionPieces.SimpleGrid layout) {
            boolean bl = false;

            for (int i = 0; i < layout.height; i++) {
                for (int j = 0; j < layout.width; j++) {
                    if (layout.get(j, i) == 0) {
                        int k = 0;
                        k += isHouse(layout, j + 1, i) ? 1 : 0;
                        k += isHouse(layout, j - 1, i) ? 1 : 0;
                        k += isHouse(layout, j, i + 1) ? 1 : 0;
                        k += isHouse(layout, j, i - 1) ? 1 : 0;
                        if (k >= 3) {
                            layout.set(j, i, 2);
                            bl = true;
                        } else if (k == 2) {
                            int l = 0;
                            l += isHouse(layout, j + 1, i + 1) ? 1 : 0;
                            l += isHouse(layout, j - 1, i + 1) ? 1 : 0;
                            l += isHouse(layout, j + 1, i - 1) ? 1 : 0;
                            l += isHouse(layout, j - 1, i - 1) ? 1 : 0;
                            if (l <= 1) {
                                layout.set(j, i, 2);
                                bl = true;
                            }
                        }
                    }
                }
            }

            return bl;
        }

        private void setupThirdFloor() {
            List<Tuple<Integer, Integer>> list = Lists.newArrayList();
            WoodlandMansionPieces.SimpleGrid simpleGrid = this.floorRooms[1];

            for (int i = 0; i < this.thirdFloorGrid.height; i++) {
                for (int j = 0; j < this.thirdFloorGrid.width; j++) {
                    int k = simpleGrid.get(j, i);
                    int l = k & 983040;
                    if (l == 131072 && (k & 2097152) == 2097152) {
                        list.add(new Tuple<>(j, i));
                    }
                }
            }

            if (list.isEmpty()) {
                this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
            } else {
                Tuple<Integer, Integer> tuple = list.get(this.random.nextInt(list.size()));
                int m = simpleGrid.get(tuple.getA(), tuple.getB());
                simpleGrid.set(tuple.getA(), tuple.getB(), m | 4194304);
                Direction direction = this.get1x2RoomDirection(this.baseGrid, tuple.getA(), tuple.getB(), 1, m & 65535);
                int n = tuple.getA() + direction.getStepX();
                int o = tuple.getB() + direction.getStepZ();

                for (int p = 0; p < this.thirdFloorGrid.height; p++) {
                    for (int q = 0; q < this.thirdFloorGrid.width; q++) {
                        if (!isHouse(this.baseGrid, q, p)) {
                            this.thirdFloorGrid.set(q, p, 5);
                        } else if (q == tuple.getA() && p == tuple.getB()) {
                            this.thirdFloorGrid.set(q, p, 3);
                        } else if (q == n && p == o) {
                            this.thirdFloorGrid.set(q, p, 3);
                            this.floorRooms[2].set(q, p, 8388608);
                        }
                    }
                }

                List<Direction> list2 = Lists.newArrayList();

                for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                    if (this.thirdFloorGrid.get(n + direction2.getStepX(), o + direction2.getStepZ()) == 0) {
                        list2.add(direction2);
                    }
                }

                if (list2.isEmpty()) {
                    this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
                    simpleGrid.set(tuple.getA(), tuple.getB(), m);
                } else {
                    Direction direction3 = list2.get(this.random.nextInt(list2.size()));
                    this.recursiveCorridor(this.thirdFloorGrid, n + direction3.getStepX(), o + direction3.getStepZ(), direction3, 4);

                    while (this.cleanEdges(this.thirdFloorGrid)) {
                    }
                }
            }
        }

        private void identifyRooms(WoodlandMansionPieces.SimpleGrid layout, WoodlandMansionPieces.SimpleGrid roomFlags) {
            ObjectArrayList<Tuple<Integer, Integer>> objectArrayList = new ObjectArrayList<>();

            for (int i = 0; i < layout.height; i++) {
                for (int j = 0; j < layout.width; j++) {
                    if (layout.get(j, i) == 2) {
                        objectArrayList.add(new Tuple<>(j, i));
                    }
                }
            }

            Util.shuffle(objectArrayList, this.random);
            int k = 10;

            for (Tuple<Integer, Integer> tuple : objectArrayList) {
                int l = tuple.getA();
                int m = tuple.getB();
                if (roomFlags.get(l, m) == 0) {
                    int n = l;
                    int o = l;
                    int p = m;
                    int q = m;
                    int r = 65536;
                    if (roomFlags.get(l + 1, m) == 0
                        && roomFlags.get(l, m + 1) == 0
                        && roomFlags.get(l + 1, m + 1) == 0
                        && layout.get(l + 1, m) == 2
                        && layout.get(l, m + 1) == 2
                        && layout.get(l + 1, m + 1) == 2) {
                        o = l + 1;
                        q = m + 1;
                        r = 262144;
                    } else if (roomFlags.get(l - 1, m) == 0
                        && roomFlags.get(l, m + 1) == 0
                        && roomFlags.get(l - 1, m + 1) == 0
                        && layout.get(l - 1, m) == 2
                        && layout.get(l, m + 1) == 2
                        && layout.get(l - 1, m + 1) == 2) {
                        n = l - 1;
                        q = m + 1;
                        r = 262144;
                    } else if (roomFlags.get(l - 1, m) == 0
                        && roomFlags.get(l, m - 1) == 0
                        && roomFlags.get(l - 1, m - 1) == 0
                        && layout.get(l - 1, m) == 2
                        && layout.get(l, m - 1) == 2
                        && layout.get(l - 1, m - 1) == 2) {
                        n = l - 1;
                        p = m - 1;
                        r = 262144;
                    } else if (roomFlags.get(l + 1, m) == 0 && layout.get(l + 1, m) == 2) {
                        o = l + 1;
                        r = 131072;
                    } else if (roomFlags.get(l, m + 1) == 0 && layout.get(l, m + 1) == 2) {
                        q = m + 1;
                        r = 131072;
                    } else if (roomFlags.get(l - 1, m) == 0 && layout.get(l - 1, m) == 2) {
                        n = l - 1;
                        r = 131072;
                    } else if (roomFlags.get(l, m - 1) == 0 && layout.get(l, m - 1) == 2) {
                        p = m - 1;
                        r = 131072;
                    }

                    int s = this.random.nextBoolean() ? n : o;
                    int t = this.random.nextBoolean() ? p : q;
                    int u = 2097152;
                    if (!layout.edgesTo(s, t, 1)) {
                        s = s == n ? o : n;
                        t = t == p ? q : p;
                        if (!layout.edgesTo(s, t, 1)) {
                            t = t == p ? q : p;
                            if (!layout.edgesTo(s, t, 1)) {
                                s = s == n ? o : n;
                                t = t == p ? q : p;
                                if (!layout.edgesTo(s, t, 1)) {
                                    u = 0;
                                    s = n;
                                    t = p;
                                }
                            }
                        }
                    }

                    for (int v = p; v <= q; v++) {
                        for (int w = n; w <= o; w++) {
                            if (w == s && v == t) {
                                roomFlags.set(w, v, 1048576 | u | r | k);
                            } else {
                                roomFlags.set(w, v, r | k);
                            }
                        }
                    }

                    k++;
                }
            }
        }
    }

    static class MansionPiecePlacer {
        private final StructureTemplateManager structureTemplateManager;
        private final RandomSource random;
        private int startX;
        private int startY;

        public MansionPiecePlacer(StructureTemplateManager manager, RandomSource random) {
            this.structureTemplateManager = manager;
            this.random = random;
        }

        public void createMansion(
            BlockPos pos, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.MansionGrid parameters
        ) {
            WoodlandMansionPieces.PlacementData placementData = new WoodlandMansionPieces.PlacementData();
            placementData.position = pos;
            placementData.rotation = rotation;
            placementData.wallType = "wall_flat";
            WoodlandMansionPieces.PlacementData placementData2 = new WoodlandMansionPieces.PlacementData();
            this.entrance(pieces, placementData);
            placementData2.position = placementData.position.above(8);
            placementData2.rotation = placementData.rotation;
            placementData2.wallType = "wall_window";
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.SimpleGrid simpleGrid = parameters.baseGrid;
            WoodlandMansionPieces.SimpleGrid simpleGrid2 = parameters.thirdFloorGrid;
            this.startX = parameters.entranceX + 1;
            this.startY = parameters.entranceY + 1;
            int i = parameters.entranceX + 1;
            int j = parameters.entranceY;
            this.traverseOuterWalls(pieces, placementData, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, j);
            this.traverseOuterWalls(pieces, placementData2, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, j);
            WoodlandMansionPieces.PlacementData placementData3 = new WoodlandMansionPieces.PlacementData();
            placementData3.position = placementData.position.above(19);
            placementData3.rotation = placementData.rotation;
            placementData3.wallType = "wall_window";
            boolean bl = false;

            for (int k = 0; k < simpleGrid2.height && !bl; k++) {
                for (int l = simpleGrid2.width - 1; l >= 0 && !bl; l--) {
                    if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid2, l, k)) {
                        placementData3.position = placementData3.position.relative(rotation.rotate(Direction.SOUTH), 8 + (k - this.startY) * 8);
                        placementData3.position = placementData3.position.relative(rotation.rotate(Direction.EAST), (l - this.startX) * 8);
                        this.traverseWallPiece(pieces, placementData3);
                        this.traverseOuterWalls(pieces, placementData3, simpleGrid2, Direction.SOUTH, l, k, l, k);
                        bl = true;
                    }
                }
            }

            this.createRoof(pieces, pos.above(16), rotation, simpleGrid, simpleGrid2);
            this.createRoof(pieces, pos.above(27), rotation, simpleGrid2, null);
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.FloorRoomCollection[] floorRoomCollections = new WoodlandMansionPieces.FloorRoomCollection[]{
                new WoodlandMansionPieces.FirstFloorRoomCollection(),
                new WoodlandMansionPieces.SecondFloorRoomCollection(),
                new WoodlandMansionPieces.ThirdFloorRoomCollection()
            };

            for (int m = 0; m < 3; m++) {
                BlockPos blockPos = pos.above(8 * m + (m == 2 ? 3 : 0));
                WoodlandMansionPieces.SimpleGrid simpleGrid3 = parameters.floorRooms[m];
                WoodlandMansionPieces.SimpleGrid simpleGrid4 = m == 2 ? simpleGrid2 : simpleGrid;
                String string = m == 0 ? "carpet_south_1" : "carpet_south_2";
                String string2 = m == 0 ? "carpet_west_1" : "carpet_west_2";

                for (int n = 0; n < simpleGrid4.height; n++) {
                    for (int o = 0; o < simpleGrid4.width; o++) {
                        if (simpleGrid4.get(o, n) == 1) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (n - this.startY) * 8);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.EAST), (o - this.startX) * 8);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "corridor_floor", blockPos2, rotation));
                            if (simpleGrid4.get(o, n - 1) == 1 || (simpleGrid3.get(o, n - 1) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "carpet_north", blockPos2.relative(rotation.rotate(Direction.EAST), 1).above(), rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(o + 1, n) == 1 || (simpleGrid3.get(o + 1, n) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        "carpet_east",
                                        blockPos2.relative(rotation.rotate(Direction.SOUTH), 1).relative(rotation.rotate(Direction.EAST), 5).above(),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(o, n + 1) == 1 || (simpleGrid3.get(o, n + 1) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string,
                                        blockPos2.relative(rotation.rotate(Direction.SOUTH), 5).relative(rotation.rotate(Direction.WEST), 1),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(o - 1, n) == 1 || (simpleGrid3.get(o - 1, n) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string2,
                                        blockPos2.relative(rotation.rotate(Direction.WEST), 1).relative(rotation.rotate(Direction.NORTH), 1),
                                        rotation
                                    )
                                );
                            }
                        }
                    }
                }

                String string3 = m == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String string4 = m == 0 ? "indoors_door_1" : "indoors_door_2";
                List<Direction> list = Lists.newArrayList();

                for (int p = 0; p < simpleGrid4.height; p++) {
                    for (int q = 0; q < simpleGrid4.width; q++) {
                        boolean bl2 = m == 2 && simpleGrid4.get(q, p) == 3;
                        if (simpleGrid4.get(q, p) == 2 || bl2) {
                            int r = simpleGrid3.get(q, p);
                            int s = r & 983040;
                            int t = r & 65535;
                            bl2 = bl2 && (r & 8388608) == 8388608;
                            list.clear();
                            if ((r & 2097152) == 2097152) {
                                for (Direction direction : Direction.Plane.HORIZONTAL) {
                                    if (simpleGrid4.get(q + direction.getStepX(), p + direction.getStepZ()) == 1) {
                                        list.add(direction);
                                    }
                                }
                            }

                            Direction direction2 = null;
                            if (!list.isEmpty()) {
                                direction2 = list.get(this.random.nextInt(list.size()));
                            } else if ((r & 1048576) == 1048576) {
                                direction2 = Direction.UP;
                            }

                            BlockPos blockPos3 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (p - this.startY) * 8);
                            blockPos3 = blockPos3.relative(rotation.rotate(Direction.EAST), -1 + (q - this.startX) * 8);
                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid4, q - 1, p) && !parameters.isRoomId(simpleGrid4, q - 1, p, m, t)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction2 == Direction.WEST ? string4 : string3, blockPos3, rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(q + 1, p) == 1 && !bl2) {
                                BlockPos blockPos4 = blockPos3.relative(rotation.rotate(Direction.EAST), 8);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction2 == Direction.EAST ? string4 : string3, blockPos4, rotation
                                    )
                                );
                            }

                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid4, q, p + 1) && !parameters.isRoomId(simpleGrid4, q, p + 1, m, t)) {
                                BlockPos blockPos5 = blockPos3.relative(rotation.rotate(Direction.SOUTH), 7);
                                blockPos5 = blockPos5.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction2 == Direction.SOUTH ? string4 : string3,
                                        blockPos5,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (simpleGrid4.get(q, p - 1) == 1 && !bl2) {
                                BlockPos blockPos6 = blockPos3.relative(rotation.rotate(Direction.NORTH), 1);
                                blockPos6 = blockPos6.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction2 == Direction.NORTH ? string4 : string3,
                                        blockPos6,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (s == 65536) {
                                this.addRoom1x1(pieces, blockPos3, rotation, direction2, floorRoomCollections[m]);
                            } else if (s == 131072 && direction2 != null) {
                                Direction direction3 = parameters.get1x2RoomDirection(simpleGrid4, q, p, m, t);
                                boolean bl3 = (r & 4194304) == 4194304;
                                this.addRoom1x2(pieces, blockPos3, rotation, direction3, direction2, floorRoomCollections[m], bl3);
                            } else if (s == 262144 && direction2 != null && direction2 != Direction.UP) {
                                Direction direction4 = direction2.getClockWise();
                                if (!parameters.isRoomId(simpleGrid4, q + direction4.getStepX(), p + direction4.getStepZ(), m, t)) {
                                    direction4 = direction4.getOpposite();
                                }

                                this.addRoom2x2(pieces, blockPos3, rotation, direction4, direction2, floorRoomCollections[m]);
                            } else if (s == 262144 && direction2 == Direction.UP) {
                                this.addRoom2x2Secret(pieces, blockPos3, rotation, floorRoomCollections[m]);
                            }
                        }
                    }
                }
            }
        }

        private void traverseOuterWalls(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            WoodlandMansionPieces.PlacementData wallPiece,
            WoodlandMansionPieces.SimpleGrid layout,
            Direction direction,
            int startI,
            int startJ,
            int endI,
            int endJ
        ) {
            int i = startI;
            int j = startJ;
            Direction direction2 = direction;

            do {
                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), j + direction.getStepZ())) {
                    this.traverseTurn(pieces, wallPiece);
                    direction = direction.getClockWise();
                    if (i != endI || j != endJ || direction2 != direction) {
                        this.traverseWallPiece(pieces, wallPiece);
                    }
                } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), j + direction.getStepZ())
                    && WoodlandMansionPieces.MansionGrid.isHouse(
                        layout,
                        i + direction.getStepX() + direction.getCounterClockWise().getStepX(),
                        j + direction.getStepZ() + direction.getCounterClockWise().getStepZ()
                    )) {
                    this.traverseInnerTurn(pieces, wallPiece);
                    i += direction.getStepX();
                    j += direction.getStepZ();
                    direction = direction.getCounterClockWise();
                } else {
                    i += direction.getStepX();
                    j += direction.getStepZ();
                    if (i != endI || j != endJ || direction2 != direction) {
                        this.traverseWallPiece(pieces, wallPiece);
                    }
                }
            } while (i != endI || j != endJ || direction2 != direction);
        }

        private void createRoof(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            WoodlandMansionPieces.SimpleGrid layout,
            @Nullable WoodlandMansionPieces.SimpleGrid nextFloorLayout
        ) {
            for (int i = 0; i < layout.height; i++) {
                for (int j = 0; j < layout.width; j++) {
                    BlockPos blockPos15 = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                    blockPos15 = blockPos15.relative(rotation.rotate(Direction.EAST), (j - this.startX) * 8);
                    boolean bl = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, j, i);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i) && !bl) {
                        pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof", blockPos15.above(3), rotation));
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i)) {
                            BlockPos blockPos2 = blockPos15.relative(rotation.rotate(Direction.EAST), 6);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockPos2, rotation));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i)) {
                            BlockPos blockPos3 = blockPos15.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos3 = blockPos3.relative(rotation.rotate(Direction.SOUTH), 7);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos3, rotation.getRotated(Rotation.CLOCKWISE_180)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                            BlockPos blockPos4 = blockPos15.relative(rotation.rotate(Direction.WEST), 1);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos4, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                            BlockPos blockPos5 = blockPos15.relative(rotation.rotate(Direction.EAST), 6);
                            blockPos5 = blockPos5.relative(rotation.rotate(Direction.SOUTH), 6);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos5, rotation.getRotated(Rotation.CLOCKWISE_90)
                                )
                            );
                        }
                    }
                }
            }

            if (nextFloorLayout != null) {
                for (int k = 0; k < layout.height; k++) {
                    for (int l = 0; l < layout.width; l++) {
                        BlockPos var17 = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (k - this.startY) * 8);
                        var17 = var17.relative(rotation.rotate(Direction.EAST), (l - this.startX) * 8);
                        boolean bl2 = WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, l, k);
                        if (WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k) && bl2) {
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l + 1, k)) {
                                BlockPos blockPos7 = var17.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockPos7, rotation));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l - 1, k)) {
                                BlockPos blockPos8 = var17.relative(rotation.rotate(Direction.WEST), 1);
                                blockPos8 = blockPos8.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos8, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k - 1)) {
                                BlockPos blockPos9 = var17.relative(rotation.rotate(Direction.WEST), 0);
                                blockPos9 = blockPos9.relative(rotation.rotate(Direction.NORTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos9, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k + 1)) {
                                BlockPos blockPos10 = var17.relative(rotation.rotate(Direction.EAST), 6);
                                blockPos10 = blockPos10.relative(rotation.rotate(Direction.SOUTH), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos10, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l + 1, k)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k - 1)) {
                                    BlockPos blockPos11 = var17.relative(rotation.rotate(Direction.EAST), 7);
                                    blockPos11 = blockPos11.relative(rotation.rotate(Direction.NORTH), 2);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockPos11, rotation)
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k + 1)) {
                                    BlockPos blockPos12 = var17.relative(rotation.rotate(Direction.EAST), 8);
                                    blockPos12 = blockPos12.relative(rotation.rotate(Direction.SOUTH), 7);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos12, rotation.getRotated(Rotation.CLOCKWISE_90)
                                        )
                                    );
                                }
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l - 1, k)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k - 1)) {
                                    BlockPos blockPos13 = var17.relative(rotation.rotate(Direction.WEST), 2);
                                    blockPos13 = blockPos13.relative(rotation.rotate(Direction.NORTH), 1);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos13, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                        )
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, l, k + 1)) {
                                    BlockPos blockPos14 = var17.relative(rotation.rotate(Direction.WEST), 1);
                                    blockPos14 = blockPos14.relative(rotation.rotate(Direction.SOUTH), 8);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos14, rotation.getRotated(Rotation.CLOCKWISE_180)
                                        )
                                    );
                                }
                            }
                        }
                    }
                }
            }

            for (int m = 0; m < layout.height; m++) {
                for (int n = 0; n < layout.width; n++) {
                    BlockPos var19 = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (m - this.startY) * 8);
                    var19 = var19.relative(rotation.rotate(Direction.EAST), (n - this.startX) * 8);
                    boolean bl3 = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, n, m);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, n, m) && !bl3) {
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n + 1, m)) {
                            BlockPos blockPos16 = var19.relative(rotation.rotate(Direction.EAST), 6);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n, m + 1)) {
                                BlockPos blockPos17 = blockPos16.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockPos17, rotation));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, n + 1, m + 1)) {
                                BlockPos blockPos18 = blockPos16.relative(rotation.rotate(Direction.SOUTH), 5);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockPos18, rotation)
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n, m - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos16, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, n + 1, m - 1)) {
                                BlockPos blockPos19 = var19.relative(rotation.rotate(Direction.EAST), 9);
                                blockPos19 = blockPos19.relative(rotation.rotate(Direction.NORTH), 2);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos19, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n - 1, m)) {
                            BlockPos blockPos20 = var19.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos20 = blockPos20.relative(rotation.rotate(Direction.SOUTH), 0);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n, m + 1)) {
                                BlockPos blockPos21 = blockPos20.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos21, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, n - 1, m + 1)) {
                                BlockPos blockPos22 = blockPos20.relative(rotation.rotate(Direction.SOUTH), 8);
                                blockPos22 = blockPos22.relative(rotation.rotate(Direction.WEST), 3);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos22, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, n, m - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos20, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, n - 1, m - 1)) {
                                BlockPos blockPos23 = blockPos20.relative(rotation.rotate(Direction.SOUTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos23, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }
                        }
                    }
                }
            }
        }

        private void entrance(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData wallPiece) {
            Direction direction = wallPiece.rotation.rotate(Direction.WEST);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager, "entrance", wallPiece.position.relative(direction, 9), wallPiece.rotation
                )
            );
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.SOUTH), 16);
        }

        private void traverseWallPiece(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData wallPiece) {
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager,
                    wallPiece.wallType,
                    wallPiece.position.relative(wallPiece.rotation.rotate(Direction.EAST), 7),
                    wallPiece.rotation
                )
            );
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.SOUTH), 8);
        }

        private void traverseTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData wallPiece) {
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.SOUTH), -1);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "wall_corner", wallPiece.position, wallPiece.rotation));
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.SOUTH), -7);
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.WEST), -6);
            wallPiece.rotation = wallPiece.rotation.getRotated(Rotation.CLOCKWISE_90);
        }

        private void traverseInnerTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData wallPiece) {
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.SOUTH), 6);
            wallPiece.position = wallPiece.position.relative(wallPiece.rotation.rotate(Direction.EAST), 8);
            wallPiece.rotation = wallPiece.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        }

        private void addRoom1x1(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction direction,
            WoodlandMansionPieces.FloorRoomCollection pool
        ) {
            Rotation rotation2 = Rotation.NONE;
            String string = pool.get1x1(this.random);
            if (direction != Direction.EAST) {
                if (direction == Direction.NORTH) {
                    rotation2 = rotation2.getRotated(Rotation.COUNTERCLOCKWISE_90);
                } else if (direction == Direction.WEST) {
                    rotation2 = rotation2.getRotated(Rotation.CLOCKWISE_180);
                } else if (direction == Direction.SOUTH) {
                    rotation2 = rotation2.getRotated(Rotation.CLOCKWISE_90);
                } else {
                    string = pool.get1x1Secret(this.random);
                }
            }

            BlockPos blockPos = StructureTemplate.getZeroPositionWithTransform(new BlockPos(1, 0, 0), Mirror.NONE, rotation2, 7, 7);
            rotation2 = rotation2.getRotated(rotation);
            blockPos = blockPos.rotate(rotation);
            BlockPos blockPos2 = pos.offset(blockPos.getX(), 0, blockPos.getZ());
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, string, blockPos2, rotation2));
        }

        private void addRoom1x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction connectedRoomDirection,
            Direction entranceDirection,
            WoodlandMansionPieces.FloorRoomCollection pool,
            boolean staircase
        ) {
            if (entranceDirection == Direction.EAST && connectedRoomDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2SideEntrance(this.random, staircase), blockPos, rotation
                    )
                );
            } else if (entranceDirection == Direction.EAST && connectedRoomDirection == Direction.NORTH) {
                BlockPos blockPos2 = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2SideEntrance(this.random, staircase), blockPos2, rotation, Mirror.LEFT_RIGHT
                    )
                );
            } else if (entranceDirection == Direction.WEST && connectedRoomDirection == Direction.NORTH) {
                BlockPos blockPos3 = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos3 = blockPos3.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2SideEntrance(this.random, staircase), blockPos3, rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (entranceDirection == Direction.WEST && connectedRoomDirection == Direction.SOUTH) {
                BlockPos blockPos4 = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2SideEntrance(this.random, staircase), blockPos4, rotation, Mirror.FRONT_BACK
                    )
                );
            } else if (entranceDirection == Direction.SOUTH && connectedRoomDirection == Direction.EAST) {
                BlockPos blockPos5 = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        pool.get1x2SideEntrance(this.random, staircase),
                        blockPos5,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.LEFT_RIGHT
                    )
                );
            } else if (entranceDirection == Direction.SOUTH && connectedRoomDirection == Direction.WEST) {
                BlockPos blockPos6 = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2SideEntrance(this.random, staircase), blockPos6, rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (entranceDirection == Direction.NORTH && connectedRoomDirection == Direction.WEST) {
                BlockPos blockPos7 = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos7 = blockPos7.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        pool.get1x2SideEntrance(this.random, staircase),
                        blockPos7,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.FRONT_BACK
                    )
                );
            } else if (entranceDirection == Direction.NORTH && connectedRoomDirection == Direction.EAST) {
                BlockPos blockPos8 = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos8 = blockPos8.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        pool.get1x2SideEntrance(this.random, staircase),
                        blockPos8,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (entranceDirection == Direction.SOUTH && connectedRoomDirection == Direction.NORTH) {
                BlockPos blockPos9 = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos9 = blockPos9.relative(rotation.rotate(Direction.NORTH), 8);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2FrontEntrance(this.random, staircase), blockPos9, rotation
                    )
                );
            } else if (entranceDirection == Direction.NORTH && connectedRoomDirection == Direction.SOUTH) {
                BlockPos blockPos10 = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos10 = blockPos10.relative(rotation.rotate(Direction.SOUTH), 14);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        pool.get1x2FrontEntrance(this.random, staircase),
                        blockPos10,
                        rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (entranceDirection == Direction.WEST && connectedRoomDirection == Direction.EAST) {
                BlockPos blockPos11 = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2FrontEntrance(this.random, staircase), blockPos11, rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (entranceDirection == Direction.EAST && connectedRoomDirection == Direction.WEST) {
                BlockPos blockPos12 = pos.relative(rotation.rotate(Direction.WEST), 7);
                blockPos12 = blockPos12.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        pool.get1x2FrontEntrance(this.random, staircase),
                        blockPos12,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (entranceDirection == Direction.UP && connectedRoomDirection == Direction.EAST) {
                BlockPos blockPos13 = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, pool.get1x2Secret(this.random), blockPos13, rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (entranceDirection == Direction.UP && connectedRoomDirection == Direction.SOUTH) {
                BlockPos blockPos14 = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos14 = blockPos14.relative(rotation.rotate(Direction.NORTH), 0);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, pool.get1x2Secret(this.random), blockPos14, rotation));
            }
        }

        private void addRoom2x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction connectedRoomDirection,
            Direction entranceDirection,
            WoodlandMansionPieces.FloorRoomCollection pool
        ) {
            int i = 0;
            int j = 0;
            Rotation rotation2 = rotation;
            Mirror mirror = Mirror.NONE;
            if (entranceDirection == Direction.EAST && connectedRoomDirection == Direction.SOUTH) {
                i = -7;
            } else if (entranceDirection == Direction.EAST && connectedRoomDirection == Direction.NORTH) {
                i = -7;
                j = 6;
                mirror = Mirror.LEFT_RIGHT;
            } else if (entranceDirection == Direction.NORTH && connectedRoomDirection == Direction.EAST) {
                i = 1;
                j = 14;
                rotation2 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
            } else if (entranceDirection == Direction.NORTH && connectedRoomDirection == Direction.WEST) {
                i = 7;
                j = 14;
                rotation2 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (entranceDirection == Direction.SOUTH && connectedRoomDirection == Direction.WEST) {
                i = 7;
                j = -8;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_90);
            } else if (entranceDirection == Direction.SOUTH && connectedRoomDirection == Direction.EAST) {
                i = 1;
                j = -8;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (entranceDirection == Direction.WEST && connectedRoomDirection == Direction.NORTH) {
                i = 15;
                j = 6;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_180);
            } else if (entranceDirection == Direction.WEST && connectedRoomDirection == Direction.SOUTH) {
                i = 15;
                mirror = Mirror.FRONT_BACK;
            }

            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), i);
            blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), j);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, pool.get2x2(this.random), blockPos, rotation2, mirror));
        }

        private void addRoom2x2Secret(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos pos, Rotation rotation, WoodlandMansionPieces.FloorRoomCollection pool
        ) {
            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, pool.get2x2Secret(this.random), blockPos, rotation, Mirror.NONE)
            );
        }
    }

    static class PlacementData {
        public Rotation rotation;
        public BlockPos position;
        public String wallType;
    }

    static class SecondFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource random) {
            return "1x1_b" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean staircase) {
            return staircase ? "1x2_c_stairs" : "1x2_c" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean staircase) {
            return staircase ? "1x2_d_stairs" : "1x2_d" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            return "1x2_se" + (random.nextInt(1) + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            return "2x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    static class SimpleGrid {
        private final int[][] grid;
        final int width;
        final int height;
        private final int valueIfOutside;

        public SimpleGrid(int n, int m, int fallback) {
            this.width = n;
            this.height = m;
            this.valueIfOutside = fallback;
            this.grid = new int[n][m];
        }

        public void set(int i, int j, int value) {
            if (i >= 0 && i < this.width && j >= 0 && j < this.height) {
                this.grid[i][j] = value;
            }
        }

        public void set(int i0, int j0, int i1, int j1, int value) {
            for (int i = j0; i <= j1; i++) {
                for (int j = i0; j <= i1; j++) {
                    this.set(j, i, value);
                }
            }
        }

        public int get(int i, int j) {
            return i >= 0 && i < this.width && j >= 0 && j < this.height ? this.grid[i][j] : this.valueIfOutside;
        }

        public void setif(int i, int j, int expected, int newValue) {
            if (this.get(i, j) == expected) {
                this.set(i, j, newValue);
            }
        }

        public boolean edgesTo(int i, int j, int value) {
            return this.get(i - 1, j) == value || this.get(i + 1, j) == value || this.get(i, j + 1) == value || this.get(i, j - 1) == value;
        }
    }

    static class ThirdFloorRoomCollection extends WoodlandMansionPieces.SecondFloorRoomCollection {
    }

    public static class WoodlandMansionPiece extends TemplateStructurePiece {
        public WoodlandMansionPiece(StructureTemplateManager manager, String template, BlockPos pos, Rotation rotation) {
            this(manager, template, pos, rotation, Mirror.NONE);
        }

        public WoodlandMansionPiece(StructureTemplateManager manager, String template, BlockPos pos, Rotation rotation, Mirror mirror) {
            super(StructurePieceType.WOODLAND_MANSION_PIECE, 0, manager, makeLocation(template), template, makeSettings(mirror, rotation), pos);
        }

        public WoodlandMansionPiece(StructureTemplateManager manager, CompoundTag nbt) {
            super(
                StructurePieceType.WOODLAND_MANSION_PIECE,
                nbt,
                manager,
                id -> makeSettings(Mirror.valueOf(nbt.getString("Mi")), Rotation.valueOf(nbt.getString("Rot")))
            );
        }

        @Override
        protected ResourceLocation makeTemplateLocation() {
            return makeLocation(this.templateName);
        }

        private static ResourceLocation makeLocation(String identifier) {
            return ResourceLocation.withDefaultNamespace("woodland_mansion/" + identifier);
        }

        private static StructurePlaceSettings makeSettings(Mirror mirror, Rotation rotation) {
            return new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setRotation(rotation)
                .setMirror(mirror)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putString("Rot", this.placeSettings.getRotation().name());
            nbt.putString("Mi", this.placeSettings.getMirror().name());
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
            if (metadata.startsWith("Chest")) {
                Rotation rotation = this.placeSettings.getRotation();
                BlockState blockState = Blocks.CHEST.defaultBlockState();
                if ("ChestWest".equals(metadata)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.WEST));
                } else if ("ChestEast".equals(metadata)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.EAST));
                } else if ("ChestSouth".equals(metadata)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.SOUTH));
                } else if ("ChestNorth".equals(metadata)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.NORTH));
                }

                this.createChest(world, boundingBox, random, pos, BuiltInLootTables.WOODLAND_MANSION, blockState);
            } else {
                List<Mob> list = new ArrayList<>();
                switch (metadata) {
                    case "Mage":
                        list.add(EntityType.EVOKER.create(world.getLevel()));
                        break;
                    case "Warrior":
                        list.add(EntityType.VINDICATOR.create(world.getLevel()));
                        break;
                    case "Group of Allays":
                        int i = world.getRandom().nextInt(3) + 1;

                        for (int j = 0; j < i; j++) {
                            list.add(EntityType.ALLAY.create(world.getLevel()));
                        }
                        break;
                    default:
                        return;
                }

                for (Mob mob : list) {
                    if (mob != null) {
                        mob.setPersistenceRequired();
                        mob.moveTo(pos, 0.0F, 0.0F);
                        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null);
                        world.addFreshEntityWithPassengers(mob);
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
