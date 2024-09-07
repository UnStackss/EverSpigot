package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Stopwatch total = Stopwatch.createUnstarted();

    public ProfiledReloadInstance(
        ResourceManager manager,
        List<PreparableReloadListener> reloaders,
        Executor prepareExecutor,
        Executor applyExecutor,
        CompletableFuture<Unit> initialStage
    ) {
        super(
            prepareExecutor,
            applyExecutor,
            manager,
            reloaders,
            (synchronizer, resourceManager, reloader, prepare, apply) -> {
                AtomicLong atomicLong = new AtomicLong();
                AtomicLong atomicLong2 = new AtomicLong();
                ActiveProfiler activeProfiler = new ActiveProfiler(Util.timeSource, () -> 0, false);
                ActiveProfiler activeProfiler2 = new ActiveProfiler(Util.timeSource, () -> 0, false);
                CompletableFuture<Void> completableFuture = reloader.reload(
                    synchronizer, resourceManager, activeProfiler, activeProfiler2, preparation -> prepare.execute(() -> {
                            long l = Util.getNanos();
                            preparation.run();
                            atomicLong.addAndGet(Util.getNanos() - l);
                        }), application -> apply.execute(() -> {
                            long l = Util.getNanos();
                            application.run();
                            atomicLong2.addAndGet(Util.getNanos() - l);
                        })
                );
                return completableFuture.thenApplyAsync(
                    dummy -> {
                        LOGGER.debug("Finished reloading " + reloader.getName());
                        return new ProfiledReloadInstance.State(
                            reloader.getName(), activeProfiler.getResults(), activeProfiler2.getResults(), atomicLong, atomicLong2
                        );
                    },
                    applyExecutor
                );
            },
            initialStage
        );
        this.total.start();
        this.allDone = this.allDone.thenApplyAsync(this::finish, applyExecutor);
    }

    private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> summaries) {
        this.total.stop();
        long l = 0L;
        LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

        for (ProfiledReloadInstance.State state : summaries) {
            ProfileResults profileResults = state.preparationResult;
            ProfileResults profileResults2 = state.reloadResult;
            long m = TimeUnit.NANOSECONDS.toMillis(state.preparationNanos.get());
            long n = TimeUnit.NANOSECONDS.toMillis(state.reloadNanos.get());
            long o = m + n;
            String string = state.name;
            LOGGER.info("{} took approximately {} ms ({} ms preparing, {} ms applying)", string, o, m, n);
            l += n;
        }

        LOGGER.info("Total blocking time: {} ms", l);
        return summaries;
    }

    public static class State {
        final String name;
        final ProfileResults preparationResult;
        final ProfileResults reloadResult;
        final AtomicLong preparationNanos;
        final AtomicLong reloadNanos;

        State(String name, ProfileResults prepareProfile, ProfileResults applyProfile, AtomicLong prepareTimeMs, AtomicLong applyTimeMs) {
            this.name = name;
            this.preparationResult = prepareProfile;
            this.reloadResult = applyProfile;
            this.preparationNanos = prepareTimeMs;
            this.reloadNanos = applyTimeMs;
        }
    }
}
