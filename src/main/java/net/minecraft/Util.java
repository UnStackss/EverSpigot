package net.minecraft;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SingleKeyCache;
import net.minecraft.util.TimeSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public class Util {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final ExecutorService BACKGROUND_EXECUTOR = makeExecutor("Main", -1); // Paper - Perf: add priority
    private static final ExecutorService IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final ExecutorService DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    // Paper start - don't submit BLOCKING PROFILE LOOKUPS to the world gen thread
    public static final ExecutorService PROFILE_EXECUTOR = Executors.newFixedThreadPool(2, new java.util.concurrent.ThreadFactory() {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable run) {
            Thread ret = new Thread(run);
            ret.setName("Profile Lookup Executor #" + this.count.getAndIncrement());
            ret.setUncaughtExceptionHandler((Thread thread, Throwable throwable) -> {
                LOGGER.error("Uncaught exception in thread " + thread.getName(), throwable);
            });
            return ret;
        }
    });
    // Paper end - don't submit BLOCKING PROFILE LOOKUPS to the world gen thread
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(fileSystemProvider -> fileSystemProvider.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    public static final double COLLISION_EPSILON = 1.0E-7; // Paper - Check distance in entity interactions
    private static Consumer<String> thePauser = message -> {
    };

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> property, Object value) {
        return property.getName((T)value);
    }

    public static String makeDescriptionId(String type, @Nullable ResourceLocation id) {
        return id == null ? type + ".unregistered_sadface" : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return System.nanoTime(); // Paper
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static ExecutorService makeExecutor(String s, int priorityModifier) { // Paper - Perf: add priority
        // Paper start - Perf: use simpler thread pool that allows 1 thread and reduce worldgen thread worker count for low core count CPUs
        int cpus = Runtime.getRuntime().availableProcessors() / 2;
        int i;
        if (cpus <= 4) {
            i = cpus <= 2 ? 1 : 2;
        } else if (cpus <= 8) {
            // [5, 8]
            i = Math.max(3, cpus - 2);
        } else {
            i = cpus * 2 / 3;
        }
        i = Math.min(8, i);
        i = Integer.getInteger("Paper.WorkerThreadCount", i);
        ExecutorService executorService;
        if (i <= 0) {
            executorService = MoreExecutors.newDirectExecutorService();
        } else {
            executorService = new java.util.concurrent.ThreadPoolExecutor(i, i,0L, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), target -> new io.papermc.paper.util.ServerWorkerThread(target, s, priorityModifier));
        }
        /*
                    @Override
                    protected void onTermination(Throwable throwable) {
                        if (throwable != null) {
                            Util.LOGGER.warn("{} died", this.getName(), throwable);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(throwable);
                    }
                };
                forkJoinWorkerThread.setName("Worker-" + name + "-" + atomicInteger.getAndIncrement());
                return forkJoinWorkerThread;
            }, Util::onThreadException, true);
        }
        }*/ // Paper end - Perf: use simpler thread pool

        return executorService;
    }

    private static int getMaxThreads() {
        String string = System.getProperty("max.bg.threads");
        if (string != null) {
            try {
                int i = Integer.parseInt(string);
                if (i >= 1 && i <= 255) {
                    return i;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", string, 255);
            } catch (NumberFormatException var2) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", string, 255);
            }
        }

        return 255;
    }

    public static ExecutorService backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static ExecutorService ioPool() {
        return IO_POOL;
    }

    public static ExecutorService nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        shutdownExecutor(BACKGROUND_EXECUTOR);
        shutdownExecutor(IO_POOL);
    }

    private static void shutdownExecutor(ExecutorService service) {
        service.shutdown();

        boolean bl;
        try {
            bl = service.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException var3) {
            bl = false;
        }

        if (!bl) {
            service.shutdownNow();
        }
    }

    private static ExecutorService makeIoExecutor(String namePrefix, boolean daemon) {
        AtomicInteger atomicInteger = new AtomicInteger(1);
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(namePrefix + atomicInteger.getAndIncrement());
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        });
    }

    public static void throwAsRuntime(Throwable t) {
        throw t instanceof RuntimeException ? (RuntimeException)t : new RuntimeException(t);
    }

    public static void onThreadException(Thread thread, Throwable t) {
        pauseInIde(t);
        if (t instanceof CompletionException) {
            t = t.getCause();
        }

        if (t instanceof ReportedException reportedException) {
            Bootstrap.realStdoutPrintln(reportedException.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", thread), t);
    }

    @Nullable
    public static Type<?> fetchChoiceType(TypeReference typeReference, String id) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(typeReference, id);
    }

    @Nullable
    private static Type<?> doFetchChoiceType(TypeReference typeReference, String id) {
        Type<?> type = null;

        try {
            type = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))
                .getChoiceType(typeReference, id);
        } catch (IllegalArgumentException var4) {
            LOGGER.error("No data fixer registered for {}", id);
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw var4;
            }
        }

        return type;
    }

    public static Runnable wrapThreadWithTaskName(String activeThreadName, Runnable task) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String string2 = thread.getName();
            thread.setName(activeThreadName);

            try {
                task.run();
            } finally {
                thread.setName(string2);
            }
        } : task;
    }

    public static <V> Supplier<V> wrapThreadWithTaskName(String activeThreadName, Supplier<V> supplier) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String string2 = thread.getName();
            thread.setName(activeThreadName);

            Object var4;
            try {
                var4 = supplier.get();
            } finally {
                thread.setName(string2);
            }

            return (V)var4;
        } : supplier;
    }

    public static <T> String getRegisteredName(Registry<T> registry, T value) {
        ResourceLocation resourceLocation = registry.getKey(value);
        return resourceLocation == null ? "[unregistered]" : resourceLocation.toString();
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> object -> true;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).and((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicates2 = predicates.toArray(Predicate[]::new);
                yield object -> {
                    for (Predicate<T> predicate : predicates2) {
                        if (!predicate.test((T)object)) {
                            return false;
                        }
                    }

                    return true;
                };
            }
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> object -> false;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).or((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicates2 = predicates.toArray(Predicate[]::new);
                yield object -> {
                    for (Predicate<T> predicate : predicates2) {
                        if (predicate.test((T)object)) {
                            return true;
                        }
                    }

                    return false;
                };
            }
        };
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> list) {
        if (width == 1) {
            return true;
        } else {
            int i = width / 2;

            for (int j = 0; j < height; j++) {
                for (int k = 0; k < i; k++) {
                    int l = width - 1 - k;
                    T object = list.get(k + j * width);
                    T object2 = list.get(l + j * width);
                    if (!object.equals(object2)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static Util.OS getPlatform() {
        String string = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (string.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (string.contains("mac")) {
            return Util.OS.OSX;
        } else if (string.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return string.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI uRI = new URI(uri);
        String string = uRI.getScheme();
        if (string == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        } else {
            String string2 = string.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(string2)) {
                throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
            } else {
                return uRI;
            }
        }
    }

    public static Stream<String> getVmArguments() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return runtimeMXBean.getInputArguments().stream().filter(runtimeArg -> runtimeArg.startsWith("-X"));
    }

    public static <T> T lastOf(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static <T> T findNextInIterable(Iterable<T> iterable, @Nullable T object) {
        Iterator<T> iterator = iterable.iterator();
        T object2 = iterator.next();
        if (object != null) {
            T object3 = object2;

            while (object3 != object) {
                if (iterator.hasNext()) {
                    object3 = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return object2;
    }

    public static <T> T findPreviousInIterable(Iterable<T> iterable, @Nullable T object) {
        Iterator<T> iterator = iterable.iterator();
        T object2 = null;

        while (iterator.hasNext()) {
            T object3 = iterator.next();
            if (object3 == object) {
                if (object2 == null) {
                    object2 = iterator.hasNext() ? Iterators.getLast(iterator) : object;
                }
                break;
            }

            object2 = object3;
        }

        return object2;
    }

    public static <T> T make(Supplier<T> factory) {
        return factory.get();
    }

    public static <T> T make(T object, Consumer<? super T> initializer) {
        initializer.accept(object);
        return object;
    }

    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        } else if (futures.size() == 1) {
            return futures.get(0).thenApply(List::of);
        } else {
            CompletableFuture<Void> completableFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return completableFuture.thenApply(void_ -> futures.stream().map(CompletableFuture::join).toList());
        }
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(futures, completableFuture::completeExceptionally).applyToEither(completableFuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> futures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(futures, throwable -> {
            if (completableFuture.completeExceptionally(throwable)) {
                for (CompletableFuture<? extends V> completableFuture2 : futures) {
                    completableFuture2.cancel(true);
                }
            }
        }).applyToEither(completableFuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(List<? extends CompletableFuture<? extends V>> futures, Consumer<Throwable> exceptionHandler) {
        List<V> list = Lists.newArrayListWithCapacity(futures.size());
        CompletableFuture<?>[] completableFutures = new CompletableFuture[futures.size()];
        futures.forEach(future -> {
            int i = list.size();
            list.add(null);
            completableFutures[i] = future.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    exceptionHandler.accept(throwable);
                } else {
                    list.set(i, (V)value);
                }
            });
        });
        return CompletableFuture.allOf(completableFutures).thenApply(void_ -> list);
    }

    public static <T> Optional<T> ifElse(Optional<T> optional, Consumer<T> presentAction, Runnable elseAction) {
        if (optional.isPresent()) {
            presentAction.accept(optional.get());
        } else {
            elseAction.run();
        }

        return optional;
    }

    public static <T> Supplier<T> name(Supplier<T> supplier, Supplier<String> messageSupplier) {
        return supplier;
    }

    public static Runnable name(Runnable runnable, Supplier<String> messageSupplier) {
        return runnable;
    }

    public static void logAndPauseIfInIde(String message) {
        LOGGER.error(message);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }
    }

    public static void logAndPauseIfInIde(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }
    }

    public static <T extends Throwable> T pauseInIde(T t) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", t);
            doPause(t.getMessage());
        }

        return t;
    }

    public static void setPause(Consumer<String> missingBreakpointHandler) {
        thePauser = missingBreakpointHandler;
    }

    private static void doPause(String message) {
        Instant instant = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean bl = Duration.between(instant, Instant.now()).toMillis() > 500L;
        if (!bl) {
            thePauser.accept(message);
        }
    }

    public static String describeError(Throwable t) {
        if (t.getCause() != null) {
            return describeError(t.getCause());
        } else {
            return t.getMessage() != null ? t.getMessage() : t.toString();
        }
    }

    public static <T> T getRandom(T[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static int getRandom(int[] array, RandomSource random) {
        return array[random.nextInt(array.length)];
    }

    public static <T> T getRandom(List<T> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> list, RandomSource random) {
        return list.isEmpty() ? Optional.empty() : Optional.of(getRandom(list, random));
    }

    private static BooleanSupplier createRenamer(Path src, Path dest) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(src, dest);
                    return true;
                } catch (IOException var2) {
                    Util.LOGGER.error("Failed to rename", (Throwable)var2);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + src + " to " + dest;
            }
        };
    }

    private static BooleanSupplier createDeleter(Path path) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(path);
                    return true;
                } catch (IOException var2) {
                    Util.LOGGER.warn("Failed to delete", (Throwable)var2);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + path;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(Path path) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(path);
            }

            @Override
            public String toString() {
                return "verify that " + path + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(Path path) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(path);
            }

            @Override
            public String toString() {
                return "verify that " + path + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... tasks) {
        for (BooleanSupplier booleanSupplier : tasks) {
            if (!booleanSupplier.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", booleanSupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int retries, String taskName, BooleanSupplier... tasks) {
        for (int i = 0; i < retries; i++) {
            if (executeInSequence(tasks)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", taskName, i, retries);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", taskName);
        return false;
    }

    public static void safeReplaceFile(Path current, Path newPath, Path backup) {
        safeReplaceOrMoveFile(current, newPath, backup, false);
    }

    public static boolean safeReplaceOrMoveFile(Path current, Path newPath, Path backup, boolean noRestoreOnFail) {
        if (Files.exists(current)
            && !runWithRetries(10, "create backup " + backup, createDeleter(backup), createRenamer(current, backup), createFileCreatedCheck(backup))) {
            return false;
        } else if (!runWithRetries(10, "remove old " + current, createDeleter(current), createFileDeletedCheck(current))) {
            return false;
        } else if (!runWithRetries(10, "replace " + current + " with " + newPath, createRenamer(newPath, current), createFileCreatedCheck(current))
            && !noRestoreOnFail) {
            runWithRetries(10, "restore " + current + " from " + backup, createRenamer(backup, current), createFileCreatedCheck(current));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String string, int cursor, int delta) {
        int i = string.length();
        if (delta >= 0) {
            for (int j = 0; cursor < i && j < delta; j++) {
                if (Character.isHighSurrogate(string.charAt(cursor++)) && cursor < i && Character.isLowSurrogate(string.charAt(cursor))) {
                    cursor++;
                }
            }
        } else {
            for (int k = delta; cursor > 0 && k < 0; k++) {
                cursor--;
                if (Character.isLowSurrogate(string.charAt(cursor)) && cursor > 0 && Character.isHighSurrogate(string.charAt(cursor - 1))) {
                    cursor--;
                }
            }
        }

        return cursor;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> consumer) {
        return string -> consumer.accept(prefix + string);
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int length) {
        int[] is = stream.limit((long)(length + 1)).toArray();
        if (is.length != length) {
            Supplier<String> supplier = () -> "Input is not a list of " + length + " ints";
            return is.length >= length ? DataResult.error(supplier, Arrays.copyOf(is, length)) : DataResult.error(supplier);
        } else {
            return DataResult.success(is);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int length) {
        long[] ls = stream.limit((long)(length + 1)).toArray();
        if (ls.length != length) {
            Supplier<String> supplier = () -> "Input is not a list of " + length + " longs";
            return ls.length >= length ? DataResult.error(supplier, Arrays.copyOf(ls, length)) : DataResult.error(supplier);
        } else {
            return DataResult.success(ls);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int length) {
        if (list.size() != length) {
            Supplier<String> supplier = () -> "Input is not a list of " + length + " elements";
            return list.size() >= length ? DataResult.error(supplier, list.subList(0, length)) : DataResult.error(supplier);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException var2) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path src, Path dest, Path toCopy) throws IOException {
        Path path = src.relativize(toCopy);
        Path path2 = dest.resolve(path);
        Files.copy(toCopy, path2);
    }

    public static String sanitizeName(String string, CharPredicate predicate) {
        return string.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(charCode -> predicate.test((char)charCode) ? Character.toString((char)charCode) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> mapper) {
        return new SingleKeyCache<>(mapper);
    }

    public static <T, R> Function<T, R> memoize(Function<T, R> function) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T object) {
                return this.cache.computeIfAbsent(object, function);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + function + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(BiFunction<T, U, R> biFunction) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T object, U object2) {
                return this.cache.computeIfAbsent(Pair.of(object, object2), pair -> biFunction.apply(pair.getFirst(), pair.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + biFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> objectArrayList = stream.collect(ObjectArrayList.toList());
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList intArrayList = IntArrayList.wrap(stream.toArray());
        int i = intArrayList.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            intArrayList.set(j - 1, intArrayList.set(k, intArrayList.getInt(j - 1)));
        }

        return intArrayList;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> objectArrayList = new ObjectArrayList<>(array);
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> objectArrayList = new ObjectArrayList<>(list);
        shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int i = list.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            list.set(j - 1, list.set(k, list.get(j - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> resultFactory) {
        return blockUntilDone(resultFactory, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> resultFactory, Predicate<T> donePredicate) {
        BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();
        T object = resultFactory.apply(blockingQueue::add);

        while (!donePredicate.test(object)) {
            try {
                Runnable runnable = blockingQueue.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException var5) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int i = blockingQueue.size();
        if (i > 0) {
            LOGGER.warn("Tasks left in queue: {}", i);
        }

        return object;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> values) {
        int i = values.size();
        if (i < 8) {
            return values::indexOf;
        } else {
            Object2IntMap<T> object2IntMap = new Object2IntOpenHashMap<>(i);
            object2IntMap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                object2IntMap.put(values.get(j), j);
            }

            return object2IntMap;
        }
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> values) {
        int i = values.size();
        if (i < 8) {
            ReferenceList<T> referenceList = new ReferenceImmutableList<>(values);
            return referenceList::indexOf;
        } else {
            Reference2IntMap<T> reference2IntMap = new Reference2IntOpenHashMap<>(i);
            reference2IntMap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                reference2IntMap.put(values.get(j), j);
            }

            return reference2IntMap;
        }
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> type, UnaryOperator<Dynamic<?>> modifier) {
        Dynamic<?> dynamic = (Dynamic<?>)typed.write().getOrThrow();
        return readTypedOrThrow(type, modifier.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> value) {
        return readTypedOrThrow(type, value, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> value, boolean allowPartial) {
        DataResult<Typed<T>> dataResult = type.readTyped(value).map(Pair::getFirst);

        try {
            return allowPartial ? dataResult.getPartialOrThrow(IllegalStateException::new) : dataResult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Reading type");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Info");
            crashReportCategory.setDetail("Data", value);
            crashReportCategory.setDetail("Type", type);
            throw new ReportedException(crashReport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T valueToAppend) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(valueToAppend).build();
    }

    public static <T> List<T> copyAndAdd(T valueToPrepend, List<T> list) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(valueToPrepend).addAll(list).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K keyToAppend, V valueToAppend) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(keyToAppend, valueToAppend).buildKeepingLast();
    }

    public static enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"open", uri.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(final String name) {
            this.telemetryName = name;
        }

        public void openUri(URI uri) {
            throw new IllegalStateException("This method is not useful on dedicated servers."); // Paper - Fix warnings on build by removing client-only code
        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String string = uri.toString();
            if ("file".equals(uri.getScheme())) {
                string = string.replace("file:", "file://");
            }

            return new String[]{"xdg-open", string};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (IllegalArgumentException | URISyntaxException var3) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, var3);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
