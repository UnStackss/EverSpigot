package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Graph;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;

public class FeatureSorter {
    public static <T> List<FeatureSorter.StepFeatureData> buildFeaturesPerStep(
        List<T> biomes, Function<T, List<HolderSet<PlacedFeature>>> biomesToPlacedFeaturesList, boolean listInvolvedBiomesOnFailure
    ) {
        Object2IntMap<PlacedFeature> object2IntMap = new Object2IntOpenHashMap<>();
        MutableInt mutableInt = new MutableInt(0);

        record FeatureData(int featureIndex, int step, PlacedFeature feature) {
        }

        Comparator<FeatureData> comparator = Comparator.comparingInt(FeatureData::step).thenComparingInt(FeatureData::featureIndex);
        Map<FeatureData, Set<FeatureData>> map = new TreeMap<>(comparator);
        int i = 0;

        for (T object : biomes) {
            List<FeatureData> list = Lists.newArrayList();
            List<HolderSet<PlacedFeature>> list2 = biomesToPlacedFeaturesList.apply(object);
            i = Math.max(i, list2.size());

            for (int j = 0; j < list2.size(); j++) {
                for (Holder<PlacedFeature> holder : list2.get(j)) {
                    PlacedFeature placedFeature = holder.value();
                    list.add(new FeatureData(object2IntMap.computeIfAbsent(placedFeature, feature -> mutableInt.getAndIncrement()), j, placedFeature));
                }
            }

            for (int k = 0; k < list.size(); k++) {
                Set<FeatureData> set = map.computeIfAbsent(list.get(k), feature -> new TreeSet<>(comparator));
                if (k < list.size() - 1) {
                    set.add(list.get(k + 1));
                }
            }
        }

        Set<FeatureData> set2 = new TreeSet<>(comparator);
        Set<FeatureData> set3 = new TreeSet<>(comparator);
        List<FeatureData> list3 = Lists.newArrayList();

        for (FeatureData lv : map.keySet()) {
            if (!set3.isEmpty()) {
                throw new IllegalStateException("You somehow broke the universe; DFS bork (iteration finished with non-empty in-progress vertex set");
            }

            if (!set2.contains(lv) && Graph.depthFirstSearch(map, set2, set3, list3::add, lv)) {
                if (!listInvolvedBiomesOnFailure) {
                    throw new IllegalStateException("Feature order cycle found");
                }

                List<T> list4 = new ArrayList<>(biomes);

                int l;
                do {
                    l = list4.size();
                    ListIterator<T> listIterator = list4.listIterator();

                    while (listIterator.hasNext()) {
                        T object2 = listIterator.next();
                        listIterator.remove();

                        try {
                            buildFeaturesPerStep(list4, biomesToPlacedFeaturesList, false);
                        } catch (IllegalStateException var18) {
                            continue;
                        }

                        listIterator.add(object2);
                    }
                } while (l != list4.size());

                throw new IllegalStateException("Feature order cycle found, involved sources: " + list4);
            }
        }

        Collections.reverse(list3);
        Builder<FeatureSorter.StepFeatureData> builder = ImmutableList.builder();

        for (int m = 0; m < i; m++) {
            int n = m;
            List<PlacedFeature> list5 = list3.stream().filter(feature -> feature.step() == n).map(FeatureData::feature).collect(Collectors.toList());
            builder.add(new FeatureSorter.StepFeatureData(list5));
        }

        return builder.build();
    }

    public static record StepFeatureData(List<PlacedFeature> features, ToIntFunction<PlacedFeature> indexMapping) {
        StepFeatureData(List<PlacedFeature> features) {
            this(features, Util.createIndexIdentityLookup(features));
        }
    }
}
