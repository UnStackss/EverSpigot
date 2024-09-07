package net.minecraft.server.network;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.thread.ProcessorMailbox;
import org.slf4j.Logger;

public class TextFilterClient implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Chat-Filter-Worker-" + WORKER_COUNT.getAndIncrement());
        return thread;
    };
    private static final String DEFAULT_ENDPOINT = "v1/chat";
    private final URL chatEndpoint;
    private final TextFilterClient.MessageEncoder chatEncoder;
    final URL joinEndpoint;
    final TextFilterClient.JoinOrLeaveEncoder joinEncoder;
    final URL leaveEndpoint;
    final TextFilterClient.JoinOrLeaveEncoder leaveEncoder;
    private final String authKey;
    final TextFilterClient.IgnoreStrategy chatIgnoreStrategy;
    final ExecutorService workerPool;

    private TextFilterClient(
        URL chatEndpoint,
        TextFilterClient.MessageEncoder messageEncoder,
        URL joinEndpoint,
        TextFilterClient.JoinOrLeaveEncoder joinEncoder,
        URL leaveEndpoint,
        TextFilterClient.JoinOrLeaveEncoder leaveEncoder,
        String apiKey,
        TextFilterClient.IgnoreStrategy ignorer,
        int parallelism
    ) {
        this.authKey = apiKey;
        this.chatIgnoreStrategy = ignorer;
        this.chatEndpoint = chatEndpoint;
        this.chatEncoder = messageEncoder;
        this.joinEndpoint = joinEndpoint;
        this.joinEncoder = joinEncoder;
        this.leaveEndpoint = leaveEndpoint;
        this.leaveEncoder = leaveEncoder;
        this.workerPool = Executors.newFixedThreadPool(parallelism, THREAD_FACTORY);
    }

    private static URL getEndpoint(URI root, @Nullable JsonObject endpoints, String key, String fallback) throws MalformedURLException {
        String string = getEndpointFromConfig(endpoints, key, fallback);
        return root.resolve("/" + string).toURL();
    }

    private static String getEndpointFromConfig(@Nullable JsonObject json, String key, String fallback) {
        return json != null ? GsonHelper.getAsString(json, key, fallback) : fallback;
    }

    @Nullable
    public static TextFilterClient createFromConfig(String config) {
        if (Strings.isNullOrEmpty(config)) {
            return null;
        } else {
            try {
                JsonObject jsonObject = GsonHelper.parse(config);
                URI uRI = new URI(GsonHelper.getAsString(jsonObject, "apiServer"));
                String string = GsonHelper.getAsString(jsonObject, "apiKey");
                if (string.isEmpty()) {
                    throw new IllegalArgumentException("Missing API key");
                } else {
                    int i = GsonHelper.getAsInt(jsonObject, "ruleId", 1);
                    String string2 = GsonHelper.getAsString(jsonObject, "serverId", "");
                    String string3 = GsonHelper.getAsString(jsonObject, "roomId", "Java:Chat");
                    int j = GsonHelper.getAsInt(jsonObject, "hashesToDrop", -1);
                    int k = GsonHelper.getAsInt(jsonObject, "maxConcurrentRequests", 7);
                    JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "endpoints", null);
                    String string4 = getEndpointFromConfig(jsonObject2, "chat", "v1/chat");
                    boolean bl = string4.equals("v1/chat");
                    URL uRL = uRI.resolve("/" + string4).toURL();
                    URL uRL2 = getEndpoint(uRI, jsonObject2, "join", "v1/join");
                    URL uRL3 = getEndpoint(uRI, jsonObject2, "leave", "v1/leave");
                    TextFilterClient.JoinOrLeaveEncoder joinOrLeaveEncoder = profile -> {
                        JsonObject jsonObjectx = new JsonObject();
                        jsonObjectx.addProperty("server", string2);
                        jsonObjectx.addProperty("room", string3);
                        jsonObjectx.addProperty("user_id", profile.getId().toString());
                        jsonObjectx.addProperty("user_display_name", profile.getName());
                        return jsonObjectx;
                    };
                    TextFilterClient.MessageEncoder messageEncoder;
                    if (bl) {
                        messageEncoder = (profile, message) -> {
                            JsonObject jsonObjectx = new JsonObject();
                            jsonObjectx.addProperty("rule", i);
                            jsonObjectx.addProperty("server", string2);
                            jsonObjectx.addProperty("room", string3);
                            jsonObjectx.addProperty("player", profile.getId().toString());
                            jsonObjectx.addProperty("player_display_name", profile.getName());
                            jsonObjectx.addProperty("text", message);
                            jsonObjectx.addProperty("language", "*");
                            return jsonObjectx;
                        };
                    } else {
                        String string5 = String.valueOf(i);
                        messageEncoder = (profile, message) -> {
                            JsonObject jsonObjectx = new JsonObject();
                            jsonObjectx.addProperty("rule_id", string5);
                            jsonObjectx.addProperty("category", string2);
                            jsonObjectx.addProperty("subcategory", string3);
                            jsonObjectx.addProperty("user_id", profile.getId().toString());
                            jsonObjectx.addProperty("user_display_name", profile.getName());
                            jsonObjectx.addProperty("text", message);
                            jsonObjectx.addProperty("language", "*");
                            return jsonObjectx;
                        };
                    }

                    TextFilterClient.IgnoreStrategy ignoreStrategy = TextFilterClient.IgnoreStrategy.select(j);
                    String string6 = Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.US_ASCII));
                    return new TextFilterClient(uRL, messageEncoder, uRL2, joinOrLeaveEncoder, uRL3, joinOrLeaveEncoder, string6, ignoreStrategy, k);
                }
            } catch (Exception var19) {
                LOGGER.warn("Failed to parse chat filter config {}", config, var19);
                return null;
            }
        }
    }

    void processJoinOrLeave(GameProfile gameProfile, URL endpoint, TextFilterClient.JoinOrLeaveEncoder profileEncoder, Executor executor) {
        executor.execute(() -> {
            JsonObject jsonObject = profileEncoder.encode(gameProfile);

            try {
                this.processRequest(jsonObject, endpoint);
            } catch (Exception var6) {
                LOGGER.warn("Failed to send join/leave packet to {} for player {}", endpoint, gameProfile, var6);
            }
        });
    }

    CompletableFuture<FilteredText> requestMessageProcessing(
        GameProfile gameProfile, String message, TextFilterClient.IgnoreStrategy ignorer, Executor executor
    ) {
        return message.isEmpty() ? CompletableFuture.completedFuture(FilteredText.EMPTY) : CompletableFuture.supplyAsync(() -> {
            JsonObject jsonObject = this.chatEncoder.encode(gameProfile, message);

            try {
                JsonObject jsonObject2 = this.processRequestResponse(jsonObject, this.chatEndpoint);
                boolean bl = GsonHelper.getAsBoolean(jsonObject2, "response", false);
                if (bl) {
                    return FilteredText.passThrough(message);
                } else {
                    String string2 = GsonHelper.getAsString(jsonObject2, "hashed", null);
                    if (string2 == null) {
                        return FilteredText.fullyFiltered(message);
                    } else {
                        JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject2, "hashes");
                        FilterMask filterMask = this.parseMask(message, jsonArray, ignorer);
                        return new FilteredText(message, filterMask);
                    }
                }
            } catch (Exception var10) {
                LOGGER.warn("Failed to validate message '{}'", message, var10);
                return FilteredText.fullyFiltered(message);
            }
        }, executor);
    }

    private FilterMask parseMask(String message, JsonArray mask, TextFilterClient.IgnoreStrategy ignorer) {
        if (mask.isEmpty()) {
            return FilterMask.PASS_THROUGH;
        } else if (ignorer.shouldIgnore(message, mask.size())) {
            return FilterMask.FULLY_FILTERED;
        } else {
            FilterMask filterMask = new FilterMask(message.length());

            for (int i = 0; i < mask.size(); i++) {
                filterMask.setFiltered(mask.get(i).getAsInt());
            }

            return filterMask;
        }
    }

    @Override
    public void close() {
        this.workerPool.shutdownNow();
    }

    private void drainStream(InputStream inputStream) throws IOException {
        byte[] bs = new byte[1024];

        while (inputStream.read(bs) != -1) {
        }
    }

    private JsonObject processRequestResponse(JsonObject payload, URL endpoint) throws IOException {
        HttpURLConnection httpURLConnection = this.makeRequest(payload, endpoint);

        JsonObject var5;
        try (InputStream inputStream = httpURLConnection.getInputStream()) {
            if (httpURLConnection.getResponseCode() == 204) {
                return new JsonObject();
            }

            try {
                var5 = Streams.parse(new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))).getAsJsonObject();
            } finally {
                this.drainStream(inputStream);
            }
        }

        return var5;
    }

    private void processRequest(JsonObject payload, URL endpoint) throws IOException {
        HttpURLConnection httpURLConnection = this.makeRequest(payload, endpoint);

        try (InputStream inputStream = httpURLConnection.getInputStream()) {
            this.drainStream(inputStream);
        }
    }

    private HttpURLConnection makeRequest(JsonObject payload, URL endpoint) throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection)endpoint.openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(2000);
        httpURLConnection.setUseCaches(false);
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setDoInput(true);
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpURLConnection.setRequestProperty("Accept", "application/json");
        httpURLConnection.setRequestProperty("Authorization", "Basic " + this.authKey);
        httpURLConnection.setRequestProperty("User-Agent", "Minecraft server" + SharedConstants.getCurrentVersion().getName());
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream(), StandardCharsets.UTF_8);

        try (JsonWriter jsonWriter = new JsonWriter(outputStreamWriter)) {
            Streams.write(payload, jsonWriter);
        } catch (Throwable var11) {
            try {
                outputStreamWriter.close();
            } catch (Throwable var8) {
                var11.addSuppressed(var8);
            }

            throw var11;
        }

        outputStreamWriter.close();
        int i = httpURLConnection.getResponseCode();
        if (i >= 200 && i < 300) {
            return httpURLConnection;
        } else {
            throw new TextFilterClient.RequestFailedException(i + " " + httpURLConnection.getResponseMessage());
        }
    }

    public TextFilter createContext(GameProfile gameProfile) {
        return new TextFilterClient.PlayerContext(gameProfile);
    }

    @FunctionalInterface
    public interface IgnoreStrategy {
        TextFilterClient.IgnoreStrategy NEVER_IGNORE = (hashes, hashesSize) -> false;
        TextFilterClient.IgnoreStrategy IGNORE_FULLY_FILTERED = (hashes, hashesSize) -> hashes.length() == hashesSize;

        static TextFilterClient.IgnoreStrategy ignoreOverThreshold(int hashesToDrop) {
            return (hashes, hashesSize) -> hashesSize >= hashesToDrop;
        }

        static TextFilterClient.IgnoreStrategy select(int hashesToDrop) {
            return switch (hashesToDrop) {
                case -1 -> NEVER_IGNORE;
                case 0 -> IGNORE_FULLY_FILTERED;
                default -> ignoreOverThreshold(hashesToDrop);
            };
        }

        boolean shouldIgnore(String hashes, int hashesSize);
    }

    @FunctionalInterface
    interface JoinOrLeaveEncoder {
        JsonObject encode(GameProfile gameProfile);
    }

    @FunctionalInterface
    interface MessageEncoder {
        JsonObject encode(GameProfile gameProfile, String message);
    }

    class PlayerContext implements TextFilter {
        private final GameProfile profile;
        private final Executor streamExecutor;

        PlayerContext(final GameProfile gameProfile) {
            this.profile = gameProfile;
            ProcessorMailbox<Runnable> processorMailbox = ProcessorMailbox.create(TextFilterClient.this.workerPool, "chat stream for " + gameProfile.getName());
            this.streamExecutor = processorMailbox::tell;
        }

        @Override
        public void join() {
            TextFilterClient.this.processJoinOrLeave(this.profile, TextFilterClient.this.joinEndpoint, TextFilterClient.this.joinEncoder, this.streamExecutor);
        }

        @Override
        public void leave() {
            TextFilterClient.this.processJoinOrLeave(this.profile, TextFilterClient.this.leaveEndpoint, TextFilterClient.this.leaveEncoder, this.streamExecutor);
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts) {
            List<CompletableFuture<FilteredText>> list = texts.stream()
                .map(text -> TextFilterClient.this.requestMessageProcessing(this.profile, text, TextFilterClient.this.chatIgnoreStrategy, this.streamExecutor))
                .collect(ImmutableList.toImmutableList());
            return Util.sequenceFailFast(list).exceptionally(throwable -> ImmutableList.of());
        }

        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String text) {
            return TextFilterClient.this.requestMessageProcessing(this.profile, text, TextFilterClient.this.chatIgnoreStrategy, this.streamExecutor);
        }
    }

    public static class RequestFailedException extends RuntimeException {
        RequestFailedException(String message) {
            super(message);
        }
    }
}
