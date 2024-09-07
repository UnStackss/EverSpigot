package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.util.datafix.PackedBitStorage;

public class LeavesFix extends DataFix {
    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    private static final int[][] DIRECTIONS = new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final int DECAY_DISTANCE = 7;
    private static final int SIZE_BITS = 12;
    private static final int SIZE = 4096;
    static final Object2IntMap<String> LEAVES = DataFixUtils.make(new Object2IntOpenHashMap<>(), map -> {
        map.put("minecraft:acacia_leaves", 0);
        map.put("minecraft:birch_leaves", 1);
        map.put("minecraft:dark_oak_leaves", 2);
        map.put("minecraft:jungle_leaves", 3);
        map.put("minecraft:oak_leaves", 4);
        map.put("minecraft:spruce_leaves", 5);
    });
    static final Set<String> LOGS = ImmutableSet.of(
        "minecraft:acacia_bark",
        "minecraft:birch_bark",
        "minecraft:dark_oak_bark",
        "minecraft:jungle_bark",
        "minecraft:oak_bark",
        "minecraft:spruce_bark",
        "minecraft:acacia_log",
        "minecraft:birch_log",
        "minecraft:dark_oak_log",
        "minecraft:jungle_log",
        "minecraft:oak_log",
        "minecraft:spruce_log",
        "minecraft:stripped_acacia_log",
        "minecraft:stripped_birch_log",
        "minecraft:stripped_dark_oak_log",
        "minecraft:stripped_jungle_log",
        "minecraft:stripped_oak_log",
        "minecraft:stripped_spruce_log"
    );

