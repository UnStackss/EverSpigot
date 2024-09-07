package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.DoubleStream;
import net.minecraft.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {
    private static final String BYTES_PER_SECOND = "bytesPerSecond";
    private static final String COUNT = "count";
    private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
    private static final String TOTAL_BYTES = "totalBytes";
    private static final String COUNT_PER_SECOND = "countPerSecond";
    final Gson gson = new GsonBuilder().setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();

    private static void serializePacketId(PacketIdentification packet, JsonObject json) {
        json.addProperty("protocolId", packet.protocolId());
        json.addProperty("packetId", packet.packetId());
    }

    private static void serializeChunkId(ChunkIdentification chunk, JsonObject json) {
        json.addProperty("level", chunk.level());
        json.addProperty("dimension", chunk.dimension());
        json.addProperty("x", chunk.x());
        json.addProperty("z", chunk.z());
    }

    public String format(JfrStatsResult profile) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("startedEpoch", profile.recordingStarted().toEpochMilli());
        jsonObject.addProperty("endedEpoch", profile.recordingEnded().toEpochMilli());
        jsonObject.addProperty("durationMs", profile.recordingDuration().toMillis());
        Duration duration = profile.worldCreationDuration();
        if (duration != null) {
            jsonObject.addProperty("worldGenDurationMs", duration.toMillis());
        }

        jsonObject.add("heap", this.heap(profile.heapSummary()));
        jsonObject.add("cpuPercent", this.cpu(profile.cpuLoadStats()));
        jsonObject.add("network", this.network(profile));
        jsonObject.add("fileIO", this.fileIO(profile));
        jsonObject.add("serverTick", this.serverTicks(profile.tickTimes()));
        jsonObject.add("threadAllocation", this.threadAllocations(profile.threadAllocationSummary()));
        jsonObject.add("chunkGen", this.chunkGen(profile.chunkGenSummary()));
        return this.gson.toJson((JsonElement)jsonObject);
    }

    private JsonElement heap(GcHeapStat.Summary statistics) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("allocationRateBytesPerSecond", statistics.allocationRateBytesPerSecond());
        jsonObject.addProperty("gcCount", statistics.totalGCs());
        jsonObject.addProperty("gcOverHeadPercent", statistics.gcOverHead());
        jsonObject.addProperty("gcTotalDurationMs", statistics.gcTotalDuration().toMillis());
        return jsonObject;
    }

    private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> statistics) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(
            "durationNanosTotal", statistics.stream().mapToDouble(pairx -> (double)((TimedStatSummary)pairx.getSecond()).totalDuration().toNanos()).sum()
        );
        JsonArray jsonArray = Util.make(new JsonArray(), json -> jsonObject.add("status", json));

        for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : statistics) {
            TimedStatSummary<ChunkGenStat> timedStatSummary = pair.getSecond();
            JsonObject jsonObject2 = Util.make(new JsonObject(), jsonArray::add);
            jsonObject2.addProperty("state", pair.getFirst().toString());
            jsonObject2.addProperty("count", timedStatSummary.count());
            jsonObject2.addProperty("durationNanosTotal", timedStatSummary.totalDuration().toNanos());
            jsonObject2.addProperty("durationNanosAvg", timedStatSummary.totalDuration().toNanos() / (long)timedStatSummary.count());
            JsonObject jsonObject3 = Util.make(new JsonObject(), json -> jsonObject2.add("durationNanosPercentiles", json));
            timedStatSummary.percentilesNanos().forEach((quantile, value) -> jsonObject3.addProperty("p" + quantile, value));
            Function<ChunkGenStat, JsonElement> function = sample -> {
                JsonObject jsonObjectx = new JsonObject();
                jsonObjectx.addProperty("durationNanos", sample.duration().toNanos());
                jsonObjectx.addProperty("level", sample.level());
                jsonObjectx.addProperty("chunkPosX", sample.chunkPos().x);
                jsonObjectx.addProperty("chunkPosZ", sample.chunkPos().z);
                jsonObjectx.addProperty("worldPosX", sample.worldPos().x());
                jsonObjectx.addProperty("worldPosZ", sample.worldPos().z());
                return jsonObjectx;
            };
            jsonObject2.add("fastest", function.apply(timedStatSummary.fastest()));
            jsonObject2.add("slowest", function.apply(timedStatSummary.slowest()));
            jsonObject2.add(
                "secondSlowest", (JsonElement)(timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE)
            );
        }

        return jsonObject;
    }

    private JsonElement threadAllocations(ThreadAllocationStat.Summary statistics) {
        JsonArray jsonArray = new JsonArray();
        statistics.allocationsPerSecondByThread().forEach((threadName, allocation) -> jsonArray.add(Util.make(new JsonObject(), json -> {
                json.addProperty("thread", threadName);
                json.addProperty("bytesPerSecond", allocation);
            })));
        return jsonArray;
    }

    private JsonElement serverTicks(List<TickTimeStat> samples) {
        if (samples.isEmpty()) {
            return JsonNull.INSTANCE;
        } else {
            JsonObject jsonObject = new JsonObject();
            double[] ds = samples.stream().mapToDouble(sample -> (double)sample.currentAverage().toNanos() / 1000000.0).toArray();
            DoubleSummaryStatistics doubleSummaryStatistics = DoubleStream.of(ds).summaryStatistics();
            jsonObject.addProperty("minMs", doubleSummaryStatistics.getMin());
            jsonObject.addProperty("averageMs", doubleSummaryStatistics.getAverage());
            jsonObject.addProperty("maxMs", doubleSummaryStatistics.getMax());
            Map<Integer, Double> map = Percentiles.evaluate(ds);
            map.forEach((quantile, value) -> jsonObject.addProperty("p" + quantile, value));
            return jsonObject;
        }
    }

    private JsonElement fileIO(JfrStatsResult profile) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("write", this.fileIoSummary(profile.fileWrites()));
        jsonObject.add("read", this.fileIoSummary(profile.fileReads()));
        jsonObject.add("chunksRead", this.ioSummary(profile.readChunks(), JfrResultJsonSerializer::serializeChunkId));
        jsonObject.add("chunksWritten", this.ioSummary(profile.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
        return jsonObject;
    }

    private JsonElement fileIoSummary(FileIOStat.Summary statistics) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", statistics.totalBytes());
        jsonObject.addProperty("count", statistics.counts());
        jsonObject.addProperty("bytesPerSecond", statistics.bytesPerSecond());
        jsonObject.addProperty("countPerSecond", statistics.countsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        statistics.topTenContributorsByTotalBytes().forEach(pair -> {
            JsonObject jsonObjectx = new JsonObject();
            jsonArray.add(jsonObjectx);
            jsonObjectx.addProperty("path", pair.getFirst());
            jsonObjectx.addProperty("totalBytes", pair.getSecond());
        });
        return jsonObject;
    }

    private JsonElement network(JfrStatsResult profile) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("sent", this.ioSummary(profile.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        jsonObject.add("received", this.ioSummary(profile.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        return jsonObject;
    }

    private <T> JsonElement ioSummary(IoSummary<T> statistics, BiConsumer<T, JsonObject> callback) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", statistics.getTotalSize());
        jsonObject.addProperty("count", statistics.getTotalCount());
        jsonObject.addProperty("bytesPerSecond", statistics.getSizePerSecond());
        jsonObject.addProperty("countPerSecond", statistics.getCountsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        statistics.largestSizeContributors().forEach(topContributor -> {
            JsonObject jsonObjectx = new JsonObject();
            jsonArray.add(jsonObjectx);
            T object = topContributor.getFirst();
            IoSummary.CountAndSize countAndSize = topContributor.getSecond();
            callback.accept(object, jsonObjectx);
            jsonObjectx.addProperty("totalBytes", countAndSize.totalSize());
            jsonObjectx.addProperty("count", countAndSize.totalCount());
            jsonObjectx.addProperty("averageSize", countAndSize.averageSize());
        });
        return jsonObject;
    }

    private JsonElement cpu(List<CpuLoadStat> samples) {
        JsonObject jsonObject = new JsonObject();
        BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> biFunction = (samplesx, valueGetter) -> {
            JsonObject jsonObjectx = new JsonObject();
            DoubleSummaryStatistics doubleSummaryStatistics = samplesx.stream().mapToDouble(valueGetter).summaryStatistics();
            jsonObjectx.addProperty("min", doubleSummaryStatistics.getMin());
            jsonObjectx.addProperty("average", doubleSummaryStatistics.getAverage());
            jsonObjectx.addProperty("max", doubleSummaryStatistics.getMax());
            return jsonObjectx;
        };
        jsonObject.add("jvm", biFunction.apply(samples, CpuLoadStat::jvm));
        jsonObject.add("userJvm", biFunction.apply(samples, CpuLoadStat::userJvm));
        jsonObject.add("system", biFunction.apply(samples, CpuLoadStat::system));
        return jsonObject;
    }
}