    public LeavesFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        Type<?> type2 = opticFinder2.type();
        if (!(type2 instanceof ListType)) {
            throw new IllegalStateException("Expecting sections to be a list.");
        } else {
            Type<?> type3 = ((ListType)type2).getElement();
            OpticFinder<?> opticFinder3 = DSL.typeFinder(type3);
            return this.fixTypeEverywhereTyped(
                "Leaves fix",
                type,
                chunkTyped -> chunkTyped.updateTyped(
                        opticFinder,
                        levelTyped -> {
                            int[] is = new int[]{0};
                            Typed<?> typed = levelTyped.updateTyped(
                                opticFinder2,
                                sectionsTyped -> {
                                    Int2ObjectMap<LeavesFix.LeavesSection> int2ObjectMap = new Int2ObjectOpenHashMap<>(
                                        sectionsTyped.getAllTyped(opticFinder3)
                                            .stream()
                                            .map(sectionTyped -> new LeavesFix.LeavesSection((Typed<?>)sectionTyped, this.getInputSchema()))
                                            .collect(Collectors.toMap(LeavesFix.Section::getIndex, fixer -> (LeavesFix.LeavesSection)fixer))
                                    );
                                    if (int2ObjectMap.values().stream().allMatch(LeavesFix.Section::isSkippable)) {
                                        return sectionsTyped;
                                    } else {
                                        List<IntSet> list = Lists.newArrayList();

                                        for (int i = 0; i < 7; i++) {
                                            list.add(new IntOpenHashSet());
                                        }

                                        for (LeavesFix.LeavesSection leavesSection : int2ObjectMap.values()) {
                                            if (!leavesSection.isSkippable()) {
                                                for (int j = 0; j < 4096; j++) {
                                                    int k = leavesSection.getBlock(j);
                                                    if (leavesSection.isLog(k)) {
                                                        list.get(0).add(leavesSection.getIndex() << 12 | j);
                                                    } else if (leavesSection.isLeaf(k)) {
                                                        int l = this.getX(j);
                                                        int m = this.getZ(j);
                                                        is[0] |= getSideMask(l == 0, l == 15, m == 0, m == 15);
                                                    }
                                                }
                                            }
                                        }

                                        for (int n = 1; n < 7; n++) {
                                            IntSet intSet = list.get(n - 1);
                                            IntSet intSet2 = list.get(n);
                                            IntIterator intIterator = intSet.iterator();

                                            while (intIterator.hasNext()) {
                                                int o = intIterator.nextInt();
                                                int p = this.getX(o);
                                                int q = this.getY(o);
                                                int r = this.getZ(o);

                                                for (int[] js : DIRECTIONS) {
                                                    int s = p + js[0];
                                                    int t = q + js[1];
                                                    int u = r + js[2];
                                                    if (s >= 0 && s <= 15 && u >= 0 && u <= 15 && t >= 0 && t <= 255) {
                                                        LeavesFix.LeavesSection leavesSection2 = int2ObjectMap.get(t >> 4);
                                                        if (leavesSection2 != null && !leavesSection2.isSkippable()) {
                                                            int v = getIndex(s, t & 15, u);
                                                            int w = leavesSection2.getBlock(v);
                                                            if (leavesSection2.isLeaf(w)) {
                                                                int x = leavesSection2.getDistance(w);
                                                                if (x > n) {
                                                                    leavesSection2.setDistance(v, w, n);
                                                                    intSet2.add(getIndex(s, t, u));
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        return sectionsTyped.updateTyped(
                                            opticFinder3,
                                            sectionDynamic -> int2ObjectMap.get(sectionDynamic.get(DSL.remainderFinder()).get("Y").asInt(0))
                                                    .write(sectionDynamic)
                                        );
                                    }
                                }
                            );
                            if (is[0] != 0) {
                                typed = typed.update(
                                    DSL.remainderFinder(),
                                    dynamic -> {
                                        Dynamic<?> dynamic2 = DataFixUtils.orElse(dynamic.get("UpgradeData").result(), dynamic.emptyMap());
                                        return dynamic.set(
                                            "UpgradeData", dynamic2.set("Sides", dynamic.createByte((byte)(dynamic2.get("Sides").asByte((byte)0) | is[0])))
                                        );
                                    }
                                );
                            }

                            return typed;
                        }
                    )
            );
        }
    }

    public static int getIndex(int localX, int localY, int localZ) {
        return localY << 8 | localZ << 4 | localX;
    }

    private int getX(int packedLocalPos) {
        return packedLocalPos & 15;
    }

    private int getY(int packedLocalPos) {
        return packedLocalPos >> 8 & 0xFF;
    }

    private int getZ(int packedLocalPos) {
        return packedLocalPos >> 4 & 15;
    }

    public static int getSideMask(boolean westernmost, boolean easternmost, boolean northernmost, boolean southernmost) {
        int i = 0;
        if (northernmost) {
            if (easternmost) {
                i |= 2;
            } else if (westernmost) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (southernmost) {
            if (westernmost) {
                i |= 32;
            } else if (easternmost) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (easternmost) {
            i |= 4;
        } else if (westernmost) {
            i |= 64;
        }

        return i;
    }

    public static final class LeavesSection extends LeavesFix.Section {
        private static final String PERSISTENT = "persistent";
        private static final String DECAYABLE = "decayable";
        private static final String DISTANCE = "distance";
        @Nullable
        private IntSet leaveIds;
        @Nullable
        private IntSet logIds;
        @Nullable
        private Int2IntMap stateToIdMap;

        public LeavesSection(Typed<?> sectionTyped, Schema inputSchema) {
            super(sectionTyped, inputSchema);
        }

        @Override
        protected boolean skippable() {
            this.leaveIds = new IntOpenHashSet();
            this.logIds = new IntOpenHashSet();
            this.stateToIdMap = new Int2IntOpenHashMap();

            for (int i = 0; i < this.palette.size(); i++) {
                Dynamic<?> dynamic = this.palette.get(i);
                String string = dynamic.get("Name").asString("");
                if (LeavesFix.LEAVES.containsKey(string)) {
                    boolean bl = Objects.equals(dynamic.get("Properties").get("decayable").asString(""), "false");
                    this.leaveIds.add(i);
                    this.stateToIdMap.put(this.getStateId(string, bl, 7), i);
                    this.palette.set(i, this.makeLeafTag(dynamic, string, bl, 7));
                }

                if (LeavesFix.LOGS.contains(string)) {
                    this.logIds.add(i);
                }
            }

            return this.leaveIds.isEmpty() && this.logIds.isEmpty();
        }

        private Dynamic<?> makeLeafTag(Dynamic<?> tag, String name, boolean persistent, int distance) {
            Dynamic<?> dynamic = tag.emptyMap();
            dynamic = dynamic.set("persistent", dynamic.createString(persistent ? "true" : "false"));
            dynamic = dynamic.set("distance", dynamic.createString(Integer.toString(distance)));
            Dynamic<?> dynamic2 = tag.emptyMap();
            dynamic2 = dynamic2.set("Properties", dynamic);
            return dynamic2.set("Name", dynamic2.createString(name));
        }

        public boolean isLog(int index) {
            return this.logIds.contains(index);
        }

        public boolean isLeaf(int index) {
            return this.leaveIds.contains(index);
        }

        int getDistance(int index) {
            return this.isLog(index) ? 0 : Integer.parseInt(this.palette.get(index).get("Properties").get("distance").asString(""));
        }

        void setDistance(int packedLocalPos, int propertyIndex, int distance) {
            Dynamic<?> dynamic = this.palette.get(propertyIndex);
            String string = dynamic.get("Name").asString("");
            boolean bl = Objects.equals(dynamic.get("Properties").get("persistent").asString(""), "true");
            int i = this.getStateId(string, bl, distance);
            if (!this.stateToIdMap.containsKey(i)) {
                int j = this.palette.size();
                this.leaveIds.add(j);
                this.stateToIdMap.put(i, j);
                this.palette.add(this.makeLeafTag(dynamic, string, bl, distance));
            }

            int k = this.stateToIdMap.get(i);
            if (1 << this.storage.getBits() <= k) {
                PackedBitStorage packedBitStorage = new PackedBitStorage(this.storage.getBits() + 1, 4096);

                for (int l = 0; l < 4096; l++) {
                    packedBitStorage.set(l, this.storage.get(l));
                }

                this.storage = packedBitStorage;
            }

            this.storage.set(packedLocalPos, k);
        }
    }

    public abstract static class Section {
        protected static final String BLOCK_STATES_TAG = "BlockStates";
        protected static final String NAME_TAG = "Name";
        protected static final String PROPERTIES_TAG = "Properties";
        private final Type<Pair<String, Dynamic<?>>> blockStateType = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        protected final OpticFinder<List<Pair<String, Dynamic<?>>>> paletteFinder = DSL.fieldFinder("Palette", DSL.list(this.blockStateType));
        protected final List<Dynamic<?>> palette;
        protected final int index;
        @Nullable
        protected PackedBitStorage storage;

        public Section(Typed<?> sectionTyped, Schema inputSchema) {
            if (!Objects.equals(inputSchema.getType(References.BLOCK_STATE), this.blockStateType)) {
                throw new IllegalStateException("Block state type is not what was expected.");
            } else {
                Optional<List<Pair<String, Dynamic<?>>>> optional = sectionTyped.getOptional(this.paletteFinder);
                this.palette = optional.<List>map(palettes -> palettes.stream().map(Pair::getSecond).collect(Collectors.toList())).orElse(ImmutableList.of());
                Dynamic<?> dynamic = sectionTyped.get(DSL.remainderFinder());
                this.index = dynamic.get("Y").asInt(0);
                this.readStorage(dynamic);
            }
        }

        protected void readStorage(Dynamic<?> dynamic) {
            if (this.skippable()) {
                this.storage = null;
            } else {
                long[] ls = dynamic.get("BlockStates").asLongStream().toArray();
                int i = Math.max(4, DataFixUtils.ceillog2(this.palette.size()));
                this.storage = new PackedBitStorage(i, 4096, ls);
            }
        }

        public Typed<?> write(Typed<?> typed) {
            return this.isSkippable()
                ? typed
                : typed.update(DSL.remainderFinder(), remainder -> remainder.set("BlockStates", remainder.createLongList(Arrays.stream(this.storage.getRaw()))))
                    .set(
                        this.paletteFinder,
                        this.palette
                            .stream()
                            .map(propertiesDynamic -> Pair.of(References.BLOCK_STATE.typeName(), propertiesDynamic))
                            .collect(Collectors.toList())
                    );
        }

        public boolean isSkippable() {
            return this.storage == null;
        }

        public int getBlock(int index) {
            return this.storage.get(index);
        }

        protected int getStateId(String leafBlockName, boolean persistent, int distance) {
            return LeavesFix.LEAVES.get(leafBlockName) << 5 | (persistent ? 16 : 0) | distance;
        }

        int getIndex() {
            return this.index;
        }

        protected abstract boolean skippable();
    }
}
